// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3.entries

// CameraDetectionEntryScreen is provided by the app module (platform-specific)
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.StockStatus
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import me.juliana.hellomeds.ui.features.medication.AddMedicationFlowScreen
import me.juliana.hellomeds.ui.features.medication.EditMedicationScreen
import me.juliana.hellomeds.ui.features.medication.MedicationDetailScreen
import me.juliana.hellomeds.ui.features.medication.MedicationListScreen
import me.juliana.hellomeds.ui.features.medication.toAddMedicationState
import me.juliana.hellomeds.ui.features.onboarding.CriticalChannelSetupDialog
import me.juliana.hellomeds.ui.features.schedule.ScheduleBottomSheet
import me.juliana.hellomeds.ui.features.schedule.ScheduleScreen
import me.juliana.hellomeds.ui.features.settings.EditLabelScreen
import me.juliana.hellomeds.ui.features.stock.AddStockTrackingFlowScreen
import me.juliana.hellomeds.ui.features.stock.AdjustStockLevelBottomSheet
import me.juliana.hellomeds.ui.features.stock.BubbleLayoutEditorScreen
import me.juliana.hellomeds.ui.features.stock.StockTrackingDetailScreen
import me.juliana.hellomeds.ui.features.stock.StockTrackingSettingsScreen
import me.juliana.hellomeds.ui.util.canCriticalChannelBypassDnd
import me.juliana.hellomeds.ui.util.formatMedicationTypeAndStrength
import me.juliana.hellomeds.ui.viewmodel.ImportanceLabelViewModel
import me.juliana.hellomeds.ui.viewmodel.MedicationViewModel
import me.juliana.hellomeds.ui.viewmodel.ScheduleViewModel
import me.juliana.hellomeds.ui.viewmodel.StockTrackingViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the Medication List screen.
 * Shows all active medications with options to add, edit, and view details.
 */
@Composable
fun MedicationListScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onAddMedication: () -> Unit,
    onAddWithCamera: () -> Unit,
    onMedicationClick: (medicationId: Int) -> Unit,
) {
    val medicationViewModel: MedicationViewModel = koinViewModel()

    MedicationListScreen(
        medicationViewModel = medicationViewModel,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToSupport = onNavigateToSupport,
        onAddMedication = onAddMedication,
        onAddWithCamera = onAddWithCamera,
        onMedicationClick = onMedicationClick,
    )
}

/**
 * Entry point for the Medication Detail screen.
 * Shows detailed information about a specific medication.
 */
@Composable
fun MedicationDetailScreenEntry(
    medicationId: Int,
    onNavigateBack: () -> Unit,
    onEditSchedule: (medicationId: Int) -> Unit,
    onEditMedication: (medicationId: Int) -> Unit,
    onEditLabel: (medicationId: Int) -> Unit,
    onManageStock: (medicationId: Int) -> Unit,
) {
    platformContext()
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val labelViewModel: ImportanceLabelViewModel = koinViewModel()

    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)
    val allLabels by labelViewModel.allLabels.collectAsStateWithLifecycle()

    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    medication?.let { med ->
        val importanceLabel = allLabels.find { it.id == med.importanceLabelId }
        val typeAndStrength = formatMedicationTypeAndStrength(med)

        MedicationDetailScreen(
            medication = med,
            importanceLabel = importanceLabel,
            typeAndStrength = typeAndStrength,
            onNavigateBack = onNavigateBack,
            onEditSchedule = { onEditSchedule(medicationId) },
            onEditMedication = { onEditMedication(medicationId) },
            onEditLabel = { onEditLabel(medicationId) },
            onManageStock = { onManageStock(medicationId) },
            onDelete = {
                medicationViewModel.deleteMedication(med)
                onNavigateBack()
            },
            onArchive = {
                medicationViewModel.archiveMedication(medicationId)
                onNavigateBack()
            },
            onUnarchive = {
                medicationViewModel.unarchiveMedication(medicationId)
                onNavigateBack()
            },
            onNotesChange = { notes ->
                medicationViewModel.updateMedicationNotes(medicationId, notes)
            },
            onResetCycle = {
                medicationViewModel.startNewPack(medicationId)
            },
            onSetCycleDay = { day ->
                medicationViewModel.setCycleDay(medicationId, day)
            },
        )
    }
}

/**
 * Entry point for the Add Medication flow.
 * Multi-step wizard for adding a new medication.
 */
