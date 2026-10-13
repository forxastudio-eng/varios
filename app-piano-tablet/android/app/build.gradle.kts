plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.forxa.pianocascada"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.forxa.pianocascada"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Galaxy Tab S8 Ultra = arm64; el emulador de las pruebas (-PemulatorAbi) usa x86_64
        ndk { abiFilters += listOf("arm64-v8a") + (if (project.hasProperty("emulatorAbi")) listOf("x86_64") else emptyList()) }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Uso personal: se firma con la clave de depuración para poder instalarlo directamente.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    androidResources { noCompress += listOf("bin", "onnx", "json") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    implementation(project(":core"))
    implementation(project(":transcribe"))
    implementation(project(":omr"))
    // OpenCV para Android (el módulo omr solo lo declara; en el PC las pruebas usan el de openpnp)
    implementation("com.quickbirdstudios:opencv:4.5.3")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
