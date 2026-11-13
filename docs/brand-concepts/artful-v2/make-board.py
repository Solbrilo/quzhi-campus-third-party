"""Render the concept presentation and actual-size QA sheet with Pillow."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / "concepts.json").read_text(encoding="utf-8"))
FONTS = Path("C:/Windows/Fonts")
PAPER = "#F5F2EC"
INK = "#243C39"
MUTED = "#717D74"
LINE = "#DADCD1"


def font(size, style="body"):
    filename = {"body": "msyh.ttc", "bold": "msyhbd.ttc", "serif": "STSONG.TTF", "latin": "bahnschrift.ttf"}[style]
    return ImageFont.truetype(str(FONTS / filename), size)


def text(draw, xy, content, size=22, color=INK, style="body"):
    draw.text(xy, content, font=font(size, style), fill=color, anchor="lt")


def paste_icon(canvas, c, xy, size, suffix="-preview"):
    icon = Image.open(ROOT / f"{c['id']}{suffix}.png").convert("RGBA")
    icon = icon.resize((size, size), Image.Resampling.LANCZOS)
    canvas.paste(icon, xy, icon)


def make_overview():
    canvas = Image.new("RGB", (2000, 1270), PAPER)
    d = ImageDraw.Draw(canvas)
    text(d, (78, 52), "WATER / WARMTH / EVERYDAY", 19, MUTED, "latin")
    text(d, (76, 99), "水与温度之间", 64, style="serif")
    text(d, (80, 194), "四组图标与命名提案，为校园热水的日常留下一个有记忆的符号。", 25, MUTED)
    text(d, (1570, 129), "ICON STUDIES / 02", 23, MUTED, "latin")

    width, gap, left, top, bottom = 443, 24, 78, 278, 1085
    for i, c in enumerate(DATA):
        x = left + i * (width + gap)
        d.rectangle((x, top, x + width, bottom), fill="#FEFCF8", outline=LINE, width=1)
        text(d, (x + 30, top + 29), f"0{i+1}", 27, MUTED, "latin")
        text(d, (x + width - 131, top + 33), c["phonetic"], 18, MUTED)
        paste_icon(canvas, c, (x + 67, top + 103), 310)
        text(d, (x + 30, top + 460), c["name"], 44, style="serif")
        text(d, (x + 30, top + 522), c["roman"], 17, MUTED, "latin")
        text(d, (x + 30, top + 572), c["tag"], 21, MUTED)
        d.line((x + 30, top + 627, x + width - 30, top + 627), fill=LINE, width=1)
        for size, ox in [(64, 30), (48, 126), (32, 206)]:
            paste_icon(canvas, c, (x + ox, top + 664 + (64-size)//2), size)
            text(d, (x + ox, top + 750), f"{size} PX", 16, MUTED, "latin")
        d.rounded_rectangle((x + 288, top + 672, x + 336, top + 720), radius=11, fill="#E8E5DD")
        paste_icon(canvas, c, (x + 288, top + 672), 48, suffix="-monochrome")
        text(d, (x + 282, top + 750), "MONO", 16, MUTED, "latin")

    text(d, (79, 1130), "偏艺术感，推荐「洄温」；偏清爽易记，推荐「沐白」。", 25)
    text(d, (79, 1190), "原创矢量绘制 · 图标与名称可自由搭配 · 已另存完整 SVG、PNG 与单色版本", 21, MUTED)
    text(d, (1510, 1190), "CONCEPTS / 01—04", 18, MUTED, "latin")
    canvas.save(ROOT / "icon-studies.png", optimize=True)


def make_qa():
    canvas = Image.new("RGB", (1600, 1160), PAPER)
    d = ImageDraw.Draw(canvas)
    text(d, (65, 48), "小尺寸与遮罩检查", 44, style="serif")
    text(d, (67, 115), "左侧显示圆角、圆形遮罩；右侧为实际像素尺寸与真实单色前景。", 21, MUTED)
    for i, c in enumerate(DATA):
        y = 190 + i * 220
        text(d, (67, y + 45), c["name"], 32)
        paste_icon(canvas, c, (245, y), 144)
        raw = Image.open(ROOT / f"{c['id']}.png").convert("RGBA").resize((144, 144), Image.Resampling.LANCZOS)
        mask = Image.new("L", (576, 576), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, 575, 575), fill=255)
        mask = mask.resize((144, 144), Image.Resampling.LANCZOS)
        canvas.paste(raw, (435, y), mask)
        for size, x in [(64, 700), (48, 824), (32, 936)]:
            paste_icon(canvas, c, (x, y + (144-size)//2), size)
            text(d, (x, y + 162), f"{size}px", 17, MUTED, "latin")
        d.rounded_rectangle((1072, y + 40, 1136, y + 104), radius=15, fill="#E2E4DB")
        paste_icon(canvas, c, (1072, y + 40), 64, suffix="-monochrome")
        d.rounded_rectangle((1220, y + 40, 1284, y + 104), radius=15, fill="#243C39")
        mono = Image.open(ROOT / f"{c['id']}-monochrome.png").convert("RGBA").resize((64, 64), Image.Resampling.LANCZOS)
        white = Image.new("RGBA", mono.size, "#F5F2EC")
        white.putalpha(mono.getchannel("A"))
        canvas.paste(white, (1220, y + 40), white)
        text(d, (1058, y + 162), "单色 · 浅底", 17, MUTED)
        text(d, (1206, y + 162), "单色 · 深底", 17, MUTED)
        d.line((65, y + 202, 1535, y + 202), fill=LINE)
    text(d, (67, 1104), "概念验证；未更改应用资源。正式接入时按平台导出资源并在设备上确认。", 20, MUTED)
    canvas.save(ROOT / "mask-and-size-check.png", optimize=True)


if __name__ == "__main__":
    make_overview()
    make_qa()
    print(json.dumps({"overview": str(ROOT / "icon-studies.png"), "qa": str(ROOT / "mask-and-size-check.png")}, ensure_ascii=False))
