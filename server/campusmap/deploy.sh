#!/usr/bin/env bash
# 在服务器上装配「校园地图属性补丁服务」（campusmap）。幂等，可重复跑。
#
# 用法（本机 WSL，免密已配）：
#   scp -P 25894 server/campusmap/api/server.js server/campusmap/web/index.html \
#       root@47.95.12.198:/tmp/
#   ssh -p 25894 root@47.95.12.198 'bash /tmp/deploy-campusmap.sh'
#
# 它做五件事：
#   1. server.js 落到 /opt/campusmap/api（旧文件留 .bak-<时间戳>）
#   2. env 首次生成三个随机钥匙（CMAP_SECRET / CMAP_API_KEY / CMAP_ADMIN_KEY），之后不动
#   3. systemd 单元（缺才写）+ 重启服务，只监听 127.0.0.1:7870
#   4. 审核管理页装到 /var/www/campusmap-admin/，nginx 用 basic auth 挡在 /map/admin/
#   5. curl 自检：health、无钥匙 stats 应 403、带钥匙 stats 应 200、管理页应 401
#
# ⚠️ 数据目录 /var/lib/campusmap 一律不碰（那是补丁流与审核记录，只能追加不能重建）。
set -euo pipefail

APP_DIR=/opt/campusmap/api
DATA_DIR=/var/lib/campusmap
ADMIN_WWW=/var/www/campusmap-admin
ENVFILE=/opt/campusmap/api/env
ADMINFILE=/etc/campusmap-admin.env
HTPASSWD=/etc/nginx/.htpasswd-campusmap
SNIPPET=/etc/nginx/snippets/campusmap-admin.conf
SNIPPET_TLS=/etc/nginx/snippets/campusmap-admin-tls.conf
WB_SITE=/etc/nginx/sites-available/wb2api
SITE=/etc/nginx/sites-enabled/campusmap
BUN=/root/.bun/bin/bun

install -d -m 755 "$APP_DIR" "$DATA_DIR" "$ADMIN_WWW"

# ── 1. 代码 ────────────────────────────────────────────
if [ -f /tmp/server.js ]; then
  if command -v node >/dev/null 2>&1; then
    node --check /tmp/server.js          # 语法错了就别装上去，免得服务起不来
  fi
  if [ -f "$APP_DIR/server.js" ]; then
    cp -a "$APP_DIR/server.js" "$APP_DIR/server.js.bak-$(date +%Y%m%d-%H%M%S)"
  fi
  install -m 644 /tmp/server.js "$APP_DIR/server.js"
  echo "installed $APP_DIR/server.js"
else
  echo "WARN: /tmp/server.js 不存在，保持线上代码不动"
fi

if [ -f /tmp/index.html ]; then
  install -m 644 /tmp/index.html "$ADMIN_WWW/index.html"
  echo "installed $ADMIN_WWW/index.html"
else
  echo "WARN: /tmp/index.html 不存在，管理页会 404"
fi

# ── 2. 钥匙（只生成一次）────────────────────────────────
# 不覆盖已有 env：一换 CMAP_SECRET，所有已在客户端里的会话/限流桶全部作废，
# 一换 CMAP_ADMIN_KEY，审核入口就用不了。要换得人来做，脚本只负责首次生成。
if [ ! -s "$ENVFILE" ]; then
  umask 077
  {
    printf 'CMAP_SECRET=%s\n' "$(head -c 32 /dev/urandom | base64 | tr -d '/+= \n')"
    printf 'CMAP_API_KEY=%s\n' "$(head -c 24 /dev/urandom | base64 | tr -d '/+= \n')"
    printf 'CMAP_ADMIN_KEY=%s\n' "$(head -c 24 /dev/urandom | base64 | tr -d '/+= \n')"
    printf 'DATA_DIR=%s\n' "$DATA_DIR"
    printf 'PORT=7870\n'
  } > "$ENVFILE"
  chmod 600 "$ENVFILE"
  echo "created $ENVFILE"
fi

ADMIN_KEY=$(sed -n 's/^CMAP_ADMIN_KEY=//p' "$ENVFILE")

# ── 3. systemd ────────────────────────────────────────
UNIT=/etc/systemd/system/campusmap-api.service
if [ ! -f "$UNIT" ]; then
  cat > "$UNIT" <<'EOF'
