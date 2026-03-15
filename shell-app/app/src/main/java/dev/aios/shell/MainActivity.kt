// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.aios.shell.ui.AIOSApp
import dev.aios.shell.ui.theme.AIOSTheme

/// MainActivity is like your @main App struct in SwiftUI.
/// It's the entry point — sets up the Compose UI tree.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIOSTheme {
                AIOSApp()
            }
        }
    }
}
