# Plan de desarrollo: app de piano para tablet ("Piano Cascada")

> Nombre provisional: **Piano Cascada**
> Dispositivo: **Samsung Galaxy Tab S8 Ultra**, siempre en horizontal
> Uso: **personal** (por ahora una sola persona, sin publicar en tiendas)
> Público: **personas que no son músicos y dan sus primeros pasos**
> Fecha: 12/10/2026 · Estado: **borrador v2**

---

## Cambios respecto a la v1

| Tema | v1 | v2 (esta versión) |
|---|---|---|
| Ecualización | Había que validar qué significaba "ecualizar" | **Se incluyen los dos**: ecualizador de **sonido** (§13) y ecualizador de **teclado** (§14) |
| Nombres de notas | Inglés, latino, números… | **Solo letras: C D E F G A B** (§11) |
| Sonido y fluidez | Se proponían opciones | **Decisión cerrada**: motor de muestras propio en C++ y render a 120 fps (§4 y §5) |
| Uso | Comercial, con tiendas y suscripción | **Personal**: sin cuentas, sin tienda, sin cobros, instalación directa (§20) |
| Diseño | Estética neón genérica | **Premium minimalista con luz en las teclas** y sistema de diseño completo (§8 y §9) |
| Público | Todos los niveles | **No músicos, principiantes**: sin jerga, simplificador de canciones, camino de aprendizaje y coach (§7.5, §16 y §17) |
| Procesado de MP3/PDF | Backend en la nube | **En la tablet, más un "servidor en casa" opcional** en tu PC (§7.4) |

---

## Estado de la implementación (primer APK)

Lo que **ya existe** en este repositorio frente a lo planificado abajo:

| Área | Estado |
|---|---|
| Motor de sonido C++ (sampler de piano FluidR3 «Yamaha Grand», pedal, ecualizador de 5 bandas, reverb, limitador, secuenciador con reloj de audio) | ✅ hecho y probado en PC |
| Cascada + teclados (2 filas de 26 teclas blancas y reducido), luz en las teclas, letras C D E…, 120 Hz | ✅ hecho (⚠️ sin probar en tablet real) |
| Ecualizador de sonido (sencillo + avanzado, 5 timbres, sala) y ecualizador de teclado (curva, zonas, tamaño, deslizar) | ✅ |
| Modos de práctica: Espera, Ritmo, Escuchar; niveles 1–3; manos; velocidad; bucle de 4 compases; solo teclas blancas | ✅ |
| Tutorial inicial guiado | ✅ |
| **Biblioteca con 17 piezas verificadas** (9 con partitura exacta del corpus de music21; 8 transcripciones de alta confianza) | ✅ (el plan hablaba de 50: se priorizó la exactitud; ver `LICENCIAS.md`) |
| Importar **audio** (MP3/M4A/WAV…): decodificación en la tablet + Basic Pitch (ONNX) + filtro de calidad entrenado, tempo, compás, tonalidad, manos | ✅ F1 de notas ≈ 0,885 en el banco de pruebas |
| Importar **MIDI** (p. ej. «Claro de luna») | ✅ |
| Importar **PDF / foto**: servidor en casa (Audiveris + music21) | ✅ servidor y cliente; la precisión se mide con el flujo `omr-eval` de GitHub Actions |
| Piezas **«Claro de luna»** y similares | Se incluirá cuando se aporte el MIDI/MusicXML (por la decisión de usar solo material verificado) |
| Revisión de errores | ✅ ver `REPORTE_ERRORES.md` |

Diferencias con el plan original: la app es nativa Android (Kotlin + Compose + C++), no multiplataforma; la importación de audio
se hace **en la tablet** (sin servidor); solo el PDF/foto necesita el servidor.

## 0. Resumen

Una app de piano para tablet pensada para **aprender desde cero sin saber música**:

- Importas una **canción (MIDI, MusicXML, PDF, foto o MP3)**.
- La app la convierte en **notas de colores que caen** hacia el teclado. Cada nota lleva su **letra (C, D, E…)** y la **longitud de la barra es su duración**.
- Si la canción es difícil, la app **la simplifica** para tu nivel.
- Mientras tocas, **la tecla correcta se ilumina**. La app **te espera** si no llegas y **te dice qué tal lo hiciste**, sin tecnicismos.
- Sonido de **piano de cola real** (muestras grabadas) con muy poca latencia, y animaciones fluidas a **120 fps**.
- Dos formas de ver el teclado:
  - **Completo**: las 88 teclas partidas en **dos filas iguales**.
  - **Reducido**: teclas grandes, casi del tamaño de un piano real.
- **Dos ecualizadores**: uno para el **sonido** y otro para la **respuesta del teclado**.
- **Tutorial de inicio guiado** y un **camino de lecciones cortas** para avanzar paso a paso.

---

## 1. Principios del producto

Todas las decisiones de diseño se comprueban contra estos cinco principios:

1. **Cero jerga musical.** Nada de "corchea", "clave de fa" ni "sostenido" sin explicar. Se habla de *nota larga / nota corta*, *mano derecha / mano izquierda* y *tecla negra de la derecha*. Las letras C‑D‑E son lo único "técnico", y se enseñan en el primer minuto.
2. **Éxito en los primeros 5 minutos.** La primera vez que abres la app tocas una melodía completa.
3. **Calma premium.** La interfaz es oscura, limpia y con pocos elementos. **La luz está en las teclas y en las notas**, no en los botones. Mientras tocas, todo lo demás desaparece.
4. **Ergonomía primero.** Los controles quedan cerca de las manos, son grandes y se pueden pulsar sin mirar mucho. Nada obliga a soltar el teclado durante mucho tiempo.
5. **Fluidez y sonido sin concesiones.** 120 fps constantes y una respuesta del sonido por debajo de 25 ms. Si una función pone en riesgo esto, no entra.

---

## 2. Dispositivo: Galaxy Tab S8 Ultra

