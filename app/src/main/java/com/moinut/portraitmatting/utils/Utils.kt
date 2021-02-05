package com.moinut.portraitmatting.utils

import android.graphics.*
import android.media.Image
import android.net.Uri
import com.moinut.portraitmatting.config.Param
import java.io.ByteArrayOutputStream
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
        if (picWidth > Param.IMAGE_MAX_SIZE)
            opt.inSampleSize = picWidth / Param.IMAGE_MAX_SIZE
    } else {
        if (picHeight > Param.IMAGE_MAX_SIZE)
            opt.inSampleSize = picHeight / Param.IMAGE_MAX_SIZE
    }

    opt.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(image_path, opt)
        ?: throw FileNotFoundException("Couldn't open $image_path")
}

fun Image.toBitmap(): Bitmap? {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun Image.toYUVBitmap(): Bitmap {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun Bitmap.rotate(alpha: Float): Bitmap? {
    val matrix = Matrix()
    matrix.setRotate(alpha)

    val newBM = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)

    if (!this.isRecycled && this != newBM) {  // if new is the same as old, return it self, can't be recycled.
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
    if (!this.isRecycled && this != newBM) {  // if new is the same as old, return it self, can't be recycled.
        this.recycle()
    }
    return newBM
}