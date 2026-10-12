pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PianoCascada"
include(":core")
include(":transcribe")
// En entornos sin acceso a Google Maven (p. ej. el sandbox de desarrollo) se puede probar solo la lógica pura:
//   PIANO_CORE_ONLY=1 ./gradlew :core:test
if (System.getenv("PIANO_CORE_ONLY") == null) {
    include(":app")
}
