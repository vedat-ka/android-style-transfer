package com.example.androidstyletransfer.model

import android.content.res.AssetManager

class BundledSampleCatalog(
    private val assetManager: AssetManager,
) {

    fun contentSamples(): List<ImageSource.Asset> {
        val preferredFiles = listOf(
            "elephant.jpg",
            "imagenet-sample.jpg",
        )

        return preferredFiles.mapNotNull { fileName ->
            val assetPath = "samples/content/$fileName"
            if (assetExists(assetPath)) {
                ImageSource.Asset(
                    assetPath = assetPath,
                    label = prettifyFileName(fileName),
                )
            } else {
                null
            }
        }
    }

    fun styleSamples(): List<ImageSource.Asset> {
        return assetManager.list("samples/styles")
            ?.filter { fileName ->
                fileName.endsWith(".jpg", ignoreCase = true) ||
                    fileName.endsWith(".jpeg", ignoreCase = true) ||
                    fileName.endsWith(".png", ignoreCase = true) ||
                    fileName.endsWith(".webp", ignoreCase = true)
            }
            ?.sorted()
            ?.map { fileName ->
                ImageSource.Asset(
                    assetPath = "samples/styles/$fileName",
                    label = prettifyFileName(fileName),
                )
            }
            .orEmpty()
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            assetManager.open(assetPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun prettifyFileName(fileName: String): String {
        return fileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }
}