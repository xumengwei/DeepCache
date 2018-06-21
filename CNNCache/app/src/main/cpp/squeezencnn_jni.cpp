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

#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>
#include <sstream>

// ncnn
#include "net.h"

#include "squeezenet_v1.1.id.h"

#include <sys/time.h>
#include <unistd.h>

static struct timeval tv_begin;
static struct timeval tv_end;
static double elasped;

std::string float2str (float number){
    std::ostringstream buff;
    buff << number;
    return buff.str();
}

static void bench_start()
{
    gettimeofday(&tv_begin, NULL);
}

static void bench_end(const char* comment)
{
    gettimeofday(&tv_end, NULL);
    elasped = ((tv_end.tv_sec - tv_begin.tv_sec) * 1000000.0f + tv_end.tv_usec - tv_begin.tv_usec) / 1000.0f;
//     fprintf(stderr, "%.2fms   %s\n", elasped, comment);
    __android_log_print(ANDROID_LOG_DEBUG, "NCNN_CNNCache", "total elapsed: %.2f", elasped);
}

static std::vector<std::string> squeezenet_words;
static ncnn::Net squeezenet;
static ncnn::Extractor ex;
static std::string in_layer, out_layer;
static int log_tp;

static std::vector<std::string> split_string(const std::string& str, const std::string& delimiter)
{
    std::vector<std::string> strings;

    std::string::size_type pos = 0;
    std::string::size_type prev = 0;
    while ((pos = str.find(delimiter, prev)) != std::string::npos)
    {
        strings.push_back(str.substr(prev, pos - prev));
        prev = pos + 1;
    }

    // To get the last substring (or only, if delimiter is not found)
    strings.push_back(str.substr(prev));

    return strings;
}

