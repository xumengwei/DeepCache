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

#include "convolution.h"

namespace ncnn {

DEFINE_LAYER_CREATOR(Convolution)

Convolution::Convolution()
{
    one_blob_only = true;
    support_inplace = false;
}

Convolution::~Convolution()
{
}

#if NCNN_STDIO
#if NCNN_STRING
int Convolution::load_param(FILE* paramfp)
{
    int nscan = fscanf(paramfp, "%d %d %d %d %d %d %d",
                       &num_output, &kernel_size, &dilation, &stride, &pad, &bias_term,
                       &weight_data_size);
    if (nscan != 7)
    {
        fprintf(stderr, "Convolution load_param failed %d\n", nscan);
        return -1;
    }

    return 0;
}
#endif // NCNN_STRING
int Convolution::load_param_bin(FILE* paramfp)
{
    fread(&num_output, sizeof(int), 1, paramfp);

    fread(&kernel_size, sizeof(int), 1, paramfp);

    fread(&dilation, sizeof(int), 1, paramfp);

    fread(&stride, sizeof(int), 1, paramfp);

    fread(&pad, sizeof(int), 1, paramfp);

    fread(&bias_term, sizeof(int), 1, paramfp);

    fread(&weight_data_size, sizeof(int), 1, paramfp);

    return 0;
}

int Convolution::load_model(FILE* binfp)
{
    int nread;

    union
    {
        struct
        {
            unsigned char f0;
            unsigned char f1;
            unsigned char f2;
            unsigned char f3;
        };
        unsigned int tag;
    } flag_struct;

    nread = fread(&flag_struct, sizeof(flag_struct), 1, binfp);
    if (nread != 1)
    {
        fprintf(stderr, "Convolution read flag_struct failed %d\n", nread);
        return -1;
    }

    unsigned int flag = flag_struct.f0 + flag_struct.f1 + flag_struct.f2 + flag_struct.f3;

    weight_data.create(weight_data_size);
    if (weight_data.empty())
        return -100;

    if (flag_struct.tag == 0x01306B47)
    {
        // half-precision weight data
        int align_weight_data_size = alignSize(weight_data_size * sizeof(unsigned short), 4);
        std::vector<unsigned short> float16_weights;
        float16_weights.resize(align_weight_data_size);
        nread = fread(float16_weights.data(), align_weight_data_size, 1, binfp);
        if (nread != 1)
        {
            fprintf(stderr, "Convolution read float16_weights failed %d\n", nread);
            return -1;
        }

        weight_data = Mat::from_float16(float16_weights.data(), weight_data_size);
        if (weight_data.empty())
            return -100;
    }
    else if (flag != 0)
    {
        // quantized weight data
        float quantization_value[256];
        nread = fread(quantization_value, 256 * sizeof(float), 1, binfp);
        if (nread != 1)
        {
            fprintf(stderr, "Convolution read quantization_value failed %d\n", nread);
            return -1;
        }

        int align_weight_data_size = alignSize(weight_data_size * sizeof(unsigned char), 4);
        std::vector<unsigned char> index_array;
        index_array.resize(align_weight_data_size);
        nread = fread(index_array.data(), align_weight_data_size, 1, binfp);
        if (nread != 1)
        {
            fprintf(stderr, "Convolution read index_array failed %d\n", nread);
            return -1;
        }

        float* weight_data_ptr = weight_data;
        for (int i = 0; i < weight_data_size; i++)
        {
            weight_data_ptr[i] = quantization_value[ index_array[i] ];
        }
    }
    else if (flag_struct.f0 == 0)
    {
        // raw weight data
        nread = fread(weight_data, weight_data_size * sizeof(float), 1, binfp);
        if (nread != 1)
        {
            fprintf(stderr, "Convolution read weight_data failed %d\n", nread);
            return -1;
        }
    }

    if (bias_term)
    {
        bias_data.create(num_output);
        if (bias_data.empty())
            return -100;
        nread = fread(bias_data, num_output * sizeof(float), 1, binfp);
        if (nread != 1)
        {
            fprintf(stderr, "Convolution read bias_data failed %d\n", nread);
            return -1;
        }
    }

    return 0;
}
#endif // NCNN_STDIO

int Convolution::load_param(const unsigned char*& mem)
{
    num_output = *(int*)(mem);
    mem += 4;

    kernel_size = *(int*)(mem);
    mem += 4;

    dilation = *(int*)(mem);
    mem += 4;

    stride = *(int*)(mem);
    mem += 4;

    pad = *(int*)(mem);
    mem += 4;

    bias_term = *(int*)(mem);
    mem += 4;

    weight_data_size = *(int*)(mem);
    mem += 4;

    return 0;
}

int Convolution::load_model(const unsigned char*& mem)
{
    union
    {
        struct
        {
            unsigned char f0;
            unsigned char f1;
            unsigned char f2;
            unsigned char f3;
        };
        unsigned int tag;
    } flag_struct;

    memcpy(&flag_struct, mem, sizeof(flag_struct));
    mem += sizeof(flag_struct);

    unsigned int flag = flag_struct.f0 + flag_struct.f1 + flag_struct.f2 + flag_struct.f3;

    if (flag_struct.tag == 0x01306B47)
    {
        // half-precision weight data
        weight_data = Mat::from_float16((unsigned short*)mem, weight_data_size);
        mem += alignSize(weight_data_size * sizeof(unsigned short), 4);
        if (weight_data.empty())
            return -100;
    }
    else if (flag != 0)
    {
        // quantized weight data
        const float* quantization_value = (const float*)mem;
        mem += 256 * sizeof(float);

        const unsigned char* index_array = (const unsigned char*)mem;
        mem += alignSize(weight_data_size * sizeof(unsigned char), 4);

        weight_data.create(weight_data_size);
        if (weight_data.empty())
            return -100;
        float* weight_data_ptr = weight_data;
        for (int i = 0; i < weight_data_size; i++)
        {
            weight_data_ptr[i] = quantization_value[ index_array[i] ];
        }
    }
    else if (flag_struct.f0 == 0)
    {
        // raw weight data
        weight_data = Mat(weight_data_size, (float*)mem);
        mem += weight_data_size * sizeof(float);
    }

    if (bias_term)
    {
        bias_data = Mat(num_output, (float*)mem);
        mem += num_output * sizeof(float);
    }

    return 0;
}

int Convolution::forward(const Mat& bottom_blob, Mat& top_blob) const
{
    struct timeval t1, t2;
    gettimeofday(&t1, NULL);

    // convolv with NxN kernel
    // value = value + bias

    int w = bottom_blob.w;
    int h = bottom_blob.h;
    int channels = bottom_blob.c;

//     fprintf(stderr, "Convolution input %d x %d  pad = %d  ksize=%d  stride=%d\n", w, h, pad, kernel_size, stride);

    const int kernel_extent = dilation * (kernel_size - 1) + 1;

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
        int wpad = kernel_extent + (w - 1) / stride * stride - w;
        int hpad = kernel_extent + (h - 1) / stride * stride - h;
        if (wpad > 0 || hpad > 0)
        {
            copy_make_border(bottom_blob, bottom_blob_bordered, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, BORDER_CONSTANT, 0.f);
            if (bottom_blob_bordered.empty())
                return -100;
        }

        w = bottom_blob_bordered.w;
        h = bottom_blob_bordered.h;
    }

