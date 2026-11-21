#!/usr/bin/env bash
# 在服务器上装配「匿名使用统计」接收端（幂等，可重复跑）。
#
# 用法（本机 WSL，免密已配）：
#   scp -P 25894 server/telemetry/server.py server/telemetry/schema.json server/telemetry/deploy.sh \
#       root@47.95.12.198:/tmp/
#   ssh -p 25894 root@47.95.12.198 'bash /tmp/deploy.sh'
#
# 它做四件事：
#   1. 代码与数据落到 /opt/enroute/telemetry、/var/lib/enroute-telemetry
#   2. systemd 起一个只监听 127.0.0.1:18090 的 python 服务
#   3. 在已有 18081 / 18082 两个 nginx 站点里各挂一段 /api/telemetry/ 反向代理
#      （不开新端口 —— 阿里云安全组只放行了既有端口，新端口要人肉放行）
#   4. cron：每小时聚合一次（公开页读 users.json / daily.json 快照，不实时）+ 每天 04:10 清 180 天前的原始记录
set -euo pipefail

APP_DIR=/opt/enroute/telemetry
DATA_DIR=$APP_DIR/data   # server.py 固定写在自己旁边的 data/ 下
PORT=18090
SNIPPET=/etc/nginx/snippets/enroute-telemetry.conf
UNIT=/etc/systemd/system/enroute-telemetry.service
ENVFILE=/etc/enroute-telemetry.env

install -d -m 755 "$APP_DIR" "$DATA_DIR"
chmod 700 "$DATA_DIR"
install -m 644 /tmp/server.py "$APP_DIR/server.py"
install -m 644 /tmp/schema.json "$APP_DIR/schema.json"

# 自检：语法 + 白名单一致性，坏文件不要装上去
python3 "$APP_DIR/server.py" --schema-check

# 查看统计用的管理钥匙：只生成一次，之后重跑不会换（否则 curl 的脚本全失效）
if [ ! -s "$ENVFILE" ]; then
  umask 077
  printf 'ENROUTE_TELEMETRY_ADMIN_KEY=%s\n' "$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | tr -d '\n')" > "$ENVFILE"
  chmod 600 "$ENVFILE"
fi
ADMIN_KEY=$(sed -n 's/^ENROUTE_TELEMETRY_ADMIN_KEY=//p' "$ENVFILE")

cat > "$UNIT" <<'EOF'
[Unit]
Description=SYAU Enroute anonymous usage telemetry receiver
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/enroute/telemetry
EnvironmentFile=-/etc/enroute-telemetry.env
ExecStart=/usr/bin/python3 /opt/enroute/telemetry/server.py --serve --host 127.0.0.1 --port 18090
Restart=always
RestartSec=3
MemoryMax=96M
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now enroute-telemetry.service
systemctl restart enroute-telemetry.service

cat > "$SNIPPET" <<'EOF'
# /api/telemetry（含 /api/telemetry/health）→ 本地 python 接收端
# 注意：location 不带结尾斜杠、proxy_pass 也不带 URI，这样 /api/telemetry 与 /api/telemetry/health 都能通
location /api/telemetry {
    proxy_pass http://127.0.0.1:18090;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    client_max_body_size 256k;
    proxy_read_timeout 20s;
}
EOF

# 往两个站点里塞 include；已塞过就跳过（幂等）
for site in /etc/nginx/sites-enabled/cet6-http /etc/nginx/sites-enabled/campusmap; do
  [ -f "$site" ] || continue
  if ! grep -q "enroute-telemetry.conf" "$site"; then
    printf '\n    include %s;\n' "$SNIPPET" >> "$site"
    echo "wired $site"
  fi
done

# ── 公开统计页（https://wb.dkarbon.cn/enroute/）────────────────────
# TLS 站点不能复用上面那段 include：wb2api 里有一条 `location ~* ^/(v1|api)/` 正则块，
# 正则优先于普通前缀 location，/api/telemetry 会被当成网关接口转走。
# 所以单独写一份带 `^~` 的 snippet，抢在正则前面。
WEB_SRC=${WEB_SRC:-/tmp/enroute-web/index.html}
if [ -f "$WEB_SRC" ]; then
  install -d -m 755 /var/www/enroute
  install -m 644 "$WEB_SRC" /var/www/enroute/index.html
  echo "installed /var/www/enroute/index.html"