extern "C" {

// public native boolean Init(byte[] param, byte[] bin, byte[] words);
JNIEXPORT jboolean JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_Init(
        JNIEnv* env, jobject thiz, jstring param, jstring bin, jboolean is_param_bin,
        jbyteArray words, jstring input, jstring output, jint log_type)
{
    int ret;
    // init param
    {
        const char *param_path = env->GetStringUTFChars(param, JNI_FALSE);
        if (is_param_bin == JNI_TRUE)
            ret = squeezenet.load_param_bin(param_path);
        else
            ret = squeezenet.load_param(param_path);
        __android_log_print(ANDROID_LOG_DEBUG, "NCNN", "load_param %d", ret);
    }

    // init bin
    {
        const char *bin_path = env->GetStringUTFChars(bin, JNI_FALSE);
        ret = squeezenet.load_model(bin_path);
        __android_log_print(ANDROID_LOG_DEBUG, "NCNN", "load_model %d", ret);
    }

    // init words
    {
        int len = env->GetArrayLength(words);
        std::string words_buffer;
        words_buffer.resize(len);
        env->GetByteArrayRegion(words, 0, len, (jbyte*)words_buffer.data());
        squeezenet_words = split_string(words_buffer, "\n");
    }

    in_layer = env->GetStringUTFChars(input, JNI_FALSE);
    out_layer = env->GetStringUTFChars(output, JNI_FALSE);
    log_tp = log_type;

    // init extractor
    ex = squeezenet.create_extractor();

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_Release(JNIEnv* env, jobject thiz) {
    squeezenet.clear();
}

// image classification output
std::string print_log_0(JNIEnv* env, ncnn::Mat out) {
    std::vector<float> cls_scores;
    cls_scores.resize(out.c);
    for (int j=0; j<out.c; j++)
    {
        const float* prob = out.data + out.cstep * j;
        cls_scores[j] = prob[0];
    }
    // return top class
    int top_class = 0;
    float max_score = 0.f;
//    std::string flog = "FLOG";
    for (size_t i=0; i<cls_scores.size(); i++)
    {
        float s = cls_scores[i];
        // __android_log_print(ANDROID_LOG_DEBUG, "NCNN", "Detect_result %d %f", i, s);
//        flog += " ";
//        flog += cls_scores[i];
        if (s > max_score)
        {
            top_class = i;
            max_score = s;
        }
    }

    const std::string& word = squeezenet_words[top_class];
    __android_log_print(ANDROID_LOG_DEBUG, "CNNCache", "object: %s, prob: %.3f", word.c_str(), max_score);
    char tmp[32];
    sprintf(tmp, "%.3f", max_score);
    std::string result_str = std::string(word.c_str() + 10) + " = " + tmp;

    // +10 to skip leading n03179701
    return result_str;
}

// Face detection output
std::string print_log_1(JNIEnv* env, ncnn::Mat out) {
    __android_log_print(ANDROID_LOG_DEBUG, "NCNN", "Detect_result %d %d %d", out.c, out.w, out.h);
    std::string result_str = std::string("Hello");

    for (int i = 0; i < out.c; i ++)
        __android_log_print(ANDROID_LOG_DEBUG, "NCNN", "yolo %d %f", i, out.data[i]);

    return result_str;
}

// public native String Detect(Bitmap bitmap);
JNIEXPORT jstring JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_Detect(JNIEnv* env, jobject thiz, jobject bitmap)
{
    ncnn::Mat in;
    {
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, bitmap, &info);
        int width = info.width;
        int height = info.height;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
            return NULL;

        void* indata;
        AndroidBitmap_lockPixels(env, bitmap, &indata);

        in = ncnn::Mat::from_pixels((const unsigned char*)indata, ncnn::Mat::PIXEL_RGBA2BGR, width, height);

        AndroidBitmap_unlockPixels(env, bitmap);
    }

//    const float mean_vals[3] = {104.f, 117.f, 123.f};
//    in.substract_mean_normalize(mean_vals, 0); // Don't normalize if we use cache

    ex.set_light_mode(false); // Current cache impl doesn't support light mode
    ex.set_cache_mode(true);
    ex.set_num_threads(4);

    ncnn::MRect mRect;
    int x_offset = 0;
    int y_offset = 0;
    mRect.set_offset(x_offset, y_offset);
    mRect.add_rect(0, 0, 50, 50);
    ex.input_mrect(0, mRect);

    ex.input(in_layer.c_str(), in);
    bench_start();
    ncnn::Mat out;
    ex.extract(out_layer.c_str(), out);
    bench_end("detect");

    std::string ret;
    if (log_tp == 0)
        ret = print_log_0(env, out);
    else if (log_tp == 1)
        ret = print_log_1(env, out);

    ex.update_cnncache();
    ex.clear_blob_data();

    return env->NewStringUTF(ret.c_str());
}

JNIEXPORT jstring JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_Classify(
        JNIEnv* env, jobject thiz, jobject bitmap, jboolean juse_cache, jboolean jupdate_cache,
        jint size, jintArray x1, jintArray y1, jintArray x2, jintArray y2, jint off_x, jint off_y) {
    bool use_cache = (juse_cache == JNI_TRUE);
    bool update_cache = (jupdate_cache == JNI_TRUE);
    ncnn::Mat in;
    {
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, bitmap, &info);
        int width = info.width;
        int height = info.height;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
            return NULL;

        void *indata;
        AndroidBitmap_lockPixels(env, bitmap, &indata);

        in = ncnn::Mat::from_pixels((const unsigned char *) indata, ncnn::Mat::PIXEL_RGBA2BGR,
                                    width, height);

        AndroidBitmap_unlockPixels(env, bitmap);
    }
//    const float mean_vals[3] = {104.f, 117.f, 123.f};
//    in.substract_mean_normalize(mean_vals, 0); // Don't normalize if we use cache

    ex.set_light_mode(false); // Current cache impl doesn't support light mode
    ex.set_cache_mode(use_cache);
    ex.set_num_threads(4);

    if (use_cache) {
        ncnn::MRect mRect;
        mRect.set_offset(off_x, off_y);
        jint* xx1 = env->GetIntArrayElements(x1, 0);
        jint* yy1 = env->GetIntArrayElements(y1, 0);
        jint* xx2 = env->GetIntArrayElements(x2, 0);
        jint* yy2 = env->GetIntArrayElements(y2, 0);
        for (int i = 0; i < size; i ++) {
            mRect.add_rect(xx1[i], yy1[i], xx2[i], yy2[i]);
        }
        ex.input_mrect(in_layer.c_str(), mRect); // TODO: change to string param "data"
    }

    ex.input(in_layer.c_str(), in);
    bench_start();
    ncnn::Mat out;
    ex.extract(out_layer.c_str(), out);
    bench_end("classify");

    std::string ret;
    if (log_tp == 0)
        ret = print_log_0(env, out);
    else if (log_tp == 1)
        ret = print_log_1(env, out);

    if (update_cache) {
        ex.update_cnncache();
    }

    ex.clear_blob_data();

    return env->NewStringUTF(ret.c_str());
}

