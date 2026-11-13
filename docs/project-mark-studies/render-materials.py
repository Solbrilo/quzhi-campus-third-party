"""Raster material study. Bevel normals come from editable vector masks."""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageFilter, ImageDraw
from scipy.ndimage import distance_transform_edt, gaussian_filter, shift

ROOT=Path(__file__).resolve().parent
DATA=json.loads((ROOT/'concepts.json').read_text(encoding='utf-8'))
N=1024
Y,X=np.mgrid[0:N,0:N].astype(np.float32)
U=X/(N-1);V=Y/(N-1)

def rgb(value):
    return np.array([int(value[i:i+2],16) for i in (1,3,5)],dtype=np.float32)/255

def mix(a,b,t):
    return np.asarray(a)*(1-t[...,None])+np.asarray(b)*t[...,None]

def ramp(stops,t):
    return np.stack([np.interp(t,[s[0] for s in stops],[rgb(s[1])[k] for s in stops]) for k in range(3)],axis=-1).astype(np.float32)

def over(dst,src,alpha):
    return src*alpha[...,None]+dst*(1-alpha[...,None])

def image(a):
    return Image.fromarray((np.clip(a,0,1)*255+.5).astype('uint8'))

def render(c):
    dark=c.get('style')=='luminous'
    bg=mix(rgb(c['bg'][0]),rgb(c['bg'][1]),V*.75+U*.25)
    light=np.exp(-(((U-.3)/.6)**2+((V-.2)/.7)**2))* (.045 if dark else .02)
    bg=np.clip(bg+light[...,None],0,1)
    # Soft color field, deliberately behind the artwork rather than decorative particles.
    field=np.exp(-(((U-.50)/.28)**2+((V-.61)/.27)**2))
    if dark:bg+=field[...,None]*np.array([.006,.03,.04])
    out=bg.copy();front=np.zeros((N,N,4),dtype=np.float32)
    for j,p in enumerate(c['paths']):
        alpha=np.array(Image.open(ROOT/f"{c['id']}-mask-{j}.png").convert('RGBA'))[...,3].astype(np.float32)/255
        inside=alpha>.5
        dist=distance_transform_edt(inside).astype(np.float32)
        # Rounded polished edge; broad center remains almost planar.
        bevel=10.0 if c.get('style')=='ceramic' else 13.0
        t=np.minimum(dist/bevel,1)
        height=bevel*np.sin(t*np.pi/2)
        height=gaussian_filter(height,.8)
        dy,dx=np.gradient(height)
        norm=np.sqrt(dx*dx+dy*dy+1)
        nx=-dx/norm;ny=-dy/norm;nz=1/norm
        lambert=np.clip(nx*-.48+ny*-.62+nz*.62,0,1)
        spec=np.clip(nx*-.27+ny*-.37+nz*.888,0,1)**45
        spec2=np.clip(nx*.48+ny*.20+nz*.85,0,1)**72
        fresnel=(1-nz)**.65
        if c.get('style')=='luminous':
            if j==0:
                color=ramp([(0,'#EAFFF9'),(.24,'#B5EFE9'),(.5,'#6BC5CC'),(.69,'#B3F1E3'),(1,'#44A0C1')],V+.05*U)
            else:
                color=ramp([(0,'#92C8F5'),(.24,'#79B1E6'),(.5,'#3C88C0'),(.75,'#7DB5CC'),(1,'#81D5D7')],V+.05*U)
            color*= (.86+.19*lambert)[...,None]
            edgecolor=ramp([(0,'#ECFFEF'),(.45,'#9AEEE2'),(.78,'#7FC6FF'),(1,'#C5BBFF')],U*.6+V*.4)
            color=over(color,edgecolor,np.clip(fresnel*.7,0,.8))
            color+=spec[...,None]*.55+spec2[...,None]*np.array([.11,.19,.23])
        elif c.get('style')=='two-tone':
            if j==0:
                color=ramp([(0,'#EAD7FF'),(.34,'#B4AAF6'),(.68,'#797ADC'),(1,'#C5CEFF')],V*.8+U*.2)
            else:
                color=ramp([(0,'#638AFA'),(.42,'#4D6BDA'),(.67,'#303F9B'),(1,'#657BBE')],V)
            color*= (.84+.24*lambert)[...,None]
            edgecolor=ramp([(0,'#FFFFFF'),(.4,'#D1CEFF'),(.7,'#A7BCFF'),(1,'#D7C7F1')],U*.45+V*.55)
            color=over(color,edgecolor,np.clip(fresnel*.58,0,.75))
            color+=spec[...,None]*.52+spec2[...,None]*np.array([.13,.11,.25])
        else:
            color=ramp([(0,'#F88D5C'),(.3,'#E66139'),(.60,'#CE432B'),(1,'#A92A25')],V*.8+U*.2)
            color*= (.88+.20*lambert)[...,None]
            color=over(color,rgb('#FFD0A0'),fresnel*.35)
            color+=spec[...,None]*.29+spec2[...,None]*np.array([.10,.04,.015])
        # A reflected softbox adds a quiet curved band across the material.
        band=np.exp(-((V-(.24+.18*U+.045*np.sin(U*5)))/.06)**2)
        color+=band[...,None]*(.022 if c.get('style')=='ceramic' else .06)
        # Two shadow scales establish height without a floating outer halo.
        shadow=shift(gaussian_filter(alpha,15),[16,6],order=1,mode='constant')*.20
        contact=shift(gaussian_filter(alpha,3.2),[5,2],order=1,mode='constant')*.18
        shadow_col=rgb('#030F22') if dark else rgb('#454561' if c.get('style')=='two-tone' else '#743521')
        out=over(out,shadow_col,np.clip(shadow+contact,0,.4))
        out=over(out,np.clip(color,0,1),alpha)
        front[...,:3]=over(front[...,:3],np.clip(color,0,1),alpha)
        front[...,3]=alpha+front[...,3]*(1-alpha)
    # Subpixel canvas edge in previews only; full artwork is opaque and unmasked.
    image(out).save(ROOT/f"{c['id']}.png",optimize=True)
    fg=image(front)
    # Unpremultiply edge RGB for a clean transparent export.
    fg_arr=front.copy();fg_arr[...,:3]/=np.maximum(front[...,3:4],.001)
    image(fg_arr).save(ROOT/f"{c['id']}-foreground.png",optimize=True)
    mask=Image.new('L',(N*2,N*2));ImageDraw.Draw(mask).rounded_rectangle((0,0,N*2-1,N*2-1),radius=440,fill=255)
    mask=mask.resize((N,N),Image.Resampling.LANCZOS)
    preview=image(out).convert('RGBA');preview.putalpha(mask)
    preview.save(ROOT/f"{c['id']}-preview.png",optimize=True)
    for size in [512,64,48,32]:preview.resize((size,size),Image.Resampling.LANCZOS).save(ROOT/f"{c['id']}-{size}.png")
    return image(out)

if __name__=='__main__':
    imgs=[render(c) for c in DATA]
    canvas=Image.new('RGB',(1536,512),'#F4F5F7')
    for i,im in enumerate(imgs):canvas.paste(im.resize((512,512),Image.Resampling.LANCZOS),(512*i,0))
    canvas.save(ROOT/'material-draft.png')
    print('Rendered',len(imgs),'material studies')
