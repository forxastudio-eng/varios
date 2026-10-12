package com.forxa.pianocascada.transcribe

// Generado por tools/train_note_filter.py — no editar a mano.
object NoteQualityWeights {
    fun install() {
        NoteQuality.weights = floatArrayOf(2.65308f, 0.09411f, 0.51821f, -0.51498f, 1.61384f, -0.26817f, 0.04570f, -1.01828f, -0.92789f, 0.05327f, 0.10120f, 0.86789f)
        NoteQuality.bias = 1.09133f
        NoteQuality.mean = floatArrayOf(0.55152f, 0.35944f, -1.13056f, 0.75753f, 0.27082f, 0.22554f, 0.23333f, 0.21094f, 0.21086f, 0.16837f, 0.41594f, 0.88737f)
        NoteQuality.scale = floatArrayOf(0.14748f, 0.29209f, 0.78956f, 0.20289f, 0.44438f, 0.43366f, 0.44204f, 0.40797f, 0.40792f, 0.08371f, 0.16937f, 0.31614f)
        NoteQuality.trained = true
    }
}