| Característica | Valor | Qué implica para la app |
|---|---|---|
| Pantalla | 14,6" Super AMOLED, 2960 × 1848 px, 16:10 | En horizontal hay **≈ 314 mm de ancho** y **≈ 196 mm de alto** (≈ 9,4 px/mm). |
| Frecuencia | 120 Hz | La cascada se dibuja a **120 fps** (8,3 ms por frame). |
| SoC | Snapdragon 8 Gen 1 | Hay potencia de sobra para muestras de alta calidad, reverb por convolución y modelos de IA en la tablet. |
| RAM | 8 / 12 / 16 GB | Las muestras se precargan en parte y el resto se lee del disco (*streaming*). |
| Audio | 4 altavoces AKG + USB‑C | Baja latencia con AAudio/Oboe y un perfil específico para los altavoces. |
| Táctil | Multitáctil de 10 puntos | Se pueden tocar acordes con las dos manos. |
| S Pen | Incluido | Anotar las canciones (fase posterior). |
| SO | Android 12+ (One UI) | `minSdk 31`, `targetSdk` siempre la última. |

**Cálculo de tamaños de tecla** (88 teclas, de las cuales 52 son blancas):

| Diseño | Teclas blancas visibles | Ancho por tecla blanca | ¿Se puede tocar? |
|---|---|---|---|
| 88 teclas en 1 fila | 52 | 6 mm | ❌ Demasiado pequeña |
| **88 teclas en 2 filas** | 26 por fila | **12,1 mm** | ✅ Cómodo |
| **Reducido de 2 octavas (C3–C5)** | 15 | **≈ 21 mm** | ✅ Casi como un piano real (23,5 mm) |
| Reducido de 3 octavas | 22 | ≈ 14 mm | ✅ |

---

## 3. Stack tecnológico (decisión cerrada)

Pedías buen sonido y buena fluidez, así que la elección es una **app nativa Android con el audio en C++ y la animación por GPU**. Flutter y React Native no se usan porque añaden latencia y saltos en la animación.

| Capa | Tecnología | Motivo |
|---|---|---|
| Interfaz (menús, biblioteca, ajustes, lecciones) | **Kotlin + Jetpack Compose** + Material 3 muy personalizado | Moderno, con animaciones suaves y adaptado a pantallas grandes |
| Cascada y teclado | **OpenGL ES 3.2** en un `SurfaceView` propio, sincronizado con `Choreographer` | 120 fps estables con brillo y luz, sin pasar por Compose |
| Motor de audio | **C++17 + Oboe** (AAudio en modo MMAP exclusivo, 48 kHz, float) | Es la latencia más baja que permite Android |
| Sampler | **Sampler SFZ propio** (§4) | Control total sobre la calidad: capas de velocidad, notas al soltar, resonancia |
| Efectos | EQ con filtros biquad + **reverb por convolución** + limitador | Sonido de sala real sin que se sature |
| Entrada MIDI | `android.media.midi` (USB y Bluetooth LE) | Por si algún día conectas un piano digital |
| Micrófono | **Basic Pitch** (TFLite) | Para practicar con un piano acústico (fase posterior) |
| Partitura (opcional) | **Verovio** (C++) | Mostrar el pentagrama para quien quiera aprender a leerlo |
| Datos | **Room** (SQLite) + **DataStore** | Biblioteca, progreso y ajustes, todo dentro de la tablet |
| Inyección de dependencias | **Hilt** | Estándar en Android |
| Servidor en casa (opcional) | **Python + FastAPI en Docker** en tu PC | Transcripción de MP3 de alta calidad y lectura de PDF (§7.4) |

---

## 4. Motor de sonido: cómo conseguir un buen sonido

### 4.1 Muestras de piano

- **Piano principal: Salamander Grand Piano V3** (un Yamaha C5 de cola).
  - Grabado a 48 kHz / 24 bits con **16 capas de velocidad**: suena distinto si tocas suave o fuerte, no solo más bajo.
  - Incluye **muestras de liberación** (el sonido al soltar la tecla) y ruidos de pedal.
- **Pianos alternativos**, para el selector de timbre:
  - Un piano vertical cálido (por ejemplo *Upright Piano KW*, licencia CC0).
  - Un piano eléctrico tipo Rhodes.
  - Al ser de uso personal, la app también permite **cargar cualquier SFZ** que tengas.
- **Formato en la tablet**: FLAC descomprimido al instalar a PCM de 24 bits empaquetado (o float16).
- **Precarga y streaming**:
  - Se cargan en RAM los primeros **≈ 100 ms de cada muestra** (≈ 150 MB).
  - El resto se lee del disco en un hilo aparte. La memoria UFS 3.1 de la tablet lo permite sin cortes.
  - Es la técnica que usan los samplers profesionales.

### 4.2 Calidad del motor

| Característica | Detalle |
|---|---|
| Polifonía | 128 voces. Si se llenan, se sustituyen primero las notas en *release* más suaves |
| Capas de velocidad | Fundido entre capas vecinas, para que no haya "saltos" de timbre |
| Liberación | Muestra específica de *release* al soltar, con una caída natural |
| Pedal de sustain | Mantiene las notas y añade **resonancia simpática** (las cuerdas libres vibran por simpatía), simulada con un banco de resonadores |
| Medio pedal | Con un pedal MIDI continuo |
| Reverb | **Convolución** con respuestas de impulso de salas reales (librería OpenAIR, CC) |
| Afinación | La4 = 440 Hz por defecto, ajustable |
| Salida | Bus interno en float de 32 bits → EQ → reverb → **limitador suave** (evita la distorsión en los altavoces) |
| Perfil de altavoces | Compensa los graves y añade una compresión ligera cuando suena por los altavoces de la tablet. Con auriculares el sonido es plano. Se detecta automáticamente. |

### 4.3 Latencia (objetivos)

| Ruta | Objetivo |
|---|---|
| Del toque en pantalla al sonido | **< 25 ms** |
| De piano MIDI al sonido | **< 12 ms** |
| Auriculares Bluetooth | 150–250 ms (es el límite del Bluetooth): la app avisa y recomienda USB‑C o los altavoces |

