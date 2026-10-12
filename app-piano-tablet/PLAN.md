# Plan de desarrollo: app de piano para tablet ("Piano Cascada")

> Nombre provisional: **Piano Cascada**
> Dispositivo objetivo: **Samsung Galaxy Tab S8 Ultra** (uso en horizontal)
> Autor del plan: equipo de desarrollo · Fecha: 12/10/2026 · Estado: borrador v1

---

## 0. Resumen

Una app de piano completa para tablet Android. Puedes **importar una partitura (MIDI, MusicXML, PDF o foto) o un audio (MP3/WAV)** y la app lo convierte en **notas que caen desde arriba con colores** (estilo "cascada" o *piano roll*, como en la imagen de referencia). La **longitud de cada barra es la duración de la nota**, y mientras tocas la app **escucha lo que haces** y te corrige en tiempo real.

Lo que la diferencia de otras apps:

1. **Teclado de 88 teclas partido en dos filas equilibradas**, para que quepa entero en la pantalla de 14,6" con teclas tocables.
2. **Teclado reducido** que se ajusta solo al rango de la canción y aprovecha mejor el espacio.
3. **Varios modos de ecualización**, tanto del **sonido** (EQ, timbre, reverb) como del **teclado** (sensibilidad, tamaño y respuesta por zonas).
4. **Varios modos de señalización**: cascada, teclas iluminadas, nombres de nota, digitación, colores por mano, por nota o por octava, y partitura que se desplaza.
5. **Tutorial de inicio guiado e interactivo.**

---

## 1. Dispositivo objetivo: Galaxy Tab S8 Ultra

| Característica | Valor | Qué implica para la app |
|---|---|---|
| Pantalla | 14,6" Super AMOLED, 2960 × 1848 px, 16:10 | En horizontal hay **≈ 314 mm de ancho útil**. |
| Frecuencia | 120 Hz | La cascada se dibuja a **120 fps** (8,3 ms por frame). |
| SoC | Snapdragon 8 Gen 1 | Hay GPU de sobra para la cascada con brillos y para modelos de IA ligeros en el dispositivo (TFLite/NNAPI). |
| RAM | 8 / 12 / 16 GB | Caben las muestras de piano en memoria (≈ 300–600 MB). |
| Audio | 4 altavoces AKG, Dolby Atmos, USB‑C | Baja latencia con AAudio/Oboe. Auriculares por USB‑C o BT (BT con aviso de latencia). |
| Táctil | Multitáctil de 10 puntos | Acordes reales con las dos manos. |
| S Pen | Incluido | Anotar partituras y marcar digitaciones. |
| SO | Android 12 de serie, actualizable (One UI) | `minSdk 31`, `targetSdk` siempre la última. |

### Cálculo clave: ¿por qué dos filas?

- Un piano tiene **88 teclas, de las cuales 52 son blancas**.
- **En una sola fila**: 314 mm / 52 = **6 mm por tecla blanca**. Es imposible tocar con el dedo, porque una yema mide entre 10 y 14 mm.
- **En dos filas de 26 blancas**: 314 mm / 26 = **12,1 mm por tecla blanca**. Se puede tocar bien. Es la mitad del ancho de un piano real (23,5 mm), pero es cómodo en una tablet.

---

## 2. Stack tecnológico (decisión)

**Recomendación: app nativa Android (Kotlin) con un motor de audio en C++.**

El motivo es la latencia. En un piano, más de unos 20 ms entre tocar y oír se nota mucho. Flutter y React Native añaden capas que lo complican, y además solo nos importa una plataforma (una tablet Samsung).