JNIEXPORT jfloatArray JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_run(
        JNIEnv* env, jobject thiz, jobject bitmap, jboolean use_cache, jboolean update_cache,
        jint size, jintArray x1, jintArray y1, jintArray x2, jintArray y2, jint off_x, jint off_y)
{
    __android_log_print(ANDROID_LOG_DEBUG, "NCNN_CNNCache", "RUN_BEGIN");
    ncnn::Mat in;
    {
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, bitmap, &info);
        int width = info.width;
        int height = info.height;
//        if (width != 227 || height != 227)
//            return NULL;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
            return NULL;

        void* indata;
        AndroidBitmap_lockPixels(env, bitmap, &indata);

        in = ncnn::Mat::from_pixels((const unsigned char*)indata, ncnn::Mat::PIXEL_RGBA2BGR, width, height);

        AndroidBitmap_unlockPixels(env, bitmap);
    }

    // squeezenet
    std::vector<float> cls_scores;
    {
//        const float mean_vals[3] = {104.f, 117.f, 123.f};
//        in.substract_mean_normalize(mean_vals, 0); // Don't normalize if we use cache

        ex.set_light_mode(false); // Current cache impl doesn't support light mode
        ex.set_num_threads(4);
        ex.set_cache_mode(use_cache);

        if (use_cache) {
            ncnn::MRect mRect;
            mRect.set_offset(off_x, off_y);
            jint* xx1 = env->GetIntArrayElements(x1, 0);
            jint* yy1 = env->GetIntArrayElements(y1, 0);
            jint* xx2 = env->GetIntArrayElements(x2, 0);
            jint* yy2 = env->GetIntArrayElements(y2, 0);
            for (int i = 0; i < size; i ++) {
                mRect.add_rect(xx1[i], yy1[i], xx2[i], yy2[i]);
            }
            ex.input_mrect(in_layer.c_str(), mRect); // TODO: change to string param "data"
        }

        bench_start();
//        ex.input(squeezenet_v1_1_param_id::BLOB_data, in);
        ex.input(in_layer.c_str(), in);

        ncnn::Mat out;
//        ex.extract(squeezenet_v1_1_param_id::BLOB_prob, out);
        ex.extract(out_layer.c_str(), out);
        bench_end("benchmark");

        cls_scores.resize(out.c);
        for (int j=0; j<out.c; j++)
        {
            const float* prob = out.data + out.cstep * j;
            cls_scores[j] = prob[0];
        }

        // collect output info
        std::string output_info;
        const int batch_output = 20;
        for (int i = 0; i < out.c; i ++) {
            if (i % batch_output == 0) {
                output_info = "OUTPUTRES";
            }
            output_info.append(" ");
            output_info.append(float2str(*(out.data + out.cstep * i)));
            if ((i + 1) % batch_output == 0)
                __android_log_print(ANDROID_LOG_DEBUG, "NCNN_CNNCache", "%s", output_info.c_str());
        }
    }

    if (update_cache)
        ex.update_cnncache();
    ex.clear_blob_data();

    jfloatArray ret = env->NewFloatArray(cls_scores.size());
    env->SetFloatArrayRegion(ret, 0, cls_scores.size(), &cls_scores[0]);
    __android_log_print(ANDROID_LOG_DEBUG, "NCNN_CNNCache", "RUN_END");
    return ret;
}

JNIEXPORT jboolean JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_ClearCache(JNIEnv* env, jobject thiz)
{
    ex.clear_cnncache();
    return JNI_TRUE;
}