Cómo se consigue: el evento táctil pasa al hilo de audio por una **cola sin bloqueos** (*lock‑free*), el buffer es de 2 *bursts* y el hilo de audio nunca reserva memoria ni espera a otros hilos.

---

## 5. Fluidez: cómo conseguir 120 fps estables

- **Un solo *draw call* para todas las notas** (*instancing*): la GPU dibuja miles de barras de golpe.
- **Las notas se precalculan** en buffers de la GPU al abrir la canción. Por frame solo se actualiza la posición del tiempo.
- **Brillo y luz a media resolución** con difuminado separable: queda bonito y es barato.
- **Cero reservas de memoria por frame** en el hilo de render, así el recolector de basura de Java no provoca tirones.
- **El reloj maestro es el audio** (`AAudioStream_getTimestamp`): lo que ves y lo que oyes nunca se desincronizan (menos de 5 ms de deriva).
- **Compose** con *Baseline Profiles* y R8, para que los menús abran al instante.
- Cuando está en **pausa**, la pantalla baja a 60 Hz para ahorrar batería.
- **Objetivo medible**: el 99 % de los frames por debajo de 8,3 ms con canciones de 2000 notas.

---

## 6. Arquitectura

```
┌──────────────────────────────────────────────────────────────────┐
│                       APP ANDROID (tablet)                       │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ UI Compose   │  │ Biblioteca   │  │ Tutorial + Tu camino   │  │
│  │ (inicio,     │  │ + progreso   │  │ (lecciones + coach)    │  │
│  │  ajustes)    │  │ (Room)       │  │                        │  │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘  │
│         │         ┌───────▼──────────────────────▼──────┐        │
│         └────────►│        MODELO DE CANCIÓN (Song)     │        │
│                   └───┬─────────────┬───────────────────┘        │
│      ┌────────────────▼──┐   ┌──────▼─────────────┐              │
│      │ IMPORTADORES +    │   │ TRANSPORTE / RELOJ │◄──┐          │
│      │ SIMPLIFICADOR     │   │ (espera, bucle,    │   │          │
│      │ (MIDI, MusicXML,  │   │  velocidad)        │   │          │
│      │  MP3 en la tablet)│   └──┬──────────┬──────┘   │          │
│      └────────┬──────────┘      │          │          │          │
│               │       ┌─────────▼───┐ ┌────▼────────┐ │          │
│               │       │ RENDER GL   │ │ AUDIO C++   │ │          │
│               │       │ cascada +   │ │ sampler, EQ │ │          │
│               │       │ teclado con │ │ sonido,     │ │          │
│               │       │ luz         │ │ reverb      │ │          │
│               │       └─────────────┘ └─────▲───────┘ │          │
│               │                             │         │          │
│               │       ┌─────────────────────┴───┐  ┌──┴───────┐  │
│               │       │ ENTRADAS + EQ TECLADO   │─►│EVALUADOR │  │
│               │       │ táctil · MIDI · micro   │  │ + COACH  │  │
│               │       └─────────────────────────┘  └──────────┘  │
└───────────────┼──────────────────────────────────────────────────┘
                │ Wi‑Fi de casa (opcional: MP3 de alta calidad y PDF)
┌───────────────▼──────────────────────────────────────────────────┐
│   SERVIDOR EN CASA (Docker en tu PC)                             │
│   · MP3 → notas (modelo de alta precisión + separación Demucs)   │
│   · PDF/foto → MusicXML (Audiveris)                              │
└──────────────────────────────────────────────────────────────────┘
```

### 6.1 Modelo de datos

```kotlin
data class Song(
    val id: String,
    val title: String,
    val source: SourceType,          // MIDI, MUSICXML, PDF, IMAGE, AUDIO, LESSON
    val tempoMap: List<TempoChange>,
    val timeSignatures: List<TimeSig>,
    val key: Key?,
    val tracks: List<Track>,
    val durationMs: Long,
    val versions: Map<Level, SongVersion>, // versiones simplificadas (§7.5)
)

data class Note(
    val pitch: Int,          // MIDI 21 (A0) … 108 (C8)
    val startMs: Long,
    val durationMs: Long,    // longitud de la barra
    val velocity: Int,
    val hand: Hand,          // LEFT / RIGHT
    val finger: Int?,        // 1..5
    val measure: Int,
)
```

---

## 7. Importar canciones

### 7.1 Formatos

| Entrada | Dónde se procesa | Calidad | Comentario |
|---|---|---|---|
| **MIDI (.mid)** | Tablet | ★★★★★ | La forma más fiable. MuseScore y muchas webs lo exportan. |
| **MusicXML (.musicxml / .mxl)** | Tablet | ★★★★★ | Incluye manos y a veces digitación |
| **MP3 / WAV / M4A**, modo rápido | Tablet (Basic Pitch) | ★★★☆☆ | Sin conexión, unos segundos por minuto de audio |
| **MP3**, modo calidad | Servidor en casa | ★★★★☆ con piano solo | Modelo de alta resolución + Demucs si hay más instrumentos |
| **PDF / foto de partitura** | Servidor en casa | ★★★☆☆ a ★★★★☆ | Lectura óptica de partituras (Audiveris) |

### 7.2 Proceso de MP3 a notas

Decodificar → (separar el piano con Demucs si es una canción con banda) → transcribir notas → detectar tempo y compases → cuantizar → separar manos → detectar la tonalidad → **simplificar** (§7.5).

### 7.3 Experiencia de importación (pensada para principiantes)

1. Botón grande **"+ Añadir canción"**, con opciones *Archivo*, *Cámara* y *Compartir desde otra app*.
2. El progreso se cuenta en lenguaje humano: "Escuchando la canción…", "Buscando la melodía…", "Preparando tu versión fácil…".
3. Al terminar, la app **te propone el nivel adecuado** ("Te recomendamos empezar por *Solo melodía*").
4. Vista previa de 10 segundos para comprobar que suena bien, con la opción *Revisar notas* si algo falla.

