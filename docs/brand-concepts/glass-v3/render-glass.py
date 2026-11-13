"""Original glass material renderer using editable vector geometry as the silhouette."""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import distance_transform_edt, gaussian_filter, shift

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / 'concepts.json').read_text(encoding='utf-8'))
FONTROOT = Path('C:/Windows/Fonts')


def rgb(code):
    return np.array([int(code[i:i+2], 16) for i in (1, 3, 5)], dtype=np.float32) / 255


def rgba_save(array, filename):
    Image.fromarray(np.uint8(np.clip(array, 0, 1) * 255 + .5)).save(ROOT / filename, optimize=True)


def render(c):
    alpha = np.asarray(Image.open(ROOT / f"{c['id']}-mask.png").convert('RGBA'))[..., 3].astype(np.float32) / 255
    n = alpha.shape[0]
    scale = n / 512
    yy, xx = np.mgrid[:n, :n].astype(np.float32)
    x, y = xx / n, yy / n
    distance = distance_transform_edt(alpha > .5).astype(np.float32) / scale

    # Rounded glass relief; normals follow the inner openings as well as the silhouette.
    z = 12.5 * (1 - np.exp(-distance / 9.0))
    zy, zx = np.gradient(gaussian_filter(z, 1.1 * scale), 1 / scale)
    norm = np.sqrt(zx * zx + zy * zy + 1)
    nx, ny, nz = -zx / norm, -zy / norm, 1 / norm
    light = np.array([-.48, -.62, .82], dtype=np.float32)
    light /= np.linalg.norm(light)
    half = light + np.array([0, 0, 1], dtype=np.float32)
    half /= np.linalg.norm(half)
    diffuse = np.maximum(0, nx*light[0] + ny*light[1] + nz*light[2])
    ndh = np.maximum(0, nx*half[0] + ny*half[1] + nz*half[2])
    specular = np.power(ndh, 48)

    # A pearl field stays quiet enough to expose the rim refraction.
    bg_t = np.clip((y + x * .12) / 1.1, 0, 1)
    bg = rgb('#FFFFFF')[None, None, :] * (1-bg_t[..., None]) + rgb(c['bg'])[None, None, :] * bg_t[..., None]
    glow = np.exp(-(((x-.30)/.38)**2 + ((y-.22)/.35)**2))
    bg += glow[..., None] * .012
    shadow = gaussian_filter(shift(alpha, (13*scale, 1*scale), order=1), 10*scale)
    bg *= 1 - shadow[..., None] * .19
    halo = gaussian_filter(alpha, 18*scale) * (1-alpha)
    bg += halo[..., None] * np.array([.0, .006, .01], dtype=np.float32)

    vertical = np.clip((y-.25)/.5, 0, 1)
    if c['material'] == 'smoke':
        lower_light = vertical**2
        body = rgb(c['top'])[None, None, :] * (1-lower_light[..., None]) + rgb(c['bottom'])[None, None, :] * lower_light[..., None]
        body = body*.86 + bg*.14
    else:
        body = rgb(c['top'])[None, None, :] * (1-vertical[..., None]) + rgb(c['bottom'])[None, None, :] * vertical[..., None]
        body = body*.68 + bg*.32

    body *= (.70 + .34*diffuse)[..., None]
    # Reflected studio strip on the broad face, separate from the sharp bevel glint.
    reflection = np.exp(-((y - .31*x - .40)/.065)**2)
    body = body*(1-reflection[..., None]*.16) + .90*reflection[..., None]*.16
    body += specular[..., None] * .40

    # A narrow dark contour and a displaced bright rim provide apparent thickness.
    edge = np.exp(-distance / 1.20) * alpha
    body *= 1 - edge[..., None] * .22
    rim = np.exp(-((distance-2.6)/1.25)**2) * alpha
    facing = np.clip((nx*-.60 + ny*-.80), 0, 1)
    body += (rim*(.16 + facing*.52))[..., None]

    # Restrained prismatic edging echoes the existing app icon.
    bottom = np.clip((y-.38)*3.4, 0, 1)
    spectral_rim = np.exp(-((distance-1.8)/1.8)**2)*bottom*alpha
    mix = np.clip((x-.28)/.45, 0, 1)
    warm = np.array([1.0, .76, .33], dtype=np.float32)
    cool = np.array([.20, .78, 1.0], dtype=np.float32)
    if c['material'] == 'warm':
        warm = np.array([1.0, .73, .37], dtype=np.float32)
        cool = np.array([1.0, .91, .69], dtype=np.float32)
    spectral = warm[None, None, :]*(1-mix[..., None]) + cool[None, None, :]*mix[..., None]
    body = body*(1-spectral_rim[..., None]*.38) + spectral*spectral_rim[..., None]*.48

    final = bg*(1-alpha[..., None]) + np.clip(body, 0, 1)*alpha[..., None]
    full = Image.fromarray(np.uint8(np.clip(final, 0, 1)*255+.5)).resize((1024, 1024), Image.Resampling.LANCZOS)
    full.save(ROOT / f"{c['id']}.png", optimize=True)
    corner = Image.new('L', (2048,2048), 0)
    ImageDraw.Draw(corner).rounded_rectangle((0,0,2047,2047), radius=445, fill=255)
    corner = corner.resize((1024,1024), Image.Resampling.LANCZOS)
    preview = full.convert('RGBA')
    preview.putalpha(corner)
    preview.save(ROOT / f"{c['id']}-preview.png", optimize=True)
    for size in (512, 64, 48, 32):
        preview.resize((size,size), Image.Resampling.LANCZOS).save(ROOT / f"{c['id']}-{size}.png", optimize=True)
    foreground = np.dstack((np.clip(body,0,1),alpha))
    fore = Image.fromarray(np.uint8(np.clip(foreground,0,1)*255+.5)).resize((1024,1024),Image.Resampling.LANCZOS)
    fore.save(ROOT / f"{c['id']}-foreground.png", optimize=True)
    return full


