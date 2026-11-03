import java.util.concurrent.TimeUnit

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** IP Wi-Fi do Mac na hora do build — vira o backend padrão do relógio. */
fun macLanIp(): String = try {
    val p = ProcessBuilder("ipconfig", "getifaddr", "en0").redirectErrorStream(true).start()
    p.waitFor(3, TimeUnit.SECONDS)
    p.inputStream.bufferedReader().readText().trim()
        .takeIf { Regex("""\d+\.\d+\.\d+\.\d+""").matches(it) } ?: "127.0.0.1"
} catch (e: Exception) {
    "127.0.0.1"
}

val backendUrl: String = (findProperty("harnessBackend") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: "http://${macLanIp()}:8765"

android {
    namespace = "com.allanmelo.harness"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.allanmelo.harness"
        minSdk = 28
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DEFAULT_BACKEND", "\"$backendUrl\"")
        resourceConfigurations += listOf("en", "pt")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assinado com a chave debug local: instalação manual via adb, sem Play Store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation("androidx.wear:wear:1.2.0")
    implementation("androidx.wear.tiles:tiles:1.1.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
}

println("tunny-harness: DEFAULT_BACKEND = $backendUrl")
