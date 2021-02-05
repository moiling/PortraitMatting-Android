package com.moinut.portraitmatting.vu.activity

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.moinut.portraitmatting.vu.adapter.ColorAdapter
import com.moinut.portraitmatting.ctr.MattingController
import com.moinut.portraitmatting.R
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.android.synthetic.main.activity_edit.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class EditActivity : BaseActivity() {
    private var mBackgroundColor: Int = Color.parseColor("#9e9e9e")
    private var mIsSaving: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        initColorRecyclerView()
        setListener()
        showImageAndMatting()
    }

    private fun showImageAndMatting() {
        imageViewEdit.setImageBitmap(MattingController.INSTANCE.mBitmap)
        if (MattingController.INSTANCE.mBitmap == null) {
            toast("wrong photos.")
            return
        }

        progressBar.visibility = View.VISIBLE

        doAsync {
            val out = MattingController.INSTANCE.matting()

            uiThread {
                imageViewEdit.setImageBitmap(out)
                imageViewEdit.setBackgroundColor(mBackgroundColor)
                progressBar.visibility = View.INVISIBLE
            }
        }
    }

    private fun initColorRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = ColorAdapter(
            this,
            object :
                ColorAdapter.OnColorClickListener {
                override fun onItemClick(view: View?, color: Int) {
                    if (mBackgroundColor != color) {
                        mBackgroundColor = color
                        imageViewEdit.setBackgroundColor(color)
                    }
                }
            })
    }

    private fun setListener() {
        buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Choose Background Color")
                .setPreferenceName("ColorPickerDialog")
                .setPositiveButton("OK", ColorEnvelopeListener { envelope, _ ->
                    mBackgroundColor = envelope.color
                    imageViewEdit.setBackgroundColor(envelope.color)
                })
                .setNegativeButton("Cancel") { di, _ -> di.dismiss() }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }

        buttonEditBack.setOnClickListener { finish() }
        buttonSave.setOnClickListener {
            if (MattingController.INSTANCE.mCutoutBitmap == null) {
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
                val compBitmap = MattingController.INSTANCE.composite(
                    MattingController.INSTANCE.mCutoutBitmap!!, mBackgroundColor)
                // save
                val name = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                saveBitmap("${name}.png", compBitmap)
                saveBitmap("${name}_cutout.png", MattingController.INSTANCE.mCutoutBitmap!!)

                uiThread {
                    toast("Saved.")
                    progressBar.visibility = View.INVISIBLE
                    mIsSaving = false
                }
            }
        }
    }


    private fun saveBitmap(name: String, bitmap: Bitmap) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

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
            } catch (e: Exception) { }
        } else {
            val galleryPath = Environment.getExternalStorageDirectory().toString() + File.separator + Environment.DIRECTORY_DCIM + File.separator + "Camera" + File.separator

            try {
                val file = File(galleryPath, name)
                val fileName = file.toString()
                if (file.exists()) {
                    file.delete()
                }
                val out: FileOutputStream = FileOutputStream(fileName)

                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    out.flush()
                    out.close()
                }

                MediaStore.Images.Media.insertImage(this.contentResolver, bitmap, name, null)
                this.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            } catch (e: Exception) {
                runOnUiThread {
                    e.message?.let { toast(it) }
                }
            }
        }
    }
}