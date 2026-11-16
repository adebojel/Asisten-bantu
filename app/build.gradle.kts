import java.net.URL
import java.net.URLConnection
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

        val geminiApiKey = (System.getenv("GEMINI_API_KEY") ?: "")
            .trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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

    // LiteRT-LM 0.16.1: compatible with the project Kotlin 2.2.x compiler; runs external .litertlm models.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

    // Pengujian
    testImplementation("junit:junit:4.13.2")
}


// Qwen3 is downloaded once into the Gradle build cache and packaged as an APK asset.
// The binary is intentionally not committed to Git; release builds remain reproducible
// against the pinned Hugging Face artifact URL and validate its expected size.
val qwenModelFileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
val qwenAssetDirectory = layout.buildDirectory.dir("generated/qwenModelAssets")
val downloadQwenModel by tasks.registering {
    val outputFile = qwenAssetDirectory.map { it.file(qwenModelFileName) }
    outputs.file(outputFile)
    doLast {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        if (target.isFile && target.length() in (250L * 1024L * 1024L)..(450L * 1024L * 1024L)) {
            logger.lifecycle("Qwen3 model already cached: ${target.length()} bytes")
        } else {
            val temporary = File(target.parentFile, target.name + ".download")
            temporary.delete()
            val modelUrl = System.getenv("KOBRA_QWEN_MODEL_URL")?.takeIf { it.isNotBlank() }
                ?: "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm?download=true"
            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    logger.lifecycle("Downloading bundled Qwen3 model (attempt $attempt/3)")
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
                    require(size in (250L * 1024L * 1024L)..(450L * 1024L * 1024L)) {
                        "Downloaded Qwen3 model has unexpected size: $size bytes"
                    }
                    if (target.exists()) target.delete()
                    check(temporary.renameTo(target)) { "Could not move downloaded Qwen3 model into asset directory" }
                    lastError = null
                    break
                } catch (error: Exception) {
                    lastError = error
                    temporary.delete()
                    if (attempt < 3) Thread.sleep(attempt * 2_000L)
                }
            }
            if (lastError != null) throw GradleException("Unable to download bundled Qwen3 model", lastError)
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(qwenAssetDirectory.get().asFile)
tasks.named("preBuild").configure { dependsOn(downloadQwenModel) }
