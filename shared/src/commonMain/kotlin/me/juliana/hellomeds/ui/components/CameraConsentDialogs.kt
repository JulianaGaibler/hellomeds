// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.juliana.hellomeds.data.model.enums.DetectionMethod
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.camera_consent_basic_only
import me.juliana.hellomeds.shared.camera_consent_choose_method
import me.juliana.hellomeds.shared.camera_consent_continue
import me.juliana.hellomeds.shared.camera_consent_intro
import me.juliana.hellomeds.shared.camera_consent_method_ai_body_android
import me.juliana.hellomeds.shared.camera_consent_method_ai_body_ios
import me.juliana.hellomeds.shared.camera_consent_method_ai_learn_more
import me.juliana.hellomeds.shared.camera_consent_method_ai_learn_more_url
import me.juliana.hellomeds.shared.camera_consent_method_ai_title_android
import me.juliana.hellomeds.shared.camera_consent_method_ai_title_ios
import me.juliana.hellomeds.shared.camera_consent_method_basic_body_android
import me.juliana.hellomeds.shared.camera_consent_method_basic_body_ios
import me.juliana.hellomeds.shared.camera_consent_method_basic_title
import me.juliana.hellomeds.shared.camera_consent_privacy_android
import me.juliana.hellomeds.shared.camera_consent_privacy_apple_link
import me.juliana.hellomeds.shared.camera_consent_privacy_apple_url
import me.juliana.hellomeds.shared.camera_consent_privacy_google_link
import me.juliana.hellomeds.shared.camera_consent_privacy_google_url
import me.juliana.hellomeds.shared.camera_consent_privacy_ios
import me.juliana.hellomeds.shared.camera_consent_title
import me.juliana.hellomeds.shared.camera_consent_will_download
import me.juliana.hellomeds.shared.gemini_download_failed
import me.juliana.hellomeds.shared.gemini_download_progress
import me.juliana.hellomeds.shared.gemini_download_retry
import me.juliana.hellomeds.shared.gemini_download_tip_binding
import me.juliana.hellomeds.shared.gemini_download_tip_feature_not_found
import me.juliana.hellomeds.shared.gemini_download_tip_network
import me.juliana.hellomeds.shared.gemini_download_title
import me.juliana.hellomeds.shared.gemini_download_use_basic
import org.jetbrains.compose.resources.stringResource

/**
 * Unified camera consent dialog for both Android and iOS.
 *
 * When [isAiAvailable] is true, the user picks between an on-device AI engine
 * (Gemini Nano on Android, Apple Intelligence on iOS) and a basic text extractor
 * via radio buttons. There is no default selection — Continue stays disabled until
 * the user picks one.
 *
 * When [isAiAvailable] is false, only the basic path is shown.
 */