### 7.4 Servidor en casa (opcional)

Como la app es personal, no hace falta pagar un servidor en la nube:

- Un `docker compose up` en tu PC levanta el servicio. **Con GPU va más rápido, pero no es obligatoria.**
- La tablet **lo encuentra sola en tu Wi‑Fi** (mDNS). Si quieres usarlo fuera de casa, se puede acceder a través de **Tailscale**.
- Si el servidor no está disponible, la app usa el **modo rápido en la tablet** para los MP3. Los PDF se quedan en cola hasta que el servidor esté disponible.

### 7.5 Simplificador de canciones (clave para no músicos)

Una canción original suele ser demasiado difícil para empezar. La app genera **4 versiones automáticamente**:

| Nivel | Qué toca | Para quién |
|---|---|---|
| **1 · Solo melodía** | La melodía principal con la mano derecha, una nota cada vez y ritmo simplificado | Primeros días |
| **2 · Melodía + bajo** | Lo anterior, más **una nota de bajo por compás** con la izquierda | Cuando la melodía ya sale |
| **3 · Melodía + acordes** | Melodía + acordes sencillos de 3 notas con la izquierda | Nivel intermedio |
| **4 · Original** | Todas las notas | Reto final |

Ajustes extra del simplificador:

- **Evitar teclas negras**: transporta la canción a una tonalidad que use solo teclas blancas, si es posible.
- **Ajustar a mi teclado**: reubica por octavas las notas que se salen del teclado reducido.
- **Ritmo suave**: alarga o fusiona las notas muy rápidas.
- **Tempo inicial**: empieza más lento y sube automáticamente cuando aciertas (§17.3).

---

## 8. Sistema de diseño: premium minimalista con luz en las teclas

### 8.1 Filosofía visual

- **Oscuridad elegante**: casi negro, superficies apenas diferenciadas y mucho espacio vacío.
- **Una sola fuente de luz**: **las notas y las teclas**. Los botones son discretos, en gris y blanco. Así la atención siempre va a la música.
- **Materiales**: teclas blancas color **marfil mate** y teclas negras **satinadas**, con sombras suaves. Nada de brillos de plástico.
- **Movimiento sereno**: transiciones de 180–250 ms con *ease‑out*. Sin rebotes ni efectos llamativos.

### 8.2 Paleta (tokens)

| Token | Valor | Uso |
|---|---|---|
| `bg` | `#07070A` | Fondo general (casi negro, evita la estela del AMOLED al desplazar) |
| `surface-1` | `#111116` | Tarjetas y paneles |
| `surface-2` | `#1A1A21` | Paneles elevados y hojas inferiores |
| `line` | `#26262F` | Separadores finos |
| `text-1` | `#F3F3F6` | Texto principal |
| `text-2` | `#9C9CA8` | Texto secundario |
| `key-white` | `#EDEBE5 → #DAD7CF` (degradado vertical) | Teclas blancas marfil |
| `key-black` | `#18181D → #0E0E12` | Teclas negras satinadas |
| **`light-right`** | **`#5AB8FF`** (azul hielo) | **Mano derecha**: notas y luz de tecla |
| **`light-left`** | **`#FFB454`** (ámbar cálido) | **Mano izquierda**: notas y luz de tecla |
| `ok` | `#3DDC97` | Acierto (destello breve) |
| `miss` | `#FF6B6B` | Error (solo un parpadeo suave, nunca agresivo) |

> Azul y ámbar son **muy fáciles de distinguir**, también para la mayoría de personas con daltonismo. Así la mano que toca cada nota se entiende sin pensar.

### 8.3 Tipografía

- **Inter** (licencia OFL) para toda la interfaz, con números tabulares en tiempos y marcadores.
- Letras de las notas en **Inter SemiBold**, dentro de un círculo redondeado como en la imagen de referencia.
- Escala: 32 / 24 / 18 / 15 / 13 sp. **Mínimo 15 sp** en la pantalla de práctica para leer de un vistazo.

### 8.4 La luz en las teclas (elemento estrella)

| Momento | Efecto |
|---|---|
| **Se acerca una nota** (≈ 0,6 s antes) | La tecla empieza a **brillar tenuemente desde dentro** con el color de su mano. Es un pre‑aviso suave. |
| **Toca pulsarla** | **Tira de luz** en el borde superior de la tecla (como un LED bajo el teclado) y halo sobre la línea de impacto |
| **La pulsas bien** | La tecla **se llena de luz**, se hunde ligeramente (sombra interior) y salta un destello de partículas finas hacia arriba |
| **La mantienes** | La luz permanece mientras la barra se consume. Una **línea de relleno** en la tecla indica cuánto falta. |
| **La sueltas antes de tiempo** | La luz se apaga en gris |
| **Tecla equivocada** | Parpadeo coral de 120 ms en esa tecla, y la correcta **late** para que la encuentres |
| **Piano libre** | Cada tecla pulsada se ilumina con una luz blanca cálida, con una pequeña estela |

La **línea de impacto** es una fina franja luminosa sobre el teclado que "respira" muy sutilmente con el pulso de la música, y hace de metrónomo visual.

### 8.5 Componentes

- **Botones**: redondeados (radio de 14 dp), solo icono y texto, y relleno solo para la acción principal.
- **Iconos**: Material Symbols Rounded, de trazo fino.
- **Hojas inferiores** (*bottom sheets*) para los ajustes rápidos durante la práctica: se abren sobre la cascada y no te sacan de la canción.
- **Hápticos** sutiles al confirmar en los menús (no en el teclado, para no distraer).

---

## 9. Ergonomía y distribución de la pantalla de práctica

### 9.1 Reglas de ergonomía

- **Zonas táctiles de al menos 12 × 12 mm** en todo lo que se pulsa durante la práctica.
- **Los controles van donde están las manos**:
  - En el modo de 2 filas, los controles principales se colocan en la **banda central** entre las dos filas.
  - En el modo reducido, se colocan **en las esquinas superiores**, alcanzables con el pulgar si sujetas la tablet.
