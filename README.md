# Android Style Transfer

Native Android-Studio-Projekt fuer neuronalen Stiltransfer direkt auf dem Geraet (on-device) mit Kotlin, Jetpack Compose und ONNX Runtime.

Die App verwendet ein AdaIN-Modell (Adaptive Instance Normalization): Ein Inhaltsbild (content image) liefert Struktur und Formen, ein beliebiges Stilbild (style image) liefert Farb- und Texturstatistiken. Die Stilstaerke (`alpha`) wird als echter Modellparameter an den ONNX-Graph uebergeben.

## Funktionsumfang

- Stiltransfer direkt auf dem Geraet, kein Backend und keine Server-Inferenz
- Freie Kombination aus Inhaltsbild und Stilbild
- Gebuendelte Beispielbilder fuer schnellen Einstieg
- Eigene Bilder ueber Androids System-Dateiauswahl (Storage Access Framework)
- Slider fuer Stilstaerke (`alpha`) von `0 %` bis `100 %`
- Gesichtsschutz (face preservation): erkannte Gesichter werden weich mit dem Originalbild verblendet
- Speichern des Ergebnisses in die Galerie bzw. Fotos-App
- Responsive Compose-Oberflaeche fuer Smartphone und Tablet

## Technischer Stack

- Kotlin
- Jetpack Compose
- ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android:1.24.3`)
- ML Kit Face Detection
- Coil fuer Bildvorschau

## Projektstatus

Die bisherige TFLite-/Modellvarianten-Beschreibung gilt fuer dieses Unterprojekt nicht mehr. Die aktuelle Android-App nutzt genau ein ONNX-Modell:

- `app/src/main/assets/models/adain.onnx`

Die fruehere `ModelVariant`-Logik wird nicht mehr verwendet.

## Voraussetzungen

- Android Studio mit Gradle-Support
- JDK 17
- Android SDK fuer `compileSdk = 35`
- Testgeraet oder Emulator mit `minSdk = 26`

## Projekt in Android Studio oeffnen

1. Das Unterverzeichnis `android-style-transfer` als eigenes Projekt in Android Studio oeffnen.
2. Gradle-Sync ausfuehren.
3. Einen Emulator oder ein physisches Android-Geraet waehlen.
4. Die App aus dem `app`-Modul starten.

## App verwenden

1. Inhaltsbild auswaehlen oder ein Beispielbild aus `samples/content/` verwenden.
2. Stilbild auswaehlen oder ein Beispielbild aus `samples/styles/` verwenden.
3. Stilstaerke (`alpha`) setzen.
4. Stiltransfer starten.
5. Ergebnis optional in die Galerie speichern.

## Modell- und Bild-Assets

Relevante Assets liegen unter:

- `app/src/main/assets/models/adain.onnx`
- `app/src/main/assets/samples/content/`
- `app/src/main/assets/samples/styles/`

Die Beispielbilder dienen nur als Demo-Inhalt. Fuer echte Nutzung koennen beliebige lokale Bilder als Inhalts- oder Stilbild geladen werden.

## ONNX-Modellvertrag

Das eingebundene Modell erwartet folgende Tensoren:

- `content`: `float32`, Form `[1, 3, H, W]`, Wertebereich `[0, 1]`
- `style`: `float32`, Form `[1, 3, sH, sW]`, Wertebereich `[0, 1]`
- `alpha`: `float32`, Form `[1]`, Wertebereich `0.0` bis `1.0`
- Ausgabe `output`: `float32`, Form `[1, 3, H, W]`, Wertebereich `[0, 1]`

Die Engine skaliert Eingabebilder proportional auf maximal `512` Pixel Kantenlaenge, fuehrt die Inferenz aus und skaliert das Ergebnis anschliessend wieder auf die Originalgroesse des Inhaltsbilds.

## Gesichtsschutz (Face Preservation)

Nach der Inferenz erkennt die App Gesichter im Inhaltsbild mit ML Kit Face Detection. Diese Bereiche werden anschliessend weich in das stilisierte Ergebnis eingeblendet. Das reduziert unerwuenschte Artefakte in Gesichtern, ohne den restlichen Stiltransfer stark zu beeinflussen.

## Speichern und Berechtigungen

- Ab Android 10 (API 29) wird ueber `MediaStore` ohne zusaetzliche Schreibberechtigung gespeichert.
- Fuer Android 8 bis 9 (API 26 bis 28) ist `WRITE_EXTERNAL_STORAGE` mit `maxSdkVersion=28` im Manifest hinterlegt.

## Relevante Quellordner

- `app/src/main/java/com/example/androidstyletransfer/inference/` - ONNX-Inferenz, Bildvorverarbeitung, Gesichtsschutz
- `app/src/main/java/com/example/androidstyletransfer/ui/` - Compose-UI und ViewModel
- `app/src/main/java/com/example/androidstyletransfer/model/` - Bildquellen und Beispielbild-Katalog

## Hinweise fuer Modellwechsel

Wenn du `adain.onnx` ersetzen willst, muss das neue Modell denselben Eingabe-/Ausgabevertrag verwenden oder die Engine in `StyleTransferEngine.kt` entsprechend angepasst werden.

Insbesondere muessen diese Punkte zusammenpassen:

- Input-Namen: `content`, `style`, `alpha`
- Output-Name: `output`
- Normalisierung auf `[0, 1]`
- Kanalreihenfolge `CHW`

## Bezug zum Repository

Das Android-Unterprojekt ist die mobile Umsetzung des Stiltransfer-Demos im Repository. Im Unterschied zu den Notebook-Experimenten ist die Android-App auf direkten Einsatz auf dem Geraet ausgelegt.