    int outw = (w - kernel_extent) / stride + 1;
    int outh = (h - kernel_extent) / stride + 1;

#if NCNN_CNNCACHE    
    LOGI("Convolution::forward input=%dx%dx%d output=%dx%dx%d pad=%d ksize=%d stride=%d dilation=%d\n",
        w, h, channels, outw, outh, num_output, pad, kernel_size, stride, dilation);
#endif

    top_blob.create(outw, outh, num_output);
    if (top_blob.empty())
        return -100;

    const int maxk = kernel_size * kernel_size;

    // kernel offsets
    std::vector<int> _space_ofs(maxk);
    int* space_ofs = &_space_ofs[0];
    {
        int p1 = 0;
        int p2 = 0;
        int gap = w * dilation - kernel_size * dilation;
        for (int i = 0; i < kernel_size; i++)
        {
            for (int j = 0; j < kernel_size; j++)
            {
                space_ofs[p1] = p2;
                p1++;
                p2 += dilation;
            }
            p2 += gap;
        }
    }

    // num_output
    const float* weight_data_ptr = weight_data;
    #pragma omp parallel for
    for (int p=0; p<num_output; p++)
    {
        float* outptr = top_blob.channel(p);

        for (int i = 0; i < outh; i++)
        {
            for (int j = 0; j < outw; j++)
            {
                float sum = 0.f;

                if (bias_term)
                    sum = bias_data.data[p];

                const float* kptr = weight_data_ptr + maxk * channels * p;

                // channels
                for (int q=0; q<channels; q++)
                {
                    const Mat m = bottom_blob_bordered.channel(q);
                    const float* sptr = m.data + m.w * i*stride + j*stride;

                    for (int k = 0; k < maxk; k++) // 29.23
                    {
                        float val = sptr[ space_ofs[k] ]; // 20.72
                        float w = kptr[k];
                        sum += val * w; // 41.45
                    }

                    kptr += maxk;
                }

                outptr[j] = sum;
            }

            outptr += outw;
        }
    }

