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

El OMR funciona muy bien con **PDF nítidos generados por programas de notación** (MuseScore, LilyPond, Finale, descargas
de IMSLP/Mutopia). Con **fotos o escaneos** depende de la calidad: recta, bien iluminada y sin sombras. Si algún compás
sale mal, la app avisa («Compás 5: dura 3 pulsos y debería durar 4»).

## API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/health` | `{"ok": true, "omr": true}` |
| POST | `/omr` | multipart `file` (PDF, imagen o MusicXML) → `{"job": "…"}` |
| GET | `/jobs/{job}` | `{"status", "progress", "message", "result", "warnings", "error"}` |

Los MusicXML (`.musicxml`, `.mxl`) se convierten directamente sin pasar por Audiveris.

## Pruebas

```
pip install -r requirements.txt pytest
pytest tests
```
