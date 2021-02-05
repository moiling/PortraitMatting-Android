package com.moinut.portraitmatting

import android.app.Application
import com.moinut.portraitmatting.ctr.MattingController
import org.jetbrains.anko.doAsync

class APP : Application() {
    override fun onCreate() {
        super.onCreate()
        // load model
        doAsync {
            MattingController.INSTANCE.loadModel(applicationContext)
            MattingController.INSTANCE.mModuleLoaded = true
        }

        // load alpha only model
        doAsync {
            MattingController.INSTANCE.loadAlphaModel(applicationContext)
            MattingController.INSTANCE.mAlphaModuleLoaded = true
        }
    }
}