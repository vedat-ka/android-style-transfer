package com.example.androidstyletransfer.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import android.os.Build
import android.provider.MediaStore
import com.example.androidstyletransfer.model.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

// AdaIN (Adaptive Instance Normalization) - Huang & Belongie, 2017.
// Das Modell nimmt ein Inhaltsbild und ein beliebiges Stilbild entgegen
// und uebertraegt den Stil per VGG16-Feature-Statistiken (mean/std).
// alpha wird direkt als Modellparameter uebergeben - kein Post-Processing-Blend.
//
// ONNX-Eingaben:
//   content [1, 3, H, W]   float32  [0, 1]
//   style   [1, 3, sH, sW] float32  [0, 1]
//   alpha   [1]             float32  0.0 = Inhalt, 1.0 = voller Stil
// ONNX-Ausgabe:
//   output  [1, 3, H, W]   float32  [0, 1]

private const val MODEL_ASSET_PATH = "models/adain.onnx"

// Maximale Verarbeitungsgroesse in Pixeln pro Seite (Kompromiss Qualitaet / Geschwindigkeit)
private const val MAX_SIDE = 512

class StyleTransferEngine(
    private val context: Context,
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    private fun ensureLoaded() {
        if (session != null) return
        val bytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
        session = env.createSession(bytes, OrtSession.SessionOptions())
    }

    suspend fun stylize(
        contentImage: ImageSource,
        styleImage: ImageSource,
        alpha: Float = 1.0f,
    ): Bitmap = withContext(Dispatchers.Default) {
        ensureLoaded()
        val ortSession = requireNotNull(session)

        val contentBitmap = decodeBitmap(contentImage)
        val origW = contentBitmap.width
        val origH = contentBitmap.height

        // Kurzschluss: alpha=0 liefert direkt das skalierte Originalbild ohne Modell-Inferenz.
        // Das Modell verzerrt durch VGG-Encoder/Decoder selbst bei alpha=0.
        if (alpha <= 0.01f) {
            val scaled = scaleBitmap(contentBitmap, MAX_SIDE)
            return@withContext Bitmap.createScaledBitmap(scaled, origW, origH, true)
        }

        val styleBitmap = decodeBitmap(styleImage)

        // Beide Bilder proportional auf MAX_SIDE skalieren
        val scaledContent = scaleBitmap(contentBitmap, MAX_SIDE)
        val scaledStyle = scaleBitmap(styleBitmap, MAX_SIDE)

        val cW = scaledContent.width
        val cH = scaledContent.height
        val sW = scaledStyle.width
        val sH = scaledStyle.height

        val contentTensor = OnnxTensor.createTensor(
            env,
            bitmapToNormalizedCHW(scaledContent, cW, cH),
            longArrayOf(1L, 3L, cH.toLong(), cW.toLong()),
        )
        val styleTensor = OnnxTensor.createTensor(
            env,
            bitmapToNormalizedCHW(scaledStyle, sW, sH),
            longArrayOf(1L, 3L, sH.toLong(), sW.toLong()),
        )
        val alphaTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatArrayOf(alpha.coerceIn(0f, 1f))),
            longArrayOf(1L),
        )

        val result = ortSession.run(
            mapOf("content" to contentTensor, "style" to styleTensor, "alpha" to alphaTensor)
        )
        contentTensor.close()
        styleTensor.close()
        alphaTensor.close()

        val outputTensor = result.get("output").get() as OnnxTensor
        // rewind() sicherstellen: OnnxTensor liefert einen Direct-FloatBuffer, dessen
        // Position nach dem Run nicht garantiert 0 ist.
        val outputBuffer = outputTensor.floatBuffer.also { it.rewind() }
        val resultBitmap = normalizedCHWToBitmap(outputBuffer, cW, cH)
        result.close()

        // Gesichter im skalierten Inhaltsbild erkennen und weich ueber das stilisierte Bild blenden
        val faceRects = detectFaceRegions(scaledContent)
        val blendedResult = if (faceRects.isEmpty()) resultBitmap
                            else blendFaces(scaledContent, resultBitmap, faceRects)

        // Ergebnis auf Originalgroesse des Inhaltsbilds hochskalieren
        Bitmap.createScaledBitmap(blendedResult, origW, origH, true)
    }

    override fun close() {
        session?.close()
        session = null
    }

    // Bitmap proportional skalieren sodass die laengere Seite maxSide Pixel hat.
    // Beide Dimensionen werden auf ein Vielfaches von 32 abgerundet (floor), damit der
    // VGG16-Encoder/Decoder mit 4 Pooling-Stufen keine Off-by-one-Artefakte ("bunte Linien")
    // bei ungeraden Dimensionen erzeugt. 32 ist der sichere Divisor (2^5).
    private fun scaleBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longestSide = maxOf(w, h)
        val scale = if (longestSide > maxSide) maxSide.toFloat() / longestSide else 1f
        val nW = ((w * scale).toInt() / 32 * 32).coerceAtLeast(32)
        val nH = ((h * scale).toInt() / 32 * 32).coerceAtLeast(32)
        if (nW == w && nH == h) return bitmap
        return Bitmap.createScaledBitmap(bitmap, nW, nH, true)
    }

    // Bitmap -> CHW FloatBuffer, Wertebereich [0, 1]
    private fun bitmapToNormalizedCHW(bitmap: Bitmap, width: Int, height: Int): FloatBuffer {
        val n = width * height
        val pixels = IntArray(n)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val buf = FloatBuffer.allocate(3 * n)
        for (px in pixels) buf.put((px shr 16 and 0xFF) / 255f) // R
        for (px in pixels) buf.put((px shr 8  and 0xFF) / 255f) // G
        for (px in pixels) buf.put((px        and 0xFF) / 255f) // B
        buf.rewind()
        return buf
    }

    // CHW FloatBuffer [0, 1] -> Bitmap
    private fun normalizedCHWToBitmap(buffer: FloatBuffer, width: Int, height: Int): Bitmap {
        val n = width * height
        val pixels = IntArray(n)
        for (i in 0 until n) {
            val r = (buffer[i]         * 255f).coerceIn(0f, 255f).toInt()
            val g = (buffer[n + i]     * 255f).coerceIn(0f, 255f).toInt()
            val b = (buffer[2 * n + i] * 255f).coerceIn(0f, 255f).toInt()
            pixels[i] = android.graphics.Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decodeBitmap(source: ImageSource): Bitmap {
        val raw = when (source) {
            is ImageSource.Asset -> context.assets.open(source.assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
                    ?: error("Asset ${source.assetPath} konnte nicht gelesen werden.")
            }
            is ImageSource.Picked -> decodeBitmapFromUri(context.contentResolver, source.uri)
        }
        return raw.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun decodeBitmapFromUri(resolver: ContentResolver, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(resolver, uri)
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    /**
     * Erkennt Gesichter per ML Kit Face Detection (genau, mehrere Personen).
     * Laeuft synchron auf Dispatchers.Default via Tasks.await().
     * Gibt Bounding-Rects (leicht vergroessert fuer Stirn/Kinn) zurueck.
     */
    private fun detectFaceRegions(bitmap: Bitmap): List<RectF> {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.06f) // auch kleine Gesichter im Hintergrund erkennen
            .build()
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        // Tasks.await() ist auf einem Hintergrund-Thread (Dispatchers.Default) sicher
        val faces = Tasks.await(detector.process(image))
        detector.close()
        return faces.map { face ->
            val box = face.boundingBox
            // Bounding-Box nach oben (Stirn) und unten (Kinn) leicht erweitern
            val extraH = box.height() * 0.18f
            RectF(
                box.left.toFloat().coerceAtLeast(0f),
                (box.top - extraH).coerceAtLeast(0f),
                box.right.toFloat().coerceAtMost(bitmap.width.toFloat()),
                (box.bottom + extraH * 0.4f).coerceAtMost(bitmap.height.toFloat()),
            )
        }
    }

    /**
     * Blendet Original-Gesichtsbereiche weich ueber das stilisierte Bild.
     *
     * Zweistufige Blend-Zone pro Gesicht:
     *   d < INNER  -> weight = 1.0  (reines Original, kein Stil)
     *   INNER..1.0 -> kosinusfoermiger Abfall 1.0 -> 0.0  (innerer Rand bis Ellipse)
     *   1.0..OUTER -> kosinusfoermiger Abfall 0.0 (Fortsetzung des Abfalls im Aussenring)
     *   d > OUTER  -> weight = 0.0  (reines Stiltransfer-Ergebnis)
     *
     * OUTER ist groesser als 1.0, sodass die Uebergangszone ausserhalb der ML-Kit-BoundingBox
     * weiterlaeuft und der Uebergang unsichtbar wird.
     */
    private fun blendFaces(original: Bitmap, stylized: Bitmap, regions: List<RectF>): Bitmap {
        // Innerer Kern: bis hier 100 % Original (kein sichtbarer Blend-Beginn)
        val INNER = 0.55f
        // Aussenring: bis hierher laeuft der weiche Abfall – ausserhalb der ML-Kit-Box
        val OUTER = 1.55f
        val RANGE = OUTER - INNER  // Gesamtbreite der Uebergangszone

        val w = stylized.width
        val h = stylized.height
        val origPx = IntArray(w * h)
        val stylPx = IntArray(w * h)
        original.getPixels(origPx, 0, w, 0, 0, w, h)
        stylized.getPixels(stylPx, 0, w, 0, 0, w, h)
        val outPx = stylPx.copyOf()

        for (rect in regions) {
            val cx = (rect.left + rect.right) / 2f
            val cy = (rect.top + rect.bottom) / 2f
            val rx = (rect.right - rect.left) / 2f
            val ry = (rect.bottom - rect.top) / 2f

            // Scan-Bereich schliesst den gesamten Aussenring ein
            val x0 = (cx - rx * OUTER).toInt().coerceAtLeast(0)
            val x1 = (cx + rx * OUTER).toInt().coerceAtMost(w - 1)
            val y0 = (cy - ry * OUTER).toInt().coerceAtLeast(0)
            val y1 = (cy + ry * OUTER).toInt().coerceAtMost(h - 1)

            for (py in y0..y1) {
                for (px in x0..x1) {
                    val nx = (px - cx) / rx
                    val ny = (py - cy) / ry
                    val d = sqrt(nx * nx + ny * ny)
                    if (d >= OUTER) continue

                    val weight = when {
                        d <= INNER -> 1f
                        else -> {
                            // t in [0,1] ueber die gesamte Uebergangszone
                            val t = (d - INNER) / RANGE
                            // Kosinus-Abfall: t=0 -> 1.0, t=1 -> 0.0
                            ((1f + cos(PI * t).toFloat()) / 2f).coerceIn(0f, 1f)
                        }
                    }

                    val idx = py * w + px
                    val op = origPx[idx]
                    val sp = outPx[idx]
                    val r = lerpInt((sp shr 16 and 0xFF), (op shr 16 and 0xFF), weight)
                    val g = lerpInt((sp shr 8  and 0xFF), (op shr 8  and 0xFF), weight)
                    val b = lerpInt((sp        and 0xFF), (op        and 0xFF), weight)
                    outPx[idx] = android.graphics.Color.rgb(r, g, b)
                }
            }
        }
        return Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun lerpInt(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).toInt().coerceIn(0, 255)
}
