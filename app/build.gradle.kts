import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest
plugins {
    id("com.android.application")
}

android {
    namespace = "com.asisten.bantu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.asisten.bantu"
        minSdk = 28
        targetSdk = 36
        versionCode = 9
        versionName = "7.2-KOBRA-UNIFIED"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Release artifact targets current 64-bit Android phones; omit unused x86/32-bit native payloads.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        // Kept only for source compatibility; always blank in offline-only builds.
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")

    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }

    androidResources {
        noCompress += listOf("task", "tflite", "bin", "lite", "litertlm")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX, CameraX & Material Design (stable releases aligned with the current Android toolchain)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Kotlin Coroutines & Network Client (Gemini / AI Studio REST Integration)
    // LiteRT-LM 0.16.1 calls the SendChannel.close$default interface bridge introduced in 1.11.0.
    // Keep Android and core coroutines aligned to prevent NoSuchMethodError after local inference.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // CameraX 1.6.2 (stable)
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    // Google ML Kit (latest stable bundled-model APIs; offline vision, barcode, translation & OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // Retained for legacy OfflineVision type references; no runtime detector client is created.
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:translate:17.0.3")

    // MediaPipe LLM Inference API (Pengolahan Bahasa Offline On-Device Gemma 3 1B)
    implementation("com.google.mediapipe:tasks-genai:0.10.14")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    implementation("org.maplibre.gl:android-sdk:13.7.1")

    // LiteRT-LM 0.16.1: compatible with the project Kotlin 2.2.x compiler; runs external .litertlm models.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

    // Pengujian
    testImplementation("junit:junit:4.13.2")
}


// Qwen3 is intentionally kept out of Git and fetched from an immutable upstream revision.
// The build verifies exact byte length and SHA-256 for both cached and freshly downloaded files.
val qwenModelFileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
val qwenModelExpectedSize = 347_251_840L
val qwenModelExpectedSha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
val qwenModelRevision = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7"
val qwenAssetDirectory = layout.buildDirectory.dir("generated/qwenModelAssets")

fun qwenSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

val downloadQwenModel by tasks.registering {
    val outputFile = qwenAssetDirectory.map { it.file(qwenModelFileName) }
    outputs.file(outputFile)
    doLast {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        if (target.isFile && target.length() == qwenModelExpectedSize
            && qwenSha256(target).equals(qwenModelExpectedSha256, ignoreCase = true)) {
            logger.lifecycle("Qwen3 model cache verified: ${target.length()} bytes, SHA-256 OK")
        } else {
            val temporary = File(target.parentFile, target.name + ".download")
            temporary.delete()
            val modelUrl = System.getenv("KOBRA_QWEN_MODEL_URL")?.takeIf { it.isNotBlank() }
                ?: "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/${qwenModelRevision}/${qwenModelFileName}?download=true"
            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    logger.lifecycle("Downloading pinned Qwen3 model (attempt $attempt/3)")
                    val parsedModelUrl = URL(modelUrl)
                    require(parsedModelUrl.protocol.equals("https", ignoreCase = true)) {
                        "Qwen model URL must use HTTPS; refusing insecure download"
                    }
                    val connection: URLConnection = parsedModelUrl.openConnection().apply {
                        connectTimeout = 30_000
                        readTimeout = 180_000
                        setRequestProperty("User-Agent", "KobraOS-Android-Build")
                    }
                    connection.getInputStream().use { input ->
                        require(connection.url.protocol.equals("https", ignoreCase = true)) {
                            "Qwen model download redirected away from HTTPS; refusing insecure response"
                        }
                        temporary.outputStream().buffered(1024 * 1024).use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val size = temporary.length()
                    require(size == qwenModelExpectedSize) {
                        "Downloaded Qwen3 model size mismatch: expected $qwenModelExpectedSize, got $size bytes"
                    }
                    val actualSha256 = qwenSha256(temporary)
                    require(actualSha256.equals(qwenModelExpectedSha256, ignoreCase = true)) {
                        "Downloaded Qwen3 model SHA-256 mismatch: $actualSha256"
                    }
                    if (target.exists()) check(target.delete()) { "Could not replace invalid cached Qwen3 model" }
                    check(temporary.renameTo(target)) { "Could not move verified Qwen3 model into asset directory" }
                    lastError = null
                    logger.lifecycle("Pinned Qwen3 model verified: $size bytes, SHA-256 OK")
                    break
                } catch (error: Exception) {
                    lastError = error
                    temporary.delete()
                    if (attempt < 3) Thread.sleep(attempt * 2_000L)
                }
            }
            if (lastError != null) throw GradleException("Unable to download and verify pinned Qwen3 model", lastError)
        }
    }
}

