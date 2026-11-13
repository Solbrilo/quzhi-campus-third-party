"""Original vector marks rendered as softly lit glass and satin surfaces."""
from pathlib import Path
import json
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.ndimage import distance_transform_edt,gaussian_filter,shift

ROOT=Path(__file__).resolve().parent
DATA=json.loads((ROOT/'concepts.json').read_text(encoding='utf-8'))
N=1024
Y,X=np.mgrid[0:N,0:N].astype(np.float32)
U=X/(N-1);V=Y/(N-1)

def rgb(s):return np.array([int(s[k:k+2],16) for k in (1,3,5)],dtype=np.float32)/255
def mix(a,b,t):return np.asarray(a)*(1-t[...,None])+np.asarray(b)*t[...,None]
def blend(a,b,t):return mix(a,b,np.clip(t,0,1))
def ramp(colors,t):return np.stack([np.interp(t,[0,.35,.69,1],[rgb(s)[k] for s in colors]) for k in range(3)],axis=-1).astype(np.float32)
def img(a):return Image.fromarray((np.clip(a,0,1)*255+.5).astype(np.uint8))

def render(c):
    dark=c['id']=='03-aperture'
    bg=mix(rgb(c['bg'][0]),rgb(c['bg'][1]),.72*V+.28*U)
    field=np.exp(-(((U-.33)/.57)**2+((V-.25)/.67)**2))
    bg=np.clip(bg+field[...,None]*(.035 if dark else .018),0,1)
    out=bg.copy();front=np.zeros((N,N,4),np.float32)
    for j,p in enumerate(c['paths']):
        a=np.asarray(Image.open(ROOT/f"{c['id']}-mask-{j}.png").convert('RGBA'))[...,3].astype(np.float32)/255
        dist=distance_transform_edt(a>.5).astype(np.float32)
        bevel=11.5 if c['finish']=='satin' else 17
        h=bevel*np.sin(np.minimum(dist/bevel,1)*np.pi/2)
        h+=2.3*(1-np.exp(-dist/22))
        gy,gx=np.gradient(gaussian_filter(h,.7));norm=np.sqrt(gx*gx+gy*gy+1)
        nx=-gx/norm;ny=-gy/norm;nz=1/norm
        diffuse=np.clip(nx*-.44+ny*-.56+nz*.68,0,1)
        spec=np.clip(nx*-.26+ny*-.34+nz*.904,0,1)**48
        fill=np.clip(nx*.46+ny*.22+nz*.86,0,1)**60
        fresnel=(1-nz)**.65
        face=ramp(c['palette'][j%len(c['palette'])],V*.83+U*.17)
        face*= (.81+.28*diffuse)[...,None]
        # Gently varying reflected light across the surface, with no decorative particles.
        reflection=np.exp(-((V-(.25+.22*U+.055*np.sin(U*5)))/.11)**2)
        face+=reflection[...,None]*(.035 if c['finish']=='satin' else .065)
        face=blend(face,rgb(c['edge']),fresnel*(.38 if c['finish']=='satin' else .62))
        face+=spec[...,None]*(.26 if c['finish']=='satin' else .5)
        face+=fill[...,None]*rgb(c['edge'])*(.07 if c['finish']=='satin' else .16)
        # A dim narrow region inside the polished rim gives the glass visual thickness.
        inner=np.exp(-((dist-(bevel*.85))/3.0)**2)
        face*= (1-inner*(.035 if c['finish']=='satin' else .10))[...,None]
        shadow=shift(gaussian_filter(a,16),[17,7],order=1,mode='constant')*.18
        contact=shift(gaussian_filter(a,3.5),[5,2],order=1,mode='constant')*.18
        out=blend(out,rgb(c['shadow']),shadow+contact)
        # The side wall uses the same silhouette and a small physical offset.
        side=shift(a,[5,1.5],order=1,mode='constant')
        sidecolor=ramp(c['palette'][j%len(c['palette'])],V*.6+U*.4)*.60
        out=blend(out,sidecolor,side*.7)
        out=blend(out,np.clip(face,0,1),a)
        front[...,:3]=blend(front[...,:3],np.clip(face,0,1),a)
        front[...,3]=a+front[...,3]*(1-a)
    img(out).save(ROOT/f"{c['id']}.png",optimize=True)
    front[...,:3]/=np.maximum(front[...,3:4],.001)
    img(front).save(ROOT/f"{c['id']}-foreground.png",optimize=True)
    mask=Image.new('L',(N*2,N*2));ImageDraw.Draw(mask).rounded_rectangle((0,0,N*2-1,N*2-1),radius=440,fill=255)
    pre=img(out).convert('RGBA');pre.putalpha(mask.resize((N,N),Image.Resampling.LANCZOS))
    pre.save(ROOT/f"{c['id']}-preview.png",optimize=True)
    for s in (512,64,48,32):pre.resize((s,s),Image.Resampling.LANCZOS).save(ROOT/f"{c['id']}-{s}.png")

