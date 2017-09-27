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

#include "concat.h"

namespace ncnn {

DEFINE_LAYER_CREATOR(Concat)

Concat::Concat()
{
}

int Concat::forward(const std::vector<Mat>& bottom_blobs, std::vector<Mat>& top_blobs) const
{
    int w = bottom_blobs[0].w;
    int h = bottom_blobs[0].h;

    // total channels
    int top_channels = 0;
    for (size_t b=0; b<bottom_blobs.size(); b++)
    {
        const Mat& bottom_blob = bottom_blobs[b];
        top_channels += bottom_blob.c;
    }

    Mat& top_blob = top_blobs[0];
    top_blob.create(w, h, top_channels);
    if (top_blob.empty())
        return -100;

    int q = 0;
    for (size_t b=0; b<bottom_blobs.size(); b++)
    {
        const Mat& bottom_blob = bottom_blobs[b];

        int channels = bottom_blob.c;
        int size = bottom_blob.cstep * channels;

        const float* ptr = bottom_blob;
        float* outptr = top_blob.channel(q);
        for (int i=0; i<size; i++)
        {
            outptr[i] = ptr[i];
        }

        q += channels;
    }

    return 0;
}

#if NCNN_CNNCACHE
int Concat::forward_mrect(std::vector<MRect>& bottom_mrects, std::vector<MRect>& top_mrects) const
{
    top_mrects.resize(1);
    MRect& mr = top_mrects[0];
    mr.set_offset(bottom_mrects[0].x_offset, bottom_mrects[0].y_offset);
    for (size_t i = 0, max = bottom_mrects[0].size(); i < max; i ++) {
        int x1 = bottom_mrects[0].changed_vecs[i].x1;
        int y1 = bottom_mrects[0].changed_vecs[i].y1;
        int x2 = bottom_mrects[0].changed_vecs[i].x2;
        int y2 = bottom_mrects[0].changed_vecs[i].y2;
        for (size_t j = 1, maxx = bottom_mrects.size(); j < maxx; j ++) {
            const struct rect temp = bottom_mrects[j].changed_vecs[i];
            if (temp.x1 <= x1 && temp.y1 <= y1) {
                x1 = temp.x1;
                y1 = temp.y1;
            }
            if (temp.x2 >= x2 && temp.y2 >= y2) {
                x2 = temp.x2;
                y2 = temp.y2;
            }
        }
        mr.add_rect(x1, y1, x2, y2);
        // LOGI("YYYYY %d %d %d %d\n", x1, y1, x2, y2);
    }
    return 0;
}
#endif

} // namespace ncnn