- **Mientras tocas, la interfaz se oculta sola** a los 3 s. Basta **un toque en la zona de la cascada** para que vuelva.
- **Gestos que no interfieren con el teclado**: los gestos solo funcionan en la zona de la cascada, nunca sobre las teclas.
  - Tocar con dos dedos = pausa.
  - Deslizar hacia abajo = retroceder 5 s.
- **Rechazo de palma** activado: apoyar la muñeca no hace sonar notas.
- **Orientación bloqueada en horizontal.** Si se gira, aparece un aviso amable.
- **Modo zurdo**: invierte el lado de los controles.

### 9.2 Pantalla de práctica, modo de 2 filas

```
┌────────────────────────────────────────────────────────────────────────────┐
│  ←   Himno de la alegría · Solo melodía      ▬▬▬▬▬▬▬●──────── 0:42 / 1:30  │ ← barra fina, se oculta
│                                                                            │
│                CASCADA AGUDOS (F4–C8)                                      │
│        ⓔ               ⓖ                                                   │
│        ▮       ⓓ        ▮                                                  │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│ ← línea de impacto luminosa
│ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▐                 │   TECLADO F4–C8
│ F  G  A  B  C₅ ...                                                         │
├──────────  ⏮   ▶ / ❚❚   🔁 A‑B    🐢 70 %    ✋ Der  ✋ Izq   ⚙  ───────────┤ ← BANDA CENTRAL (controles)
│                CASCADA GRAVES (A0–E4)                                      │
│                    ⓒ                                                       │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│
│ ▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ...           │   TECLADO A0–E4
│ A  B  C₁ ...                                     C₄• D  E                  │   (• = C central)
└────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 Pantalla de práctica, modo reducido (por defecto en las lecciones)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ ←                         Estrellita · Nivel 1                       ⚙  ❚❚ │
│ ░░░░░░░░░░░░░░░░[▓▓▓▓▓▓▓▓]░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ ← mini‑mapa de las 88 teclas
│                                                                            │
│                 ⓒ                                                          │
│                 ▮               ⓖ                                          │
│                 ▮       ⓒ       ▮                                          │
│                 ▮       ▮       ▮                                          │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│
│   ▌ █ ▌ █ ▐   ▌ █ ▌ █ ▌ █ ▐   ▌ █ ▌ █ ▐   ▌ █ ▌ █ ▌ █ ▐                     │   2 octavas
│   C   D   E   F   G   A   B   C•  D   E   F   G   A   B   C                │   teclas de ≈ 21 mm
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Diseños de teclado

### 10.1 Completo en 2 filas (88 teclas)

| Fila | Rango | Teclas | Blancas | Ancho de tecla blanca |
|---|---|---|---|---|
| **Superior (agudos)** | **F4 → C8** | 44 | 26 | ≈ 12,1 mm |
| **Inferior (graves)** | **A0 → E4** | 44 | 26 | ≈ 12,1 mm |

- El corte entre **E4 y F4** es el único punto que deja las dos filas **exactamente iguales**. Además, entre E y F no hay tecla negra, así que el corte se ve limpio.
- **Cada fila tiene su propia cascada**: cada nota cae justo encima de su tecla.
- La **banda central** contiene los controles (§9.2) y una etiqueta discreta "F4 ▲ ▼ E4".
- **Configurable**:
  - Punto de corte.
  - Modo "solape", que repite una octava en las dos filas.
  - Orden de las filas.
  - Proporción entre cascada y teclado, arrastrando la banda.
- **Uso recomendado**: piano libre y canciones de nivel 3–4.

### 10.2 Reducido (por defecto para principiantes)

- **Auto‑rango**: muestra solo las octavas que usa la canción. En el nivel 1 suelen ser **2 octavas con teclas de ≈ 21 mm**, casi como un piano real.
- **Seguimiento suave**: si la canción se mueve, el teclado se desliza hacia las próximas notas **antes** de que lleguen, con una animación lenta para no marear.
- **Tamaños fijos**: 2, 3, 4 o 5 octavas.
- **Octavas plegables**: las octavas que no se usan quedan como una franja fina.
- **Pellizcar** para cambiar el número de teclas y **arrastrar con dos dedos** para moverse.
- **Mini‑mapa** de las 88 teclas arriba. Si una nota cae fuera, aparece una **flecha luminosa en el borde** que avisa hacia dónde ir.

---

## 11. Nombres de las notas: letras C D E F G A B

- **Sistema único: letras en inglés.**
  - Teclas blancas: **C D E F G A B**.
  - Teclas negras: **C# D# F# G# A#**.
- Por defecto se usa **siempre `#` (sostenido)** en las negras, porque es más fácil para empezar. En *Ajustes* se puede activar "usar ♭ cuando la canción lo pida" (D♭, E♭…).
- **Dónde aparecen las letras**:
  - **En las notas que caen**, dentro de un círculo, como en la imagen de referencia.
  - **En las teclas blancas**, abajo, en gris suave. Se pueden ocultar por niveles: todas, solo las C, o ninguna.
  - **En las teclas negras**, solo cuando se iluminan, para no saturar.
- **Orientación**:
  - Cada **C** lleva su número de octava pequeño (C3, C4, C5…).
  - El **C central (C4)** tiene un punto de luz fijo. Es la "casa" del principiante.
- **Ayuda para memorizar**: en las primeras lecciones, las letras se apoyan en la forma de las teclas negras ("C está justo a la izquierda del grupo de 2 negras").

---

## 12. Modos de señalización

Se combinan libremente y hay **presets** pensados para el progreso de un principiante:

| Preset | Qué muestra | Cuándo |
|---|---|---|
| **Guiado** (por defecto) | Cascada + letras en notas y teclas + luz con pre‑aviso + números de dedo + modo Espera | Primeras semanas |
| **Confianza** | Cascada + letras solo en las notas + luz al tocar | Cuando ya conoces el teclado |
| **Memoria** | Cascada sin letras + luz solo si fallas | Para comprobar que te la sabes |
| **Lectura** (opcional) | Pentagrama que se desplaza con letras de ayuda + luz mínima | Si quieres aprender a leer partitura |