def font(size, serif=False, latin=False):
    name = 'bahnschrift.ttf' if latin else ('STSONG.TTF' if serif else 'msyh.ttc')
    return ImageFont.truetype(str(FONTROOT / name), size)


def text(d,xy,s,size=22,color='#31424A',serif=False,latin=False):
    d.text(xy,s,font=font(size,serif,latin),fill=color,anchor='lt')


def icon(canvas,c,xy,size,suffix='-preview'):
    im=Image.open(ROOT/f"{c['id']}{suffix}.png").convert('RGBA').resize((size,size),Image.Resampling.LANCZOS)
    canvas.paste(im,xy,im)


def board():
    out=Image.new('RGB',(2100,1360),'#F3F5F6')
    d=ImageDraw.Draw(out)
    text(d,(78,49),'QZXY / GLASS ICON STUDIES',19,'#81909A',latin=True)
    text(d,(75,98),'让「趣智」更有温度',65,serif=True)
    text(d,(80,196),'延续当前图标的通透玻璃与柔和折光，用更简洁的图形连接校园热水日常。',25,'#73828B')
    text(d,(1738,130),'CONCEPTS / 03',23,'#81909A',latin=True)
    for i,c in enumerate(DATA):
        x=78+i*492
        top=282
        d.rounded_rectangle((x,top,x+468,1168),radius=24,fill='#FEFEFE',outline='#DDE4E8',width=1)
        text(d,(x+30,top+30),f'0{i+1}',26,'#81909A',latin=True)
        text(d,(x+318,top+35),c['phonetic'],18,'#81909A')
        icon(out,c,(x+55,top+97),358)
        text(d,(x+32,top+490),c['name'],47,serif=True)
        text(d,(x+32,top+555),c['roman'],18,'#81909A',latin=True)
        text(d,(x+32,top+603),c['tag'],22,'#6E7E87')
        lines={
            '01-qumu':['“趣”的 Q 形轮廓，融入环形水纹。','与现有图标联系最紧密。'],
            '02-zhimu':['保留“趣智”的“智”，落在一滴水中。','清爽、直接，容易记住。'],
            '03-qucheng':['“趣”与水的清澈相遇。','三道波纹，保持安静与秩序。'],
            '04-quwen':['把“趣”与温度放在一起。','暖阳和水面，留住舒适的日常。'],
        }[c['id']]
        for j,line in enumerate(lines):text(d,(x+32,top+650+j*35),line,20,'#73828B')
        d.line((x+32,top+744,x+436,top+744),fill='#E0E6E9')
        for size,ox in [(64,32),(48,136),(32,224)]:
            icon(out,c,(x+ox,top+780+(64-size)//2),size)
            text(d,(x+ox,top+855),f'{size} PX',16,'#81909A',latin=True)
        d.rounded_rectangle((x+316,top+788,x+364,top+836),radius=11,fill='#E9EEF0')
        icon(out,c,(x+316,top+788),48,suffix='-monochrome')
        text(d,(x+312,top+855),'MONO',16,'#81909A',latin=True)
    text(d,(80,1215),'首推「趣沐」：趣智的“趣” + 沐浴的“沐”，图形也以 Q 与水纹呼应。',27)
    text(d,(80,1280),'原创图形与玻璃材质 · 1024 PNG / 透明前景 / 可编辑矢量几何稿 · 名称与图标可交叉搭配',21,'#81909A')
    out.save(ROOT/'glass-icon-studies.png',optimize=True)


def qa():
    out=Image.new('RGB',(1700,1260),'#F3F5F6')
    d=ImageDraw.Draw(out)
    text(d,(65,50),'从当前图标，到新的玻璃记忆',43,serif=True)
    text(d,(66,119),'保留白瓷背景、烟色玻璃与边缘折光。每组均检查圆形遮罩和实际小尺寸。',22,'#73828B')
    current=Image.open(ROOT.parent.parent.parent/'harmony_hotwater/AppScope/resources/base/media/app_icon.png')
    current=current.convert('RGB').resize((164,164),Image.Resampling.LANCZOS)
    # Locate repository without depending on a caller's working directory.
    out.paste(current,(66,186))
    text(d,(265,213),'当前项目图标',27)
    text(d,(265,260),'参考：白底 / 透明玻璃厚度 / 冷暖边缘折光',21,'#73828B')
    d.line((65,382,1635,382),fill='#DDE4E8')
    for i,c in enumerate(DATA):
        y=425+i*185
        text(d,(67,y+36),c['name'],29)
        icon(out,c,(241,y),128)
        raw=Image.open(ROOT/f"{c['id']}.png").convert('RGB').resize((128,128),Image.Resampling.LANCZOS)
        mask=Image.new('L',(512,512),0)
        ImageDraw.Draw(mask).ellipse((0,0,511,511),fill=255)
        out.paste(raw,(419,y),mask.resize((128,128),Image.Resampling.LANCZOS))
        for size,x in [(64,667),(48,805),(32,929)]:
            icon(out,c,(x,y+(128-size)//2),size)
            text(d,(x,y+143),f'{size}px',17,'#81909A',latin=True)
        d.rounded_rectangle((1071,y+32,1135,y+96),radius=15,fill='#E7EDF0')
        icon(out,c,(1071,y+32),64,suffix='-monochrome')
        d.rounded_rectangle((1243,y+32,1307,y+96),radius=15,fill='#253D43')
        mono=Image.open(ROOT/f"{c['id']}-monochrome.png").convert('RGBA').resize((64,64),Image.Resampling.LANCZOS)
        white=Image.new('RGBA',(64,64),'#F2F6F8');white.putalpha(mono.getchannel('A'))
        out.paste(white,(1243,y+32),white)
        text(d,(1058,y+143),'单色 · 浅底',17,'#81909A')
        text(d,(1230,y+143),'单色 · 深底',17,'#81909A')
    text(d,(67,1206),'设计提案：尚未写入正式应用资源；玻璃 PNG 为材质成稿，SVG 保留可编辑图形。',21,'#73828B')
    out.save(ROOT/'glass-size-and-mask-check.png',optimize=True)


if __name__=='__main__':
    for c in DATA:
        render(c)
    board()
    qa()
    print(json.dumps({'overview':str(ROOT/'glass-icon-studies.png'),'qa':str(ROOT/'glass-size-and-mask-check.png')},ensure_ascii=False))
