# 沐芳图标与欢迎页字体

`mufang-source.webp` 是 2026-11-14 用户指定的第二张完整方形图的压缩归档，图标不另加圆角、透明留白或玻璃外框。使用 Pillow 重建：

```powershell
python docs/app-icons/generate_icons.py
```

脚本生成 48/72/96/144/192px 的 WebP 图标与圆形入口资源。Android 8+ 优先使用 adaptive icon：中央 72dp 对应完整原图，外围 18dp 延展原图边缘，以不透明背景填满启动器蒙版，避免系统给旧式图标再次加内边距。前景透明，不重复缩小画面。`ic_launcher_round` 同时用于实况通知，因此保留资源名。

关于页使用单独的 256px `app_icon.webp`，因为 Compose `painterResource` 只支持普通图片与矢量图，不能读取 adaptive icon XML。

欢迎页使用 **Noto Serif CJK SC Regular**（思源宋体同源字体）的 21 个字符子集（含竖排标点与书名号）；字体家族重命名为 `Mufang Poem Serif`，包内仅约 14KB。原字体来自 [Noto CJK](https://github.com/notofonts/noto-cjk/blob/main/Serif/OTF/SimplifiedChinese/NotoSerifCJKsc-Regular.otf)，按 SIL OFL 1.1 分发，完整许可见 `android_hotwater/app/src/main/assets/licenses/NotoSerifCJK-OFL.txt`。子集限定欢迎诗句及作者题名，不用于动态内容。可使用 FontTools 的 `pyftsubset`，字符集为：

```text
沐芳莫弹冠，浴兰莫振衣。——李白《沐浴子》︐︒︽︾
```
