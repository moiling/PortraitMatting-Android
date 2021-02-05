package com.moinut.portraitmatting.ctr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.core.graphics.*
import com.moinut.portraitmatting.config.Param
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MattingController private constructor() {
    var mBitmap: Bitmap? = null
    var mCutoutBitmap: Bitmap? = null
    var mModule: Module? = null
    var mAlphaModule: Module? = null
    var mModuleLoaded: Boolean = false
    var mAlphaModuleLoaded: Boolean = false

    companion object {

        val INSTANCE: MattingController by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            MattingController()
        }
    }

    fun composite(bitmap: Bitmap, color: Int): Bitmap {
        val compBitmap = Bitmap.createBitmap(bitmap)
        // composite
        for (y in 0 until compBitmap.height) {
            for (x in 0 until compBitmap.width) {
                val alpha = compBitmap[x, y].alpha / 255f

                val r = compBitmap[x, y].red / 255f
                val g = compBitmap[x, y].green / 255f
                val b = compBitmap[x, y].blue / 255f

                val bgR = color.red / 255f
                val bgG = color.green / 255f
                val bgB = color.blue / 255f

                if (alpha > .99f) {
                    compBitmap[x, y] = Color.argb(1f, r, g, b)
                    continue
                }

                if (alpha < .01f) {
                    compBitmap[x, y] = Color.argb(1f, bgR, bgG, bgB)
                    continue
                }

                val newR = alpha * r + (1 - alpha) * bgR
                val newG = alpha * g + (1 - alpha) * bgG
                val newB = alpha * b + (1 - alpha) * bgB

                compBitmap[x, y] = Color.argb(1f, newR, newG, newB)
            }
        }
        return compBitmap
    }

    fun matting(): Bitmap {
        val mean = floatArrayOf(0f, 0f, 0f)
        val std = floatArrayOf(1f, 1f, 1f)

        val originWidth = mBitmap!!.width
        val originHeight = mBitmap!!.height
        val scaledBitmap = Bitmap.createScaledBitmap(
            mBitmap!!,
            Param.NETWORK_IMAGE_SIZE,
            Param.NETWORK_IMAGE_SIZE,
            true
        )
        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(scaledBitmap, mean, std)

        val outTensor: IValue = mModule!!.forward(IValue.from(inputTensor))

        val scaledCutoutBitmap: Bitmap = Bitmap.createBitmap(
            Param.NETWORK_IMAGE_SIZE,
            Param.NETWORK_IMAGE_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val cutoutFloat: FloatArray = outTensor.toTuple()[2].toTensor().dataAsFloatArray
        val cutoutInt = IntArray(cutoutFloat.size / 4)

        // network size for.
        for (i in cutoutInt.indices) {
            val alpha = cutoutFloat[i + 3 * cutoutInt.size]
            val r = cutoutFloat[i]
            val g = cutoutFloat[i + cutoutInt.size]
            val b = cutoutFloat[i + 2 * cutoutInt.size]
            cutoutInt[i] = Color.argb(alpha, r, g, b)
        }
        scaledCutoutBitmap.setPixels(
            cutoutInt,
            0,
            Param.NETWORK_IMAGE_SIZE,
            0,
            0,
            Param.NETWORK_IMAGE_SIZE,
            Param.NETWORK_IMAGE_SIZE
        )
        val cutoutBitmap = scaledCutoutBitmap.scale(originWidth, originHeight, true)

        // max size for.
        for (i in 0 until cutoutBitmap.width * cutoutBitmap.height) {
            val x = i.rem(cutoutBitmap.width)
            val y = i / cutoutBitmap.width
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val alpha = cutoutBitmap.getColor(x, y).alpha()

                // if alpha > 80%, use original color.
                if (alpha > .8f) {
                    val r = mBitmap!!.getColor(x, y).red()
                    val g = mBitmap!!.getColor(x, y).green()
                    val b = mBitmap!!.getColor(x, y).blue()
                    cutoutBitmap[x, y] = Color.argb(alpha, r, g, b)
                }
            } else {
                val cutoutBitmapColor = Color.valueOf(cutoutBitmap.getPixel(x, y))
                val alpha = cutoutBitmapColor.alpha()

                // if alpha > 80%, use original color.
                if (alpha > .8f) {
                    val mBitmapColor = Color.valueOf(mBitmap!!.getPixel(x, y))
                    val r = mBitmapColor.red()
                    val g = mBitmapColor.green()
                    val b = mBitmapColor.blue()
                    cutoutBitmap[x, y] = Color.argb(alpha, r, g, b)
                }
            }
        }
        mCutoutBitmap = cutoutBitmap

        return cutoutBitmap
    }

    fun alphaMatting(bitmap: Bitmap): Bitmap {
        val mean = floatArrayOf(0f, 0f, 0f)
        val std = floatArrayOf(1f, 1f, 1f)

        val originWidth = bitmap.width
        val originHeight = bitmap.height
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE,
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE,
            false
        )
        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(scaledBitmap, mean, std)

        val outTensor: IValue = mAlphaModule!!.forward(IValue.from(inputTensor))

        val scaledCutoutBitmap: Bitmap = Bitmap.createBitmap(
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE,
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val alphaFloat: FloatArray = outTensor.toTensor().dataAsFloatArray
        val alphaInt = IntArray(alphaFloat.size)

        // network size for.
        for (i in alphaInt.indices) {
            alphaInt[i] = Color.argb(alphaFloat[i], 1f, 1f, 1f)
        }
        scaledCutoutBitmap.setPixels(
            alphaInt,
            0,
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE,
            0,
            0,
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE,
            Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE
        )

        return scaledCutoutBitmap.scale(originWidth, originHeight, false)
    }

    fun loadModel(context: Context): String {
        return try {
            mModule = Module.load(assetFilePath(context,
                Param.MODEL_NAME
            ))
            "model load succeed."
        } catch (e: IOException) {
            "model load failed."
        }
    }

    fun loadAlphaModel(context: Context): String {
        return try {
            mAlphaModule = Module.load(assetFilePath(context,
                Param.ALPHA_MODEL_NAME
            ))
            "alpha model load succeed."
        } catch (e: IOException) {
            "alpha model load failed."
        }
    }

    @Throws(IOException::class)
    private fun assetFilePath(ctx: Context, assetName: String): String {
        val file = File(ctx.filesDir, assetName)
        if (file.exists() and (file.length() > 0)) {
            return file.absolutePath
        }

        // not in cache.
        try {
            ctx.assets.open(assetName).use { fis ->
                FileOutputStream(file).use { fos ->
                    val buffer = ByteArray(4 * 1024)
                    var len: Int
                    while ((fis.read(buffer).also { len = it }) != -1) {
                        fos.write(buffer, 0, len)
                    }
                    fos.flush()
                }
            }
            return file.absolutePath
        } catch (e: IOException) {
            throw e
        }
    }
}