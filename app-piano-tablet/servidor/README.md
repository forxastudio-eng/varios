# Servidor de partituras (PDF y fotos → notas)

La app lee **MP3/audio y MIDI directamente en la tablet**. Para **PDF y fotos de partituras** hace falta este pequeño
servidor en tu ordenador de casa, porque el reconocimiento óptico musical (**Audiveris**) necesita Java y bastante memoria.

## Puesta en marcha (una sola vez)

1. Instala [Docker](https://www.docker.com/products/docker-desktop/) en tu ordenador (Windows, Mac o Linux).
2. Abre una terminal en esta carpeta (`app-piano-tablet/servidor`) y ejecuta:

   ```
   docker compose up --build
   ```

   La primera vez tarda unos minutos (descarga Audiveris). Al terminar verás algo así:

   ```
   Servidor de partituras listo. Escribe en la app (Añadir canción → Servidor en casa):
        http://192.168.1.20:8765
   ```

3. En la tablet (misma red Wi‑Fi): **Añadir canción → Servidor en casa**, escribe esa dirección y pulsa **Probar conexión**.
4. Elige **Partitura en PDF o foto**. Tarda entre 20 s y 2 min según las páginas.

Para usarlo fuera de casa se puede combinar con [Tailscale](https://tailscale.com) (la dirección pasa a ser la de Tailscale).

## Qué esperar de la precisión

El servidor prepara la partitura antes de leerla, porque **el tamaño del pentagrama decide la calidad**: medido con
partituras de prueba, a 19 px entre líneas Audiveris confunde las figuras pequeñas (en una pieza de Bach solo acertó el
40 % de las notas) y a ~28 px las lee perfectamente (100 %).

- **PDF**: mide el pentagrama de la primera página y elige la resolución de lectura (entre 200 y 700 ppp) para que mida ≈ 28 px.
- **Foto o escaneo**: corrige la orientación, quita sombras y degradados de luz, endereza la imagen y la escala al tamaño ideal.

Con **PDF nítidos de programas de notación** (MuseScore, LilyPond, Finale, descargas de IMSLP/Mutopia) la lectura es
muy buena. Con **fotos** depende de la calidad: recta, bien iluminada y sin sombras fuertes. Las partituras muy densas
(varias voces por pentagrama, como el Preludio en Do de Bach) son las más difíciles. Si algún compás sale mal, la app avisa
(«Compás 5: dura 3 pulsos y debería durar 4»).

La precisión real medida está en el flujo de GitHub Actions **«Medir la precisión del OMR»** (`tests/eval_omr.py`), con tres cifras:
*contenido* (¿ves las notas correctas y en orden?), *ritmo* y *estricta* (nota por nota en el mismo instante; un compás de
más o de menos desplaza todo lo posterior y la hunde aunque las notas estén bien).

## API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/health` | `{"ok": true, "omr": true}` |
| POST | `/omr` | multipart `file` (PDF, imagen o MusicXML) → `{"job": "…"}` |
| GET | `/jobs/{job}` | `{"status", "progress", "message", "result", "warnings", "error"}` |

Los MusicXML (`.musicxml`, `.mxl`) se convierten directamente sin pasar por Audiveris.

## Pruebas

```
pip install -r requirements.txt pytest httpx
pytest tests
```
