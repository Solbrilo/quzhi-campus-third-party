# 沐芳项目官网快照

官网地址：[nebularive.top/project-qzxy-hotwater.php](https://nebularive.top/project-qzxy-hotwater.php)。本目录保留 2026-11-14 沐芳名称、诗句欢迎页、加高小组件介绍的精确页面源码与项目卡片，便于在 Gitee 和 GitHub 同时审阅；网站仍由独立的 PHP 站点部署。

- `project-qzxy-hotwater.php` 对应站点根目录同名文件，依赖站点现有公共布局与样式。
- `qzxy-project.css` 对应 `static/css/qzxy-project.css`；手机端为固定播放器和导航预留空间，页面缓存参数同步更新为 `20261114-mufang`。
- `qzxy-hotwater-project.json` 仅对应 `_data/projects.json` 中 `id=qzxy-hotwater` 的单个项目条目，更新时合并该条目，保留其他项目。
- 图标由 `../app-icons/mufang-source.webp` 缩至 1024px，WebP 质量 95，对应 `static/images/qzxy-hotwater-app-icon.webp`；页面版本参数为 `20261114-mufang`。

部署前对照线上文件哈希并备份精确目标，分别更新 PHP、项目条目和图标；部署后检查首页、项目列表、项目详情和图标 HTTP 状态及手机/桌面排版。不要将此目录当作整个站点同步。
