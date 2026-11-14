"""Generate launcher assets from the approved, full-bleed square artwork (Pillow)."""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs/app-icons/mufang-source.webp"
RES = ROOT / "android_hotwater/app/src/main/res"


def adaptive_artwork(source: Image.Image) -> Image.Image:
    # Android displays the central 72dp of the 108dp adaptive layer. Extend the
    # photograph's edges into the 18dp overscan instead of shrinking the icon or
    # cropping its water motif. Every mask and parallax position stays opaque.
    core = source.resize((288, 288), Image.Resampling.LANCZOS)
    layer = Image.new("RGB", (432, 432))
    layer.paste(core, (72, 72))
    layer.paste(core.crop((0, 0, 1, 288)).resize((72, 288)), (0, 72))
    layer.paste(core.crop((287, 0, 288, 288)).resize((72, 288)), (360, 72))
    layer.paste(layer.crop((0, 72, 432, 73)).resize((432, 72)), (0, 0))
    layer.paste(layer.crop((0, 359, 432, 360)).resize((432, 72)), (0, 360))
    return layer


def main() -> None:
    with Image.open(SOURCE) as image:
        source = image.convert("RGB")
    if source.width != source.height:
        raise ValueError("The approved artwork must be square")
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(exist_ok=True)
        icon = source.resize((size, size), Image.Resampling.LANCZOS)
        for name in ("ic_launcher", "ic_launcher_round"):
            icon.save(directory / f"{name}.webp", quality=95, method=6)
    adaptive_artwork(source).save(RES / "drawable-nodpi/ic_launcher_adaptive.webp", quality=95, method=6)
    source.resize((256, 256), Image.Resampling.LANCZOS).save(
        RES / "drawable-nodpi/app_icon.webp", quality=95, method=6,
    )
    print("Generated five densities plus opaque adaptive artwork")


if __name__ == "__main__":
    main()
