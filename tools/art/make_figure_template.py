"""Draws the ComfyUI img2img templates for figures: a plain mannequin in the three poses of a people
sheet (standing, step with the near leg in front, step with the far leg in front), 1536x512 on magenta.
The far arm and leg are darker so the model sees which leg is in front.

Run: python3 tools/art/make_figure_template.py <output folder>
"""
import os
import math, sys
OUTDIR = sys.argv[1] if len(sys.argv) > 1 else '.'
from PIL import Image, ImageDraw
SS=2  # supersampling
W,H=1536,512
INK=(20,18,30); SKIN=(240,190,150); SKIN_D=(205,150,115)
CL=(150,150,155); CL_D=(105,105,112); PANTS=(120,120,128); PANTS_D=(80,80,88); SHOE=(70,70,78); SHOE_D=(45,45,52); HAIR=(95,95,100)
OL=7
def P(x,y): return (x*SS,y*SS)
def limb(d,pts,w,col):
    for c,ww in ((INK,w+2*OL),(col,w)):
        for a,b in zip(pts,pts[1:]): d.line([P(*a),P(*b)],fill=c,width=ww*SS)
        for p in pts:
            r=ww/2; d.ellipse([P(p[0]-r,p[1]-r),P(p[0]+r,p[1]+r)],fill=c)
def seg(p,ang,l): return (p[0]+l*math.sin(math.radians(ang)), p[1]+l*math.cos(math.radians(ang)))
def shoe(d,ankle,col):
    x,y=ankle; box=[x-22,y-8,x+40,y+22]
    d.rounded_rectangle([P(box[0]-OL,box[1]-OL),P(box[2]+OL,box[3]+OL)],radius=14*SS,fill=INK)
    d.rounded_rectangle([P(*box[:2]),P(*box[2:])],radius=12*SS,fill=col)
def leg(d,hip,thigh,shin,col,scol,ground):
    knee=seg(hip,thigh,74); ank=seg(knee,shin,70)
    # stand the lowest foot on the ground
    limb(d,[hip,knee,ank],42,col); shoe(d,ank,scol); return ank
def arm(d,sh,up,lo,col):
    el=seg(sh,up,52); ha=seg(el,lo,46)
    limb(d,[sh,el],30,col); limb(d,[el,ha],26,col)
    r=15; d.ellipse([P(ha[0]-r-OL,ha[1]-r-OL),P(ha[0]+r+OL,ha[1]+r+OL)],fill=INK); d.ellipse([P(ha[0]-r,ha[1]-r),P(ha[0]+r,ha[1]+r)],fill=SKIN)
def figure(d,cx,pose,scale_shift=0):
    ground=476
    # pose: (near thigh, near shin, far thigh, far shin, near arm, far arm) angles in degrees, + = forward (right)
    nt,ns,ft,fs,na,fa=pose
    # hip height so the lower foot touches the ground
    def foot_y(t,s): return 74*math.cos(math.radians(t))+70*math.cos(math.radians(s))
    hipy=ground-22-max(foot_y(nt,ns),foot_y(ft,fs))
    hip=(cx,hipy)
    sh=(cx+4,hipy-112)
    arm(d,(sh[0]-6,sh[1]+8),fa,fa+15,CL_D)
    leg(d,(cx-6,hipy),ft,fs,PANTS_D,SHOE_D,ground)
    leg(d,(cx+6,hipy),nt,ns,PANTS,SHOE,ground)
    # torso
    tb=[cx-56,hipy-132,cx+58,hipy+20]
    d.rounded_rectangle([P(tb[0]-OL,tb[1]-OL),P(tb[2]+OL,tb[3]+OL)],radius=36*SS,fill=INK)
    d.rounded_rectangle([P(*tb[:2]),P(*tb[2:])],radius=32*SS,fill=CL)
    d.rectangle([P(tb[0],hipy-12),P(tb[2],tb[3]-8)],fill=PANTS)
    arm(d,(sh[0]+6,sh[1]+8),na,na+15,CL)
    # head (big, chibi) facing right
    hc=(cx+10,hipy-205); r=80
    d.ellipse([P(hc[0]-r-OL,hc[1]-r-OL),P(hc[0]+r+OL,hc[1]+r+OL)],fill=INK)
    d.ellipse([P(hc[0]-r,hc[1]-r),P(hc[0]+r,hc[1]+r)],fill=SKIN)
    # hair cap on the back/top
    d.chord([P(hc[0]-r,hc[1]-r),P(hc[0]+r,hc[1]+r)],170,320,fill=HAIR)
    d.ellipse([P(hc[0]-r,hc[1]-r+30),P(hc[0]-r+70,hc[1]+30)],fill=HAIR)
    # ear, eye, brow, nose, mouth
    d.ellipse([P(hc[0]-12,hc[1]+2),P(hc[0]+14,hc[1]+36)],fill=SKIN_D,outline=INK,width=4*SS)
    d.ellipse([P(hc[0]+42,hc[1]-14),P(hc[0]+56,hc[1]+6)],fill=INK)
    d.line([P(hc[0]+34,hc[1]-30),P(hc[0]+62,hc[1]-34)],fill=INK,width=6*SS)
    d.ellipse([P(hc[0]+66,hc[1]-2),P(hc[0]+94,hc[1]+24)],fill=INK); d.ellipse([P(hc[0]+70,hc[1]+2),P(hc[0]+90,hc[1]+20)],fill=SKIN)
    d.rectangle([P(hc[0]+60,hc[1]),P(hc[0]+74,hc[1]+22)],fill=SKIN)
    d.arc([P(hc[0]+48,hc[1]+26),P(hc[0]+80,hc[1]+50)],20,120,fill=INK,width=5*SS)
POSES={"stand":(4,0,-4,0,6,-6),"stepA":(28,8,-26,-38,-24,26),"stepB":(-26,-38,28,8,26,-24)}
def sheet(out,scale=1.0):
    img=Image.new("RGB",(W*SS,H*SS),(255,0,255)); d=ImageDraw.Draw(img)
    for i,k in enumerate(("stand","stepA","stepB")): figure(d,256+512*i,POSES[k])
    img=img.resize((W,H),Image.LANCZOS)
    if scale!=1.0:
        # shrink figures, keep the ground line
        small=img.resize((round(W*scale),round(H*scale)),Image.LANCZOS)
        can=Image.new("RGB",(W,H),(255,0,255))
        for i in range(3):
            cx=256+512*i; part=small.crop((round((cx-256)*scale),0,round((cx+256)*scale),small.height))
            can.paste(part,(cx-part.width//2,H-small.height-round(36*(1-scale))))
        img=can
    img.save(os.path.join(OUTDIR, out)); print(out)
sheet("vorlage_figur.png")
sheet("vorlage_figur_klein.png",0.78)
CL=(90,140,210); CL_D=(60,100,165); PANTS=(120,85,60); PANTS_D=(85,58,40); SHOE=(60,45,40); SHOE_D=(40,30,28); HAIR=(110,70,40)
sheet("vorlage_figur_bunt.png")
