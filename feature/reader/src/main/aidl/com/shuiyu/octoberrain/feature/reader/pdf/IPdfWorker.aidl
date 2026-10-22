package com.shuiyu.octoberrain.feature.reader.pdf;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import com.shuiyu.octoberrain.feature.reader.pdf.IPdfResult;
/** 仅传只读来源FD、固定视口像素FD和小型元数据；不传正文/像素数组。 */
oneway interface IPdfWorker {
    void open(in ParcelFileDescriptor source, IPdfResult result);
    void render(int page, int width, int height, float zoom, float centerX, float centerY, boolean crop,
                in SharedMemory pixels, IPdfResult result);
    void closeDocument();
}