| Capa | Tecnología | Motivo |
|---|---|---|
| UI general (menús, biblioteca, ajustes, tutorial) | **Kotlin + Jetpack Compose** + Material 3 | Moderno y rápido de desarrollar. Se adapta bien a pantallas grandes. |
| Render de la cascada y del teclado | **OpenGL ES 3.2** (o Vulkan) en un `SurfaceView`/`GLSurfaceView` propio | Mantiene 120 fps estables con brillos (*bloom*) y partículas, sin pasar por Compose. |
| Motor de audio | **C++ + Oboe (AAudio, modo MMAP de baja latencia)** | Latencia de salida objetivo **< 15 ms**. |
| Sintetizador | Sampler propio en C++ con formato **SFZ**, o **FluidSynth** (SF2) como alternativa | Muestras multicapa por velocidad, pedal de resonancia y *release*. |
| Muestras de piano | **Salamander Grand Piano** (CC‑BY 3.0) para empezar, y más adelante un piano propio grabado | Calidad de concierto sin coste de licencia (hay que citar la autoría). |
| Entrada MIDI | `android.media.midi` (USB y Bluetooth LE MIDI) | Para conectar un piano digital real. |
| Detección por micrófono | **Basic Pitch** (Spotify, Apache 2.0) en TFLite, en streaming | Detecta notas de un piano acústico sin cables. |
| Partituras | Parser **MusicXML** propio + **Verovio** (C++, LGPL) para dibujar la partitura | Muestra el pentagrama sincronizado. |
| MIDI | Parser `.mid` propio (o librería ligera tipo `kmidi`) | Formato principal interno. |
| Base de datos local | **Room** (SQLite) + **DataStore** para preferencias | Biblioteca, progreso y ajustes. |
| Inyección de dependencias | **Hilt** | Estándar en Android. |
| Backend (procesar MP3 y PDF) | **Python + FastAPI + Celery/Redis + GPU** | Los modelos pesados de transcripción y de lectura de partituras corren mejor en el servidor. |
| Almacenamiento en la nube (opcional) | S3 o Supabase Storage | Sincroniza la biblioteca entre dispositivos. |

---

## 3. Arquitectura general

```
┌──────────────────────────────────────────────────────────────────┐
│                         APP ANDROID                              │
│                                                                  │
│  ┌────────────┐   ┌─────────────┐   ┌──────────────────────────┐ │
│  │  UI Compose│   │  Biblioteca │   │  Tutorial / Onboarding   │ │
│  │ (menús,    │   │  (Room)     │   │  (motor de pasos guiados)│ │
│  │  ajustes)  │   └──────┬──────┘   └────────────┬─────────────┘ │
│  └─────┬──────┘          │                       │               │
│        │         ┌───────▼───────────────────────▼──────┐        │
│        └────────►│      MODELO DE CANCIÓN (Song)        │        │
│                  │  pistas · notas · tempo · compases   │        │
│                  └───────┬───────────────────┬──────────┘        │
│                          │                   │                   │
│        ┌─────────────────▼──┐        ┌───────▼──────────────┐    │
│        │ TRANSPORTE / RELOJ │◄──────►│ EVALUADOR DE PRÁCTICA│    │
│        │ (play, pausa, loop,│        │ (compara lo que tocas│    │
│        │  velocidad, espera)│        │  con lo esperado)    │    │
│        └───┬────────────┬───┘        └───────▲──────────────┘    │
│            │            │                    │                   │
│   ┌────────▼──────┐ ┌───▼──────────────┐ ┌───┴─────────────────┐ │
│   │ RENDER GL     │ │ MOTOR AUDIO C++  │ │ ENTRADAS            │ │
│   │ cascada +     │ │ (Oboe + sampler  │ │ táctil · MIDI ·     │ │
│   │ teclado(s)    │ │  + EQ + reverb)  │ │ micrófono (TFLite)  │ │
│   └───────────────┘ └──────────────────┘ └─────────────────────┘ │
└───────────────────────────────┬──────────────────────────────────┘
                                │ HTTPS (solo para MP3 / PDF / foto)
┌───────────────────────────────▼──────────────────────────────────┐
│                     BACKEND DE PROCESADO                         │
│  FastAPI → cola Celery → workers GPU                             │
│  · Audio → MIDI (transcripción de piano)                         │
│  · PDF/foto → MusicXML (OMR)                                     │
│  · Separación de manos, tempo, tonalidad, compases               │
└──────────────────────────────────────────────────────────────────┘
```

### 3.1 Modelo de datos central

```kotlin
data class Song(
    val id: String,
    val title: String,
    val composer: String?,
    val source: SourceType,          // MIDI, MUSICXML, PDF, IMAGE, AUDIO
    val tempoMap: List<TempoChange>, // cambios de tempo (µs por negra)
    val timeSignatures: List<TimeSig>,
    val keySignature: Key?,
    val tracks: List<Track>,
    val durationMs: Long,
)

data class Track(val name: String, val hand: Hand?, val notes: List<Note>)

data class Note(
    val pitch: Int,          // MIDI 21 (A0) … 108 (C8)
    val startMs: Long,       // inicio en tiempo absoluto
    val durationMs: Long,    // duración (la longitud de la barra)
    val velocity: Int,       // 1..127
    val hand: Hand,          // LEFT / RIGHT / UNKNOWN
    val finger: Int?,        // 1..5 (digitación), opcional
    val measure: Int,        // nº de compás, para loops y la partitura
)
```

