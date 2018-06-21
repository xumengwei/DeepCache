Implementation for our paper **DeepCache: Principled Cache for Mobile Deep Vision**.

---

This implementation is based on [Tencent ncnn](https://github.com/Tencent/ncnn).

The folder *CNNCache* is an Android demo of DeepCache, including the image matching algorithm in RenderScript, a real-time app, and the benchmark. Some configuration in CMake file needs to be updated according to your enviornment.


### Other notes
* The version of ncnn used currently is rather old. We plan to update it to newer one.
* Current implemention doesn't support neon in ncnn. Let's make it later.