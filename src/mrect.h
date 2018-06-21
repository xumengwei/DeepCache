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

struct timeval tv_begin, tv_end;
inline void log_time_begin() {
    gettimeofday(&tv_begin, NULL);
}
inline void log_time_end(char* str) {
    static int LOG_TIME_CNT = 0;
    if (strcmp("__reset", str) == 0) { // This is ugly, better to use extern
        LOG_TIME_CNT = 0;
        return;
    }
    gettimeofday(&tv_end, NULL);
    int elapsed = ((tv_end.tv_sec - tv_begin.tv_sec) * 1000000.0f + tv_end.tv_usec - tv_begin.tv_usec) / 1000.0f;
    LOGI("[%d-%s]\telapsed: %dms", LOG_TIME_CNT, str, elapsed);
    LOG_TIME_CNT ++;
}

inline bool skip_reuse(bool* cached_map, int outw, int outh) {
    int changed_pixel = 0;
    for (int i = 0; i < outw * outh; i ++) {
        changed_pixel += cached_map[i] ? 1 : 0;
    }
    float ratio = 1.0 * changed_pixel / outw / outh;
    if (ratio > 0.8) return true;
    else return false;
}

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

    MRect() {}

    void set_offset(int x, int y) {
        x_offset = x;
        y_offset = y;
    }

    void add_rect(int arg0, int arg1, int arg2, int arg3) {
        changed_vecs.push_back(rect(arg0, arg1, arg2, arg3));
    }

    void copyFrom(MRect other) {
        x_offset = other.x_offset;
        y_offset = other.y_offset;
        changed_vecs.resize(0);
    	for (struct rect r: other.changed_vecs) {
            this->changed_vecs.push_back(r);
        }
    }

    std::string info() {
        std::string ret("");
        char a[64];
        for (unsigned i = 0; i < changed_vecs.size(); i ++) {
            if (i > 0)
                ret += ", ";
            struct rect r1 = changed_vecs[i];
            sprintf(a, "(%d,%d,%d,%d)", r1.x1, r1.y1, r1.x2, r1.y2);
            ret += a;
        }
        return ret;
    }

    int size() {
        return changed_vecs.size();
    }

    void forward_rect_conv_or_pool(
        struct rect& r1, struct rect& r2, int pad, int ksize, int stride) {
        
        // TODO: not correct yet
        // int off = stride > 1 ? 1 : 0;
        if (pad >= 0) { // SAME
            r1.x1 = std::max(0, (r2.x1 - ksize / 2) / stride);
            r1.y1 = std::max(0, (r2.y1 - ksize / 2) / stride);
            r1.x2 = (r2.x2 + ksize / 2) / stride;
            r1.y2 = (r2.y2 + ksize / 2) / stride;
        }
        else if (pad == -233) { // VALID
            r1.x1 = std::max(0, (r2.x1 - ksize / 2) / stride);
            r1.y1 = std::max(0, (r2.y1 - ksize / 2) / stride);
            r1.x2 = (r2.x2 + ksize / 2) / stride;
            r1.y2 = (r2.y2 + ksize / 2) / stride;
        }
        // LOGI("Yes (%d,%d,%d,%d) -> (%d,%d,%d,%d)\n",
        //     r2.x1, r2.y1, r2.x2, r2.y2, r1.x1, r1.y1, r1.x2, r1.y2);
    }

    int forward_in_conv_or_pool(MRect& bottom_mrect, int pad, int ksize, int stride) {
        // offset
        x_offset = bottom_mrect.x_offset / stride;
        y_offset = bottom_mrect.y_offset / stride;

        size_t size = bottom_mrect.size();
        changed_vecs.resize(size);
        for (size_t i = 0; i < size; i ++) {
            forward_rect_conv_or_pool(
                changed_vecs[i], bottom_mrect.changed_vecs[i], pad, ksize, stride);
        }
        return 0;
    }

    int x_offset;
    int y_offset;
    std::vector<struct rect> changed_vecs;
};

} // namespace ncnn

#endif // NCNN_CNNCACHE

#endif // NCNN_MRECT_H