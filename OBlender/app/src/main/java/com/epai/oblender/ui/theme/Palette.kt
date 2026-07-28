package com.epai.oblender.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

@Composable
fun cardColor(influencedByBackground: Boolean) = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun onCardColor() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun itemColor(influencedByBackground: Boolean) = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun onItemColor() = MaterialTheme.colorScheme.onSurfaceVariant