    gettimeofday(&t2, NULL);

    float elapsed_1 = ((t2.tv_sec - t1.tv_sec) * 1000000.0f + t2.tv_usec - t1.tv_usec) / 1000.0f;
    LOGI("Convolution::forward elapsed: %f\n", elapsed_1);

    return 0;
}

#if NCNN_CNNCACHE
int Convolution::forward_mrect(MRect& bottom_mrect, MRect& top_mrect) const
{
    // LOGI("Convolution::forward_mrect info: %s\n", bottom_mrect.info().c_str());
    top_mrect.forward_in_conv_or_pool(bottom_mrect, pad, kernel_size, stride);
    return 0;
}

int Convolution::forward_cached(const Mat& bottom_blob, Mat& top_blob, MRect& mrect, Mat& cached_blob) const
{
    // convolv with NxN kernel
    // value = value + bias

    if (cached_blob.empty() || mrect.changed_vecs.size() == 0) {
        // LOGI("cached_blob size: %dx%dx%d %d %p\n",
        //     cached_blob.w, cached_blob.h, cached_blob.c, cached_blob.cstep, cached_blob.data);
        return Convolution::forward(bottom_blob, top_blob);
    }

    struct timeval t1, t2;
    gettimeofday(&t1, NULL);

    // Step 1: copy the cahced blocks
    // Step 2: make a bitmap specifying which can be reused
    // Step 3: re-calculate other blocks

    int w = bottom_blob.w;
    int h = bottom_blob.h;
    int channels = bottom_blob.c;

    LOGI("Convolution::forward_cached input %dx%dx%d  pad =%d  ksize=%d output:%d stride=%d\n",
        w, h, channels, pad, kernel_size, num_output, stride);
    LOGI("mrect info: %s\n", mrect.info().c_str());

    const int kernel_extent = dilation * (kernel_size - 1) + 1;

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
        int wpad = kernel_extent + (w - 1) / stride * stride - w;
        int hpad = kernel_extent + (h - 1) / stride * stride - h;
        if (wpad > 0 || hpad > 0)
        {
            copy_make_border(bottom_blob, bottom_blob_bordered, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, BORDER_CONSTANT, 0.f);
            if (bottom_blob_bordered.empty())
                return -100;
        }

        w = bottom_blob_bordered.w;
        h = bottom_blob_bordered.h;
    }

    int outw = (w - kernel_extent) / stride + 1;
    int outh = (h - kernel_extent) / stride + 1;

    top_blob.create(outw, outh, num_output);
    if (top_blob.empty())
        return -100;

    const int maxk = kernel_size * kernel_size;

    // kernel offsets
    std::vector<int> _space_ofs(maxk);
    int* space_ofs = &_space_ofs[0];
    {
        int p1 = 0;
        int p2 = 0;
        int gap = w * dilation - kernel_size * dilation;
        for (int i = 0; i < kernel_size; i++)
        {
            for (int j = 0; j < kernel_size; j++)
            {
                space_ofs[p1] = p2;
                p1++;
                p2 += dilation;
            }
            p2 += gap;
        }
    }

    // Step 1 & Step 2
    // TODO: make it run on multiple threads
    int* cached_map = (int*) calloc(outh * outw, sizeof(int));

    std::vector<int> xx;
    std::vector<int> yy;
    for (int h = 0; h < outh; h ++) {
        xx.resize(0);
        yy.resize(0);
        for (size_t i = 0, max = mrect.size(); i < max; i ++) {
            struct rect r = mrect.changed_vecs[i];
            if (r.y1 <= h && r.y2 >= h) {
                xx.push_back(r.x1);
                yy.push_back(r.x2);
            }
        }
        // bubble sort
        for (int p = 0, max = xx.size(); p < max - 1; p ++)
            for (int q = max - 1; q > p; q --) {
                if (xx[q] < xx[q - 1]) {
                    int temp = xx[q];
                    xx[q] = xx[q - 1];
                    xx[q - 1] = temp;

                    temp = yy[q];
                    yy[q] = yy[q - 1];
                    yy[q - 1] = temp;
                }
            }
        xx.push_back(outw);
        yy.push_back(outw);
        int start = 0;
        for (size_t i = 0, max = xx.size(); i < max; i ++) {
            if (xx[i] > start) {
                // LOGI("Reuse h: %d, [%d, %d]\n", h, start, xx[i]);
                // reuse [start, xx[i])
                cached_map[h * outw + start] = xx[i] - start;
                start = yy[i] + 1;
            }
            if (xx[i] <= start) {
                start = std::max(start, yy[i]);
            }
        }

    }



    // for (size_t i = 0, max = mrect.size(); i < max; i ++) {
    //     struct rect pre = mrect.pre[i];
    //     struct rect cur = mrect.cur[i];
    //     // #pragma omp parallel for
    //     for (int j = 0; j < num_output; j ++) {
    //         float* pre_ptr = cached_blob.channel(j).row(pre.y1) + pre.x1;
    //         float* cur_ptr = top_blob.channel(j).row(cur.y1) + cur.x1;
    //         int offset = (pre.x2 - pre.x1 + 1) * sizeof(float);
    //         for (int k = cur.y1; k <= cur.y2; k ++) {
    //             // Step 2: build cache map
    //             cached_map[k * outw + cur.x1] = pre.x2 - pre.x1 + 1;
    //             // Step 1: copy cached block
    //             memcpy(cur_ptr, pre_ptr, offset);
    //             pre_ptr += top_blob.w;
    //             cur_ptr += top_blob.w;
    //         }
    //     }
    // }

    // num_output
    const float* weight_data_ptr = weight_data;
    #pragma omp parallel for
    for (int p=0; p<num_output; p++)
    {
        float* outptr = top_blob.channel(p);

        for (int i = 0; i < outh; i++)
        {
            for (int j = 0; j < outw; j++)
            {
                // Reuse the block
                int temp = cached_map[i * outw + j];
                if (temp > 0) {
                    float* pre_ptr = cached_blob.channel(p).row(i + mrect.y_offset) + (j + mrect.x_offset);
                    memcpy(&outptr[j], pre_ptr, temp * sizeof(float));
                    j += (temp - 1);
                    continue;
                }

                float sum = 0.f;

                if (bias_term)
                    sum = bias_data.data[p];

                const float* kptr = weight_data_ptr + maxk * channels * p;

                // channels
                for (int q=0; q<channels; q++)
                {
                    const Mat m = bottom_blob_bordered.channel(q);
                    const float* sptr = m.data + m.w * i*stride + j*stride;

                    for (int k = 0; k < maxk; k++) // 29.23
                    {
                        float val = sptr[ space_ofs[k] ]; // 20.72
                        float w = kptr[k];
                        sum += val * w; // 41.45
                    }

                    kptr += maxk;
                }

                outptr[j] = sum;
            }

            outptr += outw;
        }
    }

    free(cached_map);

    gettimeofday(&t2, NULL);

    float elapsed_1 = ((t2.tv_sec - t1.tv_sec) * 1000000.0f + t2.tv_usec - t1.tv_usec) / 1000.0f;
    LOGI("Convolution::forward_cached elapsed: %f\n", elapsed_1);

    return 0;
}

#endif

} // namespace ncnn
