package com.moinut.portraitmatting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var mModule: Module? = null
    private var mBitmap: Bitmap? = null
    private var mModuleLoaded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // load model
        progressBar.visibility = ProgressBar.VISIBLE
        doAsync {
            val result = loadModel()

            uiThread {
                toast(result)
                mModuleLoaded = true
                progressBar.visibility = ProgressBar.INVISIBLE
            }
        }

        // test image
        try {
            mBitmap = BitmapFactory.decodeStream(assets.open("img.jpg"))
            imageView.setImageBitmap(mBitmap)
        } catch (e: IOException) {
            toast("load image failed.")
        }

        buttonMatting.setOnClickListener {
            run {
                if (mModuleLoaded and (mBitmap != null)) {
                    toast("matting...")
                    progressBar.visibility = ProgressBar.VISIBLE

                    val startTime = System.currentTimeMillis()
                    doAsync {
                        val out = matting()

                        uiThread {
                            val useTime = (System.currentTimeMillis() - startTime) / 1000.0
                            toast(String.format("time: %.2fs", useTime))
                            imageView.setImageBitmap(out)
                            progressBar.visibility = ProgressBar.INVISIBLE
                        }
                    }
                } else {
                    toast("please load model and image first.")
                }
            }
        }

        buttonReset.setOnClickListener {
            imageView.setImageBitmap(mBitmap)
        }
    }

    private fun matting(): Bitmap {
        val mean = floatArrayOf(0f, 0f, 0f)
        val std = floatArrayOf(1f, 1f, 1f)
        val originWidth = mBitmap!!.width
        val originHeight = mBitmap!!.height
        val scaledBitmap = Bitmap.createScaledBitmap(mBitmap!!, Const.NETWORK_IMAGE_SIZE, Const.NETWORK_IMAGE_SIZE, true)
        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(scaledBitmap, mean, std)
        val outTensor: IValue = mModule!!.forward(IValue.from(inputTensor))
        val alphaFloat: FloatArray = outTensor.toTuple()[0].toTensor().dataAsFloatArray
        val alphaInt = IntArray(alphaFloat.size)
        for ((i, alpha) in alphaFloat.withIndex()) {
            alphaInt[i] = Color.argb(1f, alpha, alpha, alpha)
        }
        val outBitmap: Bitmap = Bitmap.createBitmap(Const.NETWORK_IMAGE_SIZE, Const.NETWORK_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(alphaInt, 0, Const.NETWORK_IMAGE_SIZE, 0, 0, Const.NETWORK_IMAGE_SIZE, Const.NETWORK_IMAGE_SIZE)

        return Bitmap.createScaledBitmap(outBitmap, originWidth, originHeight, true)
    }

    private fun loadModel(): String {
        return try {
            mModule = Module.load(assetFilePath(applicationContext, "model.pth"))
            "model load succeed."
        } catch (e: IOException) {
            "model load failed."
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

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, length).show()
    }
}