val objectDetectorModelFileName = "efficientdet_lite0.tflite"
val objectDetectorExpectedSize = 13_836_895L
val objectDetectorExpectedSha256 = "40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58"
val objectDetectorAssetDirectory = layout.buildDirectory.dir("generated/objectDetectorAssets")

val downloadObjectDetectorModel by tasks.registering {
    val outputFile = objectDetectorAssetDirectory.map { it.file(objectDetectorModelFileName) }
    outputs.file(outputFile)
    doLast {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        if (target.isFile && target.length() == objectDetectorExpectedSize
            && qwenSha256(target).equals(objectDetectorExpectedSha256, ignoreCase = true)) {
            logger.lifecycle("EfficientDet-Lite0 cache verified: ${target.length()} bytes, SHA-256 OK")
        } else {
            val temporary = File(target.parentFile, target.name + ".download")
            temporary.delete()
            val modelUrl = "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite"
            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    logger.lifecycle("Downloading pinned EfficientDet-Lite0 model (attempt $attempt/3)")
                    val parsedUrl = URL(modelUrl)
                    require(parsedUrl.protocol.equals("https", ignoreCase = true)) { "Object detector URL must use HTTPS" }
                    val connection: URLConnection = parsedUrl.openConnection().apply {
                        connectTimeout = 30_000
                        readTimeout = 120_000
                        setRequestProperty("User-Agent", "KobraOS-Android-Build")
                    }
                    connection.getInputStream().use { input ->
                        require(connection.url.protocol.equals("https", ignoreCase = true)) { "Object detector redirect must remain HTTPS" }
                        temporary.outputStream().buffered(1024 * 1024).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(temporary.length() == objectDetectorExpectedSize) {
                        "EfficientDet-Lite0 size mismatch: expected $objectDetectorExpectedSize, got ${temporary.length()}"
                    }
                    val actualSha256 = qwenSha256(temporary)
                    require(actualSha256.equals(objectDetectorExpectedSha256, ignoreCase = true)) {
                        "EfficientDet-Lite0 SHA-256 mismatch: $actualSha256"
                    }
                    if (target.exists()) check(target.delete()) { "Could not replace invalid object detector cache" }
                    check(temporary.renameTo(target)) { "Could not move verified object detector into assets" }
                    lastError = null
                    logger.lifecycle("EfficientDet-Lite0 verified: ${target.length()} bytes, SHA-256 OK")
                    break
                } catch (error: Exception) {
                    lastError = error
                    temporary.delete()
                    if (attempt < 3) Thread.sleep(attempt * 2_000L)
                }
            }
            if (lastError != null) throw GradleException("Unable to download and verify EfficientDet-Lite0", lastError)
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(qwenAssetDirectory.get().asFile)
android.sourceSets.getByName("main").assets.srcDir(objectDetectorAssetDirectory.get().asFile)
android.sourceSets.getByName("main").java.srcDirs(
    rootProject.file("third_party/brouter/brouter-core/src/main/java"),
    rootProject.file("third_party/brouter/brouter-mapaccess/src/main/java"),
    rootProject.file("third_party/brouter/brouter-expressions/src/main/java"),
    rootProject.file("third_party/brouter/brouter-codec/src/main/java"),
    rootProject.file("third_party/brouter/brouter-util/src/main/java")
)
android.sourceSets.getByName("main").assets.srcDir(
    rootProject.file("third_party/brouter/misc/profiles2")
)
tasks.named("preBuild").configure { dependsOn(downloadQwenModel, downloadObjectDetectorModel) }