Todo lo que se importa (MIDI, MusicXML, PDF o MP3) **acaba convertido en un `Song`**. El render, el audio y el evaluador solo trabajan con este modelo.

---

## 4. Importación de partituras y audio

### 4.1 Formatos y cómo se procesa cada uno

| Entrada | Dónde se procesa | Proceso | Calidad esperada |
|---|---|---|---|
| **MIDI (.mid)** | En el dispositivo | Parseo directo y asignación de manos por pista o por canal | ★★★★★ |
| **MusicXML / .mxl** | En el dispositivo | Parseo: notas, digitación, manos (pentagrama 1 o 2), ligaduras y repeticiones | ★★★★★ |
| **PDF de partitura** | Backend | OMR (reconocimiento óptico musical) → MusicXML → Song | ★★★☆☆ a ★★★★☆ según la calidad del PDF |
| **Foto de partitura** (cámara) | Backend | Corrección de perspectiva → OMR → MusicXML | ★★★☆☆ |
| **MP3 / WAV / M4A** | Backend (con modo ligero en el dispositivo) | Transcripción de piano → MIDI → cuantización → Song | ★★★★☆ si es piano solo; ★★☆☆☆ si es una canción con banda |

### 4.2 Pipeline de audio a notas (MP3)

1. **Decodificar** y normalizar a 16 kHz mono (o 44,1 kHz para el modelo de alta resolución).
2. **Separar fuentes** (opcional, solo si no es piano solo) con **Demucs**: se queda con la pista de piano o de "otros".
3. **Transcribir piano**. Hay varias opciones y hay que elegir tras hacer pruebas:
   - *High‑resolution Piano Transcription* (ByteDance): la más precisa para piano y detecta el pedal. **Hay que verificar su licencia.**
   - *Onsets and Frames* (Magenta, Apache 2.0).
   - *Basic Pitch* (Spotify, Apache 2.0): más ligera y apta para el dispositivo. Es el modo rápido, sin conexión.
4. **Detectar tempo y compases** (beat tracking con `madmom` o `librosa`) para alinear con una rejilla y numerar compases.
5. **Cuantizar** a semicorcheas o tresillos, con fuerza configurable (0 % = humano, 100 % = rígido).
6. **Separar manos**: heurística por registro y continuidad de voces, más un modelo opcional. El usuario puede corregir el punto de corte.
7. **Detectar la tonalidad**, para elegir bien entre sostenidos y bemoles al nombrar notas.
8. **Revisión opcional por el usuario**: un editor sencillo para borrar o mover notas mal detectadas.

### 4.3 Pipeline de PDF o foto a notas (OMR)

- Motores candidatos: **Audiveris** (maduro, licencia **AGPL**: hay que revisarlo legalmente si se modifica y se ofrece como servicio) y **oemer** (basado en deep learning, MIT).
- Preproceso: binarización, enderezado y separación por páginas.
- Salida: MusicXML. Se muestra **la partitura reconstruida junto al original** para que el usuario detecte errores.

### 4.4 Experiencia de importación

- Botón **"+ Añadir canción"** con estas opciones: Archivo · Cámara · Desde Drive/Dropbox · Compartir desde otra app (intent `ACTION_SEND`).
- Una barra de progreso con estados ("Subiendo…", "Escuchando la música…", "Separando manos…").
- **Notificación cuando termina**, para que el usuario pueda seguir usando la app mientras tanto.
- **Aviso legal**: el usuario solo debe subir contenido que tenga derecho a usar. Los archivos son privados y no se comparten.

---

## 5. Pantalla de práctica (horizontal)

### 5.1 Estética, basada en la imagen de referencia

- **Fondo negro puro.** En AMOLED así se ahorra batería y se maximiza el contraste.
- Las **notas son barras redondeadas con brillo de neón** (*bloom*). La longitud de cada barra es su duración.
- La **etiqueta de la nota** va en un círculo redondeado dentro o encima de la barra (C, D, E… o Do, Re, Mi…).
- Hay una **línea de impacto luminosa** justo encima del teclado. Cuando una nota la toca, la tecla se ilumina y saltan partículas.
- **Teclado realista** con sombras suaves. La tecla pulsada se hunde y se colorea.

