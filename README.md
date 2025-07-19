# Iris Lens

Aplicación móvil para personas con discapacidad visual, orientada a la inclusión y la asistencia en tareas cotidianas.

---

## Configuración del proyecto

### OpenCV

Para la integración de OpenCV en el proyecto, es necesario seguir los pasos detallados en la siguiente guía:

* **Android Studio: Step-by-Step Guide for Setting up OpenCV SDK 4.9 on Android:**
  [https://medium.com/@sdranju/android-studio-step-by-step-guide-for-setting-up-opencv-sdk-4-9-on-android-740547f3260b](https://medium.com/@sdranju/android-studio-step-by-step-guide-for-setting-up-opencv-sdk-4-9-on-android-740547f3260b)
  (Shamsuddoha Ranju, 2024)

---

### Dependencias

Las siguientes dependencias deben estar presentes en el archivo `build.gradle` (módulo `:app`) del proyecto:

```gradle
dependencies {
    // OpenCV
    implementation project(':opencv')
    // tesseract (OCR)
    implementation 'cz.adaptech.tesseract4android:tesseract4android:4.6.0'
    // commons-text para calcular similitud de palabras
    implementation 'org.apache.commons:commons-text:1.9'
}
```

---

### Configuración de Tesseract OCR

Para que la dependencia de Tesseract OCR funcione correctamente, se debe añadir el repositorio de JitPack en el archivo settings.gradle del proyecto.

La sección dependencyResolutionManagement en settings.gradle debe contener la siguiente línea dentro de repositories:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // tesseract OCR
        maven { url 'https://jitpack.io' }
    }
}
```