### 12.1 Elementos disponibles

1. **Cascada**: barras de luz cuya longitud es la duración.
2. **Luz en las teclas**: pre‑aviso, al tocar, al mantener y en caso de error (§8.4).
3. **Letras**: en las notas, en las teclas o en ambas (§11).
4. **Números de dedo** del 1 al 5, dentro de la barra. Salen de la partitura o se calculan automáticamente.
5. **Guías verticales tenues** desde cada nota hasta su tecla, para no perderse.
6. **Flechas de borde** en el teclado reducido.
7. **Feedback de acierto**: destello verde si es a tiempo, ámbar si es un poco pronto o tarde, coral si la nota es incorrecta y gris si se suelta antes de tiempo.
8. **Pentagrama** opcional (Verovio), con cursor sincronizado.

### 12.2 Esquemas de color de las notas

| Esquema | Descripción |
|---|---|
| **Por mano** (por defecto) | Derecha azul hielo · izquierda ámbar |
| **Por nota** | Un color para cada letra (C, D, E…). Ayuda a memorizar. |
| **Por dedo** | Un color por dedo del 1 al 5 |
| **Por octava** | Graves cálidos, agudos fríos |
| **Monocromo** | Un único tono de luz blanca. Muy elegante. |
| **Daltonismo** | Paletas seguras, más formas o texturas en las barras |

---

## 13. Ecualizador de sonido

Está pensado para que **cualquiera lo use sin saber de audio**, con una capa avanzada para quien quiera más.

### 13.1 Capa sencilla (la que se ve primero)

- **Selector de piano**, con tarjetas grandes y sonido de muestra al tocarlas:
  - Gran Cola · Brillante · Cálido · Vertical · Íntimo (suave, para tocar de noche) · Eléctrico.
- **Tres deslizadores** con nombres humanos: **Graves · Cuerpo · Brillo**.
- **Sala**: Seca · Habitación · Estudio · Auditorio · Catedral, con un deslizador de "cantidad de sala".
- **Volumen** y modo **"Noche"** (comprime el sonido para tocar bajito sin perder notas suaves).

### 13.2 Capa avanzada (desplegable)

- **EQ gráfico de 5 bandas**: 60 Hz · 250 Hz · 1 kHz · 4 kHz · 12 kHz, de ±12 dB, con curva visual.
- **EQ paramétrico** de 3 bandas: frecuencia, ganancia y Q.
- **EQ por zona del teclado**: subir o bajar el volumen de graves, centro y agudos del piano (por teclas, no por frecuencia).
- **Realismo**: resonancia de cuerdas, ruido de macillos, ruido de pedal y duración de la liberación.
- **Afinación**: La4 entre 415 y 466 Hz. **Transposición** de ±12 semitonos (la cascada se mueve con ella).
- **Perfiles de salida**: Altavoces de la tablet / Auriculares, que se cambian automáticamente.
- **Guardar mis presets** con nombre.

---

## 14. Ecualizador de teclado

Ajusta **cómo responde y cómo se ve** el teclado. Se edita **sobre el propio teclado**, con capas de color que muestran cada zona.

### 14.1 Respuesta al toque

| Ajuste | Opciones |
|---|---|
| **Curva de fuerza** | Suave · Normal · Dura · Fija (todas las notas igual de fuertes) · Personalizada (curva arrastrable) |
| **Cómo detecta la fuerza en la pantalla** | Por el **tamaño del contacto** del dedo (más superficie = más fuerte), con una calibración inicial (§16, paso 6) |
| **Sensibilidad por zonas** | Arrastras sobre el teclado para definir zonas (graves, centro y agudos) y les das más o menos sensibilidad |
| **Equilibrio de manos** | Subir o bajar el volumen de lo que toca cada mano (útil cuando una mano tapa a la otra) |

### 14.2 Forma y comportamiento

| Ajuste | Opciones |
|---|---|
| **Altura del teclado** | Deslizador con vista en vivo |
| **Proporción de las teclas negras** | Largo y ancho de las negras (más grandes = más fáciles de acertar) |
| **Deslizar entre teclas** | Activado (glissando) o desactivado (evita notas accidentales). **Desactivado por defecto.** |
| **Rechazo de palma** | Activado, con nivel ajustable |
| **Pedal** | Botón en pantalla, pedal Bluetooth (pasapáginas) o pedal MIDI |

---

## 15. Entrada: cómo sabe la app lo que tocas

| Fuente | Latencia | Comentario |
|---|---|---|
| **Pantalla táctil** | ~15–25 ms | Siempre disponible. Es la entrada principal. |
| **Piano MIDI USB/BLE** (opcional) | ~5–10 ms | Se detecta solo al conectarlo |
| **Micrófono con piano acústico** (fase posterior) | ~60–120 ms | Basic Pitch en la tablet, con compensación de la latencia |

El **evaluador** acepta un margen generoso para principiantes:

| Nivel | Margen de tiempo |
|---|---|
| Relajado (por defecto) | ±120 ms |
| Normal | ±70 ms |
| Preciso | ±40 ms |

---

## 16. Tutorial de inicio guiado (≈ 5 minutos)

Es **interactivo**: se aprende tocando. Se puede saltar, se retoma donde lo dejaste y se repite desde *Ajustes*.

