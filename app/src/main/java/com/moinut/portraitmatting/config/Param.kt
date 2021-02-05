package com.moinut.portraitmatting.config

object Param {
    const val NETWORK_IMAGE_SIZE = 480
    const val IMAGE_MAX_SIZE = 860
    const val ALPHA_ONLY_IMAGE_MAX_SIZE = 320
    var ALPHA_ONLY_NETWORK_IMAGE_SIZE = 224

    const val ALPHA_MODEL_NAME = "modnet.pth"

    const val MOBILE_NET_NAME = "mobilenet_cutout.pth"
    const val PSP_NET_NAME = "pspnet_cutout.pth"
    const val MOD_NET_NAME = "modnet_cutout.pth"

    var MODEL_NAME = MOBILE_NET_NAME
}