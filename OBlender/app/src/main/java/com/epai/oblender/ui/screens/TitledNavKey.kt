package com.epai.oblender.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer

interface TitledNavKey : NavKey

@Composable
fun rememberTitledNavBackStack(vararg elements: TitledNavKey): NavBackStack<TitledNavKey> {
    return rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer())
    ) { NavBackStack(*elements) }
}
