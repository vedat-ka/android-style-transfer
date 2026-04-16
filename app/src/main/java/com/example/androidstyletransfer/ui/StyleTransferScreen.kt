package com.example.androidstyletransfer.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.androidstyletransfer.model.ImageSource
import java.util.Locale

@Composable
fun StyleTransferScreen(
    viewModel: StyleTransferViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Tablet-Breakpoint: Bildschirmbreite >= 600 dp
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val contentPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.onContentImageSelected(it)
        }
    }
    val stylePicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.onStyleImageSelected(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8F2E7),
                        Color(0xFFE7EEF8),
                        Color(0xFFF6FAFD),
                    )
                )
            )
    ) {
        if (isTablet) {
            TabletLayout(
                uiState = uiState,
                onPickContent = { contentPicker.launch(arrayOf("image/*")) },
                onPickStyle = { stylePicker.launch(arrayOf("image/*")) },
                viewModel = viewModel,
            )
        } else {
            PhoneLayout(
                uiState = uiState,
                onPickContent = { contentPicker.launch(arrayOf("image/*")) },
                onPickStyle = { stylePicker.launch(arrayOf("image/*")) },
                viewModel = viewModel,
            )
        }
    }
}

// Einzel-Spalten-Layout fuer Smartphones
@Composable
private fun PhoneLayout(
    uiState: StyleTransferUiState,
    onPickContent: () -> Unit,
    onPickStyle: () -> Unit,
    viewModel: StyleTransferViewModel,
) {
    // rememberSaveable verhindert Scroll-Reset beim Neuzeichnen nach Bildauswahl
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HeaderCard(statusMessage = uiState.statusMessage)

        ImagePickerCard(
            title = "Inhaltsbild",
            buttonLabel = "Eigenes Bild waehlen",
            helperText = "Dieses Bild liefert die Struktur fuer das Endergebnis.",
            onPick = onPickContent,
            selectedImage = uiState.contentImage,
            sampleImages = uiState.contentSamples,
            onSampleSelected = viewModel::onContentSampleSelected,
        )

        ImagePickerCard(
            title = "Stilbild",
            buttonLabel = "Eigenes Stilbild waehlen",
            helperText = "Beliebiges Bild - VGG16-Farbstatistiken werden auf das Inhaltsbild uebertragen.",
            onPick = onPickStyle,
            selectedImage = uiState.styleImage,
            sampleImages = uiState.styleSamples,
            onSampleSelected = viewModel::onStyleSampleSelected,
        )

        AlphaSliderCard(
            styleStrength = uiState.styleStrength,
            onStyleStrengthChanged = viewModel::onStyleStrengthChanged,
        )

        Button(
            onClick = viewModel::runStyleTransfer,
            enabled = !uiState.isRunning && uiState.contentImage != null && uiState.styleImage != null,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            if (uiState.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Inferenz laeuft")
            } else {
                Text("Stiltransfer auf dem Geraet starten")
            }
        }

        ResultCard(uiState = uiState)
    }
}

// Zwei-Spalten-Layout fuer Tablets (>= 600 dp Breite)
// Linke Spalte: Bilder auswaehlen | Rechte Spalte: Steuerung + Ergebnis
@Composable
private fun TabletLayout(
    uiState: StyleTransferUiState,
    onPickContent: () -> Unit,
    onPickStyle: () -> Unit,
    viewModel: StyleTransferViewModel,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Linke Spalte: Header + Inhaltsbild + Stilbild
        val leftScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(leftScroll),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeaderCard(statusMessage = uiState.statusMessage)

            ImagePickerCard(
                title = "Inhaltsbild",
                buttonLabel = "Eigenes Bild waehlen",
                helperText = "Dieses Bild liefert die Struktur fuer das Endergebnis.",
                onPick = onPickContent,
                selectedImage = uiState.contentImage,
                sampleImages = uiState.contentSamples,
                onSampleSelected = viewModel::onContentSampleSelected,
            )

            ImagePickerCard(
                title = "Stilbild",
                buttonLabel = "Eigenes Stilbild waehlen",
                helperText = "Beliebiges Bild - VGG16-Farbstatistiken werden auf das Inhaltsbild uebertragen.",
                onPick = onPickStyle,
                selectedImage = uiState.styleImage,
                sampleImages = uiState.styleSamples,
                onSampleSelected = viewModel::onStyleSampleSelected,
            )
        }

        // Rechte Spalte: Stilstaerke + Start-Button + Ergebnis
        val rightScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rightScroll),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AlphaSliderCard(
                styleStrength = uiState.styleStrength,
                onStyleStrengthChanged = viewModel::onStyleStrengthChanged,
            )

            Button(
                onClick = viewModel::runStyleTransfer,
                enabled = !uiState.isRunning && uiState.contentImage != null && uiState.styleImage != null,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                if (uiState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Inferenz laeuft")
                } else {
                    Text("Stiltransfer starten")
                }
            }

            ResultCard(uiState = uiState)
        }
    }
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

