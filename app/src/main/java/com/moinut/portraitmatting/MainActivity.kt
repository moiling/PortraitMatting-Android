package com.moinut.portraitmatting

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.pytorch.Module
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 101
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private const val PICK_PHOTO = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // load model
        doAsync {
            loadModel()

            uiThread {
                PhotoController.instance.mModuleLoaded = true
            }
        }

        // load alpha only model
        doAsync {
            PhotoController.instance.mAlphaModule = Module.load(assetFilePath(applicationContext, Const.ALPHA_MODEL_NAME))
            uiThread {
                PhotoController.instance.mAlphaModuleLoaded = true
            }
        }

        imageView.setOnLongClickListener {
            var selection = 0
            when (Const.MODEL_NAME) {
                "mobilenet_cutout.pth" -> selection = 0
                "pspnet_cutout.pth" -> selection = 1
                "modnet_cutout.pth" -> selection = 2
            }
            MaterialDialog(this).show {
                listItemsSingleChoice(
                    items = arrayListOf("MOBILE NET", "PSP NET", "MOD NET"),
                    initialSelection = selection
                ) { dialog, index, text ->
                    when (index) {
                        0 -> {
                            Const.MODEL_NAME = "mobilenet_cutout.pth"
                            toast("SWITCH TO MOBILE NET.")
                            loadModel()
                        }
                        1 -> {
                            Const.MODEL_NAME = "pspnet_cutout.pth"
                            toast("SWITCH TO PSP NET.")
                            loadModel()
                        }
                        2 -> {
                            Const.MODEL_NAME = "modnet_cutout.pth"
                            toast("SWITCH TO MOD NET.")
                            loadModel()
                        }
                    }
                }
                positiveButton(text = "OK")
            }
            true
        }

        buttonPhoto.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        buttonPhoto.setOnLongClickListener {
            MaterialDialog(this).show {
                input(inputType = InputType.TYPE_CLASS_NUMBER, prefill = Const.ALPHA_ONLY_NETWORK_IMAGE_SIZE.toString()) { dialog, text ->
                    Const.ALPHA_ONLY_NETWORK_IMAGE_SIZE = text.toString().toInt()
                }
                positiveButton(text = "OK")
            }
            false
        }

        buttonFile.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            } else {
                openPhoto()
            }
        }
    }

    private fun openPhoto() {
//        val intent = Intent(Intent.ACTION_GET_CONTENT)
//        intent.addCategory(Intent.CATEGORY_OPENABLE)
//        intent.type = "image/*"
//        startActivityForResult(intent, PICK_PHOTO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openPhoto()
            } else {
                toast("Permissions not granted by the user.")
            }
        }
    }

    private fun loadModel(): String {
        return try {
            PhotoController.instance.mModule =
                Module.load(assetFilePath(applicationContext, Const.MODEL_NAME))
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


    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_PHOTO -> if (resultCode == Activity.RESULT_OK) {
                var imagePath: String? = null
                val uri: Uri = data?.data!!

                uri.path?.let { Log.e(TAG, it) }
                if (DocumentsContract.isDocumentUri(this, uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    if ("com.android.providers.media.documents" == uri.authority) {
                        val id = docId.split(":").toTypedArray()[1]
                        val selection = MediaStore.Images.Media._ID + "=" + id
                        imagePath =
                            getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection)
                    } else if ("com.android.providers.downloads.documents" == uri.authority) {
                        val contentUri: Uri = ContentUris.withAppendedId(
                            Uri.parse("content: //downloads/public_downloads"),
                            java.lang.Long.valueOf(docId)
                        )
                        imagePath = getImagePath(contentUri, null)
                    }
                } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                    imagePath = getImagePath(uri, null)
                } else {
                    imagePath = uri.path
                }
                var bitmap = BitmapFactory.decodeFile(imagePath)
                bitmap = bitmap?.resizeIfShortBigThan(Const.IMAGE_MAX_SIZE)

                PhotoController.instance.mBitmap = bitmap
                PhotoController.instance.mCutoutBitmap = null
                val intent = Intent(this, EditActivity::class.java)
                startActivity(intent)
            }
            else -> {
            }
        }
    }


    private fun getImagePath(uri: Uri, selection: String?): String? {
        var path: String? = null
        val cursor: Cursor? = contentResolver.query(uri, null, selection, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            }
            cursor.close()
        }
        return path
    }


    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, length).show()
    }
}
