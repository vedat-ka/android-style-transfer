package com.example.androidstyletransfer.model

import android.net.Uri

sealed interface ImageSource {
    val id: String
    val label: String
    val previewModel: Any

    data class Asset(
        val assetPath: String,
        override val label: String,
    ) : ImageSource {
        override val id: String = "asset:$assetPath"
        override val previewModel: Any = "file:///android_asset/$assetPath"
    }

    data class Picked(
        val uri: Uri,
        override val label: String,
    ) : ImageSource {
        override val id: String = "uri:$uri"
        override val previewModel: Any = uri
    }
}