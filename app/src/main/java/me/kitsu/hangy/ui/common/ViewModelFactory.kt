package me.kitsu.hangy.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Tiny helper that adapts a lambda into a [ViewModelProvider.Factory]. Lets screens construct
 * ViewModels from the manual [me.kitsu.hangy.di.AppContainer] without a DI framework.
 */
inline fun <reified VM : ViewModel> viewModelFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
