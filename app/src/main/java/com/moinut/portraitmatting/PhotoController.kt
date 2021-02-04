package com.moinut.portraitmatting

import android.graphics.Bitmap
import org.pytorch.Module

class PhotoController private constructor() {
    var mBitmap: Bitmap? = null
    var mCutoutBitmap: Bitmap? = null
    var mModule: Module? = null
    var mAlphaModule: Module? = null
    var mModuleLoaded: Boolean = false
    var mAlphaModuleLoaded: Boolean = false

    companion object {

        val instance: PhotoController by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            PhotoController()
        }
    }
}