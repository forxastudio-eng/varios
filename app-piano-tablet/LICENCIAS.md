# Licencias y atribuciones

| Componente | Origen | Licencia |
|---|---|---|
| Muestras de piano (`piano.bin`) | «Yamaha Grand Piano» de **FluidR3_GM** — Frank Wen | MIT |
| Modelo de transcripción de audio (`nmp.onnx`) | **Basic Pitch** — Spotify | Apache 2.0 |
| Lector de partituras PDF/foto dentro de la app (módulo `omr`: traducción a Kotlin del método de **homr**) y sus tres modelos ONNX (`segnet`, `encoder`, `decoder`, ≈ 150 MB, se descargan al compilar con `tools/descargar_modelos_omr.sh`) | **homr** — Lukas Lang («liebharc») | AGPL-3.0 |
| Visión por computador en la tablet | **OpenCV** (paquete `com.quickbirdstudios:opencv`) | Apache 2.0 |
| Ejecución de los modelos | **ONNX Runtime** — Microsoft | MIT |
| Servidor opcional de lectura de partituras (carpeta `servidor/`, solo para desarrollo y comparaciones) | **Audiveris** | AGPL-3.0 |
| Lectura de páginas PDF en ese servidor | **PyMuPDF** | AGPL-3.0 (uso personal) |
| Partituras exactas del repertorio | Corpus de **music21** (MIT, Cuthbert Lab) con obras de dominio público | Dominio público (obras) |
| Transcripciones manuales (Himno de la alegría, Para Elisa, etc.) | Elaboración propia a partir de obras de dominio público | Propia |

Las obras (Bach, Mozart, Beethoven, Schubert, Verdi, Grieg, Haydn…) son de dominio público.
Las codificaciones del corpus de music21 se usan como datos de partitura; la app es de uso personal.

## Repertorio actual: Para Elisa y Claro de luna

Las notas de estas dos obras (Beethoven, dominio público) se reconstruyeron por **consenso de varias versiones MIDI
independientes** del conjunto público *ADL Piano MIDI* (github.com/lucasnfe/adl-piano-midi): cada nota solo se conserva si
la apoya la mayoría de las fuentes. El script `repertorio/tools/construir_desde_midi.py` documenta el proceso y qué notas se
descartaron. Los archivos MIDI de origen **no** se redistribuyen en este repositorio. El repertorio anterior (17 piezas) está
archivado en `repertorio/archivo/`.


## Partituras de terceros (p. ej. «Glimpse of Us»)
El repositorio es público, así que **no incluye partituras ni transcripciones con derechos de autor** (solo las dos obras
de dominio público del repertorio). Las canciones propias se añaden desde la app («Añadir canción»), que guarda el
resultado únicamente en la tablet. Para llevar una canción ya preparada a la tablet existe la opción «Canción
preparada (.json)», que funciona sin internet.
