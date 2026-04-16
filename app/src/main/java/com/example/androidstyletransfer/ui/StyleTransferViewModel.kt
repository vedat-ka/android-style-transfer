package com.example.androidstyletransfer.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstyletransfer.inference.StyleTransferEngine
import com.example.androidstyletransfer.model.BundledSampleCatalog
import com.example.androidstyletransfer.model.ImageSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// AdaIN (Adaptive Instance Normalization) - beliebiges Inhaltsbild + beliebiges Stilbild.
// styleStrength (alpha) wird direkt als Modellparameter an den ONNX-Graph uebergeben.
data class StyleTransferUiState(
    val contentImage: ImageSource? = null,
    val contentSamples: List<ImageSource.Asset> = emptyList(),
    val styleImage: ImageSource? = null,
    val styleSamples: List<ImageSource.Asset> = emptyList(),
    val styleStrength: Float = 1.0f,
    val resultBitmap: Bitmap? = null,
    val isRunning: Boolean = false,
    val statusMessage: String = "Waehle ein Inhaltsbild und ein Stilbild.",
)

class StyleTransferViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val engine = StyleTransferEngine(application.applicationContext)
    private val catalog = BundledSampleCatalog(application.assets)
    private var inferenceJob: Job? = null

    private val _uiState = MutableStateFlow(
        StyleTransferUiState(
            contentSamples = catalog.contentSamples(),
            styleSamples = catalog.styleSamples(),
            contentImage = catalog.contentSamples().firstOrNull(),
            styleImage = catalog.styleSamples().firstOrNull(),
            statusMessage = "AdaIN bereit. Inhaltsbild und Stilbild waehlen, dann starten.",
        )
    )
    val uiState: StateFlow<StyleTransferUiState> = _uiState.asStateFlow()

    fun onContentImageSelected(uri: Uri) {
        val label = uri.lastPathSegment?.substringAfterLast('/') ?: "Eigenes Inhaltsbild"
        _uiState.update {
            it.copy(
                contentImage = ImageSource.Picked(uri = uri, label = label),
                resultBitmap = null,
                statusMessage = "Inhaltsbild gesetzt.",
            )
        }
    }

    fun onContentSampleSelected(sample: ImageSource.Asset) {
        _uiState.update {
            it.copy(
                contentImage = sample,
                resultBitmap = null,
                statusMessage = "Inhaltsbild ${sample.label} ausgewaehlt.",
            )
        }
    }

    fun onStyleImageSelected(uri: Uri) {
        val label = uri.lastPathSegment?.substringAfterLast('/') ?: "Eigenes Stilbild"
        _uiState.update {
            it.copy(
                styleImage = ImageSource.Picked(uri = uri, label = label),
                resultBitmap = null,
                statusMessage = "Stilbild gesetzt.",
            )
        }
    }

    fun onStyleSampleSelected(sample: ImageSource.Asset) {
        _uiState.update {
            it.copy(
                styleImage = sample,
                resultBitmap = null,
                statusMessage = "Stilbild ${sample.label} ausgewaehlt.",
            )
        }
    }

    fun onStyleStrengthChanged(value: Float) {
        _uiState.update { it.copy(styleStrength = value, resultBitmap = null) }
    }

    fun runStyleTransfer() {
        val state = _uiState.value
        val contentImage = state.contentImage ?: return
        val styleImage = state.styleImage ?: return

        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    resultBitmap = null,
                    statusMessage = "AdaIN-Stiltransfer laeuft (alpha=${"%.2f".format(state.styleStrength)}) ...",
                )
            }

            runCatching {
                engine.stylize(
                    contentImage = contentImage,
                    styleImage = styleImage,
                    alpha = _uiState.value.styleStrength,
                )
            }.onSuccess { bitmap ->
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        resultBitmap = bitmap,
                        statusMessage = "Fertig - AdaIN Stiltransfer direkt auf dem Geraet.",
                    )
                }
            }.onFailure { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        statusMessage = throwable.message ?: "Inferenz fehlgeschlagen.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}
