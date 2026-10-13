plugins {
    id("org.jetbrains.kotlin.jvm")
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

// En el sandbox de desarrollo Maven Central a veces limita las descargas: OMR_OPENCV_JAR apunta a un JAR ya descargado.
val openCv: Any = System.getenv("OMR_OPENCV_JAR")?.let { files(it) } ?: "org.openpnp:opencv:4.9.0-0"

dependencies {
    api(project(":core"))
    // Misma API en el PC (pruebas) y en Android: la app usa onnxruntime-android y el OpenCV de Android.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    compileOnly(openCv)
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    testImplementation(openCv)
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