@Composable
private fun HeaderCard(statusMessage: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15212D)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Android Style Transfer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFF3D8),
            )
            Text(
                text = "AdaIN + VGG16 - beliebiges Stilbild, alpha als echter Modellparameter.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD3E4F7),
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE7EEF8),
            )
        }
    }
}

@Composable
private fun ImagePickerCard(
    title: String,
    buttonLabel: String,
    helperText: String,
    onPick: () -> Unit,
    selectedImage: ImageSource?,
    sampleImages: List<ImageSource.Asset>,
    onSampleSelected: (ImageSource.Asset) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedImage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)  // Platzhalterproportion 4:3
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF2F0EB)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Noch kein Bild ausgewaehlt",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = selectedImage.previewModel,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()   // Hoehe folgt dem originalen Seitenverhaeltnis
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.FillWidth,
                )
                Text(
                    text = "Aktuell: ${selectedImage.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sampleImages.isNotEmpty()) {
                Text(
                    text = "Beispielauswahl",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sampleImages.forEach { sample ->
                        SampleImageTile(
                            image = sample,
                            isSelected = selectedImage?.id == sample.id,
                            onClick = { onSampleSelected(sample) },
                        )
                    }
                }
            }
            Button(onClick = onPick) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun SampleImageTile(
    image: ImageSource.Asset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = image.previewModel,
            contentDescription = image.label,
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    border = BorderStroke(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFD8DEE5),
                    ),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(onClick = onClick),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = image.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlphaSliderCard(
    styleStrength: Float,
    onStyleStrengthChanged: (Float) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF4)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Stilstaerke (alpha): ${String.format(Locale.US, "%.0f", styleStrength * 100)} %",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = styleStrength,
                onValueChange = onStyleStrengthChanged,
                valueRange = 0.0f..1.0f,
            )
            Text(
                text = "0 % = reiner Inhalt  100 % = voller Stil (AdaIN-Modellparameter, kein Post-Processing)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(uiState: StyleTransferUiState) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Ergebnis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (uiState.resultBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF0EDE8)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uiState.isRunning) "Inferenz laeuft..." else "Noch kein Ergebnis",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                androidx.compose.foundation.Image(
                    bitmap = uiState.resultBitmap.asImageBitmap(),
                    contentDescription = "Stiltransfer-Ergebnis",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.FillWidth,
                )
                Button(
                    onClick = {
                        saveBitmapToGallery(context, uiState.resultBitmap)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15212D),
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("In Fotos speichern")
                }
            }
        }
    }
}

// Speichert das Ergebnis-Bitmap in die Galerie (Fotos).
// Ab Android 10 (API 29): MediaStore ohne Berechtigung.
// API 26-28: braucht WRITE_EXTERNAL_STORAGE (im Manifest mit maxSdkVersion=28 deklariert).
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "StyleTransfer_${System.currentTimeMillis()}.jpg"
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StyleTransfer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore URI konnte nicht erstellt werden")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .resolve("StyleTransfer").also { it.mkdirs() }
            val file = dir.resolve(filename)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            // Galerie-Scan ausloesen damit das Bild sofort sichtbar ist
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
            )
        }
        Toast.makeText(context, "Gespeichert: $filename", Toast.LENGTH_SHORT).show()
    }.onFailure { e ->
        Toast.makeText(context, "Speichern fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
    }
}