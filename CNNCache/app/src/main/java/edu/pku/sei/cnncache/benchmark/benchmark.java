package edu.pku.sei.cnncache.benchmark;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by echo on 05/10/2017.
 */

public class benchmark {
	private final String path = "/sdcard/ncnn/benchmark/";
	private static HashMap<String, BConfig> BMaps;
	private static final String TAG = "CNNCache";
	static {
		BMaps = new HashMap<>();
		BMaps.put("1", new BConfig("1", 80, 20, 3));
		BMaps.put("2", new BConfig("2", 20, 20, 2));
		BMaps.put("3", new BConfig("3", 20, 20, 3));
		BMaps.put("4", new BConfig("4", 1, 20, 1));
		BMaps.put("8", new BConfig("8", 1, 20, 3));
		BMaps.put("self3", new BConfig("self3", 60, 20, 3));
		BMaps.put("plant", new BConfig("plant", 30, 20, 3));
		BMaps.put("bookshelf", new BConfig("bootshelf", 30, 20, 3));
		BMaps.put("sofa", new BConfig("sofa", 30, 20, 3));
		BMaps.put("desk4", new BConfig("desk4", 15, 20, 3));
		BMaps.put("road1", new BConfig("road1", 117, 20, 3));
		BMaps.put("road2", new BConfig("road2", 180, 20, 3));
		BMaps.put("road3", new BConfig("road3", 69, 20, 3));
		BMaps.put("road4", new BConfig("road4", 150, 20, 3));
		BMaps.put("road7", new BConfig("road7", 0, 20, 3));
		BMaps.put("road8", new BConfig("road8", 45, 20, 3));
		BMaps.put("road9-1", new BConfig("road9", 40, 20, 3));
		BMaps.put("road9-2", new BConfig("road9", 320, 20, 3));
		BMaps.put("road10", new BConfig("road10", 30, 20, 3));
	}
	public static Bitmap[] loadBitmaps(String name) {
		return loadBitmaps(name, 227, 227);
	}
	public static Bitmap[] loadBitmaps(String name, int img_w, int img_h) {
		if (!BMaps.containsKey(name)) {
			Log.e(TAG, "No benchmark key: " + name);
			return null;
		}
		BConfig config = BMaps.get(name);
		Bitmap[] bitmaps = new Bitmap[config.number];
		for (int i = config.start, bitmap_index = 0; bitmap_index < config.number; i += config.offset) {
			Bitmap bitmap = BitmapFactory.decodeFile(
					"/sdcard/ncnn/benchmark/" + name + "/" + i + ".jpg");
			Bitmap rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true);
			// resize to 227x227
			bitmaps[bitmap_index ++] = Bitmap.createScaledBitmap(rgba, img_w, img_h, false);
		}
		return bitmaps;
	}

	static class MyFilter implements FilenameFilter {
		String[] pres;
		MyFilter(String[] _pres) {
			pres = _pres;
		}

		@Override
		public boolean accept(File file, String s) {
			for (String p: pres) {
				if (s.startsWith(p)) return true;
			}
			return false;
		}
	}

	public static String[] get_clips() {
		List<String> ret = new ArrayList<>();
		List<String> clips = new ArrayList<>();
//		clips.add("v_ApplyEyeMakeup");
//		clips.add("v_ApplyLipstick");
//		clips.add("v_BalanceBeam");
//		clips.add("v_BandMarching");
//		clips.add("v_Basketball");
//		clips.add("v_Billiards");
		clips.add("v_BlowDryHair");
		clips.add("v_CliffDiving");
		clips.add("v_BrushingTeeth");
		clips.add("v_CleanAndJerk");

//		clips.add("v_BabyCrawling");
//		clips.add("v_BaseballPitch");
//		clips.add("v_BenchPress");
//		clips.add("v_Biking");
//		clips.add("v_BlowingCandles");
//		clips.add("v_BodyWeightSquats");
//		clips.add("v_Bowling");
//		clips.add("v_BoxingPunchingBag");

		for (String key : clips) {
			for (int g = 4; g <= 14; g ++)
				for (int c = 1; c <= 2; c ++)
				{
					String temp = key;
					if (g < 10)
						temp = temp + "_g0" + g;
					else
						temp = temp + "_g" + g;
					temp = temp + "_c0" + c;
					ret.add(temp);
				}
		}
		return ret.toArray(new String[ret.size()]);
	}


	public static Bitmap[] loadUCF101(String name) {
		return loadUCF101(name, 227, 227, 20);
	}
	public static Bitmap[] loadUCF101(String name, int img_w, int img_h, int img_num) {
		Bitmap[] bitmaps = new Bitmap[img_num];
		for (int i = 1; i <= img_num; i ++) {
			String path = "/sdcard/ncnn/datasets/UCF101/" + name + "-" + i + ".jpg";
			try {
				File f = new File(path);
				if (!f.exists()) return null;
				Bitmap bitmap = BitmapFactory.decodeFile(path);
				Bitmap rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true);
				bitmaps[i - 1] = Bitmap.createScaledBitmap(rgba, img_w, img_h, false);
			}
			catch (Exception e) {
				Log.e(TAG, "Fail to load bitmap: " + path);
				e.printStackTrace();
				return null;
			}
		}
		return bitmaps;
	}
}

class BConfig {
	String name;
	int start, number, offset;
	public BConfig(String _name, int _start, int _number, int _offset) {
		name = _name;
		start = _start;
		number = _number;
		offset = _offset;
	}
}