@Composable
fun CameraConsentDialog(
    isApplePlatform: Boolean,
    isAiAvailable: Boolean,
    geminiNeedsDownload: Boolean = false,
    onContinue: (DetectionMethod) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedMethod by remember { mutableStateOf<DetectionMethod?>(null) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(Res.string.camera_consent_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.camera_consent_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = cameraPrivacyAnnotatedString(isApplePlatform = isApplePlatform),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    if (isAiAvailable) {
                        Text(
                            text = stringResource(Res.string.camera_consent_choose_method),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        MethodRadioOption(
                            selected = selectedMethod == DetectionMethod.GEMINI,
                            onSelect = { selectedMethod = DetectionMethod.GEMINI },
                            title = stringResource(
                                if (isApplePlatform) {
                                    Res.string.camera_consent_method_ai_title_ios
                                } else {
                                    Res.string.camera_consent_method_ai_title_android
                                },
                            ),
                            body = if (isApplePlatform) {
                                appleIntelligenceBodyAnnotatedString()
                            } else {
                                buildAnnotatedString {
                                    append(stringResource(Res.string.camera_consent_method_ai_body_android))
                                }
                            },
                        )

                        MethodRadioOption(
                            selected = selectedMethod == DetectionMethod.HEURISTIC,
                            onSelect = { selectedMethod = DetectionMethod.HEURISTIC },
                            title = stringResource(Res.string.camera_consent_method_basic_title),
                            body = buildAnnotatedString {
                                append(
                                    stringResource(
                                        if (isApplePlatform) {
                                            Res.string.camera_consent_method_basic_body_ios
                                        } else {
                                            Res.string.camera_consent_method_basic_body_android
                                        },
                                    ),
                                )
                            },
                        )

                        if (!isApplePlatform &&
                            selectedMethod == DetectionMethod.GEMINI &&
                            geminiNeedsDownload
                        ) {
                            Text(
                                text = stringResource(Res.string.camera_consent_will_download),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.camera_consent_basic_only),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.action_cancel))
                    }

                    Button(
                        onClick = {
                            val method = if (isAiAvailable) selectedMethod else DetectionMethod.HEURISTIC
                            if (method != null) onContinue(method)
                        },
                        enabled = !isAiAvailable || selectedMethod != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.camera_consent_continue))
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodRadioOption(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    body: androidx.compose.ui.text.AnnotatedString,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Annotated string for the camera privacy paragraph (ML Kit / Vision framework) with the
 * vendor's privacy policy link inlined. Shared between the consent dialog and the
 * camera settings subpage.
 */
@Composable
fun cameraPrivacyAnnotatedString(isApplePlatform: Boolean): androidx.compose.ui.text.AnnotatedString {
    val template = stringResource(
        if (isApplePlatform) Res.string.camera_consent_privacy_ios else Res.string.camera_consent_privacy_android,
    )
    val linkText = stringResource(
        if (isApplePlatform) {
            Res.string.camera_consent_privacy_apple_link
        } else {
            Res.string.camera_consent_privacy_google_link
        },
    )
    val url = stringResource(
        if (isApplePlatform) {
            Res.string.camera_consent_privacy_apple_url
        } else {
            Res.string.camera_consent_privacy_google_url
        },
    )
    return inlineLinkAnnotatedString(template, linkText, url)
}

/**
 * Annotated string for the Apple Intelligence option body with the "Learn more" link inlined.
 * Used by both the consent dialog and the camera settings subpage.
 */
@Composable
fun appleIntelligenceBodyAnnotatedString(): androidx.compose.ui.text.AnnotatedString {
    val template = stringResource(Res.string.camera_consent_method_ai_body_ios)
    val linkText = stringResource(Res.string.camera_consent_method_ai_learn_more)
    val url = stringResource(Res.string.camera_consent_method_ai_learn_more_url)
    return inlineLinkAnnotatedString(template, linkText, url)
}

@Composable
private fun inlineLinkAnnotatedString(
    template: String,
    linkText: String,
    url: String,
): androidx.compose.ui.text.AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        val placeholder = "%1\$s"
        val index = template.indexOf(placeholder)
        if (index < 0) {
            append(template)
            return@buildAnnotatedString
        }
        append(template.substring(0, index))
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ),
            ),
        ) {
            append(linkText)
        }
        append(template.substring(index + placeholder.length))
    }
}

/**
 * Dialog showing Gemini Nano download progress.
 */
@Composable
fun GeminiDownloadDialog(
    downloadProgress: Float, // 0.0 to 1.0
    downloadedMB: Int,
    totalMB: Int,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = { }) { // Non-dismissible during download
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.gemini_download_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )

                Text(
                    text = stringResource(Res.string.gemini_download_progress, downloadedMB, totalMB),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        }
    }
}

/**
 * Dialog showing download error with helpful troubleshooting info.
 */
@Composable
fun GeminiDownloadErrorDialog(errorMessage: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.gemini_download_failed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(errorMessage)

                // Provide helpful context based on error type
                if (errorMessage.contains("BINDING_FAILURE", ignoreCase = true)) {
                    Text(
                        text = stringResource(Res.string.gemini_download_tip_binding),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (errorMessage.contains("FEATURE_NOT_FOUND", ignoreCase = true)) {
                    Text(
                        text = stringResource(Res.string.gemini_download_tip_feature_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (errorMessage.contains("Unable to resolve host", ignoreCase = true)) {
                    Text(
                        text = stringResource(Res.string.gemini_download_tip_network),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.gemini_download_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.gemini_download_use_basic))
            }
        },
    )
}
