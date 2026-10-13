package com.forxa.pianocascada.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Abre el selector de archivos del sistema. Devuelve null si se cancela. */
fun pickFile(title: String, extensions: List<String> = emptyList()): File? {
    val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (extensions.isNotEmpty()) dlg.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
    dlg.isVisible = true
    val f = dlg.file ?: return null
    return File(dlg.directory, f)
}
