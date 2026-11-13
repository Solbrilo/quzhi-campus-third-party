"""Local inpainting for the approved pale-blue icon, without regeneration."""
from pathlib import Path
import argparse
import numpy as np
from PIL import Image,ImageDraw
from skimage.restoration import inpaint

def retouch(source,destination,mask_out=None):
    image=Image.open(source).convert('RGB')
    width,height=image.size
    mask=Image.new('L',image.size)
    draw=ImageDraw.Draw(mask)
    def ellipse(cx,cy,rx,ry):
        draw.ellipse(((cx-rx)*width/200,(cy-ry)*height/200,
                      (cx+rx)*width/200,(cy+ry)*height/200),fill=255)
    ellipse(94.5,76.5,16.5,16)
    draw.polygon([(119*width/200,49*height/200),
                  (132*width/200,49*height/200),
                  (144*width/200,72*height/200),
                  (141*width/200,83*height/200),
                  (124*width/200,82*height/200),
                  (116*width/200,65*height/200)],fill=255)
    array=np.asarray(image).astype(np.float64)/255
    m=np.asarray(mask)>0
    restored=inpaint.inpaint_biharmonic(array,m,channel_axis=-1)
    result=np.asarray(image).copy()
    result[m]=np.clip(restored[m]*255+.5,0,255).astype(np.uint8)
    Image.fromarray(result).save(destination)
    if mask_out:mask.save(mask_out)
    assert np.array_equal(result[~m],np.asarray(image)[~m])
    print(f'Retouched {width} x {height}; {int(m.sum())} masked pixels; outside mask unchanged')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('source',type=Path)
    parser.add_argument('destination',type=Path)
    parser.add_argument('--mask',type=Path)
    args=parser.parse_args()
    retouch(args.source,args.destination,args.mask)