[Unit]
Description=Campus Map Attribute Patch API (campusmap)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/campusmap/api
EnvironmentFile=/opt/campusmap/api/env
ExecStart=/root/.bun/bin/bun /opt/campusmap/api/server.js
Restart=always
RestartSec=3
# 小机器，限制一下内存，别影响现有服务
MemoryMax=180M
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=read-only

[Install]
WantedBy=multi-user.target
EOF
  echo "created $UNIT"
fi

systemctl daemon-reload
systemctl enable campusmap-api.service >/dev/null 2>&1 || true

# 线上历史遗留：老单元的 unit 名是 campusmap.service，和新单元抢同一个 7870 ——
# 不停掉它，新单元会 EADDRINUSE 反复重启，而 7870 上跑的仍是旧代码
# （旧代码的 dataset 不过审核闸门，看起来「部署成功了其实没换」）。
# 只 stop + disable，不删 unit 文件，要回退随时 systemctl enable --now campusmap.service。
if systemctl is-active --quiet campusmap.service 2>/dev/null; then
  systemctl stop campusmap.service || true
  systemctl disable campusmap.service >/dev/null 2>&1 || true
  echo "stopped legacy unit campusmap.service (was holding 7870)"
fi

systemctl reset-failed campusmap-api.service >/dev/null 2>&1 || true
systemctl restart campusmap-api.service

# ── 4. 审核页 + basic auth ─────────────────────────────
# 页面本身能做的只有「通过」，钥匙还是要在弹框里输 —— 所以两层：
# nginx basic auth 挡陌生访客，页面里再要一次 CMAP_ADMIN_KEY 才能调接口。
if [ ! -s "$ADMINFILE" ]; then
  umask 077
  printf 'ADMIN_USER=admin\nADMIN_PASS=%s\n' "$(head -c 18 /dev/urandom | base64 | tr -d '/+= \n')" > "$ADMINFILE"
  chmod 600 "$ADMINFILE"
  echo "created $ADMINFILE"
fi
ADMIN_USER=$(sed -n 's/^ADMIN_USER=//p' "$ADMINFILE")
ADMIN_PASS=$(sed -n 's/^ADMIN_PASS=//p' "$ADMINFILE")
HASH=$(openssl passwd -apr1 "$ADMIN_PASS")
printf '%s:%s\n' "$ADMIN_USER" "$HASH" > "$HTPASSWD"
chmod 640 "$HTPASSWD"
chgrp www-data "$HTPASSWD" 2>/dev/null || chmod 644 "$HTPASSWD"

cat > "$SNIPPET" <<'EOF'
# 补丁审核管理页：basic auth 挡在前面，页面里再要一次 CMAP_ADMIN_KEY
location = /map/admin { return 301 /map/admin/; }

location ^~ /map/admin/ {
    auth_basic "campusmap admin";
    auth_basic_user_file /etc/nginx/.htpasswd-campusmap;
    alias /var/www/campusmap-admin/;
    index index.html;
    charset utf-8;
    add_header X-Robots-Tag "noindex, nofollow" always;
    add_header Cache-Control "no-store" always;
    add_header X-Content-Type-Options "nosniff" always;
}
EOF

if [ -f "$SITE" ] && ! grep -q 'campusmap-admin.conf' "$SITE"; then
  python3 - "$SITE" "$SNIPPET" <<'PY'
import io, sys
site, snippet = sys.argv[1], sys.argv[2]
src = io.open(site, encoding="utf-8").read()
needle = "    server_name _;"
if needle not in src:
    print("WARN: campusmap 站点里没找到 server_name _;，跳过管理页接线")
    sys.exit(0)
src = src.replace(needle, needle + "\n    include " + snippet + ";", 1)
io.open(site, "w", encoding="utf-8").write(src)
print("wired " + site)
PY
fi

# ── 4b. TLS 入口（wb.dkarbon.cn）────────────────────────
# 手机侧仍走明文 18082（中间设备会 RST 掉 8443 的握手，见 docs §8）；
# 人审补丁走域名的 https，省得把 47.95.12.198:18082 这种明文地址发给人。
# 页面里的 JS 用同源 /map/api/...，所以 API 前缀也挂在这台 server 上，同样两层凭据。
cat > "$SNIPPET_TLS" <<'EOF'
# TLS 站点（wb.dkarbon.cn）上的人工审核入口（2026-11-27 加）
location = /map/admin { return 301 /map/admin/; }

