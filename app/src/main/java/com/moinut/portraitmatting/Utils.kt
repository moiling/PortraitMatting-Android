package com.moinut.portraitmatting

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.net.Uri
import java.io.FileNotFoundException


fun loadBitmap(uri: Uri): Bitmap {
    return loadBitmap(uri.path!!)
}

fun loadBitmap(image_path: String): Bitmap {
    val opt = BitmapFactory.Options()
    opt.inJustDecodeBounds = true
    BitmapFactory.decodeFile(image_path, opt)

    val picWidth = opt.outWidth
    val picHeight = opt.outHeight

    opt.inSampleSize = 1

    if (picWidth > picHeight) {
        if (picWidth > Const.IMAGE_MAX_SIZE)
            opt.inSampleSize = picWidth / Const.IMAGE_MAX_SIZE
    } else {
        if (picHeight > Const.IMAGE_MAX_SIZE)
            opt.inSampleSize = picHeight / Const.IMAGE_MAX_SIZE
    }

    opt.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(image_path, opt)
        ?: throw FileNotFoundException("Couldn't open $image_path")
}

fun Image.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun Bitmap.rotate(alpha: Float): Bitmap? {
    val matrix = Matrix()
    matrix.setRotate(alpha)

    val newBM = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)

    if (!this.isRecycled) {
        this.recycle()
    }
    return newBM
}

fun Bitmap.resizeIfShortBigThan(size: Int): Bitmap? {
    var ratio = 1f

    if (width < height) {
        if (width > size) {
            ratio = size.toFloat() / width.toFloat()
        }
    } else {
        if (height > size) {
            ratio = size.toFloat() / height.toFloat()
        }
    }

    val matrix = Matrix()
    matrix.postScale(ratio, ratio)

    val newBM = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (!this.isRecycled && this != newBM) {  // if new is the same as old, return it self, can't be recycled.
        this.recycle()
    }
    return newBM
}

fun Bitmap.flipHorizontal(): Bitmap? {

    val matrix = Matrix()
    matrix.postScale(-1f, 1f)

    val newBM = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (!this.isRecycled) {
        this.recycle()
    }
    return newBM
}