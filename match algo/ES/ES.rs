#pragma version(1)
#pragma rs java_package_name(com.example.zmz.es_api23)
#pragma rs_fp_relaxed
#include "rs_debug.rsh"
const infinity=1<<30;
int img_sizex,img_sizey,blk_size,blk_numx,blk_numy,steps,remx,remy,gap=1;// see details in initial function
rs_allocation bm_ori;//bitmap origin
rs_allocation bm_tar;//bitmap target
// rs_allocation match_prex;//matching block's x coordinate of the previous picture
// rs_allocation match_prey;//matching block's y coordinate of the previous picture
rs_allocation match_x;//matching block's x coordinate in the present picture
rs_allocation match_y;//matching block's y coordinate in the present picture
rs_allocation match_result_es;
rs_allocation match_result_recalculate;//matching block's PSNR value(how well it match its "matching" block)
rs_allocation pre_movement;//previous matching movement for x&y
rs_allocation parameter;//some initialized paremeters, see details in initial function
void __attribute__ ((kernel)) initial(int in)//initializing function
{
    img_sizex=rsGetElementAt_int(parameter,0);
    blk_size=rsGetElementAt_int(parameter,1);
    blk_numx=rsGetElementAt_int(parameter,2);
    steps=rsGetElementAt_int(parameter,3);
    remx=rsGetElementAt_int(parameter,4);//same with remX in es function, remain pixels in x coordinate
    img_sizey=rsGetElementAt_int(parameter,5);
    blk_numy=rsGetElementAt_int(parameter,6);
    remy=rsGetElementAt_int(parameter,7);//same with remY in es function, remain pixels in y coordinate
    gap=rsGetElementAt_int(parameter,8);// only if in%gap==0, the block will be calculated, where "in" is the sequence number
}

