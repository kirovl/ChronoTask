package io.github.chronotask.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.chronotask.sample.android.ui.ChronoTaskDashboardScreen
import io.github.chronotask.sample.android.ui.theme.ChronoTaskTheme
import io.github.chronotask.sample.android.viewmodel.ChronoTaskViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChronoTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChronoTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChronoTaskDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
