"""Compose the new icon presentation and mask checks from final vector renders."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent
DATA=json.loads((ROOT/'concepts.json').read_text(encoding='utf-8'))
FONTS=Path('C:/Windows/Fonts')


def font(size,style='body'):
    return ImageFont.truetype(str(FONTS/{'body':'msyh.ttc','serif':'STSONG.TTF','latin':'bahnschrift.ttf'}[style]),size)


def text(d,xy,value,size=24,color='#D9E1E7',style='body'):
    d.text(xy,value,font=font(size,style),fill=color,anchor='lt')


def icon(canvas,c,xy,size,suffix='-preview'):
    im=Image.open(ROOT/(c['id']+suffix+'.png')).convert('RGBA').resize((size,size),Image.Resampling.LANCZOS)
    canvas.paste(im,xy,im)


def overview():
    canvas=Image.new('RGB',(2180,1820),'#141C25')
    d=ImageDraw.Draw(canvas)
    text(d,(80,51),'OPTICAL OBJECTS / A FRESH START',20,'#A3B2BF','latin')
    text(d,(77,102),'另起一笔',71,'#F0F2F3','serif')
    text(d,(82,208),'折页、交叠、光隙、棱花。让玻璃的透明，成为图形的一部分。',26,'#A3B2BF')
    text(d,(1690,143),'FOUR ICON STUDIES',22,'#A3B2BF','latin')
    positions=[(80,295),(1110,295),(80,1008),(1110,1008)]
    descriptions=[['“智”的 Z，折成一条通透的玻璃带。','留白与折面建立一笔连续的节奏。'],['像校园的窗，让两片玻璃轻轻相遇。','冷暖颜色只在交叠处发生变化。'],['两块不对称的玻璃，留下一道光隙。','安静、有张力，像自己的片刻空间。'],['一片稍稍旋转的晶体，像舒展的花。','以棱面与柔和的色散，表达温度。']]
    for i,(c,(x,y)) in enumerate(zip(DATA,positions)):
        w,h=990,675
        d.rounded_rectangle((x,y,x+w,y+h),radius=10,fill='#202B36',outline='#3A4754',width=1)
        d.rounded_rectangle((x+1,y+1,x+510,y+h-1),radius=9,fill=c['bg'][0])
        # Keep the panel boundary straight, with the outside corners rounded.
        d.rectangle((x+485,y+1,x+510,y+h-1),fill=c['bg'][0])
        text(d,(x+26,y+25),f"0{i+1} / {c['title']}",22,c['text'])
        icon(canvas,c,(x+24,y+105),464)
        rx=x+548
        text(d,(rx,y+52),c['name'],51,'#F1F2F3','serif')
        text(d,(rx,y+124),c['roman'],18,'#A3B2BF','latin')
        text(d,(rx,y+175),c['pronunciation'],20,'#A3B2BF')
        for j,line in enumerate(descriptions[i]):text(d,(rx,y+260+j*43),line,22,'#BAC7D1')
        d.line((rx,y+390,x+w-32,y+390),fill='#46525F')
        for size,offset in [(64,0),(48,106),(32,198)]:
            icon(canvas,c,(rx+offset,y+432+(64-size)//2),size)
            text(d,(rx+offset,y+520),f'{size} PX',17,'#A3B2BF','latin')
        geometry=Image.open(ROOT/(c['id']+'-geometry.png')).convert('RGBA').resize((52,52),Image.Resampling.LANCZOS)
        white=Image.new('RGBA',(52,52),'#E0EBEF');white.putalpha(geometry.getchannel('A'))
        canvas.paste(white,(rx+294,y+438),white)
        text(d,(rx+286,y+520),'MONO',17,'#A3B2BF','latin')
        text(d,(rx,y+602),'ORIGINAL FORM / GLASS',16,'#8FA3B2','latin')
    text(d,(82,1743),'名称与图标可交叉搭配 · 完整材质 SVG / 1024 PNG / 透明前景 / 单色轮廓',22,'#A3B2BF')
    canvas.save(ROOT/'overview.png',optimize=True)


def checks():
    canvas=Image.new('RGB',(1700,1260),'#F0F2F4')
    d=ImageDraw.Draw(canvas)
    text(d,(66,48),'小尺寸与遮罩检查',48,'#263C4B','serif')
    text(d,(67,125),'圆角、圆形遮罩，以及实际 64 / 48 / 32 像素与单色轮廓。',22,'#74818D')
    for i,c in enumerate(DATA):
        y=211+i*232
        text(d,(65,y+52),c['name'],32,'#263C4B')
        icon(canvas,c,(250,y),150)
        original=Image.open(ROOT/(c['id']+'.png')).convert('RGB').resize((150,150),Image.Resampling.LANCZOS)
        mask=Image.new('L',(600,600));ImageDraw.Draw(mask).ellipse((0,0,599,599),fill=255)
        canvas.paste(original,(445,y),mask.resize((150,150),Image.Resampling.LANCZOS))
        for size,x in [(64,730),(48,862),(32,985)]:
            icon(canvas,c,(x,y+(150-size)//2),size)
            text(d,(x,y+177),str(size)+'px',18,'#74818D','latin')
        icon(canvas,c,(1110,y+43),64,suffix='-geometry')
        d.rounded_rectangle((1286,y+43,1350,y+107),radius=15,fill='#263C4B')
        mono=Image.open(ROOT/(c['id']+'-geometry.png')).convert('RGBA').resize((64,64),Image.Resampling.LANCZOS)
        white=Image.new('RGBA',(64,64),'#F1F5F7');white.putalpha(mono.getchannel('A'))
        canvas.paste(white,(1286,y+43),white)
        text(d,(1095,y+177),'单色 · 浅底',18,'#74818D')
        text(d,(1271,y+177),'单色 · 深底',18,'#74818D')
        d.line((65,y+217,1635,y+217),fill='#D7DDE2')
    text(d,(67,1190),'概念提案 · 未写入应用资源 · 图标与名称可自由组合',22,'#74818D')
    canvas.save(ROOT/'size-check.png',optimize=True)


if __name__=='__main__':
    overview();checks()
    print(json.dumps({'overview':str(ROOT/'overview.png'),'check':str(ROOT/'size-check.png')},ensure_ascii=False))
