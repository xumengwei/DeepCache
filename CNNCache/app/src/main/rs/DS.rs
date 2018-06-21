#pragma version(1)
#pragma rs java_package_name(edu.pku.sei.cnncache.DS_versionEight)
#pragma rs_fp_relaxed
#include "rs_debug.rsh"
//#include <math.h>

int img_sizex,img_sizey,blk_size,blk_numx,blk_numy,steps,remx,remy;
int mx[8]={0,1,2,1,0,-1,-2,-1};
int my[8]={2,1,0,-1,-2,-1,0,1};
int mx_ls[4]={0,1,0,-1};
int my_ls[4]={1,0,-1,0};
rs_allocation bm_ori;
rs_allocation bm_tar;
rs_allocation match_prex;
rs_allocation match_prey;
rs_allocation match_x;
rs_allocation match_y;
rs_allocation match_v;
rs_allocation match_result;
rs_allocation am;
rs_allocation para;
void __attribute__ ((kernel)) initial(int in)
{
    img_sizex=rsGetElementAt_int(para,0);
    blk_size=rsGetElementAt_int(para,1);
    blk_numx=rsGetElementAt_int(para,2);
    steps=rsGetElementAt_int(para,3);
    remx=rsGetElementAt_int(para,4);
    img_sizey=rsGetElementAt_int(para,5);
    blk_numy=rsGetElementAt_int(para,6);
    remy=rsGetElementAt_int(para,7);
}

