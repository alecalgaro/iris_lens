# Iris Lens

Aplicación móvil para personas con discapacidad visual, orientada a la inclusión y la asistencia en tareas cotidianas.

---

## Configuración del proyecto

### OpenCV

Para la integración de OpenCV en el proyecto, es necesario seguir los pasos detallados en la siguiente guía:

* **Android Studio: Step-by-Step Guide for Setting up OpenCV SDK 4.9 on Android:**
  [https://medium.com/@sdranju/android-studio-step-by-step-guide-for-setting-up-opencv-sdk-4-9-on-android-740547f3260b](https://medium.com/@sdranju/android-studio-step-by-step-guide-for-setting-up-opencv-sdk-4-9-on-android-740547f3260b)
  (Shamsuddoha Ranju, 2024)

### Firebase y Cloud Firestore

Para habilitar los servicios de Firebase en la aplicación y contar con la base de datos remota, seguir la [documentación oficial de Firebase](https://firebase.google.com/docs/android/setup?hl=es) para asegurarse de que los pasos estén actualizados.

1. Ingresar a la [Consola de Firebase](https://console.firebase.google.com/) y crear un nuevo proyecto (o selecciona uno existente).
2. Agregar una nueva aplicación Android al proyecto de Firebase. Ingresar el nombre del paquete correspondiente.
3. Descargar el archivo `google-services.json` que proporciona Firebase.
4. Colocar el archivo `google-services.json` dentro de la carpeta `app` del proyecto (`/app/google-services.json`).
5. Seguir la documentación oficial para completar la configuración en los archivos `build.gradle`.

#### Selección y configuración de Cloud Firestore para almacenar medicamentos y principios activos

Durante la configuración en la consola de Firebase, seleccionar el servicio **Cloud Firestore** como base de datos.

A continuación se muestra un ejemplo del esquema de la base de datos remota en Cloud Firestore, donde se observa la organización en colecciones y documentos. Cada colección contiene documentos identificados por un ID único, y cada documento almacena los datos del medicamento o principio activo correspondiente.

**Ejemplo de estructura:**

- Colección: `medicamentos`
  - Documento: `8TDG7qRajGMcUYWQZhhw`
    - `nombre`: “Decidex Compuesto”
    - `descripcion`: “Contiene clorfenamina, pseudoefedrina y paracetamol. Indicado para el tratamiento sintomático del cuadro gripal que se acompañe de fiebre o dolor y congestión nasal, sinusal u ocular.”
- Colección: `principios_activos`
  - Documento: `6b2h9uLJhdfLSqAF23rd`
    - `nombre`: “Clorfenamina”
  - Documento: `GfrDhreohb9sX5lMkQhr`
    - `nombre`: “Pseudoefedrina”
  - Documento: `unOvzftW8atqxBzNnGEP`
    - `nombre`: “Paracetamol”

Consultar la [documentación de Cloud Firestore](https://firebase.google.com/docs/firestore?hl=es) para más detalles sobre la gestión de colecciones y documentos.

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