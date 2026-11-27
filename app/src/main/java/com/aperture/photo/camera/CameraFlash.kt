package com.aperture.photo.camera

enum class CameraFlashMode(val title: String) {
    OFF("Off"), AUTO("Auto"), ON("On"), TORCH("Torch");

    companion object {
        fun choices(supported: Boolean): List<CameraFlashMode> =
            if (supported) entries.toList() else listOf(OFF)
    }
}

data class CameraFlashState(val mode: CameraFlashMode, val updating: Boolean = false, val error: String? = null)
