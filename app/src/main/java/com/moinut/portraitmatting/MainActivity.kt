package com.moinut.portraitmatting

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.graphics.scale
import androidx.core.graphics.set
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
        progressBar.visibility = View.INVISIBLE
        textView.paint.flags = Paint.STRIKE_THRU_TEXT_FLAG
        // load model
        doAsync {
            loadModel()

            uiThread {
                mModuleLoaded = true
            }
        }

        buttonPhoto.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivityForResult(intent, 0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0) { // camera activity
            data?.let { if (it.getBooleanExtra("success", false)) {
                mBitmap = PhotoController.instance.mBitmap
                imageView.setImageBitmap(mBitmap)
                if (!mModuleLoaded or (mBitmap == null)) {
                    toast("wrong photos.")
                    return
                }

                progressBar.visibility = View.VISIBLE

                doAsync {
                    val out = matting()

                    uiThread {
                        imageView.setImageBitmap(out)
                        val backgroundColor = Color.argb(1f, 33 / 255f, 150 / 255f, 243 / 255f)
                        imageView.setBackgroundColor(backgroundColor)
                        progressBar.visibility = View.INVISIBLE
                    }
                }
            }}
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

        val scaledCutoutBitmap: Bitmap = Bitmap.createBitmap(Const.NETWORK_IMAGE_SIZE, Const.NETWORK_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
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
        scaledCutoutBitmap.setPixels(cutoutInt, 0, Const.NETWORK_IMAGE_SIZE, 0, 0, Const.NETWORK_IMAGE_SIZE, Const.NETWORK_IMAGE_SIZE)
        val cutoutBitmap = scaledCutoutBitmap.scale(originWidth, originHeight, true)

        // max size for.
        for (i in 0 until cutoutBitmap.width * cutoutBitmap.height) {
            val x = i.rem(cutoutBitmap.width)
            val y = i / cutoutBitmap.width
            val alpha = cutoutBitmap.getColor(x, y).alpha()

            // if alpha > 80%, use original color.
            if (alpha > .8f) {
                val r = mBitmap!!.getColor(x, y).red()
                val g = mBitmap!!.getColor(x, y).green()
                val b = mBitmap!!.getColor(x, y).blue()
                cutoutBitmap[x, y] = Color.argb(alpha, r, g, b)
            }
        }
        PhotoController.instance.mCutoutBitmap = cutoutBitmap

        return cutoutBitmap
    }

    private fun loadModel(): String {
        return try {
            mModule = Module.load(assetFilePath(applicationContext, Const.MODEL_NAME))
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
