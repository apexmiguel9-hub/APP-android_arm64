package com.epai.oblender.ui.screens

import kotlinx.serialization.Serializable

sealed interface NormalNavKey : TitledNavKey {
    @Serializable data object OverView : NormalNavKey
    @Serializable data object Config : NormalNavKey
}
