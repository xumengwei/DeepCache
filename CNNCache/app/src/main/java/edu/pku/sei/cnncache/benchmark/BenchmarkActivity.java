// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package edu.pku.sei.cnncache.benchmark;

import android.app.Activity;
import android.os.Bundle;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;

import edu.pku.sei.cnncache.DS.DS;
import edu.pku.sei.cnncache.DS.DS_RES;
import edu.pku.sei.cnncache.Models.NCNN;
import edu.pku.sei.cnncache.R;

public class BenchmarkActivity extends Activity implements View.OnClickListener {
	private static final int SELECT_IMAGE = 1;
	private static String TAG = "NCNN_CNNCACHE";

	private TextView infoResult;
	private ImageView imageView;
	private Bitmap yourSelectedImage = null;

	private NCNN ncnn = new NCNN();

	int img_width = 227;
	int img_height = 227;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.benchmark_layout);

		try {
			initNcnn();
		} catch (IOException e) {
			Log.e("MainActivity", "initNcnn error");
		}

		infoResult = (TextView) findViewById(R.id.infoResult);
		imageView = (ImageView) findViewById(R.id.imageView);

		Button buttonImage = (Button) findViewById(R.id.buttonImage);
		buttonImage.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(Intent.ACTION_PICK);
				i.setType("image/*");
				startActivityForResult(i, SELECT_IMAGE);
			}
		});

		Button buttonDetect = (Button) findViewById(R.id.buttonDetect);
		buttonDetect.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (yourSelectedImage == null)
					return;

				String result = ncnn.Detect(yourSelectedImage);

				if (result == null) {
					infoResult.setText("detect failed");
				} else {
					infoResult.setText(result);
				}
			}
		});

		Button buttonClearCache = (Button) findViewById(R.id.buttonClearCache);
		buttonClearCache.setOnClickListener(this);

		Button buttonDSTest = (Button) findViewById(R.id.DSTest);
		buttonDSTest.setOnClickListener(this);

		Button buttonTest = (Button) findViewById(R.id.buttonTest);
		buttonTest.setOnClickListener(this);

		Button runTest = (Button) findViewById(R.id.runBenchmark);
		runTest.setOnClickListener(this);
	}

	private void initNcnn() throws IOException {
		byte[] words;
		{
			InputStream assetsInputStream = getAssets().open("synset_words.txt");
			int available = assetsInputStream.available();
			words = new byte[available];
			int byteCode = assetsInputStream.read(words);
			assetsInputStream.close();
		}

		// Squeezenet
//        ncnn.Init(
//                "/sdcard/ncnn/models/squeezenet.param",
//                "/sdcard/ncnn/models/squeezenet.bin",
//                false, words, "data", "prob", 0);

		// Alexnet
//        ncnn.Init(
//                "/sdcard/ncnn/models/alexnet.param",
//                "/sdcard/ncnn/models/alexnet.bin",
//                false, words, "data", "prob", 0);

		// GoogleNet
		ncnn.Init(
				"/sdcard/ncnn/models/googlenet.param",
				"/sdcard/ncnn/models/googlenet.bin",
				false, words, "data", "prob", 0);

		// MobileNet: https://github.com/shicai/MobileNet-Caffe
//		ncnn.Init(
//				"/sdcard/ncnn/models/mobilenet.param",
//				"/sdcard/ncnn/models/mobilenet.bin",
//				false, words, "data", "fc7", 0);

		// ResNet-50: https://github.com/KaimingHe/deep-residual-networks
//		ncnn.Init(
//				"/sdcard/ncnn/models/resnet.param",
//				"/sdcard/ncnn/models/resnet.bin",
//				false, words, "data", "prob", 0);

		// Face detection: https://arxiv.org/pdf/1502.02766.pdf
//		ncnn.Init(
//				"/sdcard/ncnn/models/face_detection.param",
//				"/sdcard/ncnn/models/face_detection.bin",
//				false, words, "data", "prob", 1);

		// object detection: https://github.com/hojel/caffe-yolo-model
//		ncnn.Init(
//				"/sdcard/ncnn/models/yolo_small.param",
//				"/sdcard/ncnn/models/yolo_small.bin",
//				false, words, "data", "result", 1);
//		img_width = 227;
//		img_height = 227;

		// Face detection
//		ncnn.Init(
//				"/sdcard/ncnn/models/48net.param",
//				"/sdcard/ncnn/models/48net.bin",
//				false, words, "data", "prob", 1);
//		img_width = 48;
//		img_height = 48;

		// Face recognition: https://github.com/ydwen/caffe-face
//		ncnn.Init(
//				"/sdcard/ncnn/models/face.param",
//				"/sdcard/ncnn/models/face.bin",
//				false, words, "data", "fc5", 1);
//		img_width = 112;
//		img_height = 96;

//		ncnn.Init(
//				"/sdcard/ncnn/models/driving.proto",
//				"/sdcard/ncnn/models/driving.bin",
//				false, words, "data", "fc5", 1);
//		img_width = 66;
//		img_height = 200;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK && null != data) {
			Uri selectedImage = data.getData();

			try {
				if (requestCode == SELECT_IMAGE) {
					Bitmap bitmap = decodeUri(selectedImage);

					Bitmap rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true);

					// resize to 227x227
					yourSelectedImage = Bitmap.createScaledBitmap(rgba, img_width, img_height, false);

					imageView.setImageBitmap(yourSelectedImage);
				}
			} catch (FileNotFoundException e) {
				Log.e("MainActivity", "FileNotFoundException");
				return;
			}
		}
	}

	private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException {
		// Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o);

		// The new size we want to scale to
		final int REQUIRED_SIZE = 400;

		// Find the correct scale value. It should be the power of 2.
		int width_tmp = o.outWidth, height_tmp = o.outHeight;
		int scale = 1;
		while (true) {
			if (width_tmp / 2 < REQUIRED_SIZE
					|| height_tmp / 2 < REQUIRED_SIZE) {
				break;
			}
			width_tmp /= 2;
			height_tmp /= 2;
			scale *= 2;
		}

		// Decode with inSampleSize
		BitmapFactory.Options o2 = new BitmapFactory.Options();
		o2.inSampleSize = scale;
		return BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, o2);
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.buttonClearCache) {
			ncnn.ClearCache();
		} else if (view.getId() == R.id.buttonTest) {
			ncnn.test();
		} else if (view.getId() == R.id.DSTest) {
			run_DSTest();
		} else if (view.getId() == R.id.runBenchmark) {
			BenchmarkRunner br = new BenchmarkRunner();
			br.start();
		}
	}

	private Bitmap loadBitmap(int resource) {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		options.inScaled = false;
		return BitmapFactory.decodeResource(getResources(), resource, options);
	}

	private void run_DSTest() {
		int img_size = 224;
		int blk_size = 10;
		int steps = 3;
		double threshold = 25;
		int img_num = 73;
		int[] id;
		DS ds = new DS();
		ds.init(this, img_size, img_size, blk_size, steps, threshold);
		id = new int[img_num + 1];
		for (int i = 1; i <= img_num; i++) {
			id[i] = getResources().getIdentifier("p" + i, "drawable", getPackageName());
		}
		DS_RES ds_res = new DS_RES();
		for (int i = 1; i + 1 <= img_num; i++) {
			Bitmap pre_pic = loadBitmap(id[i]);
			Bitmap cur_pic = loadBitmap(id[i + 1]);
			ds_res = ds.run(pre_pic, cur_pic, ds_res, true);
		}
	}

	class BenchmarkRunner extends Thread {
		@Override
		public void run() {
			Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
			String[] bs = benchmark.get_clips();
			int repeat = 1;
			int img_num = 10;
			for (String s : bs) {
				Bitmap[] bitmaps = benchmark.loadUCF101(s, img_width, img_height, img_num);
				if (bitmaps == null)
					continue;
				for (int i = 0; i < repeat; i ++) {
					Log.i(TAG, "run_Benchmark " + s + " " + i);
					run_Benchmark(bitmaps);
//					try {
//						Thread.sleep(30 * 1000);
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
				}
			}
		}
	}

	private void run_Benchmark(Bitmap[] bitmaps) {
		int img_size = 227;
		int blk_size = 10;
		int steps = 3;
		double threshold = 20;
		int round = 10;
		int blk_num = img_size / blk_size;
		int img_num = bitmaps.length;
		DS ds = new DS();
		ds.init(this, img_size, img_size, blk_size, steps, threshold);

		// ground truth
		ArrayList<float[]> gt = new ArrayList<>();
		for (int i = 0; i < img_num; i ++) {
			gt.add(ncnn.run(bitmaps[i], false, false,
					0, null, null, null, null, 0, 0));
		}

		// use cache
		ArrayList<float[]> rs = new ArrayList<>();
		DS_RES ds_res = new DS_RES();
		int[] x1 = new int[blk_num * blk_num];
		int[] y1 = new int[blk_num * blk_num];
		int[] x2 = new int[blk_num * blk_num];
		int[] y2 = new int[blk_num * blk_num];
		for (int i = 0; i < img_num; i ++) {
			if (i % round == 0) {
				float[] res = ncnn.run(bitmaps[0], false, true,
						0, x1, y1, x2, y2, ds_res.offset_y, ds_res.offset_x);
				rs.add(res);
				continue;
			}
			ds_res = ds.run(bitmaps[i - 1], bitmaps[i], ds_res, true);
			int size = 0;
			int ox = ds_res.offset_y;
			int oy = ds_res.offset_x;
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
//					Log.i(TAG, "Low PSNR: " + index_x + " " + index_y + " " + ds_res.PSNR[j]);
				}
			}

			StringBuilder info = new StringBuilder();
			info.append("iter:" + i);
			info.append(", size:" + size + "/" + (blk_num * blk_num));
			info.append(", offset:(" + ox + "," + oy + ")");
