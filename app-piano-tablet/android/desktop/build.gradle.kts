import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Los mismos archivos que la app de la tablet: banco de piano, canciones, modelos de lectura de partituras y de audio
sourceSets.main {
    resources.srcDir("../app/src/main/assets")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))
    implementation(project(":transcribe"))
    implementation(project(":omr"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    implementation("org.openpnp:opencv:4.9.0-0")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "3g"
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

compose.desktop {
    application {
        mainClass = "com.forxa.pianocascada.desktop.MainKt"
        jvmArgs += listOf("-Xmx3g")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "PianoCascada"
            packageVersion = "1.0.0"
            includeAllModules = true
            description = "Aprende piano con notas que caen"
            windows { menuGroup = "Piano Cascada"; shortcut = true }
            linux { shortcut = true }
        }
    }
}
