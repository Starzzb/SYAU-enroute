# 15: App 内检查更新（自建下载 + FileProvider 安装）

**What to build:** 设置页「检查更新」能查到新版本并一键下载安装；启动时每天最多静默检查一次，只提示不打扰。

**Blocked by:** 无（服务器与域名都在；只差把 APK 目录挂到 nginx）

**Status:** ready-for-agent

---

## 已具备

- 服务器 `47.95.12.198`，nginx 已开 `18081`（明文，手机可达）与 `18082`
- 域名 `wb.dkarbon.cn`（TLS 证书就绪，但手机链路 TLS 会被 RST，更新包走 18081 明文）
- 发版流程见 `docs/版本控制与发版流程.md` §4

## 服务器端要加（一次性）

```
/var/www/app/
  latest.json
  enroute-<versionName>.apk
```

```jsonc
{
  "versionCode": 20,
  "versionName": "0.1.0-m20",
  "url": "http://47.95.12.198:18081/app/enroute-0.1.0-m20.apk",
  "sha256": "…",
  "size": 23100000,
  "notes": "修复空闲教室、新增校园地图属性编辑",
  "minSupported": 15          // 低于此 versionCode 强制提示更新（可选）
}
```

nginx（挂在 18081 那个 server 块里，**只加 location，不动既有规则**）：

```nginx
location ^~ /app/ {
    alias /var/www/app/;
    autoindex off;
    add_header Cache-Control "no-cache" always;
    add_header X-Robots-Tag "noindex, nofollow" always;
}
```

## 客户端行为

- **手动**：设置页「检查更新」→ 有新版显示 `m20 · 23MB · 更新内容` + 「下载并安装」
- **自动**：启动时若距上次检查 > 24h 且**未在漫游/移动数据**（可选）→ 静默 GET `latest.json`；
  有新版只在设置页条目上打一个红点/「有新版本」字样，**不弹窗、不打断**
- 下载到 `filesDir/updates/enroute-<ver>.apk`，**校验 sha256**；不匹配删除并提示重试
- 安装：`FileProvider` 暴露该目录 + `ACTION_VIEW`（`application/vnd.android.package-archive`）
  - `AndroidManifest.xml` 加 `REQUEST_INSTALL_PACKAGES` 权限
  - Android 11+ 需要 `<queries><intent><action android:name="android.intent.action.VIEW"/><data android:mimeType="application/vnd.android.package-archive"/></intent></queries>`
- 安装前必须**已下载完且校验通过**；权限被拒时给可执行提示（「去设置里允许安装未知应用」）
- 下载**显示进度**，可取消；不用前台服务、不后台偷偷下

## 红线

- **不静默安装**、不后台自动下大包、不弹全屏广告式弹窗
- 更新只从**本项目自己的域名/IP**拉取，URL 写常量，不接受服务端返回任意域名的下载地址（防劫持）
- 校验失败一律丢弃，不「凑合装」
- 不上报任何个人信息（可上报 versionCode 做统计，但默认关）

## 验收标准

- [ ] 服务器放好 `latest.json` + APK 后，App 能正确显示新版本号与更新说明
- [ ] 点下载有进度、可取消；完成后能拉起系统安装器并装上（versionCode 递增时）
- [ ] 篡改 APK 后 sha256 不匹配 → 拒绝安装并提示重试
- [ ] 已是新版时不提示（`versionCode` 比较正确，含「服务器版本更低」的情况）
- [ ] 启动静默检查每天最多一次（有单测验证时间窗口）
- [ ] 下载地址非本项目域名时**拒绝**（单测覆盖）
- [ ] 全量单测绿（基线以最新为准）；`assembleDebug` 成功

## 边界

- 只读拉取 + 下载安装，不上传任何数据
- 写盘范围：`data/Update*.kt`、`ui/settings/SettingsScreen.kt`、`AndroidManifest.xml`、
  `res/xml/file_paths.xml`、测试