### 5.2 Barra superior (se oculta sola)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ◄ Biblioteca │ "Nocturno Op.9 nº2"  │ ⏮ ▶ ⏭ │ 🐢 75% 🐇 │ A‑B 🔁 │ ✋I ✋D │ ⚙ │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬●────────────────────────────── 1:16 / 4:32  Compás 23 │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Velocidad** del 25 % al 150 % sin cambiar el tono.
- **Bucle A‑B** por compases, con la opción "repetir N veces y subir la velocidad un 5 %".
- **Manos**: practicar con la izquierda, con la derecha o con las dos. La mano que no practicas puede sonar sola, sonar bajito o silenciarse.
- **Modos de reproducción**:
  - **Espera**: la música se para hasta que tocas la nota correcta. Es el modo ideal para aprender.
  - **Ritmo**: la música sigue y te puntúa la precisión.
  - **Escuchar**: la app toca sola y tú miras.
  - **Libre**: sin canción, solo piano.

### 5.3 Duración en tiempo real

- Las **barras que caen** tienen la longitud exacta de la duración. La velocidad de caída es configurable (zoom temporal).
- **Mientras mantienes la tecla**, la barra se va "consumiendo" sobre la línea de impacto y la tecla muestra un **indicador de relleno** con el tiempo que falta.
- Si sueltas antes de tiempo, la parte que falta se queda en **gris** y se marca como "nota corta".
- Indicador de **pedal de sustain**: una banda horizontal encima del teclado mientras el pedal debe estar pisado.
- **Metrónomo visual**: líneas tenues de compás y de pulso que cruzan la cascada.

---

## 6. Diseños de teclado

### 6.1 Teclado completo partido en 2 mitades (modo principal)

Las 88 teclas se dividen en **dos filas exactamente iguales**:

| Fila | Rango | Teclas | Blancas | Ancho de tecla blanca |
|---|---|---|---|---|
| **Superior (agudos)** | **F4 → C8** | 44 | 26 | ≈ 12,1 mm (≈ 114 px) |
| **Inferior (graves)** | **A0 → E4** | 44 | 26 | ≈ 12,1 mm (≈ 114 px) |

Por qué el corte va entre **E4 y F4**:

- Es el único punto que deja **44 teclas y 26 blancas en cada fila**, así que las dos tienen exactamente el mismo tamaño de tecla.
- **Entre E y F no hay tecla negra**, así que el corte visual es limpio.
- El Do central (C4) queda en la fila inferior, cerca de su borde derecho, que es donde suele trabajar la mano izquierda que sube.

