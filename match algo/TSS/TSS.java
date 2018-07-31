package com.example.zmz.tss_api23;

/**
 * Created by zmz on 2018/6/23.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

class TestBigDecimal {
    //keep significant figures
    public static double significand(double oldDouble, int scale) {
        RoundingMode rMode =null;
        if(oldDouble>0){
            rMode=RoundingMode.DOWN;
        }else{
            rMode=RoundingMode.UP;
        }
        BigDecimal b = new BigDecimal(Double.toString(oldDouble),new MathContext(scale,rMode));
        return b.doubleValue();
    }
}
class TSS_RES
{
    int pre_movementX;
    int pre_movementY;
    double[] PSNR;
    int remX;
    int remY;
    double evaluation;
    double time;
}

public class TSS
{
    int blk_size;
    int img_sizex,img_sizey;
    int blk_numx,blk_numy;
    int blk_num_sq;//blk_numx*blk_numy
    int remx,remy;
    int steps;
    double threshold1;//psnr threshold to filter the reasonable matching result
    double threshold2;//psnr threshold for final determination
    RenderScript rs;
    ScriptC_TSS ms;
    double []PSNR;
    /*
        name in RS                  name in Allocation      name in array           meaning
        match_x                     alloc_matchX            vec_matchX              x coordinate of each block's matching result
        match_y                     alloc_matchY            vec_matchY              y coordinate of each block's matching result
        parameter                   alloc_parameter         vec_parameter           some required parameters, see datails in tss.rs
        pre_movement                alloc_pre_movement      vec_pre_movement        matching movement of the previous picture
        match_result_tss             alloc_res_tss            vec_res_tss              matching result(psnr) of the search
        match_result_recalculate    alloc_res_recalculate   vec_res_recalculate     matching result(psnr) of the recalculate function
        bm_ori                      alloc_pic1              do not have             the picture which matches pic2
        bm_tar                      alloc_pic2              do not have             the picture to be matched
        in                          alloc_seq               vec_seq                 the sequence number of each block
    */
    Allocation alloc_matchX,alloc_matchY,alloc_res_tss,alloc_seq,alloc_res_recalculate,alloc_pre_movement,alloc_parameter,no_meaning;
    int[] vec_matchX,vec_matchY,vec_seq,vec_pre_movement,vec_parameter;
    double[] vec_res_recalculate,vec_res_tss;
    void init(Context ctx,int img_sizex,int img_sizey,int blk_size,int steps,double threshold,int gap)
    {
        this.img_sizex=img_sizex;
        this.img_sizey=img_sizey;
        this.blk_size=blk_size;
        this.steps=steps;
        blk_numx=img_sizex/blk_size;
        blk_numy=img_sizey/blk_size;
        blk_num_sq=blk_numx*blk_numy;
        remx=img_sizex%blk_size;
        remy=img_sizey%blk_size;
        threshold2=threshold;
        threshold1=0.9*threshold;

        rs=RenderScript.create(ctx);
        ms=new ScriptC_TSS(rs);


        Type tSquareInt = new Type.Builder(rs, Element.I32(rs)).setX(blk_num_sq).create();//define allocation types
        Type t4 = new Type.Builder(rs, Element.I32(rs)).setX(4).create();
        Type tSquareFloat = new Type.Builder(rs, Element.F64(rs)).setX(blk_num_sq).create();
        Type t9 = new Type.Builder(rs, Element.I32(rs)).setX(9).create();
        Type t1 = new Type.Builder(rs, Element.I32(rs)).setX(1).create();



        alloc_matchX = Allocation.createTyped(rs, tSquareInt);// set allocation types
        alloc_matchY = Allocation.createTyped(rs, tSquareInt);
        alloc_res_tss = Allocation.createTyped(rs, tSquareFloat);
        alloc_seq=Allocation.createTyped(rs,tSquareInt);
        alloc_res_recalculate = Allocation.createTyped(rs, tSquareFloat);
        alloc_pre_movement = Allocation.createTyped(rs, t4);
        alloc_parameter=Allocation.createTyped(rs,t9);
        no_meaning=Allocation.createTyped(rs,t1);
        vec_res_tss=new double[blk_num_sq];
        vec_matchY=new int[blk_num_sq];
        vec_matchX=new int[blk_num_sq];
        vec_seq=new int [blk_num_sq];
        vec_res_recalculate=new double [blk_num_sq];
        vec_pre_movement=new int[4];
        vec_parameter=new int[9];
        vec_parameter[0]=img_sizex;
        vec_parameter[1]=blk_size;
        vec_parameter[2]=blk_numx;
        vec_parameter[3]=steps;
        vec_parameter[4]=remx;
        vec_parameter[5]=img_sizey;
        vec_parameter[6]=blk_numy;
        vec_parameter[7]=remy;
        vec_parameter[8]=gap;

        PSNR=new double[blk_num_sq];
        for(int i=0;i<blk_numx;i++)
            for(int j=0;j<blk_numy;j++)
            {
                vec_matchX[i*blk_numy+j]=i*blk_size;
                vec_matchY[i*blk_numy+j]=j*blk_size;
            }

        ms.set_match_x(alloc_matchX);
        ms.set_match_y(alloc_matchY);
        ms.set_match_result_tss(alloc_res_tss);
        ms.set_match_result_recalculate(alloc_res_recalculate);
        ms.set_pre_movement(alloc_pre_movement);
        ms.set_parameter(alloc_parameter);
        alloc_parameter.copyFrom(vec_parameter);


        ms.forEach_initial(no_meaning);//initialize parameteers

        for(int i=0;i<blk_num_sq;i++)//set sequence number
            vec_seq[i]=i;
        alloc_seq.copyFrom(vec_seq);
    }
    TSS_RES run(Bitmap pre_pic,Bitmap cur_pic,TSS_RES pre_tss_res,boolean logging)// invoke tss
    {
        long time_begin=SystemClock.uptimeMillis();
        TSS_RES tss_res=new TSS_RES();

        tss_res.PSNR=new double[blk_num_sq];
        vec_pre_movement[0]=pre_tss_res.pre_movementX;
        vec_pre_movement[1]=pre_tss_res.pre_movementY;
        vec_pre_movement[2]=pre_tss_res.remX;
        vec_pre_movement[3]=pre_tss_res.remY;
        alloc_pre_movement.copyFrom(vec_pre_movement);


        Allocation alloc_pic1=Allocation.createFromBitmap(rs,pre_pic);//load bitmap
        ms.set_bm_tar(alloc_pic1);
        Allocation alloc_pic2=Allocation.createFromBitmap(rs,cur_pic);
        ms.set_bm_ori(alloc_pic2);

        ms.forEach_tss(alloc_seq);// !!!important!!! invoke TSS function in rs

        alloc_matchX.copyTo(vec_matchX);// copy result to array
        alloc_matchY.copyTo(vec_matchY);
        alloc_res_tss.copyTo(vec_res_tss);
        double avg_movementX,avg_movementY;
        double tmpsum_movementX=0,tmpsum_movementY=0;


        int times=0;// filter the reasonable matching movement by using the threshold1
        for(int j=0;j<blk_num_sq;j++)
        {

            PSNR[j]=vec_res_tss[j];
            int x0=(j/blk_numy)*blk_size,y0=(j%blk_numy)*blk_size;// origin location in current picture
            if(PSNR[j]>threshold1)
            {
                times++;
                tmpsum_movementX += vec_matchX[j] - x0 - vec_pre_movement[2];
                tmpsum_movementY += vec_matchY[j] - y0 - vec_pre_movement[3];
            }
        }
        avg_movementX=tmpsum_movementX/times;// calculate the average movement
        avg_movementY=tmpsum_movementY/times;



        tmpsum_movementX=0;
        tmpsum_movementY=0;
        times=0;// filter the reasonable movement by using the distance and the average movement above
        for(int j=0;j<blk_num_sq;j++)
        {
            int x0=(j/blk_numy)*blk_size,y0=(j%blk_numy)*blk_size;// origin location in current picture
            if(Math.abs(vec_matchX[j]-x0-vec_pre_movement[2]-avg_movementX)+Math.abs(vec_matchY[j]-y0-vec_pre_movement[3]-avg_movementY)<Math.abs(avg_movementX)+Math.abs(avg_movementY))
            {
                times++;
                tmpsum_movementX+=vec_matchX[j]-vec_pre_movement[2]-x0;
                tmpsum_movementY+=vec_matchY[j]-vec_pre_movement[3]-y0;
            }
        }
        avg_movementX=tmpsum_movementX/times;//update the average movement
        avg_movementY=tmpsum_movementY/times;



        vec_pre_movement[0]=(int) Math.rint(avg_movementX);// type conversion to int
        tss_res.pre_movementX=vec_pre_movement[0];
        vec_pre_movement[1]=(int) Math.rint(avg_movementY);
        tss_res.pre_movementY=vec_pre_movement[1];
        vec_pre_movement[2]=vec_pre_movement[3]=0;
        /* to make the most of the pixels of the pictures
         eg. if a picture is going right to match the previous one,
         the potential matching blocks are conceivably to be in the left of this picture*/
        if(avg_movementX<0)
            vec_pre_movement[2]=remx;
        if(avg_movementY<0)
            vec_pre_movement[3]=remy;
        tss_res.remX=vec_pre_movement[2];
        tss_res.remY=vec_pre_movement[3];
        alloc_pre_movement.copyFrom(vec_pre_movement);

        ms.forEach_recalculate(alloc_seq);// recalculate the matching result of the final movement by using the recalculate function in rs



        alloc_res_recalculate.copyTo(vec_res_recalculate);



        // do some analysis
        int evaluation=0;
        for(int j=0;j<blk_num_sq;j++)
        {

            double psnr=vec_res_recalculate[j];
            tss_res.PSNR[j]=vec_res_recalculate[j];
            if(psnr>threshold2)
                evaluation++;


        }
        tss_res.evaluation=evaluation*1.0/blk_num_sq;
        long time_end=SystemClock.uptimeMillis();
        tss_res.time=time_end-time_begin;
        if(logging==true)
        {
            System.out.println("x,y="+tss_res.pre_movementX+" "+tss_res.pre_movementY);
            System.out.println("delta t="+(time_end-time_begin));
            System.out.println("correct ratio="+TestBigDecimal.significand(tss_res.evaluation,4));
        }

        return tss_res;
    }
}
