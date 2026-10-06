package ru.homebooks.library;

/** Normalized corners, clockwise from the upper left. Reject crossed/degenerate crops. */
final class CoverCrop {
    static float[] full(){return new float[]{0,0,1,0,1,1,0,1};}
    static boolean valid(float[] p){
        if(p==null||p.length!=8)return false;
        for(float v:p)if(Float.isNaN(v)||Float.isInfinite(v)||v<0||v>1)return false;
        float area=0;
        for(int i=0;i<4;i++){
            int a=2*i,b=2*((i+1)%4),c=2*((i+2)%4);
            float cross=(p[b]-p[a])*(p[c+1]-p[b+1])-(p[b+1]-p[a+1])*(p[c]-p[b]);
            if(cross<0.002f)return false;
            area+=p[a]*p[b+1]-p[b]*p[a+1];
        }
        return area>0.02f;
    }
}
