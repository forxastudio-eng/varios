package com.forxa.pianocascada

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.applySound
import com.forxa.pianocascada.piano.HandsMode
import com.forxa.pianocascada.ui.components.BackHandler
import com.forxa.pianocascada.ui.screens.FreePianoScreen
import com.forxa.pianocascada.ui.screens.HomeScreen
import com.forxa.pianocascada.ui.screens.ImportScreen
import com.forxa.pianocascada.ui.screens.LibraryScreen
import com.forxa.pianocascada.ui.screens.OnboardingScreen
import com.forxa.pianocascada.ui.screens.PracticeParams
import com.forxa.pianocascada.ui.screens.PracticeScreen
import com.forxa.pianocascada.ui.screens.SettingsScreen
import com.forxa.pianocascada.ui.screens.SongDetailScreen
import com.forxa.pianocascada.ui.theme.PC

sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data class Detail(val songId: String) : Screen
    data class Practice(val params: PracticeParams) : Screen
    data object Free : Screen
    data object Settings : Screen
    data object Onboarding : Screen
    data object Import : Screen
}

@Composable
fun AppRoot(app: PianoApp, startScreen: Screen? = null) {
    val settings by app.settings.state.collectAsState()
    val songs by app.songs.songs.collectAsState()
    val progress by app.progress.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(startScreen ?: if (app.settings.value.onboardingDone) Screen.Home else Screen.Onboarding) }

    LaunchedEffect(settings.sound) { if (app.audio.ready) applySound(app.audio, settings.sound) }

    if (!app.audio.ready) {
        Box(Modifier.fillMaxSize().background(PC.bg), contentAlignment = Alignment.Center) {
            Text("No se pudo iniciar el motor de audio${app.audio.lastError?.let { ": $it" } ?: "."}", color = PC.text1)
        }
        return
    }

    BackHandler(enabled = screen is Screen.Library || screen is Screen.Settings || screen is Screen.Import || screen is Screen.Free) { screen = Screen.Home }
    BackHandler(enabled = screen is Screen.Detail) { screen = Screen.Library }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            songs, progress,
            onContinue = { song -> screen = Screen.Detail(song.id) },
            onLibrary = { screen = Screen.Library }, onFree = { screen = Screen.Free },
            onSettings = { screen = Screen.Settings }, onTutorial = { screen = Screen.Onboarding },
            onImport = { screen = Screen.Import },
        )
        Screen.Library -> LibraryScreen(
            songs, progress, onBack = { screen = Screen.Home }, onOpen = { screen = Screen.Detail(it.id) },
            onFavorite = { app.progress.toggleFavorite(it.id) }, onImport = { screen = Screen.Import },
        )
        is Screen.Detail -> {
            val song = songs.firstOrNull { it.id == s.songId }
            if (song == null) LaunchedEffect(s) { screen = Screen.Library } else SongDetailScreen(
                song, progress[song.id], settings, onBack = { screen = Screen.Library },
                onFavorite = { app.progress.toggleFavorite(song.id) },
                onStart = { p -> screen = Screen.Practice(p) },
                onDelete = if (song.isUserSong) ({ app.songs.deleteUserSong(song.id); screen = Screen.Library }) else null,
            )
        }
        is Screen.Practice -> PracticeScreen(
            app, s.params, settings,
            onExit = { screen = s.params.songId?.let { Screen.Detail(it) } ?: Screen.Home },
            onRetryWith = { p -> screen = Screen.Practice(p) },
        )
        Screen.Free -> FreePianoScreen(app, settings, onExit = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(app, settings, onBack = { screen = Screen.Home })
        Screen.Onboarding -> OnboardingScreen(app, settings) { startId ->
            app.settings.update { it.copy(onboardingDone = true) }
            screen = if (startId != null) {
                Screen.Practice(PracticeParams(startId, 1, HandsMode.RIGHT, PracticeMode.WAIT, 0.7f, true, LayoutMode.REDUCED))
            } else Screen.Home
        }
        Screen.Import -> ImportScreen(app, settings, onBack = { screen = Screen.Library }, onImported = { id -> screen = Screen.Detail(id) })
    }
}