| Paso | Pantalla | Qué haces | Objetivo |
|---|---|---|---|
| 1 | **Bienvenida** | Ves notas de luz caer y sonar una melodía conocida | Entender la app en 10 s |
| 2 | **Coloca la tablet** | Animación: en horizontal, sobre una mesa o un atril, a la altura de los codos | Ergonomía |
| 3 | **Tu primera tecla** | "Toca cualquier tecla", y la tecla se ilumina y suena | Primer contacto y primera alegría |
| 4 | **El mapa del teclado** | Descubres los grupos de 2 y 3 teclas negras y encuentras **C** a la izquierda del grupo de 2 | Orientarse sin saber música |
| 5 | **Las letras** | Tocas C‑D‑E‑F‑G y cada letra aparece al pulsarla | Aprender los nombres |
| 6 | **Calibra tu toque** | "Toca suave… ahora fuerte" | Ajustar el ecualizador de teclado |
| 7 | **Leer la cascada** | Caen 3 notas lentas, y la app espera a que toques cada una al llegar a la línea | Entender la mecánica |
| 8 | **Notas largas** | "Mantén pulsado hasta que la barra se acabe" | Entender la duración |
| 9 | **Tus dedos** | Mano en pantalla con números del 1 al 5 y pulgar derecho en el C central | Posición básica |
| 10 | **Tu primera canción** | *Himno de la alegría* (nivel 1, mano derecha, modo Espera) | **Primer logro 🎉** |
| 11 | **Elige tu teclado** | Comparas en vivo *Reducido* y *Completo en 2 filas* | Que conozcas los dos |
| 12 | **Tus canciones** | Importas el MIDI de ejemplo incluido y ves cómo se simplifica | Conocer la función estrella |
| 13 | **Mini tour** | Marcas breves sobre velocidad, bucle, manos y los dos ecualizadores | Conocer el resto de herramientas |

**Después**: ayudas contextuales la primera vez que entras en cada pantalla, consejos breves en las cargas y vídeos de 30 s en *Ayuda*.

---

## 17. Aprender desde cero: "Tu camino"

### 17.1 Camino de lecciones

Son lecciones de **2 a 5 minutos**, cada una con tres partes:

1. **Una idea**: una animación y una frase.
2. **Práctica guiada**.
3. **Mini‑reto**, que da estrellas.

| Módulo | Contenido (sin jerga) |
|---|---|
| **1. Conoce el teclado** | Grupos de negras, encontrar C, las 7 letras, qué es "más agudo y más grave", las octavas |
| **2. Tus manos** | Números de dedo, posición de C, relajar la muñeca |
| **3. Largo y corto** | Notas largas y cortas con la cascada, contar pulsos con la luz de la línea de impacto |
| **4. Melodías con 5 notas** | Canciones sencillas con C‑D‑E‑F‑G y la mano derecha |
| **5. La mano izquierda** | Las mismas ideas con la izquierda (notas ámbar) |
| **6. Las teclas negras** | Qué es `#`: "la tecla negra justo a la derecha" |
| **7. Dos manos** | Melodía con la derecha y una nota larga con la izquierda |
| **8. Acordes fáciles** | C, F, G y Am como "formas" de 3 dedos, para acompañar canciones populares |
| **9. Moverse por el teclado** | Cambiar de posición sin perderse |

**Canciones incluidas** (dominio público): Himno de la alegría, Estrellita (*Twinkle Twinkle*), Cumpleaños feliz, Jingle Bells, Oh When the Saints, Minueto en G, Para Elisa (inicio), Canon de Pachelbel (simplificado)…

### 17.2 Modos de práctica

- **Espera** (por defecto): la música se para hasta que tocas la nota correcta.
- **Ritmo**: la música sigue y tú la acompañas.
- **Escuchar**: la app toca sola y tú miras las luces.
- **Por manos**: solo la derecha, solo la izquierda o las dos. La otra mano puede sonar sola, sonar bajito o silenciarse.
- **Bucle**: tocas dos puntos de la barra de progreso para repetir un trozo.

### 17.3 El coach (feedback amable)

- Al terminar: **"¡9 de 10 notas! 🎉"**, con las 1–2 cosas a mejorar en lenguaje humano: "En esta parte te adelantas un poco".
- **Detección de trozos difíciles**: "Este trozo te cuesta. ¿Lo repetimos más despacio?", y prepara el bucle a un toque.
- **Tempo adaptativo**: empieza al 60 % y sube un 5 % cada vez que superas el 90 % de aciertos.
- **Subida de nivel**: cuando dominas el nivel 1 de una canción, te propone el nivel 2.
- **Motivación tranquila**: minutos practicados, días seguidos y canciones aprendidas, sin presión ni castigos por perder la racha.

---

## 18. Pantallas principales

### 18.1 Inicio

Como máximo cuatro opciones, todas grandes:

```
┌────────────────────────────────────────────────────────────────────────────┐
│  Buenas tardes                                        🔥 4 días   ⚙        │
│                                                                            │
│  ┌──────────────────────────────────────┐  ┌───────────────────────────┐   │
│  │  CONTINUAR                           │  │  TU CAMINO                │   │
│  │  Himno de la alegría · Nivel 2       │  │  Módulo 4 · Lección 2     │   │
│  │  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░░░░░  72 %   ▶      │  │  ●●●●○○○○○                │   │
│  └──────────────────────────────────────┘  └───────────────────────────┘   │
│  ┌──────────────────────────────────────┐  ┌───────────────────────────┐   │
│  │  MIS CANCIONES          + Añadir     │  │  PIANO LIBRE              │   │
│  │  ▢ ▢ ▢ ▢                             │  │  Toca sin partitura       │   │
│  └──────────────────────────────────────┘  └───────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────┘
```

### 18.2 Resto de pantallas

- **Mis canciones**: cuadrícula con portada, nivel actual, estrellas y progreso. Filtros: *En progreso*, *Favoritas* y *Añadidas*.
- **Detalle de canción**: escuchar la vista previa, elegir nivel y mano, y empezar.
- **Progreso**: calendario de práctica, canciones aprendidas y trozos que más te cuestan.
- **Ajustes**: Sonido (ecualizador de sonido) · Teclado (ecualizador de teclado) · Señalización · Entrada · Servidor en casa · Tutorial · Copia de seguridad · Accesibilidad.

---

## 19. Accesibilidad

- Paletas para daltonismo y formas además del color.
- Texto escalable.
- Modo zurdo.
- *Reducir movimiento*: sin partículas y con transiciones simples.
- Contraste alto opcional en las letras de las teclas.

---

## 20. Uso personal: qué cambia

