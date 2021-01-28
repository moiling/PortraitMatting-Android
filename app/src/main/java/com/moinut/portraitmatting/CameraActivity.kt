package com.moinut.portraitmatting

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraActivity : AppCompatActivity() {
    private var mPreview: Preview? = null
    private var mImageCapture: ImageCapture? = null
    private var mImageAnalyzer: ImageAnalysis? = null
    private var mCameraSelector: CameraSelector? = null
    private var mCameraProvider: ProcessCameraProvider? = null
    private var mCameraInfo: CameraInfo? = null
    private var mCameraControl: CameraControl? = null

    private var mAspectRatioInt = AspectRatio.RATIO_16_9
    private var mCameraSelectorInt = CameraSelector.LENS_FACING_BACK

    private lateinit var mOutputDirectory: File
    private lateinit var mCameraExecutor: ExecutorService

    companion object {
        private const val TAG = "CameraActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        // Request camera permissions
        if (allPermissionsGranted())
            initCamera()
        else
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        // Setup the listener for take photo button
        buttonTakePhoto.setOnClickListener { takePhoto() }
        buttonChangeCamera.setOnClickListener { changeCamera() }

        mOutputDirectory = getOutputDirectory()

        mCameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        initCameraSelector()
        initPreview()
        initImageAnalyzer()
        initImageCapture()

        cameraProviderFuture.addListener(Runnable {
            mCameraProvider = cameraProviderFuture.get()
            mCameraProvider?.unbindAll()
            mPreview?.setSurfaceProvider(viewFinder.surfaceProvider)

            val camera = mCameraProvider?.bindToLifecycle(this, mCameraSelector!!, mPreview, mImageCapture, mImageAnalyzer)
            mCameraInfo = camera?.cameraInfo
            mCameraControl = camera?.cameraControl


            initCameraListener()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun initCameraListener() {
        val zoomState: LiveData<ZoomState> = mCameraInfo?.zoomState!!
    }

    private fun initImageAnalyzer() {
        mImageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(
                if (mAspectRatioInt == AspectRatio.RATIO_4_3)
                    Size(720, 960) else Size(720, 1280)
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    private fun initImageCapture() {
        mImageCapture = ImageCapture.Builder()
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setTargetAspectRatio(mAspectRatioInt)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()


//        val orientationEventListener by lazy {
//            object : OrientationEventListener(this) {
//                override fun onOrientationChanged(orientation: Int) {
//
//                    val rotation = when (orientation) {
//                        in 45 until 135 -> Surface.ROTATION_270
//                        in 135 until 225 -> Surface.ROTATION_180
//                        in 225 until 315 -> Surface.ROTATION_90
//                        else -> Surface.ROTATION_0
//                    }
//
//                    mImageAnalyzer?.targetRotation = rotation
//                    mImageCapture?.targetRotation = rotation
//                }
//            }
//        }
//
//        orientationEventListener.enable()
    }

    private fun initPreview() {
        mPreview = Preview.Builder().setTargetAspectRatio(mAspectRatioInt).build()
    }

    private fun initCameraSelector() {
        mCameraSelector = CameraSelector.Builder().requireLensFacing(mCameraSelectorInt).build()
    }

    private fun takePhoto() {
        val photoFile = File(mOutputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

//        mImageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(exc: ImageCaptureException) {
//                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
//                }
//
//                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
//                    val savedUri = Uri.fromFile(photoFile)
//                    val msg = "Photo capture succeeded: ${savedUri.path}"
//                    toast(msg)
//                    Log.d(TAG, msg)
//
//                    val intent = Intent()
//                    intent.putExtra("photo_path", savedUri.path)
//                    setResult(Activity.RESULT_OK, intent)
//                    finish()
//                }
//            }
//        )

        mImageCapture?.takePicture(ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeExperimentalUsageError")
                override fun onCaptureSuccess(image: ImageProxy) {

                    var bitmap = image.image?.toBitmap()

                    bitmap = bitmap?.rotate(image.imageInfo.rotationDegrees + 0f)
                    bitmap = bitmap?.resizeIfShortBigThan(Const.IMAGE_MAX_SIZE)
                    if (mCameraSelectorInt == CameraSelector.LENS_FACING_FRONT) {
                        bitmap = bitmap?.flipHorizontal()
                    }

                    PhotoController.instance.mBitmap = bitmap

                    val intent = Intent()
                    intent.putExtra("success", true)
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            })
    }

    private fun changeCamera() {
        when (mCameraSelectorInt) {
            CameraSelector.LENS_FACING_BACK -> {
                mCameraSelectorInt = CameraSelector.LENS_FACING_FRONT
            }
            CameraSelector.LENS_FACING_FRONT -> {
                mCameraSelectorInt = CameraSelector.LENS_FACING_BACK
            }
        }
        if (allPermissionsGranted()) initCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initCamera()
            } else {
                toast("Permissions not granted by the user.")
                finish()
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, length).show()
    }
}