// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.support_contact_button_email
import me.juliana.hellomeds.shared.support_contact_button_github
import me.juliana.hellomeds.shared.support_contact_description
import me.juliana.hellomeds.shared.support_contact_email
import me.juliana.hellomeds.shared.support_contact_email_url
import me.juliana.hellomeds.shared.support_contact_github_url
import me.juliana.hellomeds.shared.support_contact_section
import me.juliana.hellomeds.shared.support_copy
import me.juliana.hellomeds.shared.support_generate
import me.juliana.hellomeds.shared.support_report_description
import me.juliana.hellomeds.shared.support_report_section
import me.juliana.hellomeds.shared.support_title
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.viewmodel.SupportViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(viewModel: SupportViewModel, onNavigateBack: () -> Unit) {
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )

    val reportText by viewModel.reportText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val email = stringResource(Res.string.support_contact_email)
    val emailUrl = stringResource(Res.string.support_contact_email_url)
    val githubUrl = stringResource(Res.string.support_contact_github_url)

    AppScaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.support_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== Contact section =====
            item {
                SettingsHeader(
                    text = stringResource(Res.string.support_contact_section),
                    isFirst = true,
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.support_contact_description, email),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.settingsContentPadding(),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { uriHandler.openUri(emailUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Text(
                            text = stringResource(Res.string.support_contact_button_email),
                            textAlign = TextAlign.Center,
                        )
                    }
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(githubUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Text(
                            text = stringResource(Res.string.support_contact_button_github),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ===== Diagnostic report section =====
            item {
                SettingsHeader(text = stringResource(Res.string.support_report_section))
            }
            item {
                Text(
                    text = stringResource(Res.string.support_report_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.settingsContentPadding(),
                )
            }

            if (reportText == null) {
                item {
                    Button(
                        onClick = { viewModel.generateReport() },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(stringResource(Res.string.support_generate))
                    }

                    if (isGenerating) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                item {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(reportText!!))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(stringResource(Res.string.support_copy))
                    }
                }

                item {
                    SelectionContainer {
                        Text(
                            text = reportText!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}
