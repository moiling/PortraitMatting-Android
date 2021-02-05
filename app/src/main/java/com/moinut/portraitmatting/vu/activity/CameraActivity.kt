package com.moinut.portraitmatting.vu.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import com.moinut.portraitmatting.R
import com.moinut.portraitmatting.config.Param
import com.moinut.portraitmatting.ctr.MattingController
import com.moinut.portraitmatting.utils.*
import com.moinut.portraitmatting.vu.listener.CameraTouchListener
import kotlinx.android.synthetic.main.activity_camera.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class CameraActivity : BaseActivity() {
    private var mPreview: Preview? = null
    private var mImageCapture: ImageCapture? = null
    private var mImageAnalyzer: ImageAnalysis? = null
    private var mCameraSelector: CameraSelector? = null
    private var mCameraProvider: ProcessCameraProvider? = null
    private var mCameraInfo: CameraInfo? = null
    private var mCameraControl: CameraControl? = null
    private var mShowAlpha = false

    private var mAspectRatioInt = AspectRatio.RATIO_4_3
    private var mCameraSelectorInt = CameraSelector.LENS_FACING_BACK

    private lateinit var mCameraExecutor: ExecutorService

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        if (allPermissionsGranted())
            initCamera()
        else
            ActivityCompat.requestPermissions(this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )

        previewLayoutVisible(false)

        val windowWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.windowManager.currentWindowMetrics.bounds.width()
        } else {
            this.windowManager.defaultDisplay.width
        }

        if (mAspectRatioInt == AspectRatio.RATIO_16_9) {
            viewFinder.layoutParams.height = windowWidth * 16 / 9
            imageCamera.layoutParams.height = windowWidth * 16 / 9
        } else {
            viewFinder.layoutParams.height = windowWidth * 4 / 3
            imageCamera.layoutParams.height = windowWidth * 4 / 3
        }

        imageAlpha.visibility = View.INVISIBLE

        setListener()
    }

    private fun setListener() {
        buttonTakePhoto.setOnClickListener { takePhoto() }
        buttonChangeCamera.setOnClickListener { changeCamera() }
        buttonBack.setOnClickListener { finish() }
        buttonCancel.setOnClickListener { previewLayoutVisible(false) }
        buttonOk.setOnClickListener {
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
            finish()
        }

        buttonShowAlpha.setOnClickListener {
            mShowAlpha = !mShowAlpha
            if (mShowAlpha) {
                imageAlpha.visibility = View.VISIBLE
            } else {
                imageAlpha.visibility = View.INVISIBLE
            }
        }
    }

    private fun previewLayoutVisible(visible: Boolean) {
        if (visible) {
            imageCamera.visibility = View.VISIBLE
            buttonCancel.visibility = View.VISIBLE
            buttonOk.visibility = View.VISIBLE
            buttonTakePhoto.visibility = View.INVISIBLE
            viewFinder.visibility = View.INVISIBLE
            buttonChangeCamera.visibility = View.INVISIBLE
            buttonFlash.visibility = View.INVISIBLE
            imageAlpha.visibility = View.INVISIBLE
            buttonShowAlpha.visibility = View.INVISIBLE
        } else {
            imageCamera.visibility = View.INVISIBLE
            buttonCancel.visibility = View.INVISIBLE
            buttonOk.visibility = View.INVISIBLE
            buttonTakePhoto.visibility = View.VISIBLE
            viewFinder.visibility = View.VISIBLE
            buttonChangeCamera.visibility = View.VISIBLE
            buttonFlash.visibility = View.VISIBLE
            if (mShowAlpha) {
                imageAlpha.visibility = View.VISIBLE
            } else {
                imageAlpha.visibility = View.INVISIBLE
            }
            buttonShowAlpha.visibility = View.VISIBLE
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun initCamera() {
        mCameraExecutor = Executors.newSingleThreadExecutor()

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

        val cameraXPreviewViewTouchListener =
            CameraTouchListener(this)
        cameraXPreviewViewTouchListener.setCustomTouchListener(object :
            CameraTouchListener.CustomTouchListener {
            override fun zoom(delta: Float) {
                val currentZoomRatio = zoomState.value!!.zoomRatio
                mCameraControl!!.setZoomRatio(currentZoomRatio * delta)
            }

            override fun click(x: Float, y: Float) {
                val factory: MeteringPointFactory = viewFinder.meteringPointFactory
                val point = factory.createPoint(x, y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                mCameraControl!!.startFocusAndMetering(action)
            }

            override fun doubleClick(x: Float, y: Float) {
                val currentZoomRatio = zoomState.value!!.zoomRatio
                if (currentZoomRatio > zoomState.value!!.minZoomRatio) {
                    mCameraControl!!.setLinearZoom(0f)
                } else {
                    mCameraControl!!.setLinearZoom(0.5f)
                }
            }

            override fun longClick(x: Float, y: Float) {}
        })
        viewFinder.setOnTouchListener(cameraXPreviewViewTouchListener)
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun initImageAnalyzer() {
        mImageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(
                if (mAspectRatioInt == AspectRatio.RATIO_4_3)
                    Size(720, 960) else Size(720, 1280)
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        mImageAnalyzer!!.setAnalyzer(mCameraExecutor, ImageAnalysis.Analyzer {
            if (!MattingController.INSTANCE.mAlphaModuleLoaded || !mShowAlpha) {
                it.close()
                return@Analyzer
            }
            var bitmap = it.image?.toYUVBitmap()

            if (bitmap == null) {
                it.close()
                return@Analyzer
            }

            bitmap = bitmap.rotate(it.imageInfo.rotationDegrees + 0f)
            if (mCameraSelectorInt == CameraSelector.LENS_FACING_FRONT) {
                bitmap = bitmap?.flipHorizontal()
            }
            bitmap = bitmap!!.resizeIfShortBigThan(Param.ALPHA_ONLY_IMAGE_MAX_SIZE)
            // matting

            val out = MattingController.INSTANCE.alphaMatting(bitmap!!)
            runOnUiThread {
                imageAlpha.setImageBitmap(out)
            }
            it.close()
        })
    }

    private fun initImageCapture() {
        mImageCapture = ImageCapture.Builder()
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setTargetAspectRatio(mAspectRatioInt)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()


        val orientationEventListener by lazy {
            object : OrientationEventListener(this) {
                override fun onOrientationChanged(orientation: Int) {

                    val rotation = when (orientation) {
                        in 45 until 135 -> Surface.ROTATION_270
                        in 135 until 225 -> Surface.ROTATION_180
                        in 225 until 315 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }

                    mImageAnalyzer?.targetRotation = rotation
                    mImageCapture?.targetRotation = rotation
                }
            }
        }

        orientationEventListener.enable()
    }

    private fun initPreview() {
        mPreview = Preview.Builder().setTargetAspectRatio(mAspectRatioInt).build()
    }

    private fun initCameraSelector() {
        mCameraSelector = CameraSelector.Builder().requireLensFacing(mCameraSelectorInt).build()
    }

    private fun takePhoto() {
        mImageCapture?.takePicture(mCameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeExperimentalUsageError")
                override fun onCaptureSuccess(image: ImageProxy) {

                    var bitmap = image.image?.toBitmap()

                    bitmap = bitmap?.rotate(image.imageInfo.rotationDegrees + 0f)
                    bitmap = bitmap?.resizeIfShortBigThan(Param.IMAGE_MAX_SIZE)
                    if (mCameraSelectorInt == CameraSelector.LENS_FACING_FRONT) {
                        bitmap = bitmap?.flipHorizontal()
                    }

                    MattingController.INSTANCE.mBitmap = bitmap
                    MattingController.INSTANCE.mCutoutBitmap = null
                    runOnUiThread {
                        imageCamera.setImageBitmap(bitmap)
                        previewLayoutVisible(true)
                    }
                    image.close()
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
}