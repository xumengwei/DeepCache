package edu.pku.sei.cnncache.DS;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.os.SystemClock;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.Type;


import edu.pku.sei.cnncache.DS_versionEight.ScriptC_DS;

public class DS
{
	//    final static int img_num=73;
//    final static int img_size=224;
//    final static int blk_size=16;
//    final static int blk_num=img_size/blk_size;
//    int rem=img_size%blk_size;
//    final static int blk_num_sq=blk_num*blk_num;
//    final static int threshold2=25;
//    final static int threshold1=(int)Math.rint(0.9*threshold2);
//    final static int interval=1;
//    final static int iter_time=1;
	int blk_size;
	int img_sizex,img_sizey;
	int blk_numx,blk_numy;
	int blk_num_sq;
	int remx,remy;
	int steps;
	double threshold1;
	double threshold2;
	//Context ctx;
	RenderScript rs;
	ScriptC_DS ms;
	//double []MSE;
	double []PSNR;
	Allocation match_x,match_y,match_v,match_prey,match_prex,data,match_result,am,para,tmp;
	int[] mx,my,mprex,mprey,d,amvector,parameter;
	double[] matres,mv;
	public void init(Context ctx,int img_sizex,int img_sizey,int blk_size,int steps,double threshold)
	{
		this.img_sizex=img_sizex;
		this.img_sizey=img_sizey;
		this.blk_size=blk_size;
		this.steps=steps;
		blk_numx=img_sizex/blk_size;
		blk_numy=img_sizey/blk_size;
		blk_num_sq=blk_numx*blk_numy;
		//System.out.println(blk_num_sq+" "+blk_numx+" "+blk_numy);
		remx=img_sizex%blk_size;
		remy=img_sizey%blk_size;
		threshold2=threshold;
		threshold1=0.9*threshold;

		//System.out.println("0000");

		rs=RenderScript.create(ctx);
		ms=new ScriptC_DS(rs);

		//
		Type t = new Type.Builder(rs, Element.I32(rs)).setX(blk_num_sq).create();
		Type t2 = new Type.Builder(rs, Element.I32(rs)).setX(4).create();
		Type t3 = new Type.Builder(rs, Element.F64(rs)).setX(blk_num_sq).create();
		Type t4 = new Type.Builder(rs, Element.I32(rs)).setX(8).create();
		Type t5 = new Type.Builder(rs, Element.I32(rs)).setX(1).create();
		match_x = Allocation.createTyped(rs, t);
		match_y = Allocation.createTyped(rs, t);
		match_v = Allocation.createTyped(rs, t3);
		match_prey = Allocation.createTyped(rs, t);
		match_prex = Allocation.createTyped(rs, t);
		data=Allocation.createTyped(rs,t);
		match_result = Allocation.createTyped(rs, t3);
		am = Allocation.createTyped(rs, t2);
		para=Allocation.createTyped(rs,t4);
		tmp=Allocation.createTyped(rs,t5);
		mx=new int[blk_num_sq];
		my=new int[blk_num_sq];
		mv=new double[blk_num_sq];
		mprey=new int[blk_num_sq];
		mprex=new int[blk_num_sq];
		d=new int [blk_num_sq];
		matres=new double [blk_num_sq];
		amvector=new int[4];
		//System.out.println("2222");
		parameter=new int[8];
		parameter[0]=img_sizex;
		parameter[1]=blk_size;
		parameter[2]=blk_numx;
		parameter[3]=steps;
		parameter[4]=remx;
		parameter[5]=img_sizey;
		parameter[6]=blk_numy;
		parameter[7]=remy;
		PSNR=new double[blk_num_sq];
		//System.out.println("2222");
		for(int i=0;i<blk_numx;i++)
			for(int j=0;j<blk_numy;j++)
			{
				mprex[i*blk_numy+j]=i*blk_size;
				mprey[i*blk_numy+j]=j*blk_size;
			}

		ms.set_match_x(match_x);
		ms.set_match_y(match_y);
		ms.set_match_v(match_v);
		ms.set_match_prey(match_prey);
		ms.set_match_prex(match_prex);
		ms.set_match_result(match_result);
		ms.set_am(am);
		ms.set_para(para);
		para.copyFrom(parameter);


		ms.forEach_initial(tmp);

		for(int i=0;i<blk_num_sq;i++)
			d[i]=i;
		data.copyFrom(d);

//        id=new int[img_num+1];
//        for(int i=1;i<=img_num;i++)
//        {
//            id[i] = ctx.getResources().getIdentifier("p"+i ,"drawable", ctx.getPackageName());
//        }
	}
	public DS_RES run(Bitmap pre_pic,Bitmap cur_pic,DS_RES pre_ds_res,boolean logging)
	{
//        long time_begin=SystemClock.uptimeMillis();
		DS_RES ds_res=new DS_RES();
		//ds_res.MSE=new double[blk_num_sq];
		ds_res.PSNR=new double[blk_num_sq];
//            long time1_02= SystemClock.uptimeMillis();
		amvector[0]=pre_ds_res.offset_x;
		amvector[1]=pre_ds_res.offset_y;
		amvector[2]=pre_ds_res.bias_x;
		amvector[3]=pre_ds_res.bias_y;
		am.copyFrom(amvector);

//            long time1_11=SystemClock.uptimeMillis();
//            System.out.println("copyfrom: t1.11-t1.02="+(time1_11-time1_02));
//            long time1_12=SystemClock.uptimeMillis();

//        Bitmap p1=loadBitmap(id[i]);
		Allocation alp1=Allocation.createFromBitmap(rs,pre_pic);
		ms.set_bm_tar(alp1);
//        Bitmap p2=loadBitmap(id[i+1]);
		Allocation alp2=Allocation.createFromBitmap(rs,cur_pic);
		ms.set_bm_ori(alp2);

//            long time1_21=SystemClock.uptimeMillis();
//            System.out.println("loadbitmap: t1.21-t1.12="+(time1_21-time1_12));
//            long time1_22=SystemClock.uptimeMillis();
		ms.forEach_ds(data);
//            long time2_01=SystemClock.uptimeMillis();
//            System.out.println("rs1: t2.01-t1.22="+(time2_01-time1_22));
//            long time2_02=SystemClock.uptimeMillis();

		match_x.copyTo(mprex);
		match_y.copyTo(mprey);
		match_v.copyTo(mv);
		//System.out.println(mv[0]);
		double amx,amy;
		double smx=0,smy=0;

//            long time2_11=SystemClock.uptimeMillis();
//            System.out.println("copyto: t2.11-t2.02="+(time2_11-time2_02));
//            long time2_12=SystemClock.uptimeMillis();

//            double threshold3=0;
//            int flag0=0;
//            for(int j=0;j<blk_num_sq;j++)
//            {
//                if(mv[j]>0&&mv[j]<100)
//                {
//                    threshold3+=mv[j];
//                    flag0++;
//                }
//
//            }
//            threshold3/=flag0;

		int s=0;
		for(int j=0;j<blk_num_sq;j++)
		{
//                MSE[i][j]=mv[j]*1.0/(3*blk_num_sq);
//                PSNR[i][j]=10*Math.log(Math.pow((Math.pow(2,8)-1),2)/MSE[i][j])/Math.log(10);
			PSNR[j]=mv[j];
			int x0=(j/blk_numy)*blk_size,y0=(j%blk_numy)*blk_size;
			//System.out.println((mprex[j]-x0-amvector[2])+" y="+(mprey[j]-y0-amvector[3])+" "+mv[j]);
			if(PSNR[j]>threshold1)
			{
				s++;
				smx += mprex[j] - x0 - amvector[2];
				smy += mprey[j] - y0 - amvector[3];
			}
		}
		amx=smx/s;
		amy=smy/s;

//            long time2_21=SystemClock.uptimeMillis();
//            System.out.println("for 1: t2.21-t2.12="+(time2_21-time2_12));
//            long time2_22=SystemClock.uptimeMillis();



		smx=0;
		smy=0;
		s=0;
		for(int j=0;j<blk_num_sq;j++)
		{
			int x0=(j/blk_numy)*blk_size,y0=(j%blk_numy)*blk_size;
			if(Math.abs(mprex[j]-x0-amvector[2]-amx)+Math.abs(mprey[j]-y0-amvector[3]-amy)<Math.abs(amx)+Math.abs(amy))
			//if(Math.abs(mprex[j]-x0-amx+mprey[j]-y0-amy)<Math.abs(amy+amx))
			{
				s++;
				smx+=mprex[j]-amvector[2]-x0;
				smy+=mprey[j]-amvector[3]-y0;
			}
		}
		amx=smx/s;
		amy=smy/s;


//            long time3_01=SystemClock.uptimeMillis();
//            System.out.println("for 2: t3.01-t2.22="+(time3_01-time2_22));
//            long time3_02=SystemClock.uptimeMillis();

		amvector[0]=(int) Math.rint(amx);
		ds_res.offset_x=amvector[0];
		amvector[1]=(int) Math.rint(amy);
		ds_res.offset_y=amvector[1];
		//System.out.println(amx+" "+amy+" "+rem);
		amvector[2]=amvector[3]=0;
		if(amx<0)
			amvector[2]=remx;
		if(amy<0)
			amvector[3]=remy;
		ds_res.bias_x=amvector[2];
		ds_res.bias_y=amvector[3];
		am.copyFrom(amvector);

//            long time3_11=SystemClock.uptimeMillis();
//            System.out.println("copyfrom: t3.11-t3.02="+(time3_11-time3_02));
//            long time3_12=SystemClock.uptimeMillis();
		ms.forEach_test(data);


//            long time3_21=SystemClock.uptimeMillis();
//            System.out.println("rs2: t3.21-t3.12="+(time3_21-time3_12));
//            long time3_22=SystemClock.uptimeMillis();

		match_result.copyTo(matres);

//            long time3_31=SystemClock.uptimeMillis();
//            System.out.println("copyto: t3.31-t3.22="+(time3_31-time3_22));
//            long time3_32=SystemClock.uptimeMillis();


		int res=0;
		for(int j=0;j<blk_num_sq;j++)
		{
//                if(matres[j]!=-1)
//                {
//                    double mse=matres[j]*1.0/(3*blk_num_sq);
//                    double psnr=10*Math.log(Math.pow((Math.pow(2,8)-1),2)/mse)/Math.log(10);
//                    if(psnr>threshold2)
//                    {
//                        res++;
//                    }
//                }
			double psnr=matres[j];
			ds_res.PSNR[j]=matres[j];
			if(psnr>threshold2)
				res++;


		}
		ds_res.res=res*1.0/blk_num_sq;
//        long time_end=SystemClock.uptimeMillis();
		if(logging==true)
		{
			System.out.println("x,y="+ds_res.offset_x+" "+ds_res.offset_y);
//            System.out.println("delta t="+(time_end-time_begin));
			System.out.println("correct ratio="+ds_res.res);
		}
//            long time3_41=SystemClock.uptimeMillis();
//            System.out.println("for psnr: t3.41-t3.32="+(time3_41-time3_32));
//            long time3_42=SystemClock.uptimeMillis();

		return ds_res;




	}


}
