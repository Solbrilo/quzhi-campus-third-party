# 趣智校园使用统计与公告管理

线上服务仍使用 `hotwater-activity.service`，监听 `127.0.0.1:8101`，代码位于 `/opt/hotwater-activity`，数据库位于 `/var/lib/hotwater-activity/activity.sqlite3`。本目录为可审查的服务端源码，运行数据与认证配置不进入仓库。

- 既有 `POST /api/hotwater-activity`（`day`、`token`）和 `/monthly`（`month`、`token`）保持兼容，可额外传入严格 `X.Y.Z` 格式的 `version`。
- 版本升级重新归类当前周期中的安装，不重复增加日活/月活。历史未提供版本的数据显示“旧版 / 未上报”。这是安装计数，无法等同于自然人或跨设备账户数。
- `GET /api/hotwater-notices` 返回当前有效公告；`POST /api/hotwater-notices/response` 接收每版公告独立的匿名回复，幂等更新。
- 管理页 `/project-qzxy-console/` 不加入网站导航或站点地图，设置 `noindex`。统计、公告编辑及回复接口需要有效会话；公开入口本身不构成认证。
- 密码仅以随机盐和 PBKDF2-SHA256（600,000 次）摘要存放在服务数据目录 `admin-auth.json`，权限 `0600`。实际密码不在源码中。会话 8 小时过期，Cookie 使用 Secure、HttpOnly、SameSite=Strict，写请求校验 Origin 和 CSRF。登录失败按临时来源摘要限流，不存储 IP 或请求日志。
- 公告的生效、弹窗推送、到期时间均为北京时间。应用进入前台时同步，并在前台每分钟检查；应用关闭时不会承诺准点唤醒。生效后可在消息列表查看，推送时间到达后弹窗，到期后不再返回。
- 修改公告生成新版本，历史回复及当时的选项文案保留；当前界面支持切换版本。每份自由输入最多 1000 字，管理界面最多展示每版最新 500 条。客户端不上传账号、手机号或活动统计标识；回复标识按公告隔离。
- “应用反馈”读取既有 Android / HarmonyOS 反馈，按归档月份、平台、类型筛选，每页 25 条，最新记录在前。归档月份沿用原接口的 UTC 月份，显示时间为北京时间；联系邮箱与编号默认折叠。页面退出登录后清除反馈内容，所有文字按纯文本呈现。

反馈只读服务使用 `feedback_reader.py` 和 `hotwater-feedback-reader.service`，以原 `nebularive-feedback` 用户读取私有 JSONL，不修改原目录 `0700`、文件 `0600` 权限，也不改变提交接口。仅在本机 Unix socket `/run/hotwater-feedback-reader/reader.sock` 提供分页结果；`hotwater-feedback-view` 系统组只授予管理服务连接权限，`hotwater-activity-feedback.conf` 安装到原服务的 `service.d/feedback.conf`。先创建 `hotwater-feedback-view` 系统组，再启用 reader 服务、重载 systemd 并重启 activity 服务；不要把此组授予其他站点进程。浏览器的 `/project-qzxy-console/feedback` 接口先验证管理会话再访问此 socket，返回 `no-store`，没有公开反馈读接口。不要把反馈正文或邮箱复制到日志、测试数据或仓库。

2026-11-08 反馈查看功能已部署；代码和服务配置备份位于 `/www/backup/qzxy-feedback-20261108-213233/`，没有修改反馈数据。回滚时先停用 reader，再移除本次新增 drop-in 并恢复备份代码，`daemon-reload` 后重启 activity 服务。`new-files.json` 记录新增文件，原统计数据库无需恢复。

首次部署前先用 SQLite backup API 备份原数据库，再部署明确列出的源码与 Nginx 扩展。迁移只增加列和表，保留旧计数。不要用本地数据库覆盖生产。认证文件可用 Python `secrets.token_bytes(16)` 生成盐，再对交互输入的密码运行 `hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 600000)`；只落盘盐与摘要。

窄测试：`python -m unittest discover -s server/hotwater-activity -p "test_*.py" -v`。测试使用临时数据库和合成反馈，不接触线上数据；可通过 `HOTWATER_TEST_TEMP` 指定有空间的临时目录。
