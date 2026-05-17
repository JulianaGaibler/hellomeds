// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ml.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageProxy
import me.juliana.hellomeds.data.util.AppLogger
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.domain.ml.HeuristicMedicationEngine
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.domain.ml.MedicationDictionaries
import me.juliana.hellomeds.domain.ml.StrengthSuggestion
import me.juliana.hellomeds.util.await
import org.json.JSONObject
import kotlin.math.sqrt

private const val TAG = "MedicationDetector"

/**
 * Medication detector using ML Kit Object Detection and Gemini Nano
 */
class MedicationDetector(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Heuristic fallback engine (pure Kotlin, no platform AI dependency)
    private val heuristicEngine = HeuristicMedicationEngine()

    // ML Kit Object Detector
    private val objectDetectorOptions = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .build()
    private val objectDetector = ObjectDetection.getClient(objectDetectorOptions)

    // ML Kit Text Recognition (for reading medication labels)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Gemini Nano model for on-device AI analysis (via ML Kit GenAI)
    // Note: May not be available on all devices (requires Android 14+, supported hardware)
    private val generativeModel by lazy {
        try {
            Generation.getClient()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Gemini Nano not available: ${e.message}")
            null
        }
    }

    // Track if model is available or being downloaded
    private var modelDownloaded = false

    /**
     * Warmup Gemini Nano by checking availability and downloading if needed
     * Call this proactively (e.g., when user presses shutter button) to reduce latency
     */
    suspend fun warmupGemini() {
        isGeminiNanoAvailable()
    }

    /**
     * Check current Gemini Nano status without triggering download.
     * Returns: UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, or AVAILABLE
     */
    @FeatureStatus
    suspend fun checkGeminiStatus(): Int {
        val model = generativeModel ?: return FeatureStatus.UNAVAILABLE
        return try {
            model.checkStatus()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking Gemini status", e)
            FeatureStatus.UNAVAILABLE
        }
    }

    /**
     * Check if Gemini Nano is downloaded and available to use.
     */
    suspend fun isGeminiDownloaded(): Boolean {
        return checkGeminiStatus() == FeatureStatus.AVAILABLE
    }

    /**
     * Download Gemini Nano with progress updates.
     * Emits download status updates via the returned Flow.
     *
     * @return Flow of DownloadStatus updates
     */
    fun downloadGemini(): Flow<DownloadStatus> {
        val model = generativeModel
        return if (model != null) {
            model.download()
        } else {
            flow {
                emit(
                    DownloadStatus.DownloadFailed(
                        GenAiException(
                            null,
                            8, // NOT_AVAILABLE - feature not available on this device
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Check if Gemini Nano is available, and download if needed
     */
    private suspend fun isGeminiNanoAvailable(): Boolean {
        val model = generativeModel ?: return false

        return try {
            when (val status = model.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> {
                    AppLogger.w(TAG, "Gemini Nano not supported on this device")
                    false
                }

                FeatureStatus.DOWNLOADABLE -> {
                    Log.d(TAG, "Gemini Nano available for download, starting download...")
                    // Start download and wait for completion
                    model.download().collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadStarted ->
                                Log.d(TAG, "Starting download for Gemini Nano")

                            is DownloadStatus.DownloadProgress ->
                                Log.d(TAG, "Gemini Nano ${downloadStatus.totalBytesDownloaded} bytes downloaded")

                            DownloadStatus.DownloadCompleted -> {
                                Log.d(TAG, "Gemini Nano download complete")
                                modelDownloaded = true
                            }

                            is DownloadStatus.DownloadFailed -> {
                                AppLogger.e(TAG, "Gemini Nano download failed: ${downloadStatus.e.message}")
                            }
                        }
                    }
                    modelDownloaded
                }

                FeatureStatus.DOWNLOADING -> {
                    Log.d(TAG, "Gemini Nano currently being downloaded")
                    // Wait a bit and check again
                    false
                }

                FeatureStatus.AVAILABLE -> {
                    Log.d(TAG, "Gemini Nano available and ready to use")
                    modelDownloaded = true
                    true
                }

                else -> {
                    AppLogger.w(TAG, "Unknown Gemini Nano status: $status")
                    false
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking Gemini Nano availability", e)
            false
        }
    }

    // Throttle detection to avoid overwhelming the system
    private var lastObjectDetectionTime = 0L
    private val objectDetectionThrottleMs = 100L // 100ms between object detections (fast polling)

    private var lastOcrTime = 0L
    private val ocrThrottleMs = 300L // 300ms between OCR attempts (faster)

    /**
     * Data class to hold object detection result
     */
    data class ObjectDetectionResult(
        val boundingBox: Rect,
        val croppedBitmap: Bitmap,
    )

    /**
     * Detect only objects in the frame (fast, for continuous polling)
     * Now takes a Bitmap instead of ImageProxy to avoid "already closed" issues
     *
     * @param preferCenter If true, heavily weights objects near center (for frozen analysis).
     *                     If false, uses simple largest-object selection (for live scanning).
     */
    fun detectObject(
        bitmap: Bitmap,
        rotation: Int,
        onObjectDetected: (ObjectDetectionResult) -> Unit,
        onNoObject: () -> Unit,
        preferCenter: Boolean = false,
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastObjectDetectionTime < objectDetectionThrottleMs) {
            return // Throttle detection
        }
        lastObjectDetectionTime = currentTime

        scope.launch {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, rotation)
                val detectedObjects = objectDetector.process(inputImage).await()

                Log.d(TAG, "ML Kit detected ${detectedObjects.size} objects (preferCenter=$preferCenter)")

                val primaryObject = if (preferCenter) {
                    // Prefer objects near the center (for frozen analysis / reticle initialization)
                    val centerX = bitmap.width / 2f
                    val centerY = bitmap.height / 2f

                    detectedObjects.maxByOrNull { obj ->
                        val box = obj.boundingBox
                        val area = box.width() * box.height()

                        val boxCenterX = (box.left + box.right) / 2f
                        val boxCenterY = (box.top + box.bottom) / 2f
                        val distanceX = boxCenterX - centerX
                        val distanceY = boxCenterY - centerY
                        val centerDistance = sqrt(distanceX * distanceX + distanceY * distanceY)

                        // Larger area + closer to center scores higher.
                        val score = area / (centerDistance + 100f)

                        Log.d(
                            TAG,
                            "  Object: box=$box, area=$area, centerDist=%.1f, score=%.1f".format(
                                centerDistance,
                                score,
                            ),
                        )
                        score
                    }
                } else {
                    // Simple largest-object selection (for live scanning - detect anything)
                    detectedObjects.maxByOrNull { obj ->
                        val area = obj.boundingBox.width() * obj.boundingBox.height()
                        Log.d(TAG, "  Object: box=${obj.boundingBox}, area=$area")
                        area
                    }
                }

                if (primaryObject != null) {
                    Log.d(TAG, "Selected primary object: ${primaryObject.boundingBox}")
                } else {
                    Log.d(TAG, "No primary object selected")
                }

                if (primaryObject != null) {
                    val objectBox = primaryObject.boundingBox
                    try {
                        val croppedBitmap = Bitmap.createBitmap(
                            bitmap,
                            objectBox.left.coerceAtLeast(0),
                            objectBox.top.coerceAtLeast(0),
                            objectBox.width().coerceAtMost(bitmap.width - objectBox.left),
                            objectBox.height().coerceAtMost(bitmap.height - objectBox.top),
                        )
                        onObjectDetected(ObjectDetectionResult(objectBox, croppedBitmap))
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to crop object frame: ${e.message}")
                        onNoObject()
                    }
                } else {
                    onNoObject()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Object detection error", e)
                onNoObject()
            }
        }
    }

    /**
     * Recognize text from a bitmap (for polling detected region)
     */
    fun recognizeText(
        bitmap: Bitmap,
        onTextRecognized: (String, Int) -> Unit, // text and word count
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOcrTime < ocrThrottleMs) {
            return // Throttle OCR (silent)
        }
        lastOcrTime = currentTime

        scope.launch {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val recognizedText = textRecognizer.process(inputImage).await()
                val extractedText = recognizedText.text.trim()
                val wordCount = extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size

                Log.d(TAG, "OCR: $wordCount words")
                onTextRecognized(extractedText, wordCount)
            } catch (e: Exception) {
                AppLogger.e(TAG, "OCR error", e)
                onTextRecognized("", 0)
            }
        }
    }

    /**
     * Full medication analysis with Gemini Nano or heuristic (called after freezing on good OCR)
     *
     * @param useGemini If true, use Gemini Nano; if false, use heuristic
     */
    suspend fun analyzeFullMedication(
        bitmap: Bitmap,
        boundingBox: Rect,
        extractedText: String,
        useGemini: Boolean = true,
    ): MedicationDetectionResult {
        try {
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                boundingBox.left.coerceAtLeast(0),
                boundingBox.top.coerceAtLeast(0),
                boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top),
            )

            return analyzeWithGemini(
                extractedText = extractedText,
                bitmap = croppedBitmap,
                useGemini = useGemini,
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Full analysis error", e)
            throw e
        }
    }

    /**
     * Analyze detected medication using Gemini Nano (on-device) or heuristic
     * Returns multiple suggestions for each field
     * Falls back to heuristic if Gemini Nano unavailable or user prefers heuristic
     *
     * @param useGemini If true, try to use Gemini Nano; if false, use heuristic directly
     */
    private suspend fun analyzeWithGemini(
        extractedText: String,
        bitmap: Bitmap,
        useGemini: Boolean = true,
    ): MedicationDetectionResult {
        if (!useGemini || !isGeminiNanoAvailable()) {
            AppLogger.w(
                TAG,
                if (!useGemini) "User prefers heuristic" else "Gemini Nano not available, using heuristic fallback",
            )
            return heuristicEngine.guessMedicationDetails(extractedText) ?: MedicationDetectionResult()
        }

        try {
            // Prompt is kept concise to stay under Gemini Nano's ~4000 token limit.
            val allowedTypes = MedicationDictionaries.ALLOWED_TYPES.joinToString(", ")
            val allowedUnits = MedicationDictionaries.ALLOWED_UNITS.joinToString(", ")

            val prompt = """
      Extract medication from OCR text. Return JSON only, no markdown.
      OCR: "$extractedText"

      JSON format:
      {"n":["name1","name2"],"t":["type"],"v":100,"u":"mg"}

      "n": 0-4 distinct names from OCR. Include generic (pantoprazol, colecalciferol), brand (Buscopan, Gynokadin), or common names (Vitamin D3) as separate entries. Don't combine names or include manufacturer suffixes (Aristo, Accord).
      "t": 0-3 types. ONLY use: $allowedTypes
      "v": strength number or null
      "u": ONLY use: $allowedUnits (case-insensitive), or null

      JSON:
            """.trimIndent()

            // Call on-device Gemini Nano with image and prompt using ML Kit GenAI API
            // Using low temperature for deterministic entity extraction task
            val request = generateContentRequest(ImagePart(bitmap), TextPart(prompt)) {
                temperature = 0.2f // Low temperature for deterministic extraction task
                topK = 40
            }

            val response = generativeModel?.generateContent(request)
                ?: throw Exception("Gemini model not initialized")

            val jsonResponse = response.candidates.firstOrNull()?.text
                ?: throw Exception("Empty response from Gemini")
            Log.d(TAG, "Gemini response: $jsonResponse")

            // Parse JSON response - returns null if no valid data
            val result = parseGeminiResponse(jsonResponse)
            if (result == null) {
                // No valid data extracted, use heuristic fallback
                AppLogger.w(TAG, "Gemini returned no valid medication data, using heuristic fallback")
                return heuristicEngine.guessMedicationDetails(extractedText) ?: MedicationDetectionResult()
            }

            return result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Gemini analysis error: ${e.message}", e)
            // Fallback: use sophisticated heuristic
            return heuristicEngine.guessMedicationDetails(extractedText) ?: MedicationDetectionResult()
        }
    }

    /**
     * Parse Gemini JSON response into MedicationDetectionResult
     * New format: {"n":["name1"],"t":["type1"],"v":100,"u":"mg"}
     */
    private fun parseGeminiResponse(jsonResponse: String): MedicationDetectionResult? {
        try {
            // Clean response (remove markdown code blocks if present)
            val cleanJson = jsonResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleanJson)

            // Parse medication names (0-4 allowed)
            val names = mutableListOf<String>()
            val namesArray = json.optJSONArray("n")
            if (namesArray != null) {
                for (i in 0 until minOf(namesArray.length(), 4)) {
                    val name = namesArray.optString(i, "").trim()
                    if (name.isNotBlank() && name != "?" && name.lowercase() != "null") {
                        names.add(normalizeCapitalization(name))
                    }
                }
            }

            // Parse medication types (0-3 allowed, MUST be from predefined list)
            val types = mutableListOf<MedicationType>()
            val typesArray = json.optJSONArray("t")
            if (typesArray != null) {
                for (i in 0 until minOf(typesArray.length(), 3)) {
                    val typeStr = typesArray.optString(i, "").trim()

                    // Convert string to enum (case-insensitive)
                    val typeEnum = MedicationType.fromValue(typeStr)
                    if (typeEnum != null) {
                        types.add(typeEnum)
                        Log.d(TAG, "  Type detected: '${typeEnum.value}'")
                    } else if (typeStr.isNotBlank()) {
                        AppLogger.w(TAG, "Discarding invalid medication type: $typeStr")
                    }
                }
            }

            // Parse strength value and unit (take first/only)
            val strengthValue = json.optDouble("v", Double.NaN)
            val strengthUnitStr = if (json.has("u")) json.optString("u")?.trim() else null

            // Strength unit must be from the predefined enum; reject anything else.
            val strength = if (!strengthValue.isNaN() && strengthValue > 0 && strengthUnitStr != null) {
                val unitEnum = MedicationStrengthUnit.fromValue(strengthUnitStr)
                if (unitEnum != null) {
                    Log.d(TAG, "  Strength detected: $strengthValue ${unitEnum.value}")
                    StrengthSuggestion(strengthValue, unitEnum)
                } else {
                    AppLogger.w(TAG, "Discarding invalid strength unit: $strengthUnitStr")
                    null
                }
            } else {
                null
            }

            // Validation: At least 1 name OR 1 type OR strength must be present
            val hasValidData = names.isNotEmpty() || types.isNotEmpty() || strength != null

            if (!hasValidData) {
                AppLogger.w(TAG, "No valid medication data extracted from Gemini response")
                return null // Indicates no valid data
            }

            return MedicationDetectionResult(
                nameSuggestions = names.distinctBy { it.lowercase() },
                typeSuggestions = types.distinct(),
                strengthSuggestion = strength,
                usedAI = true,
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse Gemini response", e)
            return null // Indicates parsing error
        }
    }

    /**
     * Normalize capitalization for medication names.
     * Words longer than 3 characters that are ALL CAPS are converted to Title Case.
     *
     * Examples:
     * - "PANTOPRAZOL" -> "Pantoprazol"
     * - "VITAMIN D3" -> "Vitamin D3" (D3 is ≤3 chars, stays as-is)
     * - "Ibuprofen" -> "Ibuprofen" (already proper case)
     */
    private fun normalizeCapitalization(name: String): String {
        return name.split(" ").joinToString(" ") { word ->
            if (word.length > 3 && word.all { it.isUpperCase() || !it.isLetter() }) {
                // Word is >3 chars and ALL CAPS -> convert to Title Case
                word.lowercase().replaceFirstChar { it.titlecase() }
            } else {
                // Keep as-is (already proper case or ≤3 chars)
                word
            }
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
