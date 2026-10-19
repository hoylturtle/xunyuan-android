package com.btsou.search;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small, consistent line icons that do not depend on the device emoji font. */
final class UiIcon extends View {
    private final String name;
    private final int color;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    UiIcon(Context c,String name,int color){super(c);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();float scale=Math.min(getWidth(),getHeight())/24f;c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);
        p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(name){
            case "search":c.drawCircle(10.5f,10.5f,6.3f,p);c.drawLine(15,15,20,20,p);break;
            case "bookmark":{Path path=new Path();path.moveTo(6,4);path.lineTo(18,4);path.lineTo(18,21);path.lineTo(12,17);path.lineTo(6,21);path.close();c.drawPath(path,p);break;}
            case "sources":{Path path=new Path();path.moveTo(3,8);path.lineTo(12,3);path.lineTo(21,8);path.lineTo(12,13);path.close();c.drawPath(path,p);path.reset();path.moveTo(3,12);path.lineTo(12,17);path.lineTo(21,12);c.drawPath(path,p);path.reset();path.moveTo(3,16);path.lineTo(12,21);path.lineTo(21,16);c.drawPath(path,p);break;}
            case "settings":for(int i=0;i<3;i++){float y=5+i*7;float x=i==1?15:8;c.drawLine(3,y,x-3,y,p);c.drawLine(x+3,y,21,y,p);c.drawCircle(x,y,2.7f,p);}break;
            case "arrow":c.drawLine(5,12,19,12,p);c.drawLine(14,7,19,12,p);c.drawLine(14,17,19,12,p);break;
            case "chevron":c.drawLine(9,6,15,12,p);c.drawLine(15,12,9,18,p);break;
            case "copy":c.drawRoundRect(8,8,20,21,2,2,p);{Path path=new Path();path.moveTo(15,5);path.lineTo(15,3);path.lineTo(3,3);path.lineTo(3,16);path.lineTo(5,16);c.drawPath(path,p);}break;
            case "cloud":{Path path=new Path();path.moveTo(6,18);path.cubicTo(-1,18,0,8,7,9);path.cubicTo(8,-1,23,3,20,11);path.cubicTo(27,14,22,19,18,18);path.close();c.drawPath(path,p);break;}
            case "shield":{Path path=new Path();path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,14);path.quadTo(18,20,12,23);path.quadTo(6,20,4,14);path.lineTo(3,6);path.close();c.drawPath(path,p);c.drawLine(8,12,11,15,p);c.drawLine(11,15,16,9,p);break;}
            case "close":c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);break;
            case "history":c.drawArc(4,4,21,21,-140,290,false,p);c.drawLine(3,4,3,10,p);c.drawLine(3,10,9,10,p);c.drawLine(12,8,12,13,p);c.drawLine(12,13,16,15,p);break;
            case "download":c.drawLine(12,3,12,16,p);c.drawLine(7,11,12,16,p);c.drawLine(17,11,12,16,p);c.drawLine(4,17,4,21,p);c.drawLine(4,21,20,21,p);c.drawLine(20,21,20,17,p);break;
            case "upload":c.drawLine(12,16,12,3,p);c.drawLine(7,8,12,3,p);c.drawLine(17,8,12,3,p);c.drawLine(4,17,4,21,p);c.drawLine(4,21,20,21,p);c.drawLine(20,21,20,17,p);break;
            case "link":c.drawArc(1,9,14,22,90,230,false,p);c.drawArc(10,1,23,14,-90,230,false,p);c.drawLine(8,16,16,8,p);break;
            default:{c.drawCircle(12,12,9,p);Path path=new Path();path.moveTo(16,7);path.lineTo(14,14);path.lineTo(7,17);path.lineTo(10,10);path.close();c.drawPath(path,p);}
        }
        c.restore();
    }
}
