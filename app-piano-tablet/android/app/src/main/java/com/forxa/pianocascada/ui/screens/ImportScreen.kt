package com.forxa.pianocascada.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.theme.PC

@Composable
fun ImportScreen(app: PianoApp, onBack: () -> Unit, onImported: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(PC.bg).padding(48.dp)) {
        IconBtn(G.BACK, onBack)
        Text("Añadir canción", color = PC.text1, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 24.dp))
    }
}
