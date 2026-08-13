package com.example.shilv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.RootScreen
import com.example.shilv.ui.theme.ShiLvTheme

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShiLvTheme {
                RootScreen(model = model)
            }
        }
        model.start()
    }
}