else
  echo "WARN: $WEB_SRC 不存在，公开页会 404"
fi

TLS_SNIPPET=/etc/nginx/snippets/enroute-telemetry-tls.conf
cat > "$TLS_SNIPPET" <<'EOF'
# TLS 站点（wb.dkarbon.cn）上的遥测入口 + 公开统计页
# ^~ 必需：wb2api 的 `location ~* ^/(v1|api)/` 正则优先于普通前缀 location
location ^~ /api/telemetry {
    proxy_pass http://127.0.0.1:18090;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    client_max_body_size 256k;
    proxy_read_timeout 20s;
}

location = /enroute { return 301 /enroute/; }

location ^~ /enroute/ {
    root /var/www;
    index index.html;
    charset utf-8;
    add_header X-Robots-Tag "noindex, nofollow" always;
    add_header Cache-Control "no-store" always;
    add_header X-Content-Type-Options "nosniff" always;
}
EOF

TLS_SITE=/etc/nginx/sites-enabled/wb2api
if [ -f "$TLS_SITE" ] && ! grep -q 'enroute-telemetry-tls.conf' "$TLS_SITE"; then
  python3 - "$TLS_SITE" "$TLS_SNIPPET" <<'PY'
import io, sys
site, snippet = sys.argv[1], sys.argv[2]
src = io.open(site, encoding='utf-8').read()
needle = '    server_name wb.dkarbon.cn;'
if needle not in src:
    print('WARN: 没找到 wb.dkarbon.cn 的 server 块，跳过 TLS 接线')
    sys.exit(0)
src = src.replace(needle, needle + '\n    include ' + snippet + ';', 1)
io.open(site, 'w', encoding='utf-8').write(src)
print('wired ' + site)
PY
fi

nginx -t
systemctl reload nginx

# cron：每小时聚合 + 每天 04:10 聚合并清理
# 公开页读的是 users.json / daily.json 快照，不是原始 ndjson —— 所以聚合要够勤，
# 页面才不会一直停在几小时前的数字。清理（--prune）只跑一次，没必要每小时删一遍。
CRON=/etc/cron.d/enroute-telemetry
cat > "$CRON" <<EOF
0 * * * * root cd $APP_DIR && /usr/bin/python3 server.py --aggregate --all >> /var/log/enroute-telemetry-cron.log 2>&1
10 4 * * * root cd $APP_DIR && /usr/bin/python3 server.py --aggregate --all --prune --days 180 >> /var/log/enroute-telemetry-cron.log 2>&1
EOF
chmod 644 "$CRON"

sleep 1
curl -fsS "http://127.0.0.1:$PORT/api/telemetry/health" && echo
curl -fsS -o /dev/null -w 'via nginx 18082: %{http_code}\n' http://127.0.0.1:18082/api/telemetry/health || true
curl -fsS -o /dev/null -w 'via nginx 18081: %{http_code}\n' http://127.0.0.1:18081/api/telemetry/health || true
echo "--- stats ---"
curl -fsS -H "X-Admin-Key: $ADMIN_KEY" http://127.0.0.1:18082/api/telemetry/stats.txt || true
echo "--- public page ---"
curl -fsS -o /dev/null -w "public api via tls site: %{http_code}\n" -k https://127.0.0.1/api/telemetry/public -H "Host: wb.dkarbon.cn" || true
curl -fsS -o /dev/null -w "public page via tls site: %{http_code}\n" -k https://127.0.0.1/enroute/ -H "Host: wb.dkarbon.cn" || true
echo "admin key: $ADMIN_KEY（也可 cat $ENVFILE）"
echo "deployed. tail -f $DATA_DIR/events-\$(date +%F).ndjson"