- **Sin cuentas, sin login, sin anuncios, sin analíticas.** Todo se queda en la tablet.
- **Instalación directa**: APK compilado con Android Studio o con GitHub Actions, e instalado por cable o por enlace. No hace falta pasar por Google Play.
- **Actualizaciones**: nueva versión del APK. Los datos se conservan.
- **Copia de seguridad**: exportar e importar un archivo `.zip` (canciones, progreso y ajustes) a Google Drive o a tu PC.
- **Licencias**: para uso privado y sin distribuir, se pueden usar sin problema componentes con licencias exigentes (por ejemplo Audiveris, AGPL) y cualquier canción tuya. Si algún día se publica, habría que revisarlas (queda anotado en §24).
- **Nada de monetización.** Todas las funciones están desbloqueadas.

---

## 21. Estructura del proyecto

```
piano-cascada/
├── app/                      # Módulo principal (Compose, navegación, tema)
├── designsystem/             # Tokens (§8.2), tipografía, componentes, iconos
├── core/
│   ├── model/                # Song, Track, Note…
│   ├── importers/            # MIDI, MusicXML, cliente del servidor en casa
│   ├── simplifier/           # Generación de los niveles 1–4 (§7.5)
│   ├── transport/            # Reloj, modo Espera, bucle, tempo adaptativo
│   ├── evaluator/            # Aciertos + coach
│   └── data/                 # Room, DataStore, copia de seguridad
├── feature/
│   ├── home/
│   ├── library/
│   ├── practice/             # Pantalla de práctica (Compose + GL)
│   ├── journey/              # Tu camino (lecciones)
│   ├── onboarding/           # Tutorial inicial
│   ├── settings/             # Incluye los dos ecualizadores
│   └── progress/
├── render/                   # OpenGL: cascada, teclado, luz, partículas
├── audio/                    # C++: Oboe, sampler SFZ, EQ, convolución, limitador
├── input/                    # Táctil (fuerza, palma), MIDI, micrófono
├── assets/
│   ├── samples/              # Salamander Grand V3 + alternativos
│   ├── impulses/             # Respuestas de impulso de salas
│   ├── songs/                # Canciones de lecciones (dominio público)
│   └── models/               # basic_pitch.tflite
└── home-server/              # Docker: FastAPI + transcripción + Audiveris
```

---

## 22. Hoja de ruta (proyecto personal)

| Fase | Duración aprox.* | Entregable |
|---|---|---|
| **0. Prototipos** | 1–2 semanas | Latencia real con Oboe y Salamander en la S8 Ultra · cascada GL a 120 fps · prueba del diseño de luz en las teclas |
| **1. Base tocable** | 4–5 semanas | Piano libre con buen sonido · **sistema de diseño** · teclado **reducido** y de **2 filas** · luz en las teclas · letras C‑D‑E |
| **2. Practicar canciones** | 4 semanas | Importar MIDI y MusicXML · cascada con duración · modos Espera, Ritmo y Escuchar · manos · bucle · velocidad · **simplificador niveles 1–4** |
| **3. Primeros pasos** | 4 semanas | **Tutorial de inicio** completo · **Tu camino** módulos 1–5 · coach y tempo adaptativo · pantalla de inicio y progreso |
| **4. Los dos ecualizadores** | 2–3 semanas | **Ecualizador de sonido** (capa sencilla y avanzada, reverb por convolución) · **ecualizador de teclado** (curva, zonas, forma) · presets de señalización |
| **5. MP3 y PDF** | 3–4 semanas | MP3 en la tablet (Basic Pitch) · **servidor en casa** (MP3 de alta calidad + PDF) |
| **6. Pulido** | 2–3 semanas | Tu camino módulos 6–9 · accesibilidad · copia de seguridad · micrófono (opcional) |

\* Estimación para **una persona desarrollando con ayuda de IA**. Total: **unos 5–6 meses** a ritmo constante. Cada fase termina con algo **usable en la tablet**.

---

## 23. Pruebas y calidad

- **Sonido**: comparación A/B con un piano real o una grabación de referencia. Medir la latencia del toque al sonido con *OboeTester*.
- **Fluidez**: JankStats y Macrobenchmark en la S8 Ultra. Fallo si el p99 supera 8,3 ms.
- **Parsers**: un corpus de más de 100 MIDI y MusicXML.
- **Simplificador**: comprobar a oído que el nivel 1 de 20 canciones es reconocible y fácil de tocar.
- **Transcripción**: medida con canciones de piano solo (dataset MAESTRO como referencia).
- **UX con principiantes reales**: 3–5 personas sin conocimientos musicales hacen el tutorial sin ayuda. Objetivo: todas terminan la primera canción y entienden las letras.

---

## 24. Riesgos

| Riesgo | Mitigación |
|---|---|
| La transcripción de MP3 con banda o voz es imprecisa | Separación con Demucs + simplificador (el nivel 1 solo necesita la melodía) + revisar notas + recomendar MIDI cuando exista |
| Lectura de PDF con errores | Mostrar el original al lado y priorizar MIDI y MusicXML |
| Latencia de auriculares Bluetooth | Aviso + recomendar los altavoces o USB‑C + compensación visual |
| Teclas de 12 mm en el modo de 2 filas | El teclado reducido es el modo por defecto para aprender |
| Memoria con muestras grandes | Precarga parcial y streaming (§4.1) |
| Cansancio o abandono del principiante | Lecciones cortas, simplificador, coach amable y éxito en 5 minutos |
| Licencias si algún día se publica | Revisar Audiveris (AGPL), las muestras y los modelos antes de distribuir |

---

## 25. Próximos pasos

1. **Prototipo de sonido**: Oboe + Salamander V3 en la S8 Ultra, midiendo la latencia.
2. **Prototipo visual**: cascada + teclado con **luz en las teclas** a 120 fps, para validar la estética premium minimalista.
3. **Diseño en Figma** de Inicio, Práctica (reducido y 2 filas), los dos ecualizadores y el tutorial.
4. Arrancar la **fase 1**.
