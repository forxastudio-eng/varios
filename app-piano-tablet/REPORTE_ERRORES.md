# Reporte de errores y posibles fallas — Piano Cascada v0.1

Revisión parte por parte del código (motor de audio C++, puente JNI, controlador de práctica, vista de la cascada,
lógica de puntuación, importadores, servidor, pruebas). Gravedad: **A** = rompe una función central · **M** = se nota al
usar la app · **B** = menor / de pulido.

Estado: ✅ reparado en este mismo ciclo · 📝 aceptado y documentado · ⚠️ pendiente de comprobar en la tablet real.

## Resumen

| # | Parte | Gravedad | Problema | Estado |
|---|---|---|---|---|
| 1 | Vista (`PianoSurfaceView.release`) | **A** | Al levantar el dedo la tecla **se quedaba iluminada para siempre**: `release()` comprobaba si «otro dedo sigue en la tecla» mirando la tabla de punteros antes de borrar el del propio dedo, y siempre se encontraba a sí mismo. | ✅ |
| 2 | Vista | M | Si Android perdía un `ACTION_UP` (cambio de app, gesto del sistema) el puntero quedaba registrado y la nota sonaba/brillaba sin parar; al volver a pulsar con el mismo id se sobrescribía sin soltar la anterior. | ✅ |
| 3 | Controlador | **A** | Al pasar a **Nivel 1** estando en «Izquierda» quedaba una sesión **sin notas que tocar** (el nivel 1 solo tiene mano derecha y el botón de manos se oculta): la canción se «terminaba» sola con 0 estrellas. | ✅ |
| 4 | Controlador | M | Pulsar teclas con la canción en pausa puntuaba (y podía contar fallos). Ahora en pausa solo suena. | ✅ |
| 5 | Controlador | M | El final de la práctica se detectaba **al pulsar la última nota** (comparaba con su *inicio*): salía el resultado cortando la nota y sin medir si la mantenías. Ahora espera al final de la nota (máx. 0,8 s). | ✅ |
| 6 | Controlador | B | Mano sin notas (sesión con 0 notas) mostraba «Sigue practicando» y guardaba un 0 %. Ahora no puntúa ni guarda. | ✅ |
| 7 | Audio (`PianoAudio.monitor`) | M | Si al reiniciar la salida de audio (auriculares/Bluetooth desconectados) `nStart()` fallaba, **no se volvía a intentar** y la app quedaba muda hasta salir y entrar. Ahora reintenta cada 250 ms. | ✅ |
| 8 | Audio (`AudioStream`) | B | El búfer de salida era fijo (2 ráfagas); en un dispositivo cargado podría haber chasquidos. Ahora se agranda automáticamente si aparecen *xruns*. | ✅ |
| 9 | Importación de audio | M | Un MP3 largo a 48 kHz (20 min ≈ 230 MB en `float`) podía agotar la memoria de la app sin `largeHeap`. Se activa `largeHeap` y se limita a 15 min. | ✅ |
| 10 | Importación | M | Si el resultado no tenía notas (audio silencioso, PDF ilegible, MIDI vacío) se ofrecía «Guardar» una canción vacía. Ahora da un error claro. | ✅ |
| 11 | `AppRoot` | B | `Screen.Detail` escribía el estado durante la composición (`if (song == null) screen = …`). Funciona pero Compose lo desaconseja y puede provocar un parpadeo. Ahora se hace en un `LaunchedEffect`. | ✅ |
| 12 | Biblioteca | B | Truco `width300()` por un import que faltaba. | ✅ |
| 13 | Ajustes | B | Un `split`/`reducedSpan` corrupto en las preferencias haría fallar `KeyboardPlanner.twoRows` (`require`) al abrir el teclado de 2 filas. Ahora se validan al leer. | ✅ |
| 14 | Biblioteca | B | Las canciones propias salían en orden arbitrario (el que devuelve `listFiles`). Ahora por fecha de creación (el id lleva la hora). | ✅ |
| 15 | Pruebas (`CoreTests`) | M | Dos pruebas **no comprobaban nada** (`sequenceMatchesSongTiming` usaba un `let` que siempre da el resultado esperado; `blackKeysSitOnTopOfWhiteBoundaries` terminaba con `if (it == 62) 62 else 62`). Reescritas (ahora comparan cada nota con el tiempo esperado), más una prueba que documenta la causa del error 3. Los errores 1–2 y 4–6 viven en código de Android (vista/controlador) que no se puede ejecutar en las pruebas de PC: se comprueban al compilar y en la tablet (⚠️ 20). Pruebas del núcleo: 38, todas pasan. | ✅ |
| 16 | Motor C++ | B | Si dos `loadSequence` llegan antes de que el hilo de control libere el primero, el primero se pierde (fuga de unos KB). No ocurre con el uso normal (una canción cada vez). | 📝 |
| 17 | Vista | B | Durante el desplazamiento de la ventana del teclado reducido se reconstruye `KeyRow` en cada fotograma (pequeñas asignaciones). Solo mientras se desliza (~0,4 s). | 📝 |
| 18 | Vista | B | `drawGlow` crea un `PorterDuffColorFilter` por destello. Unos pocos objetos por fotograma; no se aprecia. | 📝 |
| 19 | Transcriptor | M | Error de compás/fase en 3 de 8 piezas de la evaluación y reparto de manos ≈ 86 %. Es la propuesta automática: la pantalla de revisión lo avisa y se puede escuchar antes de guardar. | 📝 |
| 20 | Todo el Android | ⚠️ | **No se ha podido probar en una tablet real** (solo compilar en CI y probar la lógica en JVM/PC): latencia real, tacto, rendimiento a 120 Hz y apariencia final deben comprobarse en la Tab S8 Ultra. | ⚠️ |

## Detalle de los más importantes

### 1. Teclas que no se apagaban (A)
```kotlin
// antes
release(ptrPitch[id], l); ptrPitch[id] = -1      // release() mira ptrPitch y se ve a sí mismo
// ahora
val pitch = ptrPitch[id]; ptrPitch[id] = -1; release(pitch, l)
```
Afectaba también al modo «deslizar» y a `ACTION_CANCEL`. Como el sonido se cortaba bien (`onKeyUp` sí se llamaba),
el fallo solo era visual: la luz de la tecla quedaba encendida y la nota *fantasma* se quedaba pintada en la
cascada como pulsada.

### 3. Nivel 1 con mano izquierda (A)
`changeLevel(1)` mantenía `hands = LEFT`. En el nivel 1 solo existe la melodía (mano derecha), así que
`PracticeSession` recibía 0 notas, `isFinished()` era cierto desde el primer instante y la canción se «terminaba».
Ahora el nivel 1 fuerza «Ambas» (que equivale a la derecha porque solo hay esa mano).

### 5. Fin de la práctica
`lastNoteEnd()` devolvía el *inicio* de la última nota. Se calcula ahora `inicio + min(duración, 800 ms)`.

## Qué no se ha revisado a fondo
- `Onboarding.kt`, `SettingsScreen.kt`, `Components.kt`, `Theme.kt`, `Palette.kt`: solo hojeados (interfaz sin lógica crítica).
- El módulo `transcribe` ya está cubierto por el banco de evaluación (`Eval.kt`); no se ha vuelto a auditar línea a línea.
