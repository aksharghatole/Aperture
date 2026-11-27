package com.aperture.photo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import com.aperture.photo.ui.PhotoOperations
import com.aperture.photo.ui.CameraScreen
import com.aperture.photo.ui.EditorScreen
import com.aperture.photo.ui.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val operations = ViewModelProvider(this)[PhotoOperations::class.java]
        setContent {
            val library = operations.library
            LaunchedEffect(library) {
                withContext(Dispatchers.IO) { library.recoverInterruptedWork() }
            }
            val screenState = rememberSaveableStateHolder()
            var inLibrary by rememberSaveable { mutableStateOf(false) }
            var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
            val photos = remember(operations.libraryRevision) { library.all() }
            val selected = photos.firstOrNull { it.file.name == selectedName }

            BackHandler(inLibrary) {
                if (selectedName != null) selectedName = null else inLibrary = false
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xffddf5a3),
                onPrimary = Color(0xff17200e),
                background = Color(0xff111413),
                onBackground = Color(0xfff4f5ed),
                surface = Color(0xff1b201d),
                onSurface = Color(0xfff4f5ed),
                onSurfaceVariant = Color(0xffaeb9ad),
                outline = Color(0xff424c43)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        when {
                            selected != null -> EditorScreen(selected, operations, { selectedName = null }) {
                                operations.libraryChanged()
                                selectedName = null
                            }
                            inLibrary -> LibraryScreen(photos, operations, { inLibrary = false }) {
                                operations.libraryChanged()
                                selectedName = it.file.name
                            }
                            else -> screenState.SaveableStateProvider("camera") {
                                CameraScreen(operations, photos.firstOrNull()) {
                                    if (!operations.captureBusy) {
                                        operations.libraryChanged()
                                        inLibrary = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
