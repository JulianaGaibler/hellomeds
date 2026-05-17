// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.juliana.hellomeds.data.backup.AutoBackupService
import me.juliana.hellomeds.data.backup.AutoBackupStorageProvider
import me.juliana.hellomeds.data.backup.BackupEncryption
import me.juliana.hellomeds.data.crypto.PassphraseManager
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var service: AutoBackupService
    private lateinit var storageProvider: AutoBackupStorageProvider
    private lateinit var preferences: AutoBackupPreferences
    private lateinit var passphraseManager: PassphraseManager

    // Stateful preference flows so individual tests can mutate via `.value = ...`.
    private val enabled = MutableStateFlow(false)
    private val lastBackupTimestamp = MutableStateFlow(0L)
    private val lastBackupStatus = MutableStateFlow("NEVER")
    private val lastBackupErrorMessage = MutableStateFlow<String?>(null)
    private val lastBackupMedicationCount = MutableStateFlow(0)
    private val consecutiveFailures = MutableStateFlow(0)
    private val backupRetentionCount = MutableStateFlow(7)
    private val backupDestinationUri = MutableStateFlow<String?>(null)
    private val passphraseHint = MutableStateFlow<String?>(null)

    // Stateful passphrase mock. `answers { ... }` reads this on every call, avoiding the
    // returnsMany pitfall when `combine` re-evaluates an unpredictable number of times.
    private var hasPassphrase: Boolean = false

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        mockkObject(PlatformCapabilities)
        every { PlatformCapabilities.supportsAutoBackupFolderPicker() } returns true

        mockkObject(BackupEncryption)
        every { BackupEncryption.encrypt(any(), any()) } returns ByteArray(0)
        every { BackupEncryption.decrypt(any(), any()) } returns
            "HelloMeds passphrase verification test"

        service = mockk(relaxed = true)
        storageProvider = mockk(relaxed = true) {
            coEvery { isDestinationAvailable() } returns false
        }
        preferences = mockk(relaxed = true) {
            every { autoBackupEnabled } returns enabled
            every { lastBackupTimestamp } returns this@AutoBackupViewModelTest.lastBackupTimestamp
            every { lastBackupStatus } returns this@AutoBackupViewModelTest.lastBackupStatus
            every { lastBackupErrorMessage } returns this@AutoBackupViewModelTest.lastBackupErrorMessage
            every { lastBackupMedicationCount } returns
                this@AutoBackupViewModelTest.lastBackupMedicationCount
            every { consecutiveFailures } returns this@AutoBackupViewModelTest.consecutiveFailures
            every { backupRetentionCount } returns this@AutoBackupViewModelTest.backupRetentionCount
            every { backupDestinationUri } returns this@AutoBackupViewModelTest.backupDestinationUri
            every { passphraseHint } returns this@AutoBackupViewModelTest.passphraseHint
        }
        passphraseManager = mockk(relaxed = true)
        every { passphraseManager.hasPassphrase() } answers { hasPassphrase }
        every { passphraseManager.setPassphrase(any()) } answers {
            hasPassphrase = true
            true
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun TestScope.newViewModel(): AutoBackupViewModel {
        val vm = AutoBackupViewModel(service, storageProvider, preferences, passphraseManager)
        // Keep uiState subscribed so the combine block actually runs. `WhileSubscribed(5000)`
        // otherwise leaves uiState.value at its initial seed (all defaults), and onEnableToggled
        // would always see hasPassphrase=false / destinationUri=null regardless of test setup.
        // backgroundScope is auto-cancelled at the end of runTest.
        backgroundScope.launch { vm.uiState.collect { /* drain */ } }
        runCurrent()
        return vm
    }

    @Test
    fun hasPassphrase_flips_true_after_setPassphrase_without_other_prefs_changing() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.uiState.test {
            // Seed: combine fires once at startup, hasPassphrase false.
            assertFalse(awaitItem().hasPassphrase)

            viewModel.setPassphrase("hunter22-strong", null) { /* onResult */ }
            runCurrent()

            // No preference flow has changed; the trigger SharedFlow alone must re-evaluate combine.
            val state = awaitItem()
            assertTrue("hasPassphrase should be true after setPassphrase success", state.hasPassphrase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEnableToggled_false_disablesAndDoesNotEmit() = runTest(dispatcher) {
        enabled.value = true
        val viewModel = newViewModel()
        runCurrent()

        viewModel.events.test {
            viewModel.onEnableToggled(false)
            runCurrent()
            expectNoEvents()
        }
        coVerify(exactly = 1) { preferences.setAutoBackupEnabled(false) }
    }

    @Test
    fun onEnableToggled_true_withoutPassphrase_requestsPassphrase() = runTest(dispatcher) {
        hasPassphrase = false
        backupDestinationUri.value = null
        val viewModel = newViewModel()
        runCurrent()

        viewModel.events.test {
            viewModel.onEnableToggled(true)
            assertEquals(AutoBackupEvent.RequestPassphrase, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun onEnableToggled_true_withPassphraseButNoDestination_android_requestsFolder() = runTest(dispatcher) {
        hasPassphrase = true
        backupDestinationUri.value = null
        val viewModel = newViewModel()
        runCurrent()

        viewModel.events.test {
            viewModel.onEnableToggled(true)
            assertEquals(AutoBackupEvent.RequestPickFolder, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun onEnableToggled_true_withAllPrereqs_android_enablesImmediately() = runTest(dispatcher) {
        hasPassphrase = true
        backupDestinationUri.value = "content://primary:Documents/HelloMeds/backups"
        val viewModel = newViewModel()
        runCurrent()

        viewModel.events.test {
            viewModel.onEnableToggled(true)
            runCurrent()
            expectNoEvents()
        }
        coVerify(exactly = 1) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun onEnableToggled_true_withPassphrase_ios_enablesImmediately() = runTest(dispatcher) {
        every { PlatformCapabilities.supportsAutoBackupFolderPicker() } returns false
        hasPassphrase = true
        backupDestinationUri.value = null
        val viewModel = newViewModel()
        runCurrent()

        viewModel.events.test {
            viewModel.onEnableToggled(true)
            runCurrent()
            expectNoEvents()
        }
        coVerify(exactly = 1) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun enableFlow_chains_passphraseThenFolderThenEnabled() = runTest(dispatcher) {
        hasPassphrase = false
        backupDestinationUri.value = null
        // Make setBackupDestinationUri actually flip the flow, so the VM sees the change.
        coEvery { preferences.setBackupDestinationUri(any()) } answers {
            backupDestinationUri.value = firstArg()
        }
        val viewModel = newViewModel()
        runCurrent()

        viewModel.events.test {
            viewModel.onEnableToggled(true)
            assertEquals(AutoBackupEvent.RequestPassphrase, awaitItem())

            viewModel.setPassphrase("hunter22-strong", null) { }
            runCurrent()
            assertEquals(AutoBackupEvent.RequestPickFolder, awaitItem())

            viewModel.setDestinationUri("content://primary:Documents/HelloMeds/backups")
            runCurrent()

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun dismissingPassphraseDialog_clearsPendingEnable_soLaterSetPassphrase_doesNotHijack() = runTest(dispatcher) {
        hasPassphrase = false
        backupDestinationUri.value = null
        val viewModel = newViewModel()
        runCurrent()

        // Kick off the chain, then immediately dismiss.
        viewModel.onEnableToggled(true)
        runCurrent()
        viewModel.dismissPassphraseDialog()

        // User later sets a passphrase from the Settings row. Must NOT cascade.
        viewModel.events.test {
            viewModel.setPassphrase("hunter22-strong", null) { }
            runCurrent()
            expectNoEvents()
        }
        coVerify(exactly = 0) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun cancellingFolderPicker_clearsPendingEnable_soLaterPick_doesNotHijack() = runTest(dispatcher) {
        hasPassphrase = true
        backupDestinationUri.value = null
        coEvery { preferences.setBackupDestinationUri(any()) } answers {
            backupDestinationUri.value = firstArg()
        }
        val viewModel = newViewModel()
        runCurrent()

        // Kick off, then cancel the picker (notified via onFolderPickerCancelled, NOT
        // setDestinationUri(null), so any previously stored destination is preserved).
        viewModel.onEnableToggled(true)
        runCurrent()
        viewModel.onFolderPickerCancelled()
        runCurrent()

        // User later picks a destination from the Settings row. Must NOT enable backups.
        viewModel.setDestinationUri("content://primary:Documents/later")
        runCurrent()

        coVerify(exactly = 0) { preferences.setAutoBackupEnabled(true) }
    }

    @Test
    fun cancellingFolderPicker_preservesExistingDestination() = runTest(dispatcher) {
        // User had a destination previously. They open the picker (from Settings row),
        // change their mind, and back out. The stored URI must remain intact.
        val existing = "content://primary:Documents/previously-picked"
        hasPassphrase = true
        backupDestinationUri.value = existing
        val viewModel = newViewModel()
        runCurrent()

        viewModel.onFolderPickerCancelled()
        runCurrent()

        coVerify(exactly = 0) { preferences.setBackupDestinationUri(null) }
        coVerify(exactly = 0) { preferences.setBackupDestinationUri(any()) }
    }
}