static bool is_valid(int x)
{
	if(x>=0&&x+blk_size<=img_sizex)
		return true;
	return false;
}
static bool is_valid2(int x)
{
	if(x>=0&&x<img_sizex)
		return true;
	return false;
}
static bool is_valid3(int x)
{
	if(x>=0&&x<img_sizey)
		return true;
	return false;
}
void __attribute__ ((kernel)) ds(int in)
{
	int amx=rsGetElementAt_int(am,0);
	int amy=rsGetElementAt_int(am,1);
	int biasx=rsGetElementAt_int(am,2);
	int biasy=rsGetElementAt_int(am,3);

	int x=(in/blk_numy)*blk_size+biasx;
	int y=(in%blk_numy)*blk_size+biasy;
	int x1=x+amx;
	int y1=y+amy;
	//rsDebug("aaaaaaaaa-in",in,x,y,x1);
	// int x=(in/blk_num)*blk_size;
	// int y=(in%blk_num)*blk_size;
	// int x1=rsGetElementAt_int(match_prex,in);
	// int y1=rsGetElementAt_int(match_prey,in);
	int x2,y2;
	double compute_result[img_sizex+blk_size][img_sizey+blk_size];
	for(int i=0;i<img_sizex+blk_size;i++)
		for(int j=0;j<img_sizey+blk_size;j++)
			compute_result[i][j]=-1;
	compute_result[x1+blk_size][y1+blk_size]=0;
	int time0=0;
	for(int dx=0;dx<blk_size;dx++)
	{
		if(!is_valid2(x1+dx))
			continue;
		for(int dy=0;dy<blk_size;dy++)
		{
			if(!is_valid3(y1+dy))
				continue;
			time0++;
			uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
            uchar4 p2=rsGetElementAt_uchar4(bm_tar,x1+dx,y1+dy);
			compute_result[x1+blk_size][y1+blk_size]+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
		}
	}

	if(time0==0)
		compute_result[x1+blk_size][y1+blk_size]=1<<30;
	else
		compute_result[x1+blk_size][y1+blk_size]/=3*time0;
	for(int s=1;s<=steps;s++)
	{
		int flag=-1;
		double _min=compute_result[x1+blk_size][y1+blk_size];
		for(int m=0;m<8;m++)
		{
			x2=x1+mx[m],y2=y1+my[m];
			if(compute_result[x2+blk_size][y2+blk_size]!=-1)
				continue;
			int sum=0,time1=0;
			for(int dx=0;dx<blk_size;dx++)
			{
				if(!is_valid2(x2+dx))
					continue;
				for(int dy=0;dy<blk_size;dy++)
				{
					if(!is_valid3(y2+dy))
						continue;
					time1++;
					uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
    				uchar4 p2=rsGetElementAt_uchar4(bm_tar,x2+dx,y2+dy);
    				sum+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
				}
			}
			compute_result[x2+blk_size][y2+blk_size]=sum;
			if(time1==0)
				compute_result[x2+blk_size][y2+blk_size]=1<<30;
			else
				compute_result[x2+blk_size][y2+blk_size]/=3*time1;
			if(compute_result[x2+blk_size][y2+blk_size]<_min)
			{
				_min=compute_result[x2+blk_size][y2+blk_size];
				flag=m;
			}
		}
		if(flag==-1)
			break;
		x1+=mx[flag];
		y1+=my[flag];
	}


	int flag=-1;
	double _min=compute_result[x1+blk_size][y1+blk_size];
	for(int m=0;m<4;m++)
	{
		x2=x1+mx_ls[m],y2=y1+my_ls[m];
		int sum=0,time2=0;
		for(int dx=0;dx<blk_size;dx++)
		{
			if(!is_valid2(x2+dx))
				continue;
			for(int dy=0;dy<blk_size;dy++)
			{
				if(!is_valid3(y2+dy))
					continue;
				time2++;
				uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
				uchar4 p2=rsGetElementAt_uchar4(bm_tar,x2+dx,y2+dy);
				sum+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
			}
		}
		compute_result[x2+blk_size][y2+blk_size]=sum;
		if(time2==0)
			compute_result[x2+blk_size][y2+blk_size]=1<<30;
		else
			compute_result[x2+blk_size][y2+blk_size]/=3*time2;
		if(compute_result[x2+blk_size][y2+blk_size]<_min)
		{
			_min=compute_result[x2+blk_size][y2+blk_size];
			flag=m;
		}
	}
	if(flag!=-1)
	{
		x1+=mx_ls[flag];
		y1+=my_ls[flag];
	}
	rsSetElementAt_int(match_x,x1,in);
	rsSetElementAt_int(match_y,y1,in);

	//double MSE=compute_result[x1][y1]*1.0/(3*blk_size*blk_size);
    double PSNR=10*log10((float)(pow((float)(pow((float)2,8)-1),2)/compute_result[x1+blk_size][y1+blk_size]));
	//if(in==100)
	//{
	//    rsDebug("",x,y,x1,y1);
	//    rsDebug("",PSNR);

	//}
	rsSetElementAt_double(match_v,PSNR,in);
	//if(in==0)
	//{
	//    rsDebug("compute_result[x1][y1]=",compute_result[x1][y1]);
	//    rsDebug("MSE",MSE);
	//    rsDebug("PSNR",PSNR);
	//}
	return;
}
void __attribute__ ((kernel)) test(int in)
{
	int amx=rsGetElementAt_int(am,0);
	int amy=rsGetElementAt_int(am,1);
	int biasx=rsGetElementAt_int(am,2);
	int biasy=rsGetElementAt_int(am,3);
	int x=(in/blk_numy)*blk_size+biasx;
	int y=(in%blk_numy)*blk_size+biasy;
	int x1=x+amx;
	int y1=y+amy;
	//rsDebug("xy",in,y,blk_numy,biasy);
	//if(!(is_valid(x1)&&is_valid(y1)))
	//{
	//   rsSetElementAt_double(match_result,-1,in);
	//  return;
	//}
	int sum=0,flag=0;
	for(int dx=0;dx<blk_size;dx++)
	{
		//int nx=x1+dx;
		if(!is_valid2(x1+dx))
		    continue;
		    //break;
		for(int dy=0;dy<blk_size;dy++)
		{
			if(!is_valid3(y1+dy))
			    continue;
			    //break;
			flag++;
			uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
			uchar4 p2=rsGetElementAt_uchar4(bm_tar,x1+dx,y1+dy);
			sum+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
		}
	}
    if(flag==0)
    {
        rsSetElementAt_double(match_result,-1,in);
        return;
    }

	double MSE=sum*1.0/(3*flag);//blk_size*blk_size
    double PSNR=10*log10((float)(pow((float)(pow((float)2,8)-1),2)/MSE));
    rsSetElementAt_double(match_result,PSNR,in);
    //if(flag!=100)
    //    rsDebug("flag",flag,PSNR);
    return;
}
