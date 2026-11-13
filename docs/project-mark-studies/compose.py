"""Presentation, detail notes, and mask QA for bespoke project marks."""
from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parent
DATA=json.loads((ROOT/'concepts.json').read_text(encoding='utf-8'))
FONTS=Path('C:/Windows/Fonts')
INK='#2A3040';MUTED='#7B7E89';PAPER='#F5F4F2';LINE='#DFDFE4'

def font(s,style='body'):
    return ImageFont.truetype(str(FONTS/{'body':'msyh.ttc','serif':'STSONG.TTF','latin':'bahnschrift.ttf'}[style]),s)

def text(d,xy,value,size=24,color=INK,style='body'):
    d.text(xy,value,font=font(size,style),fill=color,anchor='lt')

def icon(canvas,c,xy,size,suffix='-preview'):
    image=Image.open(ROOT/(c['id']+suffix+'.png')).convert('RGBA').resize((size,size),Image.Resampling.LANCZOS)
    canvas.paste(image,xy,image)

def overview():
    canvas=Image.new('RGB',(2130,1410),PAPER);d=ImageDraw.Draw(canvas)
    text(d,(76,49),'APP IDENTITY / THREE NEW DIRECTIONS',20,MUTED,'latin')
    text(d,(72,100),'三种轮廓，三种性格',64,INK,'serif')
    text(d,(78,192),'从成熟应用的识别方式出发，重新探索品牌轮廓、配色与材质。',27,MUTED)
    text(d,(1690,133),'QZXY / ICON STUDIES',22,MUTED,'latin')
    lines=[c['captions'] for c in DATA]
    for i,c in enumerate(DATA):
        x=76+i*666;y=280;w=642
        d.rounded_rectangle((x,y,x+w,1268),radius=8,fill='#FCFDFB',outline=LINE,width=1)
        text(d,(x+28,y+25),f'0{i+1}',25,MUTED,'latin')
        text(d,(x+w-130,y+29),c['title'],21,MUTED)
        icon(canvas,c,(x+56,y+83),530)
        text(d,(x+29,y+649),c['name'],48,INK,'serif')
        for j,s in enumerate(lines[i]):text(d,(x+29,y+735+j*38),s,23,MUTED)
        d.line((x+29,y+838,x+w-29,y+838),fill=LINE)
        for size,ox in [(64,29),(48,145),(32,246)]:
            icon(canvas,c,(x+ox,y+874+(64-size)//2),size)
            text(d,(x+ox,y+951),f'{size} PX',17,MUTED,'latin')
        mono=Image.open(ROOT/(c['id']+'-geometry.png')).convert('RGBA').resize((54,54),Image.Resampling.LANCZOS)
        canvas.paste(mono,(x+365,y+879),mono)
        text(d,(x+360,y+951),'MONO',17,MUTED,'latin')
    text(d,(78,1324),'趣序：QZ 连字　／　趣合：双向嵌合　／　知沐：字形重构',25)
    canvas.save(ROOT/'overview.png',optimize=True)

def details():
    canvas=Image.new('RGB',(2130,1630),'#14272E');d=ImageDraw.Draw(canvas)
    text(d,(76,51),'FORM / COLOR / CHARACTER',20,'#A6BDC0','latin')
    text(d,(73,102),'把品牌线索，放进图形本身',61,'#EDF4F3','serif')
    text(d,(79,195),'连字、双向曲面与当代字印，分别建立不同的识别方式。',25,'#A6BDC0')
    positions=[(76,294),(742,294),(1408,294)]
    for i,(c,(x,y)) in enumerate(zip(DATA,positions)):
        d.rounded_rectangle((x,y,x+642,1516),radius=8,fill='#203840',outline='#45616A',width=1)
        text(d,(x+28,y+28),f"0{i+1} / {c['name']} · {c['title']}",31,'#E3EFED')
        icon(canvas,c,(x+46,y+100),550)
        text(d,(x+30,y+696),c['tagline'],22,'#CBDCD9')
        for j,(heading,body) in enumerate(c['points']):
            cy=y+794+j*173
            d.line((x+30,cy-25,x+612,cy-25),fill='#49636A')
            text(d,(x+30,cy),f'0{j+1}',21,'#8CB9BA','latin')
            text(d,(x+82,cy-4),heading,26,'#F0F4EF')
            # Wrap by measured width rather than character count.
            line='';rows=[]
            for ch in body:
                if d.textlength(line+ch,font=font(23))>526 and ch not in '。，、；：！？）》”':
                    rows.append(line);line=ch
                else:line+=ch
            if line:rows.append(line)
            for k,row in enumerate(rows):text(d,(x+82,cy+50+k*38),row,23,'#AFC6C7')
    canvas.save(ROOT/'detail-notes.png',optimize=True)

def size_check():
    canvas=Image.new('RGB',(1700,1060),PAPER);d=ImageDraw.Draw(canvas)
    text(d,(65,50),'小尺寸与遮罩检查',47,INK,'serif')
    text(d,(67,123),'对照完整轮廓、交接间隙和小尺寸辨识度。',23,MUTED)
    for i,c in enumerate(DATA):
        y=220+i*245
        text(d,(65,y+48),c['name'],32)
        icon(canvas,c,(250,y),150)
        im=Image.open(ROOT/(c['id']+'.png')).convert('RGB').resize((150,150),Image.Resampling.LANCZOS)
        mask=Image.new('L',(600,600),0);ImageDraw.Draw(mask).ellipse((0,0,599,599),fill=255)
        canvas.paste(im,(444,y),mask.resize((150,150),Image.Resampling.LANCZOS))
        for size,x in [(64,730),(48,865),(32,986)]:
            icon(canvas,c,(x,y+(150-size)//2),size)
            text(d,(x,y+178),f'{size}px',18,MUTED,'latin')
        icon(canvas,c,(1123,y+43),64,suffix='-geometry')
        d.rounded_rectangle((1302,y+43,1366,y+107),radius=15,fill=INK)
        mono=Image.open(ROOT/(c['id']+'-geometry.png')).convert('RGBA').resize((64,64),Image.Resampling.LANCZOS)
        white=Image.new('RGBA',(64,64),'#EFF5F0');white.putalpha(mono.getchannel('A'))
        canvas.paste(white,(1302,y+43),white)
        text(d,(1107,y+178),'单色 · 浅底',18,MUTED)
        text(d,(1286,y+178),'单色 · 深底',18,MUTED)
        d.line((65,y+217,1635,y+217),fill=LINE)
    text(d,(67,1000),'设计研究 · 尚未替换正式应用资源',22,MUTED)
    canvas.save(ROOT/'size-check.png',optimize=True)

def silhouette_sheet():
    canvas=Image.new('RGB',(2130,990),'#F7F8F5');d=ImageDraw.Draw(canvas)
    text(d,(77,51),'FORM BEFORE MATERIAL',20,MUTED,'latin')
    text(d,(73,103),'先看轮廓，再看材质',57,INK,'serif')
    for i,c in enumerate(DATA):
        x=76+i*666
        d.rounded_rectangle((x,244,x+642,902),radius=6,fill='#FFFFFF',outline=LINE)
        mono=Image.open(ROOT/(c['id']+'-geometry.png')).convert('RGBA').resize((500,500),Image.Resampling.LANCZOS)
        ink=Image.new('RGBA',(500,500),'#183A46');ink.putalpha(mono.getchannel('A'))
        canvas.paste(ink,(x+71,273),ink)
        text(d,(x+29,808),c['name'],37,INK,'serif')
        text(d,(x+483,818),c['title'],22,MUTED)
    canvas.save(ROOT/'silhouette-sheet.png',optimize=True)

if __name__=='__main__':
    overview();details();size_check();silhouette_sheet()
    print(json.dumps({'overview':str(ROOT/'overview.png'),'details':str(ROOT/'detail-notes.png')},ensure_ascii=False))
