package com.moinut.portraitmatting.vu.activity

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.moinut.portraitmatting.ctr.MattingController
import org.jetbrains.anko.doAsync

open class BaseActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()

        // load model
        if (MattingController.INSTANCE.mModule == null) {
            doAsync {
                MattingController.INSTANCE.loadModel(applicationContext)
                MattingController.INSTANCE.mModuleLoaded = true
            }
        }

        // load alpha only model
        if (MattingController.INSTANCE.mAlphaModule == null) {
            doAsync {
                MattingController.INSTANCE.loadAlphaModel(applicationContext)
                MattingController.INSTANCE.mAlphaModuleLoaded = true
            }
        }
    }

    protected fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, length).show()
    }
}