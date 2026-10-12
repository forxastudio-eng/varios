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

dependencies {
    api(project(":core"))
    // La misma API de ONNX Runtime sirve en el PC (pruebas) y en Android (la app usa onnxruntime-android).
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Evaluación local del transcriptor con audio de referencia (ver tools/make_eval_audio.py):
//   EVAL_DIR=../../tools/eval_data EVAL_MODEL=../app/src/main/assets/basic_pitch.onnx ./gradlew :transcribe:evalTranscription
tasks.register<JavaExec>("evalTranscription") {
    group = "verification"
    description = "Mide la precisión del transcriptor (F1 de notas, tempo, tonalidad, manos)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.forxa.pianocascada.transcribe.EvalKt")
    maxHeapSize = "3g"
}
