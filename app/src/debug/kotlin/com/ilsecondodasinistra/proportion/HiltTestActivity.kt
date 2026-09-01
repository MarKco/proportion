package com.ilsecondodasinistra.proportion

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Empty activity that participates in the Hilt graph, so a Compose test can host screens whose
 * ViewModels are injected. Debug source set only: it never ships in a release build.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
