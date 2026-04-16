package com.example.androidstyletransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.androidstyletransfer.ui.StyleTransferScreen
import com.example.androidstyletransfer.ui.StyleTransferViewModel
import com.example.androidstyletransfer.ui.theme.AndroidStyleTransferTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StyleTransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndroidStyleTransferTheme {
                StyleTransferScreen(
                    viewModel = viewModel,
                )
            }
        }
    }
}