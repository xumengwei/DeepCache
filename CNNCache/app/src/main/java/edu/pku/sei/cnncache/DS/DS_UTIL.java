package edu.pku.sei.cnncache.DS;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

/**
 * Created by echo on 12/06/2018.
 */

public class DS_UTIL {
	private static final String TAG = "DS_UTIL";
	DS_RES pre_ds_res;
	public int ox = 0, oy = 0;
	public int[] x1, x2, y1, y2;
	public int img_size, blk_size, blk_num;
	public DS ds;
	public int size;
	public DS_UTIL(DS _ds){
		pre_ds_res = new DS_RES();
		ds = _ds;
		img_size = ds.img_sizex;
		blk_size = ds.blk_size;
		blk_num = img_size / blk_size;
		x1 = new int[blk_num * blk_num];
		y1 = new int[blk_num * blk_num];
		x2 = new int[blk_num * blk_num];
		y2 = new int[blk_num * blk_num];
	}
	public void run_image_matching(Bitmap pre, Bitmap cur, double threshold) {
		long t1 = SystemClock.elapsedRealtime();
		DS_RES ds_res = ds.run(pre, cur, pre_ds_res, false);
		size = 0;
		ox = ds_res.offset_y;
		oy = ds_res.offset_x;
		if (ox > 0) {
			x1[size] = img_size - ox;
			y1[size] = 0;
			x2[size] = img_size - 1;
			y2[size] = img_size - 1;
			size ++;
		}
		else if (ox < 0) {
			x1[size] = 0;
			y1[size] = 0;
			x2[size] = -ox - 1;
			y2[size] = img_size - 1;
			size ++;
		}

		if (oy > 0) {
			x1[size] = 0;
			y1[size] = img_size - oy;
			x2[size] = img_size - 1;
			y2[size] = img_size - 1;
			size ++;
		}
		else if (oy < 0) {
			x1[size] = 0;
			y1[size] = 0;
			x2[size] = img_size - 1;
			y2[size] = -oy - 1;
			size ++;
		}

		for (int j = 0; j < ds_res.PSNR.length; j ++) {
			if (ds_res.PSNR[j] < threshold) {
				// Important! DS x-y should be reversed to match the ncnn x-y
				// TODO: make the x-y axis consistent!
				int index_y = j / blk_num * blk_size + ds_res.bias_x;
				int index_x = j % blk_num * blk_size + ds_res.bias_y;
				boolean c1 = contain_rect(x1[0], y1[0], x2[0], y2[0], index_x, index_y, index_x + blk_size, index_y + blk_size);
				boolean c2 = contain_rect(x1[1], y1[1], x2[1], y2[1], index_x, index_y, index_x + blk_size, index_y + blk_size);
				if (!c1 && !c2) {
					x1[size] = index_x;
					y1[size] = index_y;
					x2[size] = index_x + blk_size - 1;
					y2[size] = index_y + blk_size - 1;
					size++;
				}
//				Log.i(TAG, "Low PSNR: " + index_x + " " + index_y + " " + ds_res.PSNR[j]);
			}
		}

		long t2 = SystemClock.elapsedRealtime();

		StringBuilder info = new StringBuilder();
		info.append("elapsed:" + (t2 - t1));
		info.append(", size:" + size + "/" + (blk_num * blk_num));
		info.append(", offset:(" + ox + "," + oy + ")");
//			info.append(", rect:");
//			for (int j = 0; j < size; j ++)
//				info.append("(" + x1[j] + "," + y1[j] + "," + x2[j] + "," + y2[j] + ")");
		Log.i(TAG, info.toString());
	}

	private boolean contain_rect(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
		return (x1 <= x3) && (y1 <= y3) && (x2 >= x4) && (y2 >= y4);
	}
}