@Composable
fun AddMedicationScreenEntry(
    onClose: () -> Unit,
    onMedicationAdded: (Long, String, me.juliana.hellomeds.ui.features.medication.AddMedicationState) -> Unit,
    detectionData: MedicationDetectionResult? = null,
) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val labelViewModel: ImportanceLabelViewModel = koinViewModel()
    val labels by labelViewModel.allLabels.collectAsStateWithLifecycle()

    // Add flow always defaults to the non-critical FOLLOW_UPS label, so the
    // critical-channel education dialog is unreachable from here. The
    // equivalent flow still lives in EditLabelScreenEntry for users who later
    // pick a critical label.
    AddMedicationFlowScreen(
        importanceLabels = labels,
        onClose = onClose,
        onMedicationAdded = { medicationId, state ->
            onClose()
            onMedicationAdded(medicationId, state.name, state)
        },
        initialState = detectionData?.toAddMedicationState(),
        modifier = Modifier.fillMaxSize(),
        medicationViewModel = medicationViewModel,
    )
}

/**
 * Entry point for the Edit Medication screen.
 * Allows editing name, type, strength, display name, timezone, cycle, and visual appearance.
 */
@Composable
fun EditMedicationScreenEntry(medicationId: Int, onNavigateBack: () -> Unit) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)

    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    medication?.let { med ->
        EditMedicationScreen(
            medication = med,
            modifier = Modifier.fillMaxSize(),
            onNavigateBack = onNavigateBack,
            onSave = { updatedMedication ->
                medicationViewModel.updateMedication(updatedMedication)
            },
        )
    }
}

/**
 * Entry point for the Edit Schedule screen.
 * Manages schedules for a specific medication.
 */