static bool is_valid(int x,int y)
{
	if(x>=0&&x<img_sizex+blk_size&&y>=0&&y<img_sizex+blk_size)
		return true;
	return false;
}
static bool is_validX(int x)
{
	if(x>=0&&x<img_sizex)
		return true;
	return false;
}
static bool is_validY(int y)
{
	if(y>=0&&y<img_sizey)
		return true;
	return false;
}
void __attribute__ ((kernel)) es(int in)//Exhaustive Search algorithm
{
	if(in%gap!=0)
	{
	    rsSetElementAt_int(match_x,infinity,in);
        rsSetElementAt_int(match_y,infinity,in);
        double PSNR=-1.0;
        rsSetElementAt_double(match_result_es,PSNR,in);
        return;
	}

	int pre_movementX=rsGetElementAt_int(pre_movement,0);//matching movement for x in previous picture
	int pre_movementY=rsGetElementAt_int(pre_movement,1);//matching movement for y in previous picture
	int remX=rsGetElementAt_int(pre_movement,2);// remain pixels in x coordinate
	int remY=rsGetElementAt_int(pre_movement,3);// remain pixels in y coordinate

	int x=(in/blk_numy)*blk_size+remX;// x coordinate of a block of the bm_ori
	int y=(in%blk_numy)*blk_size+remY;// y coordinate of a block of the bm_ori
	int x_pre=x+pre_movementX;// current optimal x coordinate of a block of bm_tar
	int y_pre=y+pre_movementY;// current optimal y coordinate of a block of bm_tar
	int x_current_optimal=x_pre,y_current_optimal=y_pre;
	int x_in_attempt,y_in_attempt;//  x or y coordinate in attempt of a block of the matching picture
	if(!is_valid(x_pre+blk_size,y_pre+blk_size))//initial values have already been out of range => return
	{
		rsSetElementAt_int(match_x,infinity,in);
		rsSetElementAt_int(match_y,infinity,in);
	    double PSNR=0.0;
		rsSetElementAt_double(match_result_es,PSNR,in);
		return;
	}
	double result=0;
	int time0=0;
	for(int dx=0;dx<blk_size;dx++)//initialize x_pre and y_pre
	{
		if(!is_validX(x_pre+dx))
			continue;
		for(int dy=0;dy<blk_size;dy++)
		{
			if(!is_validY(y_pre+dy))
				continue;
			time0++;
			uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
            uchar4 p2=rsGetElementAt_uchar4(bm_tar,x_pre+dx,y_pre+dy);
			result+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
		}
	}
	result/=3*time0;
	for(int s=1;s<=2*steps+1;s++)
	{
		for(int t=0;t<=s;t++)
		{
			x_in_attempt=x_pre+t,y_in_attempt=y_pre+(s-t);
			if(!is_valid(x_in_attempt+blk_size,y_in_attempt+blk_size))
				continue;
			double sum=0;int time1=0;
			for(int dx=0;dx<blk_size;dx++)
			{
				if(!is_validX(x_in_attempt+dx))
					continue;
				for(int dy=0;dy<blk_size;dy++)
				{
					if(!is_validY(y_in_attempt+dy))
						continue;
					time1++;
					uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
    				uchar4 p2=rsGetElementAt_uchar4(bm_tar,x_in_attempt+dx,y_in_attempt+dy);
    				sum+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
				}
			}
			if(time1==0)
				continue;
			else
				sum/=3*time1;
			if(sum<result)
			{
				result=sum;
				x_current_optimal=x_in_attempt;
				y_current_optimal=y_in_attempt;
			}

			x_in_attempt=x_pre-t,y_in_attempt=y_pre-(s-t);
			if(!is_valid(x_in_attempt+blk_size,y_in_attempt+blk_size))
				continue;
			sum=0;time1=0;
			for(int dx=0;dx<blk_size;dx++)
			{
				if(!is_validX(x_in_attempt+dx))
					continue;
				for(int dy=0;dy<blk_size;dy++)
				{
					if(!is_validY(y_in_attempt+dy))
						continue;
					time1++;
					uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
					uchar4 p2=rsGetElementAt_uchar4(bm_tar,x_in_attempt+dx,y_in_attempt+dy);
					sum+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
				}
			}
				sum/=3*time1;
			if(sum<result)
			{
				result=sum;
				x_current_optimal=x_in_attempt;
				y_current_optimal=y_in_attempt;
			}
		}
	}
	rsSetElementAt_int(match_x,x_current_optimal,in);
	rsSetElementAt_int(match_y,y_current_optimal,in);

    double PSNR=10*log10((float)(pow((float)(pow((float)2,8)-1),2)/result));
	rsSetElementAt_double(match_result_es,PSNR,in);
	return;
}
void __attribute__ ((kernel)) recalculate(int in)// when the movement is determined, recalculate the psnr
{
	int pre_movementX=rsGetElementAt_int(pre_movement,0);
	int pre_movementY=rsGetElementAt_int(pre_movement,1);
	int remX=rsGetElementAt_int(pre_movement,2);
	int remY=rsGetElementAt_int(pre_movement,3);
	int x=(in/blk_numy)*blk_size+remX;
	int y=(in%blk_numy)*blk_size+remY;
	int x_pre=x+pre_movementX;
	int y_pre=y+pre_movementY;
	int sum=0,flag=0;
	for(int dx=0;dx<blk_size;dx++)
	{
		if(!is_validX(x_pre+dx))
		    continue;
		for(int dy=0;dy<blk_size;dy++)
		{
			if(!is_validY(y_pre+dy))
			    continue;
			flag++;
			uchar4 p1=rsGetElementAt_uchar4(bm_ori,x+dx,y+dy);
			uchar4 p2=rsGetElementAt_uchar4(bm_tar,x_pre+dx,y_pre+dy);
			sum+=(p1.r-p2.r)*(p1.r-p2.r)+(p1.g-p2.g)*(p1.g-p2.g)+(p1.b-p2.b)*(p1.b-p2.b);
		}
	}
    if(flag==0)
    {
        rsSetElementAt_double(match_result_recalculate,-1,in);
        return;
    }

	double MSE=sum*1.0/(3*flag);
    double PSNR=10*log10((float)(pow((float)(pow((float)2,8)-1),2)/MSE));
    rsSetElementAt_double(match_result_recalculate,PSNR,in);
    return;
}
