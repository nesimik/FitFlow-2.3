package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.ui.AppViewModel
import com.example.ui.screens.ProgramAdvisorScreen

/** 2.25: Akıllı öneriler artık ProgramAdvisor tabanlı tam ekran sayfada. */
@Composable
fun WeakLinkRecommendationDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) = ProgramAdvisorScreen(vm, onDismiss)
