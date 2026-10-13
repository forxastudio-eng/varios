// El plugin de Android solo se carga si no estamos en modo "solo lógica pura" (PIANO_CORE_ONLY=1),
// que sirve para ejecutar los tests del núcleo en entornos sin acceso a Google Maven.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        if (System.getenv("PIANO_CORE_ONLY") == null) {
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
}