//			info.append(", rect:");
//			for (int j = 0; j < size; j ++)
//				info.append("(" + x1[j] + "," + y1[j] + "," + x2[j] + "," + y2[j] + ")");
			Log.i(TAG, info.toString());

			float[] res = ncnn.run(bitmaps[i], true, true,
					size, x1, y1, x2, y2, ox, oy);
			rs.add(res);
		}

		// Compute the accuracy, efficiency
//		for (int i = 0; i < rs.size(); i ++) {
//			ArrayList<Score> rs_score = new ArrayList<>();
//			ArrayList<Score> gt_score = new ArrayList<>();
//			for (int j = 0; j < rs.get(i).length; j ++) {
//				rs_score.add(new Score(j, rs.get(i)[j]));
//				gt_score.add(new Score(j, gt.get(i)[j]));
//			}
//			Collections.sort(rs_score, new ScoreComparator());
//			Collections.sort(gt_score, new ScoreComparator());
//			for (int j = 0; j < 5; j ++) {
//				Log.i(TAG, "gt top-" + j + " " + gt_score.get(j).index + " " + gt_score.get(j).score);
//				Log.i(TAG, "rs top-" + j + " " + rs_score.get(j).index + " " + rs_score.get(j).score);
//			}
//			Log.i(TAG, "Compare " + cal_dev(rs.get(i), gt.get(i)) + " " + cal_dev(gt.get(0), gt.get(i)));
//		}
	}

	private boolean contain_rect(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
		return (x1 <= x3) && (y1 <= y3) && (x2 >= x4) && (y2 >= y4);
	}

	private double cal_dev(float[] x, float[] y) {
		double res = 0;
		for (int i = 0; i < x.length; i ++) {
			res += Math.pow(x[i] - y[i], 2);
		}
		return Math.sqrt(res / x.length);
	}


	class Score {
		final int index;
		final float score;
		Score(int i, float s) {
			index = i;
			score = s;
		}
	}

	class ScoreComparator implements Comparator<Score> {
		@Override
		public int compare(Score score, Score t1) {
			return score.score < t1.score ? 1 : -1;
		}
	}
}
