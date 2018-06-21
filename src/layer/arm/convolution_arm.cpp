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

#include "convolution_arm.h"

namespace ncnn {

#include "convolution_1x1.h"
#include "convolution_2x2.h"
#include "convolution_3x3.h"
#include "convolution_4x4.h"
#include "convolution_5x5.h"
#include "convolution_7x7.h"

DEFINE_LAYER_CREATOR(Convolution_arm)

int Convolution_arm::forward(const Mat& bottom_blob, Mat& top_blob) const
{
    // convolv with NxN kernel
    // value = value + bias

    log_time_begin();

    if (kernel_size > 7 || stride > 4 || dilation != 1)
    {
        return Convolution::forward(bottom_blob, top_blob);
    }

    typedef void (*conv_func)(const Mat&, Mat&, const Mat&, const Mat&);

    // kernel_size x stride
    conv_func conv_func_table[7][4] =
    {
        {
            conv1x1s1_neon,
            conv1x1s2_neon,
            0,
            0
        }, // kernel_size = 1
        {
            conv2x2s1_neon,
            0,
            0,
            0
        }, // kernel_size = 2
        {
            conv3x3s1_neon,
            conv3x3s2_neon,
            0,
            0
        }, // kernel_size = 3
        {
            0,
            0,
            0,
            conv4x4s4_neon
        }, // kernel_size = 4
        {
            conv5x5s1_neon,
            conv5x5s2_neon,
            0,
            0
        }, // kernel_size = 5
        {
            0,
            0,
            0,
            0
        }, // kernel_size = 6
        {
            conv7x7s1_neon,
            conv7x7s2_neon,
            0,
            0
        }  // kernel_size = 7
    };

    conv_func conv = conv_func_table[kernel_size-1][stride-1];
    if (!conv)
    {
        return Convolution::forward(bottom_blob, top_blob);
    }

    int w = bottom_blob.w;
    int h = bottom_blob.h;
    int channels = bottom_blob.c;

    Mat bottom_blob_bordered = bottom_blob;
    if (pad > 0)
    {
        copy_make_border(bottom_blob, bottom_blob_bordered, pad, pad, pad, pad, BORDER_CONSTANT, 0.f);
        if (bottom_blob_bordered.empty())
            return -100;

        w = bottom_blob_bordered.w;
        h = bottom_blob_bordered.h;
    }
    else if (pad == -233)
    {
        int wpad = kernel_size + (w - 1) / stride * stride - w;
        int hpad = kernel_size + (h - 1) / stride * stride - h;
        if (wpad > 0 || hpad > 0)
        {
            copy_make_border(bottom_blob, bottom_blob_bordered, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, BORDER_CONSTANT, 0.f);
            if (bottom_blob_bordered.empty())
                return -100;
        }

        w = bottom_blob_bordered.w;
        h = bottom_blob_bordered.h;
    }

    int outw = (w - kernel_size) / stride + 1;
    int outh = (h - kernel_size) / stride + 1;

    LOGI("Convolution_arm::forward input=%dx%dx%d pad=%d ksize=%d output=%d stride=%d\n",
        bottom_blob.w, bottom_blob.h, channels, pad, kernel_size, num_output, stride);

    top_blob.create(outw, outh, num_output);
    if (top_blob.empty())
        return -100;

    conv(bottom_blob_bordered, top_blob, weight_data, bias_data);

    log_time_end("conv_arm");

    return 0;
}

#if NCNN_CNNCACHE
int Convolution_arm::forward_cached(const Mat& bottom_blob, Mat& top_blob, MRect& mrect, Mat& cached_blob) const
{
    // convolv with NxN kernel
    // value = value + bias

    if (kernel_size > 7 || stride > 4 || dilation != 1)
    {
        return Convolution::forward_cached(bottom_blob, top_blob, mrect, cached_blob);
    }

    // No cache data available
    if (cached_blob.empty())
    {
        return Convolution_arm::forward(bottom_blob, top_blob);
    }

    log_time_begin();

    typedef void (*conv_func)(const Mat&, Mat&, const Mat&, const Mat&, bool*);

    // kernel_size x stride
    conv_func conv_func_table[7][4] =
    {
        {
            conv1x1s1_neon_cached,
            conv1x1s2_neon_cached,
            0,
            0
        }, // kernel_size = 1
        {
            conv2x2s1_neon_cached,
            0,
            0,
            0
        }, // kernel_size = 2
        {
            conv3x3s1_neon_cached,
            conv3x3s2_neon_cached,
            0,
            0
        }, // kernel_size = 3
        {
            0,
            0,
            0,
            conv4x4s4_neon_cached
        }, // kernel_size = 4
        {
            conv5x5s1_neon_cached,
            conv5x5s2_neon_cached,
            0,
            0
        }, // kernel_size = 5
        {
            0,
            0,
            0,
            0
        }, // kernel_size = 6
        {
            conv7x7s1_neon_cached,
            conv7x7s2_neon_cached,
            0,
            0
        }  // kernel_size = 7
    };

    conv_func conv = conv_func_table[kernel_size-1][stride-1];
    if (!conv)
    {
        return Convolution::forward_cached(bottom_blob, top_blob, mrect, cached_blob);
    }

    int w = bottom_blob.w;
    int h = bottom_blob.h;
    int channels = bottom_blob.c;

    Mat bottom_blob_bordered = bottom_blob;
    if (pad > 0)
    {
        copy_make_border(bottom_blob, bottom_blob_bordered, pad, pad, pad, pad, BORDER_CONSTANT, 0.f);
        if (bottom_blob_bordered.empty())
            return -100;

        w = bottom_blob_bordered.w;
        h = bottom_blob_bordered.h;
    }
    else if (pad == -233)
    {
        int wpad = kernel_size + (w - 1) / stride * stride - w;
        int hpad = kernel_size + (h - 1) / stride * stride - h;
        if (wpad > 0 || hpad > 0)
        {
            copy_make_border(bottom_blob, bottom_blob_bordered, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, BORDER_CONSTANT, 0.f);
            if (bottom_blob_bordered.empty())
                return -100;
        }

        w = bottom_blob_bordered.w;
        h = bottom_blob_bordered.h;
    }

    int outw = (w - kernel_size) / stride + 1;
    int outh = (h - kernel_size) / stride + 1;

    // If we the output feature map is already too squeezed, don't reuse
    if (outw <= 5 || outh <= 5) {
        return Convolution_arm::forward(bottom_blob, top_blob);
    }


    std::vector<struct rect>::iterator itr = mrect.changed_vecs.begin();
    while (itr != mrect.changed_vecs.end()) {
        if (itr->x1 <= 0 && itr->y1 <= 0 && itr->x2 >= (outw - 1) && itr->y2 >= (outh - 1)) // No room for reusing now!
            return Convolution_arm::forward(bottom_blob, top_blob);
        ++ itr;
    }

    // LOGI("Convolution_arm::forward_cached input=%dx%dx%d pad=%d ksize=%d output=%d stride=%d\n",
    //     bottom_blob.w, bottom_blob.h, channels, pad, kernel_size, num_output, stride);
    
    top_blob.create(outw, outh, num_output);
    if (top_blob.empty())
        return -100;

    if (mrect.size() == 0) {
        memcpy(top_blob.data, cached_blob.data, cached_blob.total() * sizeof(float));
        log_time_end("conv_arm_cached");
        return 0;
    }


    // struct timeval t1, t2;
    // gettimeofday(&t1, NULL);

    // Construct cached map
    bool* cached_map = (bool*) calloc(outh * outw, sizeof(bool));
    int mrect_size = mrect.size();
    #pragma omp parallel for
    for (int i = 0; i < mrect_size; i ++) {
        struct rect r = mrect.changed_vecs[i];
        for (int h = r.y1; h <= std::min(r.y2, outh - 1); h ++)
            for (int w = r.x1; w <= std::min(r.x2, outw - 1); w ++)
                cached_map[h * outw + w] = true;
    }

    if (skip_reuse(cached_map, outh, outw)) {
        free(cached_map);
        return Convolution_arm::forward(bottom_blob, top_blob);
    }

    // Reuse cache
    // TODO: move it to neon to save time
    const int cpy_size = (outw - abs(mrect.x_offset)) * sizeof(float);
    const int sh = (mrect.y_offset >= 0 ? 0 : -mrect.y_offset);
    const int eh = (mrect.y_offset >= 0 ? (outh - mrect.y_offset) : outh);
    const int sw = (mrect.x_offset >= 0 ? 0 : -mrect.x_offset);
    #pragma omp parallel for
    for (int i = 0; i < num_output; i ++) {
        for (int h = sh; h < eh; h ++) {
            float* dst = top_blob.channel(i).row(h) + sw;
            float* src = cached_blob.channel(i).row(h + mrect.y_offset) + sw + mrect.x_offset;
            memcpy(dst, src, cpy_size);
        }

        const float* bias = bias_data;
        const float bias0 = bias_data ? bias_data[i] : 0.f;
        bool* flag = cached_map;
        float* data = (float*)top_blob.channel(i);
        int cnt = outw * outh;
        while (cnt --) {
            if (*flag) {
                *data = bias0;
            }
            flag ++;
            data ++;
        }
    }

    conv(bottom_blob_bordered, top_blob, weight_data, bias_data, cached_map);

    log_time_end("conv_arm_cached");

    free(cached_map);

    return 0;
}
#endif // NCNN_CNNCACHE

} // namespace ncnn
