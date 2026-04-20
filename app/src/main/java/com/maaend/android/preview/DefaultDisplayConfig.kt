package com.maaend.android.preview

object DefaultDisplayConfig {
    const val VD_NAME = "MAAEND_VD"
    const val DISPLAY_NONE = -1

    const val WIDTH = 1920
    const val HEIGHT = 1080
    const val DPI = 160

    val ASPECT_RATIO: Float get() = WIDTH.toFloat() / HEIGHT
}
