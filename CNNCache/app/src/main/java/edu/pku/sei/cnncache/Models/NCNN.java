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

package edu.pku.sei.cnncache.Models;

import android.graphics.Bitmap;
import java.util.LinkedHashMap;
import java.util.Map;

public class NCNN
{
	private static final String TAG = "NCNN";
	public static Map<String, MODEL> models = new LinkedHashMap<>();

    public native boolean Init(
            String param, String bin, boolean is_param_bin, byte[] words, String input, String output, int log_type);

	public boolean Init(MODEL model, byte[] words, int log_type) {
		return Init(model.param_path, model.bin_path, false,
				words, model.input_layer, model.output_layer, 0);
	}

	public native boolean Release();

    public native String Detect(Bitmap bitmap);

    public native String Classify(Bitmap bitmap, boolean use_cache, boolean update_cache,
								  int size, int[] x1, int[] y1, int[] x2, int[] y2, int off_x, int off_y);

    public native float[] run(Bitmap bitmap, boolean use_cache, boolean update_cache,
                            int size, int[] x1, int[] y1, int[] x2, int[] y2, int off_x, int off_y);

    public native boolean ClearCache();

    public native boolean test();

    static {
        System.loadLibrary("libncnn");
    }

    public NCNN() {
		models.put("googlenet", new MODEL(
				"data", "prob", 224,
				"/sdcard/ncnn/models/googlenet.param",
				"/sdcard/ncnn/models/googlenet.bin"));
		models.put("alexnet", new MODEL(
				"data", "prob", 227,
				"/sdcard/ncnn/models/alexnet.param",
				"/sdcard/ncnn/models/alexnet.bin"
		));
	}

    public class MODEL {
		public String input_layer;
		public String output_layer;
		public int input_size;
		public String param_path;
		public String bin_path;
		MODEL(String arg0, String arg1, int arg2, String arg3, String arg4) {
			input_layer = arg0;
			output_layer = arg1;
			input_size = arg2;
			param_path = arg3;
			bin_path = arg4;
		}
	}
}