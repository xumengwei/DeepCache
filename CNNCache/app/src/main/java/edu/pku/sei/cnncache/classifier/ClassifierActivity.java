/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.pku.sei.cnncache.classifier;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.pku.sei.cnncache.CameraActivity;
import edu.pku.sei.cnncache.DS.DS;
import edu.pku.sei.cnncache.DS.DS_UTIL;
import edu.pku.sei.cnncache.Models.NCNN;
import edu.pku.sei.cnncache.R;
import edu.pku.sei.cnncache.UI.BorderedText;
import edu.pku.sei.cnncache.util.ImageUtils;
import edu.pku.sei.cnncache.util.Logger;

public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
	private static final Logger LOGGER = new Logger();

//	protected static final boolean SAVE_PREVIEW_BITMAP = false;

	private Bitmap rgbFrameBitmap = null;
	private Bitmap croppedBitmap = null;
	private Bitmap preCroppedBitmap = null;
//	private Bitmap cropCopyBitmap = null;

	private long[] processingTimeMs = new long[10];
	private int processingTimeMs_pos = 0;

	private static int INPUT_SIZE = 224;


	private static final boolean MAINTAIN_ASPECT = true;

	private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);


	private Integer sensorOrientation;
	private Matrix frameToCropTransform;
	private Matrix cropToFrameTransform;


	private BorderedText borderedText;
	private Switch cache_switch;
	private boolean cache_enabled = false;

	private NCNN ncnn;
	private List<String> model_choices;
	private static final String default_model = "alexnet";
	private String current_model = default_model;
	private boolean model_changed = false;
	byte[] words = null;

	// image matching configuration
	DS ds = null;
	DS_UTIL ds_util = null;
	int MATCH_BLK_SIZE = 10;
	int MATCH_STEP = 3;
	double MATCH_THRESHOLD = 20;
	int MATCH_ITER = 10;
	int cur_iter = 0;
	boolean SAVE_BITMAP = false;
	int bitmap_cnt = 0;


	@Override
	protected int getLayoutId() {
		return R.layout.camera_connection_fragment;
	}

	@Override
	protected Size getDesiredPreviewFrameSize() {
		return DESIRED_PREVIEW_SIZE;
	}

	private static final float TEXT_SIZE_DIP = 10;

	private void chooseModel(String model_name) {
		NCNN.MODEL model = ncnn.models.get(model_name);
		INPUT_SIZE = model.input_size;
		ncnn.Init(model, words, 0);
		for (int x = 0; x < processingTimeMs.length; x ++)
			processingTimeMs[x] = 0;
		processingTimeMs_pos = 0;
		cur_iter = 0;
	}

	@Override
	public void onPreviewSizeChosen(final Size size, final int rotation) {
		// Initialize ncnn
		try {
			InputStream assetsInputStream = getAssets().open("synset_words.txt");
			int available = assetsInputStream.available();
			words = new byte[available];
			int byteCode = assetsInputStream.read(words);
			assetsInputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		ncnn = new NCNN();
		model_choices = new ArrayList<>();
		model_choices.addAll(ncnn.models.keySet());
		Spinner spinner = (Spinner) findViewById(R.id.spinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>
				(this, android.R.layout.simple_spinner_dropdown_item, model_choices);
		spinner.setAdapter(adapter);
		spinner.setSelection(model_choices.indexOf(default_model));
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				String temp = model_choices.get(i);
				if (!temp.equals(current_model)) {
					model_changed = true;
					current_model = temp;
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});

		cache_switch = (Switch) findViewById(R.id.cache);
		cache_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
				cache_enabled = b;
				cur_iter = 0;
			}
		});

		chooseModel(default_model);


		final float textSizePx = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
		borderedText = new BorderedText(textSizePx);
		borderedText.setTypeface(Typeface.MONOSPACE);
		previewWidth = size.getWidth();
		previewHeight = size.getHeight();

		sensorOrientation = rotation - getScreenOrientation();
		LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

		LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
		rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
		croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

		frameToCropTransform = ImageUtils.getTransformationMatrix(
				previewWidth, previewHeight,
				INPUT_SIZE, INPUT_SIZE,
				sensorOrientation, MAINTAIN_ASPECT);

		cropToFrameTransform = new Matrix();
		frameToCropTransform.invert(cropToFrameTransform);
	}

	void showResults(String res) {
		StringBuilder sb = new StringBuilder();
		sb.append("class: " + res + "\n");
		long lastProcessingTimeMs = processingTimeMs[
				(processingTimeMs.length + processingTimeMs_pos - 1) % processingTimeMs.length];
		if (lastProcessingTimeMs == 0)
			sb.append("processing time (last 1 frame): null\n");
		else
			sb.append("processing time (last 1 frame): " + lastProcessingTimeMs + "ms\n");
		int avgProcessingTimeMs = 0;
		int num = 0;
		for (int i = 0; i < 5; i ++) {
			long temp = processingTimeMs[
					(processingTimeMs.length + processingTimeMs_pos - i) % processingTimeMs.length];
			if (temp > 0) {
				num ++;
				avgProcessingTimeMs += temp;
			}
		}
		if (avgProcessingTimeMs == 0)
			sb.append("processing time (last 5 frame): null\n");
		else
			sb.append("processing time (last 5 frame): " + (avgProcessingTimeMs / num) + "ms\n");
		Message msg = Message.obtain();
		msg.what = RESULT_VIEW_HANDLE_MSG;
		msg.obj = sb.toString();
		this.UIHandler.sendMessage(msg);
	}

	@Override
	protected void processImage() {
		rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
		final Canvas canvas = new Canvas(croppedBitmap);
		canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

//		if (SAVE_PREVIEW_BITMAP) {
//			ImageUtils.saveBitmap(croppedBitmap);
//		}
		if (model_changed) {
			ncnn.Release();
			chooseModel(current_model);
		}
		model_changed = false;
		runInBackground(
				new Runnable() {
					@Override
					public void run() {
						_processImage();
					}
				});
	}

	private void save_bitmap(Bitmap img) {
		String root = Environment.getExternalStorageDirectory().toString();
		File myDir = new File(root + "/ncnn/imgs/");
		String fname = "Image-" + bitmap_cnt + ".jpg";
		File file = new File(myDir, fname);
		if (file.exists())
			file.delete();
		try {
			FileOutputStream out = new FileOutputStream(file);
			img.compress(Bitmap.CompressFormat.JPEG, 100, out);
			out.flush();
			out.close();
			bitmap_cnt ++;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void _processImage() {
		boolean cache_enabled_atomic = cache_enabled;
		if (cache_enabled_atomic) {
			if (ds == null) {
				ds = new DS();
				ds.init(this, INPUT_SIZE, INPUT_SIZE, MATCH_BLK_SIZE, MATCH_STEP, MATCH_THRESHOLD);
				ds_util = new DS_UTIL(ds);
			}
		}
		boolean use_cache = cache_enabled_atomic && (cur_iter != 0);
		boolean update_cache = cache_enabled_atomic && (cur_iter != (MATCH_ITER - 1));
		final long startTime = SystemClock.uptimeMillis();
		String ret;
		if (use_cache) {
			ds_util.run_image_matching(preCroppedBitmap, croppedBitmap, MATCH_THRESHOLD);
			ret = ncnn.Classify(croppedBitmap, use_cache, update_cache,
					ds_util.size, ds_util.x1, ds_util.y1, ds_util.x2, ds_util.y2, ds_util.ox, ds_util.oy);
		}
		else {
			ret = ncnn.Classify(croppedBitmap, use_cache, update_cache,
					0, null, null, null, null, 0, 0);
		}
		processingTimeMs[processingTimeMs_pos] = SystemClock.uptimeMillis() - startTime;
		processingTimeMs_pos = (processingTimeMs_pos + 1) % processingTimeMs.length;
		if (update_cache) {
			preCroppedBitmap = croppedBitmap.copy(croppedBitmap.getConfig(), true);
		}
		if (SAVE_BITMAP && use_cache) {
			save_bitmap(croppedBitmap);
		}
		if (cache_enabled_atomic) {
			cur_iter = (cur_iter + 1) % MATCH_ITER;
		}
//		cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
		showResults(ret);
//		requestRender();
		readyForNextImage();
	}
}