@Composable
fun EditScheduleScreenEntry(
    medicationId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEditLabel: (medicationId: Int) -> Unit,
) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val scheduleViewModel: ScheduleViewModel = koinViewModel()
    val notificationPrefs: NotificationPreferences = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)
    val schedules by scheduleViewModel.getSchedulesByMedicationId(medicationId)
        .collectAsStateWithLifecycle(initial = emptyList())
    val hasSeenReminderHint by notificationPrefs.hasSeenReminderTypeHint
        .collectAsStateWithLifecycle(initial = true) // assume seen until pref loads to avoid flash

    var hasLoaded by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    medication?.let { med ->
        ScheduleScreen(
            schedules = schedules,
            medicationType = med.type,
            onNavigateBack = onNavigateBack,
            onAddSchedule = {
                editingSchedule = null
                showBottomSheet = true
            },
            onEditSchedule = { schedule ->
                editingSchedule = schedule
                showBottomSheet = true
            },
            medication = med,
            showReminderTypeHint = !hasSeenReminderHint,
            onEditReminderType = { onNavigateToEditLabel(medicationId) },
            onDismissReminderTypeHint = {
                coroutineScope.launch {
                    notificationPrefs.setHasSeenReminderTypeHint(true)
                }
            },
        )

        if (showBottomSheet) {
            ScheduleBottomSheet(
                schedule = editingSchedule,
                medicationId = medicationId,
                medicationType = med.type,
                onDismiss = { showBottomSheet = false },
                onSave = { schedule ->
                    if (schedule.id == 0) {
                        scheduleViewModel.insertSchedule(schedule)
                    } else {
                        scheduleViewModel.updateSchedule(schedule)
                    }
                    showBottomSheet = false
                },
                onDelete = if (editingSchedule != null) {
                    { schedule ->
                        scheduleViewModel.deleteSchedule(schedule)
                        showBottomSheet = false
                    }
                } else {
                    null
                },
                onArchive = if (editingSchedule != null) {
                    { schedule ->
                        scheduleViewModel.archiveSchedule(schedule.id)
                        showBottomSheet = false
                    }
                } else {
                    null
                },
                onUnarchive = if (editingSchedule != null) {
                    { schedule ->
                        scheduleViewModel.unarchiveSchedule(schedule.id)
                        showBottomSheet = false
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Entry point for the Edit Label screen.
 * Allows changing the importance label for a medication.
 */
@Composable
fun EditLabelScreenEntry(medicationId: Int, onNavigateBack: () -> Unit, onNavigateToSettings: () -> Unit) {
    val context = platformContext()
    val coroutineScope = rememberCoroutineScope()
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val labelViewModel: ImportanceLabelViewModel = koinViewModel()
    val notificationPrefs: NotificationPreferences = koinInject()
    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)
    val labels by labelViewModel.allLabels.collectAsStateWithLifecycle()

    var hasLoaded by remember { mutableStateOf(false) }
    var showCriticalChannelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    medication?.let { med ->
        EditLabelScreen(
            importanceLabels = labels,
            selectedLabelId = med.importanceLabelId,
            onLabelSelected = { labelId ->
                val updatedMed = med.copy(importanceLabelId = labelId)
                medicationViewModel.updateMedication(updatedMed)

                val selectedLabel = labels.find { it.id == labelId }
                val isCriticalLabel = selectedLabel != null &&
                    (selectedLabel.isCritical || selectedLabel.criticalAfterFollowUp != null)

                if (isCriticalLabel && !canCriticalChannelBypassDnd(context)) {
                    coroutineScope.launch {
                        val hasSeen = notificationPrefs.hasSeenCriticalChannelDialog.first()
                        if (!hasSeen) {
                            showCriticalChannelDialog = true
                            notificationPrefs.setHasSeenCriticalChannelDialog(true)
                        } else {
                            onNavigateBack()
                        }
                    }
                } else {
                    onNavigateBack()
                }
            },
            onNavigateBack = onNavigateBack,
            onNavigateToSettings = onNavigateToSettings,
        )
    }

    if (showCriticalChannelDialog) {
        CriticalChannelSetupDialog(
            onDismiss = {
                showCriticalChannelDialog = false
                onNavigateBack()
            },
        )
    }
}

/**
 * Entry point for the Stock Tracking Detail screen.
 * Shows stock tracking status and provides options to add or delete tracking.
 */
@Composable
fun StockTrackingDetailScreenEntry(
    medicationId: Int,
    onNavigateBack: () -> Unit,
    onAddTracking: (medicationId: Int) -> Unit,
    onSettings: (medicationId: Int) -> Unit,
) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val stockTrackingViewModel: StockTrackingViewModel = koinViewModel()

    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)

    // Calculate live stock
    val currentStock by stockTrackingViewModel.getCurrentStock(medicationId)
        .collectAsStateWithLifecycle(initial = null)

    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    // Compute StockStatus — keyed on medication and currentStock so it
    // recomputes after refills and sealed container changes.
    val stockStatus by produceState<StockStatus?>(initialValue = null, medication, currentStock) {
        val med = medication
        if (med?.stockTrackingEnabled == true) {
            value = stockTrackingViewModel.calculateStockStatus(med)
        }
    }

    // Collect graph data
    val stockLine by produceState<StockLine?>(initialValue = null, medication) {
        val med = medication ?: return@produceState
        if (!med.stockTrackingEnabled) return@produceState
        stockTrackingViewModel.getStockGraphLine(med).collect { value = it }
    }

    // Bottom sheet states
    var showTopUpSheet by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }

    medication?.let { med ->
        rememberCoroutineScope()
        val isEstimated =
            med.trackingPrecision == me.juliana.hellomeds.data.model.enums.TrackingPrecision.ESTIMATED

        StockTrackingDetailScreen(
            medication = med,
            currentStock = currentStock,
            stockStatus = stockStatus,
            stockLine = stockLine,
            onNavigateBack = onNavigateBack,
            onSettings = { onSettings(medicationId) },
            onAddTracking = {
                onAddTracking(medicationId)
            },
            onTopUp = { showTopUpSheet = true },
            onUpdateStock = { showUpdateSheet = true },
            onContainerDepleted = {
                stockTrackingViewModel.recordContainerDepleted(medicationId)
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top Up bottom sheet
        if (showTopUpSheet) {
            me.juliana.hellomeds.ui.features.stock.TopUpBottomSheet(
                medication = med,
                currentTotal = stockStatus?.totalQuantity ?: 0.0,
                packagingQuantity = med.packagingQuantity,
                container = med.medicationContainer,
                onDismiss = { showTopUpSheet = false },
                onSubmit = { quantity ->
                    stockTrackingViewModel.recordRefill(medicationId, quantity)
                },
            )
        }

        // Update Stock bottom sheet
        if (showUpdateSheet) {
            if (isEstimated) {
                me.juliana.hellomeds.ui.features.stock.UpdateSealedContainersBottomSheet(
                    currentSealedCount = stockStatus?.sealedContainerCount ?: 0,
                    onDismiss = { showUpdateSheet = false },
                    onSubmit = { newCount ->
                        stockTrackingViewModel.setSealedContainerCount(medicationId, newCount)
                    },
                )
            } else {
                me.juliana.hellomeds.ui.features.stock.UpdateStockBottomSheet(
                    medication = med,
                    currentTotal = stockStatus?.totalQuantity,
                    packagingQuantity = med.packagingQuantity,
                    container = med.medicationContainer,
                    onDismiss = { showUpdateSheet = false },
                    onSubmit = { newTotal ->
                        stockTrackingViewModel.recordCorrection(medicationId, newTotal)
                    },
                )
            }
        }
    }
}

/**
 * Entry point for the Add Stock Tracking Flow screen.
 * Multi-step wizard for configuring stock tracking.
 */
@Composable
fun AddStockTrackingFlowScreenEntry(medicationId: Int, onClose: () -> Unit) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)

    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onClose()
        }
    }

    medication?.let { med ->
        AddStockTrackingFlowScreen(
            medication = med,
            onClose = onClose,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Entry point for the Stock Tracking Settings screen.
 * Configure tracking parameters for a medication.
 */
@Composable
fun StockTrackingSettingsScreenEntry(
    medicationId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToLayoutEditor: (Int) -> Unit = {},
) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val stockTrackingViewModel: StockTrackingViewModel = koinViewModel()

    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)

    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    medication?.let { med ->
        val coroutineScope = rememberCoroutineScope()
        var showAdjustSheet by remember { mutableStateOf(false) }

        // Reactive daily consumption for prediction preview
        val dailyConsumption by stockTrackingViewModel.getDailyConsumptionFlow(medicationId)
            .collectAsStateWithLifecycle(initial = 0.0)

        // Compute stock status for current container remaining / sealed count
        val stockStatus by produceState<StockStatus?>(initialValue = null, med) {
            value = stockTrackingViewModel.calculateStockStatus(med)
        }

        StockTrackingSettingsScreen(
            medication = med,
            onNavigateBack = onNavigateBack,
            onUpdateLowStockThreshold = { threshold ->
                stockTrackingViewModel.updateLowStockThreshold(medicationId, threshold)
            },
            onUpdateContainerType = { container ->
                stockTrackingViewModel.updateContainerType(medicationId, container)
            },
            onUpdatePackagingQuantity = { quantity ->
                stockTrackingViewModel.updatePackagingQuantity(medicationId, quantity)
            },
            onUpdateDepletionReminderEnabled = { enabled ->
                stockTrackingViewModel.updateDepletionReminderEnabled(medicationId, enabled)
            },
            onAdjustStockLevel = { showAdjustSheet = true },
            onNavigateToLayoutEditor = { onNavigateToLayoutEditor(medicationId) },
            onDeleteTracking = {
                val updated = med.copy(
                    stockTrackingEnabled = false,
                    trackingPrecision = null,
                    currentStockQuantity = null,
                    lowStockThreshold = null,
                    packagingQuantity = null,
                    medicationContainer = null,
                    depletionReminderEnabled = false,
                )
                medicationViewModel.updateMedication(updated)

                coroutineScope.launch {
                    stockTrackingViewModel.deleteAllTrackingData(medicationId)
                }

                onNavigateBack()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showAdjustSheet) {
            val status = stockStatus
            val packagingQty = med.packagingQuantity
            if (status != null && packagingQty != null) {
                AdjustStockLevelBottomSheet(
                    currentPackagingQuantity = packagingQty,
                    currentContainerRemaining = status.currentContainerRemaining ?: packagingQty,
                    dailyConsumption = dailyConsumption,
                    onDismiss = { showAdjustSheet = false },
                    onConfirm = { newPQ, newRemaining ->
                        stockTrackingViewModel.adjustEstimatedStockLevel(
                            medicationId,
                            newPQ,
                            newRemaining,
                        )
                        showAdjustSheet = false
                    },
                )
            }
        }
    }
}

/**
 * Entry point for the Bubble Layout Editor sub-screen. Lets users customize the bubble preview's
 * column count and place spacer slots, or pick auto layout.
 */
@Composable
fun StockTrackingLayoutScreenEntry(medicationId: Int, onNavigateBack: () -> Unit) {
    val medicationViewModel: MedicationViewModel = koinViewModel()
    val stockTrackingViewModel: StockTrackingViewModel = koinViewModel()

    val medication by medicationViewModel.getMedicationById(medicationId)
        .collectAsStateWithLifecycle(initial = null)

    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(medication) {
        if (medication != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    medication?.let { med ->
        BubbleLayoutEditorScreen(
            medication = med,
            onNavigateBack = onNavigateBack,
            onUpdateLayout = { manualLayout, flow ->
                stockTrackingViewModel.updateBubbleLayout(medicationId, manualLayout, flow)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
