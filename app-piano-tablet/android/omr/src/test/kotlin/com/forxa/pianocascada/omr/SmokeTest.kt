package com.forxa.pianocascada.omr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SmokeTest {
    @Test fun opencvAndOnnxLoad() {
        nu.pattern.OpenCV.loadLocally()
        val m = org.opencv.core.Mat(4, 4, org.opencv.core.CvType.CV_8UC1, org.opencv.core.Scalar(7.0))
        assertTrue(org.opencv.core.Core.mean(m).`val`[0] == 7.0)
        val dir = System.getenv("OMR_MODELS") ?: return
        val f = File(dir).walkTopDown().first { it.name.startsWith("segnet") && it.name.endsWith(".onnx") }
        val env = OrtEnvironment.getEnvironment()
        env.createSession(f.absolutePath).use { s ->
            println("entradas: " + s.inputInfo.keys + " salidas: " + s.outputInfo.keys)
            println(s.inputInfo.values.first().info)
            println(s.outputInfo.values.first().info)
        }
    }
}
