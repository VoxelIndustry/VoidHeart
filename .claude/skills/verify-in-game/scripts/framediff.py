"""Counts pixels that change strongly between consecutive sweep frames in each given folder of *sweep*.png files."""
import sys,zlib,struct,glob,os
def read(p):
    d=open(p,'rb').read();i=8;w=h=None;idat=b''
    while i<len(d):
        n,t=struct.unpack('>I4s',d[i:i+8]);c=d[i+8:i+8+n];i+=12+n
        if t==b'IHDR':w,h,b,ct=struct.unpack('>IIBB',c[:10]);assert b==8;ch={6:4,2:3}[ct]
        elif t==b'IDAT':idat+=c
    r=zlib.decompress(idat);rows=[];prev=bytearray(w*ch);o=0
    for y in range(h):
        f=r[o];l=bytearray(r[o+1:o+1+w*ch]);o+=1+w*ch
        for x in range(w*ch):
            a=l[x-ch] if x>=ch else 0;b=prev[x];cc=prev[x-ch] if x>=ch else 0
            if f==1:l[x]=(l[x]+a)&255
            elif f==2:l[x]=(l[x]+b)&255
            elif f==3:l[x]=(l[x]+(a+b)//2)&255
            elif f==4:
                pa,pb,pc=abs(b-cc),abs(a-cc),abs(a+b-2*cc);pr=a if pa<=pb and pa<=pc else (b if pb<=pc else cc);l[x]=(l[x]+pr)&255
        rows.append(l);prev=l
    return w,h,ch,rows
for run in sys.argv[1:]:
    fs=sorted(glob.glob(os.path.join(run,'*sweep*.png')))
    ims=[read(f) for f in fs];out=[]
    for A,B in zip(ims,ims[1:]):
        w,h,ch,a=A;_,_,_,b=B;big=0
        for y in range(h):
            ra,rb=a[y],b[y]
            for x in range(0,w*ch,ch):
                if abs(ra[x]-rb[x])+abs(ra[x+1]-rb[x+1])+abs(ra[x+2]-rb[x+2])>90:big+=1
        out.append(big)
    print(os.path.basename(run),'pixels changing strongly per frame pair:',out,'mean',sum(out)//max(1,len(out)))