**El punto de corte es configurable** (por ejemplo C4/C#4 o B3/C4). También hay un modo **"solape"**, que repite una octava en las dos filas para pasajes que cruzan el corte.

#### Distribución de la pantalla (2960 × 1848 px)

Cada fila tiene **su propia cascada encima**, así cada nota cae justo sobre la tecla que le corresponde:

```
┌───────────────────────────────────────────────────────────────┐  ← barra superior (autoocultable, 64 px)
│                 CASCADA AGUDOS (F4–C8)                        │
│      ▮           ▮▮                ▮        ▮                 │  ≈ 520 px
│      ▮    ▮      ▮▮       ▮        ▮                          │
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│  ← línea de impacto
│ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▐ ...  │  ≈ 340 px  TECLADO F4–C8
├───────────────────────────────────────────────────────────────┤  ← separador (etiqueta "F4 ▲ / ▼ E4")
│                 CASCADA GRAVES (A0–E4)                        │
│   ▮▮        ▮             ▮▮           ▮                      │  ≈ 520 px
│━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│
│ ▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ▌█▌█▐ ▌█▌█▌█▐ ...│  ≈ 340 px  TECLADO A0–E4
└───────────────────────────────────────────────────────────────┘
```

- La **proporción entre cascada y teclado es ajustable**, arrastrando el separador. También hay presets: *Más cascada* · *Equilibrado* · *Más teclado*.
- **Orden de las filas configurable**: graves arriba o graves abajo. Por defecto los **graves van abajo**, porque la mano izquierda descansa más cómoda en la parte inferior de la tablet.
- Opción de **cascada única**: una sola cascada arriba con las dos filas de teclado debajo. Las notas usan dos colores de carril para indicar a qué fila van.

### 6.2 Teclado reducido (ahorro de espacio)

Usa **una sola fila con teclas más grandes**. Tiene varios submodos:

1. **Auto‑rango (recomendado)**: analiza la canción y muestra **solo el rango que usa**, más un margen. Por ejemplo, si va de C3 a G5 muestra unas 3 octavas, con teclas blancas de unos 15 mm.
2. **Tamaños fijos**: 25 · 37 · 49 · 61 teclas, como los teclados MIDI reales.
3. **Seguimiento inteligente**: la ventana del teclado **se desliza suavemente** hacia donde vienen las próximas notas, 2 segundos antes de que lleguen.
4. **Octavas plegables**: las octavas que la canción no usa se **colapsan en una franja fina** con el número de octava, y se pueden expandir tocándolas.
5. **Gesto de zoom**: pellizcar para cambiar el número de teclas y arrastrar con dos dedos para desplazarse.
6. **Mini‑mapa**: una franja fina arriba con las 88 teclas y un rectángulo que marca la zona visible. Si una nota cae fuera de la vista, aparece una **flecha en el borde** que avisa hacia dónde ir.

### 6.3 Otros diseños (posteriores)

- **Dos manos independientes**: dos ventanas de teclado reducidas, una por mano, cada una con su propio seguimiento.
- **Modo estudio**: medio teclado en pantalla y la otra mitad con la partitura en pentagrama.

---

## 7. Ecualización

> "Ecualizar el teclado" puede significar dos cosas, así que el plan cubre las dos: **(A) el sonido** y **(B) la respuesta y el tamaño del teclado**.

### 7.1 Ecualización del sonido

| Ajuste | Opciones |
|---|---|
| **Presets de timbre** | Gran Cola de Concierto · Brillante (pop) · Cálido (jazz) · Vintage (vertical) · Íntimo (con sordina) · Eléctrico (estilo Rhodes) |
| **EQ gráfico de 5 bandas** | 60 Hz · 250 Hz · 1 kHz · 4 kHz · 12 kHz, de ±12 dB |
| **EQ paramétrico** (avanzado) | 3 bandas con frecuencia, ganancia y Q |
| **EQ por registro** | Ganancia distinta para graves, medios y agudos del teclado (por zonas de teclas, no por frecuencia) |
| **Reverb** | Habitación · Estudio · Sala de conciertos · Catedral, con tamaño y mezcla |
| **Resonancia y realismo** | Resonancia simpática de cuerdas, ruido de macillos y de pedal, *release* |
| **Afinación** | La4 = 415–466 Hz (440 por defecto) y temperamentos: igual, justo, Werckmeister o mesotónico |
| **Transposición** | ±12 semitonos (la cascada también se mueve) |
| **Salida** | Perfil "altavoces de la tablet", que compensa la falta de graves, y perfil "auriculares" (plano) |

### 7.2 Ecualización del teclado (respuesta y tamaño)

| Ajuste | Opciones |
|---|---|
| **Curva de velocidad** | Suave · Normal · Dura · Fija (sin dinámica) · Personalizada (curva editable) |
| **Cómo se calcula la fuerza en pantalla táctil** | Por **área de contacto** del dedo, por **velocidad de bajada** (estimada a partir del tamaño del toque) o fija. Se calibra en el tutorial. |
| **Sensibilidad por zonas** | Más o menos sensibilidad en graves, medios y agudos |
| **Tamaño de teclas** | Altura del teclado, proporción entre teclas negras y blancas, y ancho de las negras |
| **Deslizar entre teclas** | Activado (glissando) o desactivado (evita notas accidentales) |
| **Rechazo de palma** | Ignora toques grandes o apoyos accidentales |
| **Pedal virtual** | Botón en pantalla, pedal Bluetooth (pasapáginas) o pedal del piano MIDI |

---

## 8. Modos de señalización

Se pueden combinar libremente. También hay **presets** para no tener que configurarlo todo:

| Preset | Combinación |
|---|---|
| **Principiante** | Cascada + nombres de nota + teclas iluminadas + digitación + modo Espera |
| **Intermedio** | Cascada + colores por mano + teclas iluminadas, sin nombres |
| **Lector** | Partitura que se desplaza + iluminación mínima |
| **Reto** | Cascada sin etiquetas y sin iluminar teclas |

### 8.1 Elementos de señalización

1. **Cascada (notas que caen)**: el modo de la imagen de referencia.
2. **Teclas iluminadas**: la tecla se enciende con el color de la nota cuando toca pulsarla.
3. **Pre‑aviso**: la tecla empieza a brillar tenuemente un momento antes (configurable entre 0 y 1 s).
4. **Etiquetas de nota**: inglés (C D E) · latino (Do Re Mi) · números MIDI · solfeo móvil (relativo a la tonalidad) · ninguna. Se pueden poner **en la barra, en la tecla o en ambas**.
5. **Digitación**: números del 1 al 5 en la barra. Salen del MusicXML o se calculan con un algoritmo automático.
6. **Partitura que se desplaza**: pentagrama (Verovio) arriba, con un cursor sincronizado.
7. **Carriles tipo "Guitar Hero"**: guías verticales tenues que ayudan a seguir cada nota hasta su tecla.
8. **Flechas fuera de pantalla** en el teclado reducido.
9. **Feedback de acierto**: verde si es correcta y a tiempo, amarillo si es correcta pero adelantada o retrasada (en ms), rojo si la nota es incorrecta, gris si la soltaste antes de tiempo.

### 8.2 Esquemas de color de las notas

| Esquema | Descripción |
|---|---|
| **Por mano** (por defecto) | Derecha **azul neón** · izquierda **verde/cian**. Es el estilo de la referencia. |
| **Por nota (cromático)** | Un color por cada una de las 12 notas, en círculo cromático (Do rojo → Si violeta) |
| **Por octava** | El tono varía según la altura: graves cálidos, agudos fríos |
| **Por duración** | Las notas cortas son claras y las largas saturadas |
| **Por dedo** | Un color por cada dedo del 1 al 5 |
| **Por dinámica** | La intensidad o el brillo según la velocidad (p, mf o f) |
| **Monocromo** | Un solo color, para lectores avanzados |
| **Daltonismo** | Paletas seguras para protanopía, deuteranopía y tritanopía, con patrones o texturas además del color |

Temas de fondo: **Neón** (el de la referencia) · Clásico (madera y marfil) · Minimal claro · Alto contraste.

---

## 9. Entrada: cómo sabe la app lo que tocas

| Fuente | Latencia | Polifonía | Notas |
|---|---|---|---|
| **Teclado táctil en pantalla** | ~10–20 ms | 10 dedos | Siempre disponible |
| **Piano MIDI por USB‑C / BLE** | ~5–10 ms | Total | Lo mejor para practicar en serio. Se detecta automáticamente al conectarlo. |
| **Micrófono (piano acústico)** | ~60–120 ms | Buena para 3–4 notas a la vez | Basic Pitch en TFLite. El evaluador compensa la latencia. Hay calibración de ruido ambiente. |

**Evaluador**: compara cada nota esperada con lo que se ha tocado dentro de una ventana de tolerancia (±80 ms en *Fácil*, ±50 ms en *Normal* y ±30 ms en *Experto*). Mide nota correcta, adelanto o retraso, duración mantenida y dinámica.

---

## 10. Tutorial de inicio guiado

Es un **tutorial interactivo**: el usuario aprende tocando, no leyendo. Se puede **saltar y retomar**, y la app **recuerda el paso** en el que se quedó.

### 10.1 Flujo de onboarding (primera vez, unos 5 minutos)

| Paso | Pantalla | Qué hace el usuario | Objetivo |
|---|---|---|---|
| 1 | **Bienvenida** | Ve una animación corta de la cascada tocando una melodía conocida de dominio público | Que entienda en 10 s qué hace la app |
| 2 | **Tu nivel** | Elige: *Nunca he tocado* · *Sé algo* · *Leo partituras* | Configura los presets de señalización y la dificultad |
| 3 | **¿Cómo vas a tocar?** | Elige: *En la pantalla* · *Con piano MIDI* (prueba de conexión) · *Con piano acústico* (permiso de micrófono y prueba) | Configura la entrada |
| 4 | **Postura y orientación** | Ilustración de cómo colocar la tablet en horizontal sobre una mesa o un atril | Ergonomía |
| 5 | **Elige tu teclado** | Previsualización en vivo de *Completo en 2 filas* frente a *Reducido*, para tocar y comparar | Que elija el diseño |
| 6 | **Calibra tu toque** | "Toca suave… ahora fuerte" (5 pulsaciones de cada) | Ajusta la curva de velocidad (§7.2) |
| 7 | **Leer la cascada** | Caen 3 notas lentas, la app resalta "esta es la barra", "esta es la línea", "pulsa cuando llegue" y espera | Que entienda la mecánica básica |
| 8 | **Duración** | Una nota larga: "mantén pulsado hasta que la barra se acabe" | Que entienda la duración |
| 9 | **Tu primera canción** | Toca *Himno de la alegría* (dominio público) en modo Espera, con la derecha | Primer logro 🎉 |
| 10 | **Importa la tuya** | Guía de "+ Añadir canción" con un MIDI de ejemplo incluido | Que conozca la función estrella |
| 11 | **Mini tour de controles** | Marcas sobre los controles: velocidad, bucle A‑B, manos y ajustes | Que conozca el resto de herramientas |

### 10.2 Ayuda contextual posterior

- **Marcas contextuales** (*coach marks*) la primera vez que el usuario entra en cada pantalla, por ejemplo la primera vez que abre el ecualizador.
- **"¿Sabías que…?"**: consejos breves en las pantallas de carga.
- **Centro de ayuda** con vídeos cortos de 30 s por función.
- **Botón "Repetir tutorial"** en Ajustes.

### 10.3 Academia (fase posterior)

Lecciones progresivas: notas blancas → negras → ritmo → acordes → manos juntas → lectura de pentagrama. Cada lección es un `Song` con objetivos ("3 veces seguidas con más del 90 %").

---

## 11. Otras pantallas

- **Inicio / Biblioteca**: cuadrícula de canciones con portada, dificultad estimada (★), duración y progreso (%). Filtros: importadas · favoritas · en progreso · lecciones.
- **Detalle de canción**: vista previa, elección de pista o mano, dificultad, mejor puntuación e historial.
- **Estadísticas**: minutos practicados al día, racha de días, precisión por canción y compases problemáticos (mapa de calor sobre la partitura).
- **Ajustes**: Sonido (EQ) · Teclado · Señalización · Entrada (MIDI/micrófono) · Tutorial · Cuenta y sincronización · Accesibilidad.
- **Editor simple** (fase 3): corregir notas, cambiar la mano o el dedo, y anotar con el **S Pen**.

---

## 12. Rendimiento y latencia (presupuestos)

| Métrica | Objetivo | Cómo se consigue |
|---|---|---|
| Del toque al sonido (pantalla) | **< 25 ms** | Oboe en modo MMAP exclusivo, buffer de 2 bursts, la entrada táctil va directa al hilo de audio por una cola *lock‑free* |
| De MIDI al sonido | **< 12 ms** | Callback de MIDI → cola → hilo de audio |
| Frames de la cascada | **120 fps estables** (p99 < 8,3 ms) | Instancing en GL: un *draw call* para todas las notas, y *bloom* a media resolución |
| Arranque en frío | < 1,5 s | Las muestras se cargan en diferido: primero la capa media y el resto en segundo plano |
| Memoria | < 800 MB | Muestras en 16 bits y *streaming* desde disco para las capas poco usadas |
| Sincronía entre audio y vídeo | < 5 ms de deriva | El reloj maestro es la **posición de audio** (`AAudioStream_getTimestamp`), no el reloj del sistema |
| Batería | ≥ 4 h de práctica continua | Fondo negro en AMOLED y bajar a 60 Hz cuando está en pausa |

---

## 13. Accesibilidad

- Paletas para daltonismo (§8.2) y patrones además del color.
- Tamaño de texto y etiquetas escalable.
- Modo zurdo: invierte el orden de las filas y el lado de los controles.
- TalkBack en todos los menús. La cascada no se lee en voz, pero sí hay "notas habladas" opcionales.
- Reducción de movimiento: sin partículas ni *bloom*.

---

## 14. Estructura del proyecto (código)

```
piano-cascada/
├── app/                      # Módulo Android principal (Compose, navegación)
├── core/
│   ├── model/                # Song, Track, Note… (Kotlin puro)
│   ├── importers/            # MIDI, MusicXML, cliente de la API de backend
│   ├── transport/            # Reloj, bucle, modo Espera
│   ├── evaluator/            # Puntuación y feedback
│   └── data/                 # Room, DataStore, repositorios
├── feature/
│   ├── library/
│   ├── practice/             # Pantalla de práctica (contenedor Compose + GL)
│   ├── settings/
│   ├── onboarding/           # Tutorial guiado
│   └── stats/
├── render/                   # Motor OpenGL: cascada, teclado, partículas, bloom
├── audio/                    # C++: Oboe, sampler SFZ, EQ biquad, reverb (JNI)
├── input/                    # Táctil, MIDI, micrófono + TFLite
├── assets/
│   ├── samples/              # Salamander Grand (SFZ)
│   ├── songs/                # Canciones de dominio público para el tutorial
│   └── models/               # basic_pitch.tflite
└── backend/
    ├── api/                  # FastAPI
    ├── workers/              # Transcripción de audio, OMR, separación de manos
    └── docker/               # Imágenes con GPU
```

---

## 15. Hoja de ruta

| Fase | Duración aprox. | Entregable |
|---|---|---|
| **0. Prototipos técnicos** | 2 semanas | (a) Latencia real con Oboe en la S8 Ultra, (b) cascada GL a 120 fps con 2000 notas, (c) prueba de modelos de transcripción con 20 MP3 de piano |
| **1. MVP** | 6–8 semanas | Piano libre · importar **MIDI y MusicXML** · cascada con colores por mano y duración · **teclado de 2 filas** · modo Espera y Ritmo · velocidad y bucle A‑B · presets de sonido básicos · tutorial pasos 1–9 |
| **2. Audio y partituras** | 6 semanas | Backend con **MP3 → notas** y **PDF/foto → notas** · separación de manos · revisión antes de guardar · notificaciones |
| **3. Personalización completa** | 4–5 semanas | **Teclado reducido** (auto‑rango, seguimiento, plegado y mini‑mapa) · **EQ completo** de sonido y de teclado · todos los **modos de señalización** y esquemas de color · partitura que se desplaza |
| **4. Entradas avanzadas** | 3–4 semanas | Piano MIDI por USB/BLE · micrófono con Basic Pitch · calibración de latencia |
| **5. Pulido y lanzamiento** | 4 semanas | Estadísticas · Academia (10 lecciones) · editor con S Pen · accesibilidad · beta cerrada · Galaxy Store y Google Play |

**Total estimado: unos 6–7 meses** con un equipo pequeño: 2 Android/Kotlin, 1 C++/audio a media jornada, 1 ML/backend, 1 diseñador UX/UI y apoyo de QA.

### 15.1 Alcance del MVP (qué entra y qué no)

- ✅ MIDI y MusicXML, cascada, teclado de 2 filas, modos Espera y Ritmo, tutorial básico y 3 presets de sonido.
- ❌ MP3, PDF, micrófono, teclado reducido, Academia y nube. Todo eso llega en las fases 2–5.

---

## 16. Pruebas y calidad

- **Unitarias**: parsers (MIDI y MusicXML, con un corpus de más de 200 archivos), evaluador y mapa de tempo.
- **Rendimiento**: Macrobenchmark y JankStats en una S8 Ultra real. Fallo si p99 > 8,3 ms.
- **Latencia**: medición con micrófono externo y la app *OboeTester* (del toque al sonido).
- **Transcripción**: métricas F1 de onset y offset con el dataset **MAESTRO**. Objetivo: F1 de notas > 0,9 en piano solo.
- **OMR**: un conjunto de 50 PDF de prueba con su MusicXML de referencia.
- **Usabilidad**: 8–10 personas de distintos niveles hacen el tutorial sin ayuda. Objetivo: más del 80 % termina la primera canción.

---

## 17. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La transcripción de MP3 es imprecisa en canciones con banda o voz | Alto | Separar fuentes con Demucs, dejar revisar y editar al usuario y avisar de que "funciona mejor con piano solo" |
| OMR con errores en PDF escaneados | Medio | Mostrar el original al lado, editor de corrección y priorizar MusicXML y MIDI |
| Latencia de audio por Bluetooth (más de 150 ms) | Medio | Detectar la salida BT, avisar y ofrecer compensación visual (adelantar la cascada) |
| Teclas de 12 mm pequeñas para manos grandes | Medio | Teclado reducido, solape configurable y zoom |
| Licencias (AGPL de Audiveris, modelos, muestras) | Medio | Revisión legal en la fase 0 y preferir componentes Apache, MIT o CC‑BY |
| Derechos de autor de lo que suben los usuarios | Medio | Uso privado, sin compartir públicamente, con términos de uso claros |
| Coste de GPU del backend | Medio | Colas, caché por *hash* del archivo, modo sin conexión con Basic Pitch y límites en el plan gratuito |

---

## 18. Modelo de negocio (propuesta)

- **Gratis**: piano libre, MIDI y MusicXML ilimitados, 3 importaciones de MP3 o PDF al mes y presets básicos.
- **Premium** (suscripción o pago único): importaciones ilimitadas, todos los sonidos y el EQ completo, Academia, estadísticas avanzadas y sincronización en la nube.

---

## 19. Próximos pasos inmediatos

1. Validar con el cliente cómo entiende **"ecualizar el teclado"** (sonido, respuesta o ambas). El §7 cubre las dos interpretaciones.
2. Hacer los **3 prototipos técnicos de la fase 0** en una Galaxy Tab S8 Ultra real.
3. Diseñar en Figma la **pantalla de práctica** (las dos filas y la reducida) y el **flujo del tutorial**.
4. Revisar las licencias: Salamander, Verovio, Audiveris u oemer y los modelos de transcripción.
5. Arrancar el MVP (fase 1).