FONTS=Path('C:/Windows/Fonts')
INK='#32333C';MUTED='#89868A';PAPER='#F6F3EF'
def font(s,serif=False):return ImageFont.truetype(str(FONTS/('STSONG.TTF' if serif else 'msyh.ttc')),s)
def text(draw,xy,s,size=24,color=INK,serif=False):draw.text(xy,s,font=font(size,serif),fill=color,anchor='lt')
def paste(canvas,c,xy,size,suffix='-preview'):
    im=Image.open(ROOT/(c['id']+suffix+'.png')).convert('RGBA').resize((size,size),Image.Resampling.LANCZOS)
    canvas.paste(im,xy,im)

def board():
    canvas=Image.new('RGB',(2130,1280),PAPER);d=ImageDraw.Draw(canvas)
    text(d,(77,53),'YUAI  /  APP ICON STUDIES',19,MUTED)
    text(d,(74,103),'浴爱 · 流动中的温柔',62,INK,True)
    text(d,(79,190),'以曲面、留白与光线，表达温度和相互包容。',25,MUTED)
    for i,c in enumerate(DATA):
        x=76+666*i;y=276
        d.rounded_rectangle((x,y,x+642,1157),radius=14,fill='#FFFDFB',outline='#E3DDD8')
        text(d,(x+29,y+24),f'0{i+1}',22,MUTED)
        text(d,(x+420,y+27),c['roman'],17,MUTED)
        paste(canvas,c,(x+51,y+69),540)
        text(d,(x+29,y+634),c['title'],43,INK,True)
        for j,s in enumerate(c['notes']):text(d,(x+30,y+706+36*j),s,22,MUTED)
        d.line((x+30,y+797,x+612,y+797),fill='#E7E1DC')
        for s,offset in [(64,30),(48,145),(32,244)]:paste(canvas,c,(x+offset,y+819+(64-s)//2),s)
        paste(canvas,c,(x+360,y+824),54,'-geometry')
    text(d,(79,1205),'01 拥流：珊瑚环抱　／　02 相融：冷暖交汇　／　03 心隙：心形透空',24,MUTED)
    canvas.save(ROOT/'overview.png',optimize=True)

def size_board():
    canvas=Image.new('RGB',(1650,1040),PAPER);d=ImageDraw.Draw(canvas)
    text(d,(62,51),'浴爱 · 小尺寸与轮廓',47,INK,True)
    text(d,(65,123),'64 / 48 / 32 像素，以及圆形遮罩与单色版本。',22,MUTED)
    for i,c in enumerate(DATA):
        y=228+242*i
        text(d,(64,y+49),c['title'],32,INK,True)
        paste(canvas,c,(239,y),146)
        im=Image.open(ROOT/(c['id']+'.png')).resize((146,146),Image.Resampling.LANCZOS)
        mask=Image.new('L',(584,584));ImageDraw.Draw(mask).ellipse((0,0,583,583),fill=255)
        canvas.paste(im,(424,y),mask.resize((146,146),Image.Resampling.LANCZOS))
        for s,x in [(64,694),(48,818),(32,929)]:
            paste(canvas,c,(x,y+(146-s)//2),s)
            text(d,(x,y+172),str(s)+' px',17,MUTED)
        paste(canvas,c,(1096,y+33),80,'-geometry')
        d.rounded_rectangle((1301,y+32,1383,y+114),radius=19,fill='#323D48')
        mono=Image.open(ROOT/(c['id']+'-geometry.png')).convert('RGBA').resize((80,80),Image.Resampling.LANCZOS)
        white=Image.new('RGBA',(80,80),'#F9F4EC');white.putalpha(mono.getchannel('A'));canvas.paste(white,(1302,y+33),white)
        d.line((65,y+215,1585,y+215),fill='#DED8D3')
    canvas.save(ROOT/'size-check.png',optimize=True)

if __name__=='__main__':
    for c in DATA:render(c)
    board();size_board();print('Rendered 3 Yuai icon proposals')
