package com.moinut.portraitmatting

import android.graphics.Bitmap

class PhotoController private constructor() {
    var mBitmap: Bitmap? = null
    var mCutoutBitmap: Bitmap? = null

    companion object {

        val instance: PhotoController by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            PhotoController()
        }
    }
}