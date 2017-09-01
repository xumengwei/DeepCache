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

#ifndef LAYER_CONVOLUTIONDEPTHWISE_H
#define LAYER_CONVOLUTIONDEPTHWISE_H

#include "layer.h"
#include "convolution.h"

namespace ncnn {

class ConvolutionDepthWise : public Convolution
{
public:
    ConvolutionDepthWise();
    virtual ~ConvolutionDepthWise();

#if NCNN_STDIO
#if NCNN_STRING
    virtual int load_param(FILE* paramfp);
#endif // NCNN_STRING
    virtual int load_param_bin(FILE* paramfp);
#endif // NCNN_STDIO
    virtual int load_param(const unsigned char*& mem);

    virtual int forward(const Mat& bottom_blobs, Mat& top_blobs) const;

#if NCNN_CNNCACHE
    virtual int forward_mrect(MRect& bottom_mrect, MRect& top_mrect) const;
    virtual int forward_cached(const Mat& bottom_blob, Mat& top_blob, MRect& mrect, Mat& cached_blob) const;
    virtual bool needs_cache() const {return true;}
#endif
    
public:
    int group;
};

} // namespace ncnn

#endif // LAYER_CONVOLUTIONDEPTHWISE_H
