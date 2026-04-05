package com.thindie.shadowcloud.uikit

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun VSpacer(value: Dp) = Spacer(Modifier.height(value))
@Composable
fun HSpacer(value: Dp) = Spacer(Modifier.width(value))
@Composable
fun RowScope.WSpacer(value: Float = 1f) = Spacer(Modifier.weight(value))
@Composable
fun ColumnScope.WSpacer(value: Float = 1f) = Spacer(Modifier.weight(value))