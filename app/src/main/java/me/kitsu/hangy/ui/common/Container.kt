package me.kitsu.hangy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import me.kitsu.hangy.HangyApplication
import me.kitsu.hangy.di.AppContainer

/** Retrieves the app-wide manual DI container from the running [HangyApplication]. */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as HangyApplication).container
