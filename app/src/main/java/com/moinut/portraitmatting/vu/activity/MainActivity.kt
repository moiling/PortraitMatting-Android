package com.moinut.portraitmatting.vu.activity

import android.Manifest
import android.app.Activity
import android.content.ContentUris
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.moinut.portraitmatting.ctr.MattingController
import com.moinut.portraitmatting.config.Param
import com.moinut.portraitmatting.R
import com.moinut.portraitmatting.utils.resizeIfShortBigThan
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 101
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private const val PICK_PHOTO = 102
        private val NET_LIST = arrayListOf("MOBILE NET", "PSP NET", "MOD NET")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setListener()
    }

    private fun setListener() {
        imageView.setOnLongClickListener {
            val selection = when (Param.MODEL_NAME) {
                Param.MOBILE_NET_NAME -> 0
                Param.PSP_NET_NAME -> 1
                Param.MODEL_NAME -> 2
                else -> 0
            }

            MaterialDialog(this).show {
                listItemsSingleChoice(
                    items = NET_LIST,
                    initialSelection = selection
                ) { _, index, _ ->
                    when (index) {
                        0 -> {
                            Param.MODEL_NAME =
                                Param.MOBILE_NET_NAME
                            toast("SWITCH TO MOBILE NET.")
                            MattingController.INSTANCE.loadModel(applicationContext)
                        }
                        1 -> {
                            Param.MODEL_NAME =
                                Param.PSP_NET_NAME
                            toast("SWITCH TO PSP NET.")
                            MattingController.INSTANCE.loadModel(applicationContext)
                        }
                        2 -> {
                            Param.MODEL_NAME =
                                Param.MOD_NET_NAME
                            toast("SWITCH TO MOD NET.")
                            MattingController.INSTANCE.loadModel(applicationContext)
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
                input(
                    inputType = InputType.TYPE_CLASS_NUMBER,
                    prefill = Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE.toString()
                ) { _, text ->
                    Param.ALPHA_ONLY_NETWORK_IMAGE_SIZE = text.toString().toInt()
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
                openImageDir()
            }
        }
    }

    private fun openImageDir() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        startActivityForResult(intent,
            PICK_PHOTO
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openImageDir()
            } else {
                toast("Permissions not granted by the user.")
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
                        imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection)
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
                bitmap = bitmap?.resizeIfShortBigThan(Param.IMAGE_MAX_SIZE)

                MattingController.INSTANCE.mBitmap = bitmap
                MattingController.INSTANCE.mCutoutBitmap = null
                val intent = Intent(this, EditActivity::class.java)
                startActivity(intent)
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
}
