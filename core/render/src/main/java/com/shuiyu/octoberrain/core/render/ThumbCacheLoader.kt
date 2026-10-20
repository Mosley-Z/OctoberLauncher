package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import java.io.File

/**
 * 缓存未命中时的解码写入辅助。
 *
 * 调用方只关心「取一张可直接绘制的缩略图」：命中缓存直接返回，未命中时执行 decode
 * 并写入缓存，返回的位图由缓存持有。把这段流程集中在一处，接入点无需重复判断，
 * 也便于统一记录未命中次数。
 */
fun ThumbCache.loadOrDecode(
    path: File,
    decode: () -> Bitmap?,
): Bitmap? = get(path) ?: decode()?.let { bitmap -> put(path, bitmap) }
