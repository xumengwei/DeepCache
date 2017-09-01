#ifndef NCNN_MRECT_H
#define NCNN_MRECT_H

#if NCNN_CNNCACHE

namespace ncnn {

#if NCNN_CNNCACHE
#include <android/log.h>
#endif

#define  LOG_TAG    "NCNN_CNNCache"
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

struct rect{
    int x1;
    int y1;
    int x2;
    int y2;
    rect(int arg0, int arg1,int arg2, int arg3) {
        x1 = arg0;
        y1 = arg1;
        x2 = arg2;
        y2 = arg3;
    }
    rect() {}
};

class MRect
{

public:

    MRect(){}

    void add_rect(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7) {
        pre.push_back(rect(arg0, arg1, arg2, arg3));

        cur.push_back(rect(arg4, arg5, arg6, arg7));  
    }

    void copyFrom(MRect other) {
        pre.resize(0);
        cur.resize(0);
    	for (struct rect r: other.pre) {
            this->pre.push_back(r);
        }
        for (struct rect r: other.cur) {
            this->cur.push_back(r);
        }
    }

    std::string info() {
        std::string ret;
        char a[64];
        for (unsigned i = 0; i < pre.size(); i ++) {
            if (i > 0)
                ret += ", ";
            struct rect r1 = pre[i];
            struct rect r2 = cur[i];
            sprintf(a, "(%d,%d,%d,%d) -> (%d,%d,%d,%d)",
                r1.x1, r1.y1, r1.x2, r1.y2, r2.x1, r2.y1, r2.x2, r2.y2);
            ret += a;
        }
        return ret;
    }

    int size() {
        return pre.size();
    }

    void forward_rect_conv_or_pool(
        struct rect& r1, struct rect& r2, int pad, int ksize, int stride) {
        
        // TODO: not correct yet
        int off = stride > 1 ? 1 : 0;
        if (pad >= 0) { // SAME
            r1.x1 = (r2.x1 + pad + ksize / 2) / stride;
            r1.y1 = (r2.y1 + pad + ksize / 2) / stride;
            r1.x2 = (r2.x2 + pad - ksize / 2) / stride;
            r1.y2 = (r2.y2 + pad - ksize / 2) / stride;
        }
        else if (pad == -233) { // VALID
            r1.x1 = (r2.x1 + ksize / 2) / stride;
            r1.y1 = (r2.y1 + ksize / 2) / stride;
            r1.x2 = (r2.x2 - ksize / 2) / stride;
            r1.y2 = (r2.y2 - ksize / 2) / stride;
        }

    }

    int forward_in_conv_or_pool(MRect& bottom_mrect, int pad, int ksize, int stride) {
        const int threshold = 1;
        size_t size = bottom_mrect.size();
        pre.resize(0);
        cur.resize(0);
        for (size_t i = 0; i < size; i ++) {
            rect pre_r, cur_r;
            forward_rect_conv_or_pool(
                pre_r, bottom_mrect.pre[i], pad, ksize, stride);
            forward_rect_conv_or_pool(
                cur_r, bottom_mrect.cur[i], pad, ksize, stride);
            if ((pre_r.x2 - pre_r.x1 >= threshold) && (pre_r.y2 - pre_r.y1 >= threshold)) {
                pre.push_back(pre_r);
                cur.push_back(cur_r);
            }
        }
        return 0;
    }

    std::vector<struct rect> pre;
    std::vector<struct rect> cur;
};

} // namespace ncnn

#endif // NCNN_CNNCACHE

#endif // NCNN_MRECT_H