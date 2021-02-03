package com.moinut.portraitmatting

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.android.synthetic.main.activity_edit.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.pytorch.IValue
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class EditActivity : AppCompatActivity() {
    private var mBitmap: Bitmap? = null
    private var mBackgroundColor: Int = Color.argb(255, 33, 150, 243)
    private var mIsSaving: Boolean = false

    private val mColorList = intArrayOf(
        Color.argb(255, 33, 150, 243),
        Color.argb(255, 255, 255, 255),
        Color.argb(255, 255, 47, 74),
        Color.argb(255, 255, 159, 47),
        Color.argb(255, 157, 157, 157),
        Color.argb(255, 222, 222, 222),
        Color.argb(255, 55, 55, 55),
        Color.argb(255, 0, 0, 0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        progressBar.visibility = View.INVISIBLE

        recyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): RecyclerView.ViewHolder {
                return ColorViewHolder(
                    LayoutInflater.from(this@EditActivity)
                        .inflate(R.layout.item_color, parent, false)
                )
            }

            override fun getItemCount(): Int {
                return mColorList.size
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val colorHolder = holder as ColorViewHolder
                colorHolder.mColor = mColorList[position]
                colorHolder.mButton!!.setBackgroundColor(mColorList[position])
            }

            inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                var mButton: ImageButton? = null
                var mColor: Int? = null

                init {
                    mButton = itemView.findViewById(R.id.buttonColor)
                    mButton!!.setOnClickListener {
                        if (mBackgroundColor != mColor) {
                            mBackgroundColor = mColor!!
                            imageViewEdit.setBackgroundColor(mColor!!)
                        }
                    }
                }
            }
        }

        buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Choose Background Color")
                .setPreferenceName("ColorPickerDialog")
                .setPositiveButton("OK", ColorEnvelopeListener { envelope, fromUser ->
                    mBackgroundColor = envelope.color
                    imageViewEdit.setBackgroundColor(envelope.color)
                })
                .setNegativeButton("Cancel") { dialogInterface, i ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }

        buttonEditBack.setOnClickListener { finish() }
        buttonSave.setOnClickListener {
            if (PhotoController.instance.mCutoutBitmap == null) {
                toast("Matting is not finished.")
                return@setOnClickListener
            }

            if (mIsSaving) {
                toast("Is Saving...")
                return@setOnClickListener
            }

            mIsSaving = true
            progressBar.visibility = View.VISIBLE

            doAsync {
                val compBitmap = Bitmap.createBitmap(PhotoController.instance.mCutoutBitmap!!)
                // composite
                for (y in 0 until compBitmap.height) {
                    for (x in 0 until compBitmap.width) {
                        val alpha = compBitmap[x, y].alpha / 255f

                        val r = compBitmap[x, y].red / 255f
                        val g = compBitmap[x, y].green / 255f
                        val b = compBitmap[x, y].blue / 255f

                        val bgR = mBackgroundColor.red / 255f
                        val bgG = mBackgroundColor.green / 255f
                        val bgB = mBackgroundColor.blue / 255f

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
                // save
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val data = Date()
                saveBitmap("${format.format(data)}.png", compBitmap)
                saveBitmap(
                    "${format.format(data)}_cutout.png",
                    PhotoController.instance.mCutoutBitmap!!
                )

                uiThread {
                    toast("Saved.")
                    progressBar.visibility = View.INVISIBLE
                    mIsSaving = false
                }
            }
        }

        mBitmap = PhotoController.instance.mBitmap
        imageViewEdit.setImageBitmap(mBitmap)
        if (mBitmap == null) {
            toast("wrong photos.")
            return
        }

        progressBar.visibility = View.VISIBLE

        doAsync {
            val out = matting()

            uiThread {
                imageViewEdit.setImageBitmap(out)
                imageViewEdit.setBackgroundColor(mBackgroundColor)
                progressBar.visibility = View.INVISIBLE
            }
        }
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        try {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, name)
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/")
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/PNG")

            val uri: Uri? = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            if (uri != null) {

                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                    outputStream.close()
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun matting(): Bitmap {
        val mean = floatArrayOf(0f, 0f, 0f)
        val std = floatArrayOf(1f, 1f, 1f)

        val originWidth = mBitmap!!.width
        val originHeight = mBitmap!!.height
        val scaledBitmap = Bitmap.createScaledBitmap(
            mBitmap!!,
            Const.NETWORK_IMAGE_SIZE,
            Const.NETWORK_IMAGE_SIZE,
            true
        )
        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(scaledBitmap, mean, std)

        val outTensor: IValue = PhotoController.instance.mModule!!.forward(IValue.from(inputTensor))

        val scaledCutoutBitmap: Bitmap = Bitmap.createBitmap(
            Const.NETWORK_IMAGE_SIZE,
            Const.NETWORK_IMAGE_SIZE,
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
            Const.NETWORK_IMAGE_SIZE,
            0,
            0,
            Const.NETWORK_IMAGE_SIZE,
            Const.NETWORK_IMAGE_SIZE
        )
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

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, length).show()
    }
}