package com.forxa.pianocascada.omr

import com.forxa.pianocascada.core.Json
import com.forxa.pianocascada.core.asList
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba con una página fija (coral de Bach, dominio público) y los símbolos que da el lector original homr con los mismos modelos.
 * Se ejecuta con OMR_MODELS apuntando a la carpeta de los modelos (en CI se descargan con tools/descargar_modelos_omr.sh).
 */
class FixtureTest {
    @Test fun bachChoralePageReadsLikeHomrAndBecomesASong() {
        assumeTrue(TestEnv.models != null)
        TestEnv.loadOpenCv()
        val png = FixtureTest::class.java.getResourceAsStream("/fixtures/bach.png")!!.use { it.readBytes() }
        val img = Imgcodecs.imdecode(MatOfByte(*png), Imgcodecs.IMREAD_COLOR)
        val ref = Json.parse(String(FixtureTest::class.java.getResourceAsStream("/fixtures/bach_voices.json")!!.use { it.readBytes() }, Charsets.UTF_8))
            .asList().map { v -> v.asList().map { it as String } }

        val engine = OmrEngine(OmrModels(TestEnv.modelFile("segnet"), TestEnv.modelFile("encoder"), TestEnv.modelFile("decoder")), threads = 4)
        val result = engine.use { it.readPage(img) }
        assertEquals(ref.size, result.voices.size, "número de voces")
        var same = 0; var total = 0
        for ((i, rv) in ref.withIndex()) {
            val kv = result.voices[i].map { it.str() }
            total += rv.size
            for (k in rv.indices) if (k < kv.size && kv[k] == rv[k]) same++
        }
        println("símbolos idénticos a homr: $same de $total")
        assertEquals(total, same, "los símbolos deben coincidir con homr")

        val built = ScoreBuilder.build(listOf(result.systems), "bach", "Coral")
        val song = built.song
        println("canción: ${song.meter}, ${song.bars} compases, tonalidad ${song.key}, md ${song.rh.size}, mi ${song.lh.size}, avisos ${built.warnings}")
        assertEquals("4/4", song.meter)
        assertEquals("Eb", song.key)
        assertTrue(song.rh.isNotEmpty() && song.lh.isNotEmpty())
    }
}