// This func is to test whether our mrect propagation is right
JNIEXPORT jboolean JNICALL Java_edu_pku_sei_cnncache_Models_NCNN_test(JNIEnv* env, jobject thiz)
{
    srand (time(NULL));
    const int int_w = 227, int_h = 227;
    ncnn::MRect mRect;
    int x_offset = 10;
    int y_offset = 10;
    mRect.set_offset(x_offset, y_offset);
    if (x_offset > 0 && y_offset > 0) {
        mRect.add_rect(int_w - x_offset, 0, int_w - 1, int_h - 1);
        mRect.add_rect(0, int_h - y_offset, int_w - x_offset, int_h - 1);
    }
//    mRect.add_rect(100, 100, 150, 150);
//    mRect.add_rect(0, 30, 200, 30);
    ncnn::Mat m1(int_w, int_h, 3), m2(int_w, int_h, 3);
    for (int c = 0; c < m1.c; c ++)
        for (int h = 0; h < int_h; h ++)
            for (int w = 0; w < int_w; w ++) {
                m1.channel(c).row(h)[w] = rand() % 256;
            }
    for (int c = 0; c < m1.c; c ++)
        for (int h = 0; h < int_h; h ++)
            for (int w = 0; w < int_w; w ++) {
                bool cached = true;
                for (ncnn::rect r : mRect.changed_vecs) {
                    if (r.y1 <= h && r.y2 >= h && r.x1 <= w && r.x2 >= w) {
                        cached = false;
                        break;
                    }
                }
                if (cached) {
                    m2.channel(c).row(h)[w] = m1.channel(c).row(h + y_offset)[w + x_offset];
                }
                else {
                    m2.channel(c).row(h)[w] = rand() % 256;
                }
            }


    ncnn::Extractor ex1 = squeezenet.create_extractor();
    ex1.set_light_mode(false); // Current cache impl doesn't support light mode
    ex1.set_num_threads(4);
    const char input[5] = "data";
    const char output[32] = "prob";

    double t1, t2;

    ncnn::Mat out1;
    ex1.set_cache_mode(true);
    ex1.input(input, m1);
    ex1.extract(output, out1);
    ex1.update_cnncache();
    ex1.clear_blob_data();
    ex1.input(input, m2);
    ex1.input_mrect(squeezenet_v1_1_param_id::BLOB_data, mRect);

    gettimeofday(&tv_begin, NULL);
    ex1.extract(output, out1);
    gettimeofday(&tv_end, NULL);
    t1 = ((tv_end.tv_sec - tv_begin.tv_sec) * 1000000.0f + tv_end.tv_usec - tv_begin.tv_usec) / 1000.0f;


    ncnn::Mat out2;
    ex1.clear_blob_data();
    ex1.set_cache_mode(false);
    ex1.input(input, m2);

    gettimeofday(&tv_begin, NULL);
    ex1.extract(output, out2);
    gettimeofday(&tv_end, NULL);
    t2 = ((tv_end.tv_sec - tv_begin.tv_sec) * 1000000.0f + tv_end.tv_usec - tv_begin.tv_usec) / 1000.0f;

    int total = 0, ft = 0, c = 0, w = 0, h = 0;
    for (c = 0; c < out1.c; c ++) {
        for (w = 0; w < out1.w; w++)
            for (h = 0; h < out1.h; h++) {
                if (out1.channel(c).row(h)[w] != out2.channel(c).row(h)[w]) {
//                    if (c == 0) {
                        __android_log_print(ANDROID_LOG_DEBUG, "CNNCache", "Error: %d %d %f\n",
                                            w, h, out1.channel(c).row(h)[w] - out2.channel(c).row(h)[w]);
//                    }
                    ft++;
                }
                total++;
            }
    }

    __android_log_print(ANDROID_LOG_DEBUG, "CNNCache", "False: %d/%d\n", ft, total);
    __android_log_print(ANDROID_LOG_DEBUG, "CNNCache", "Time: %f %f\n", t1, t2);

    return JNI_TRUE;
}

}