location ^~ /map/admin/ {
    auth_basic "campusmap admin";
    auth_basic_user_file /etc/nginx/.htpasswd-campusmap;
    alias /var/www/campusmap-admin/;
    index index.html;
    charset utf-8;
    add_header X-Robots-Tag "noindex, nofollow" always;
    add_header Cache-Control "no-store" always;
    add_header X-Content-Type-Options "nosniff" always;
}

location ^~ /map/api/ {
    auth_basic "campusmap admin";
    auth_basic_user_file /etc/nginx/.htpasswd-campusmap;

    proxy_pass http://127.0.0.1:7870;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 15s;

    add_header X-Robots-Tag "noindex, nofollow" always;
    add_header Cache-Control "no-store" always;
    add_header X-Content-Type-Options "nosniff" always;
}
EOF
chmod 600 "$SNIPPET_TLS"

if [ -f "$WB_SITE" ] && ! grep -q 'campusmap-admin-tls.conf' "$WB_SITE"; then
  python3 - "$WB_SITE" "$SNIPPET_TLS" <<'PY'
import io, sys
site, snippet = sys.argv[1], sys.argv[2]
src = io.open(site, encoding="utf-8").read()
needle = "include /etc/nginx/snippets/enroute-telemetry-tls.conf;"
if needle not in src:
    print("WARN: wb2api 站点里没找到遥测 include 锚点，跳过 https 审核页接线")
    sys.exit(0)
src = src.replace(needle, needle + "
    include " + snippet + ";", 1)
io.open(site, "w", encoding="utf-8").write(src)
print("wired " + site)
PY
fi

nginx -t
systemctl reload nginx

# ── 5. 自检 ───────────────────────────────────────────
sleep 1
# ⚠️ 18082 上有 UA 过滤器：不带 App UA 的请求（curl 默认）一律 403。
# 所以自检的 curl 必须带 -A，否则「带钥匙也 403」会被误读成钥匙不对。
UA_CHECK="SYAU-Enroute/0.0.0-deploy-check"
curl -fsS http://127.0.0.1:7870/map/api/health && echo
curl -sS -o /dev/null -w "stats 无钥匙（应 403）: %{http_code}\n" -A "$UA_CHECK" http://127.0.0.1:18082/map/api/stats
curl -sS -o /dev/null -w "stats 带钥匙（应 200）: %{http_code}\n" -A "$UA_CHECK" -H "X-Admin-Key: $ADMIN_KEY" http://127.0.0.1:18082/map/api/stats
curl -sS -o /dev/null -w "管理页 无凭据（应 401）: %{http_code}\n" http://127.0.0.1:18082/map/admin/
curl -sS -o /dev/null -w "管理页 带凭据（应 200）: %{http_code}\n" -u "$ADMIN_USER:$ADMIN_PASS" http://127.0.0.1:18082/map/admin/
curl -sS -o /dev/null -w "dataset（应 200）: %{http_code}\n" -A "$UA_CHECK" http://127.0.0.1:18082/map/api/dataset
if [ -f "$SNIPPET_TLS" ]; then
  curl -sS -k --resolve wb.dkarbon.cn:443:127.0.0.1 -o /dev/null -w "https 管理页 无凭据（应 401）: %{http_code}\n" https://wb.dkarbon.cn/map/admin/
  curl -sS -k --resolve wb.dkarbon.cn:443:127.0.0.1 -o /dev/null -w "https 管理页 带凭据（应 200）: %{http_code}\n" -u "$ADMIN_USER:$ADMIN_PASS" https://wb.dkarbon.cn/map/admin/
  curl -sS -k --resolve wb.dkarbon.cn:443:127.0.0.1 -o /dev/null -w "https pending 带钥匙（应 200）: %{http_code}\n" -u "$ADMIN_USER:$ADMIN_PASS" -H "X-Admin-Key: $ADMIN_KEY" "https://wb.dkarbon.cn/map/api/pending?limit=1"
fi
echo "审核页 https://wb.dkarbon.cn/map/admin/  （明文备用 http://47.95.12.198:18082/map/admin/）"
echo "  用户 $ADMIN_USER  口令 $(cat "$ADMINFILE" | sed -n 's/^ADMIN_PASS=//p')"
echo "审核钥匙（页面里要填）：$ADMIN_KEY"