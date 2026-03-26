package com.bbioon.plantdisease.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bbioon.plantdisease.data.model.ScanRecord
import com.bbioon.plantdisease.data.remote.ThermalPrinterManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a scan receipt as a single bitmap and prints it.
 * Supports both LTR (English) and RTL (Arabic) layouts.
 * Cat/toy printers are image-only — all text is drawn on a Canvas.
 *
 * Prints core fields for both audiences. Professional-only fields
 * (disease_stage, favorable_conditions, notes) are omitted to keep
 * receipt length manageable on 384px thermal paper.
 */
object PrintReceiptBuilder {

    private const val WIDTH = ThermalPrinterManager.PRINT_WIDTH_PX  // 384
    private const val MARGIN = 12
    private const val TEXT_WIDTH = WIDTH - MARGIN * 2

    /**
     * Print a complete scan receipt by rendering it as one bitmap.
     */
    suspend fun printScanReceipt(
        printer: ThermalPrinterManager,
        scan: ScanRecord,
        context: Context,
    ) {
        val receiptBitmap = renderReceipt(scan, context)
        val dithered = ImageDithering.dither(receiptBitmap, WIDTH)
        receiptBitmap.recycle()

        printer.printBitmap(dithered)
        dithered.recycle()
    }

    /**
     * Render the entire receipt to a bitmap (white background, black text/images).
     */
    private fun renderReceipt(scan: ScanRecord, context: Context): Bitmap {
        val isRtl = isArabicLocale(context)

        // Paint configurations
        val paintTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }
        val paintSubtitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isFakeBoldText = true
        }
        val paintBody = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintSeparator = Paint().apply { color = Color.BLACK; strokeWidth = 1f }

        val alignment = Layout.Alignment.ALIGN_NORMAL  // follows text's natural direction (RTL or LTR)
        val centerAlign = Layout.Alignment.ALIGN_CENTER

        // Localized labels — existing
        val titleText = if (isRtl) "كاشف أمراض النباتات" else "Plant Disease Detector"
        val healthyText = if (isRtl) "✓ سليم" else "✓ HEALTHY"
        val diseasedText = if (isRtl) "✗ مصاب" else "✗ DISEASED"
        val plantLabel = if (isRtl) "النبات:" else "Plant:"
        val typeLabel = if (isRtl) "النوع:" else "Type:"
        val diseaseLabel = if (isRtl) "المرض:" else "Disease:"
        val descLabel = if (isRtl) "الوصف:" else "Description:"
        val treatmentLabel = if (isRtl) "العلاج:" else "Treatment:"

        // Localized labels — new fields
        val scientificLabel = if (isRtl) "الاسم العلمي:" else "Scientific:"
        val pathogenLabel = if (isRtl) "المُسبب:" else "Pathogen:"
        val severityLabel = if (isRtl) "الشدة:" else "Severity:"
        val diseaseStageLabel = if (isRtl) "مرحلة المرض:" else "Stage:"
        val symptomsLabel = if (isRtl) "الأعراض:" else "Symptoms:"
        val causeLabel = if (isRtl) "السبب:" else "Cause:"
        val spreadRiskLabel = if (isRtl) "خطر الانتشار:" else "Spread Risk:"
        val conditionsLabel = if (isRtl) "الظروف المساعدة:" else "Conditions:"
        val preventionLabel = if (isRtl) "الوقاية:" else "Prevention:"
        val notesLabel = if (isRtl) "ملاحظات:" else "Notes:"

        // Build sections: a list of (y-offset, drawable lambda)
        val sections = mutableListOf<Section>()

        // Title
        sections.add(makeStaticSection(titleText, paintTitle, centerAlign, 10f))

        // Separator
        sections.add(makeSeparatorSection(paintSeparator))

        // Plant image
        val plantBitmap = loadBitmap(context, scan.imageUri)
        if (plantBitmap != null) {
            val imgWidth = WIDTH - MARGIN * 2
            val scale = imgWidth.toFloat() / plantBitmap.width
            val imgHeight = (plantBitmap.height * scale).toInt()
            sections.add(Section(imgHeight.toFloat() + 8f) { canvas, y ->
                canvas.drawBitmap(plantBitmap, null, Rect(MARGIN, y.toInt(), MARGIN + imgWidth, y.toInt() + imgHeight), null)
            })
            sections.add(makeSeparatorSection(paintSeparator))
        }

        // Status + Severity on same conceptual line
        val statusText = if (scan.isHealthy) healthyText else diseasedText
        val severityEmoji = getSeverityEmoji(scan.severity)
        val statusDisplay = if (!scan.severity.isNullOrBlank()) {
            "$statusText  $severityEmoji $severityLabel ${scan.severity}"
        } else {
            statusText
        }
        sections.add(makeStaticSection(statusDisplay, paintLabel, centerAlign, 4f))

        // Plant info
        sections.add(makeLabelValueSection("$plantLabel ${scan.plantName}", paintLabel, paintBody, alignment))
        if (!scan.scientificName.isNullOrBlank()) {
            sections.add(makeLabelValueSection("$scientificLabel ${scan.scientificName}", paintLabel, paintBody, alignment))
        }
        sections.add(makeLabelValueSection("$typeLabel ${scan.plantType}", paintLabel, paintBody, alignment))

        if (!scan.isHealthy && !scan.diseaseName.isNullOrBlank()) {
            sections.add(makeLabelValueSection("$diseaseLabel ${scan.diseaseName}", paintLabel, paintBody, alignment))
        }
        if (!scan.pathogenType.isNullOrBlank()) {
            sections.add(makeLabelValueSection("$pathogenLabel ${scan.pathogenType}", paintLabel, paintBody, alignment))
        }
        if (!scan.diseaseStage.isNullOrBlank()) {
            sections.add(makeLabelValueSection("$diseaseStageLabel ${scan.diseaseStage}", paintLabel, paintBody, alignment))
        }

        sections.add(Section(4f) { _, _ -> })

        // Symptoms
        if (!scan.symptoms.isNullOrBlank()) {
            sections.add(makeStaticSection(symptomsLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.symptoms, paintBody, alignment, 4f))
        }

        // Cause
        if (!scan.cause.isNullOrBlank()) {
            sections.add(makeStaticSection(causeLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.cause, paintBody, alignment, 4f))
        }

        // Spread risk
        if (!scan.spreadRisk.isNullOrBlank()) {
            sections.add(makeStaticSection(spreadRiskLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.spreadRisk, paintBody, alignment, 4f))
        }

        // Favorable conditions
        if (!scan.favorableConditions.isNullOrBlank()) {
            sections.add(makeStaticSection(conditionsLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.favorableConditions, paintBody, alignment, 4f))
        }

        // Description
        if (scan.description.isNotBlank()) {
            sections.add(makeStaticSection(descLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.description, paintBody, alignment, 4f))
        }

        // Treatment
        if (!scan.treatment.isNullOrBlank()) {
            sections.add(makeStaticSection(treatmentLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.treatment, paintBody, alignment, 4f))
        }

        // Prevention
        if (!scan.prevention.isNullOrBlank()) {
            sections.add(makeStaticSection(preventionLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.prevention, paintBody, alignment, 4f))
        }

        // Notes
        if (!scan.notes.isNullOrBlank()) {
            sections.add(makeStaticSection(notesLabel, paintLabel, alignment, 0f))
            sections.add(makeStaticSection(scan.notes, paintBody, alignment, 4f))
        }

        // Footer separator
        sections.add(makeSeparatorSection(paintSeparator))

        // Date
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(scan.createdAt))
        sections.add(makeStaticSection(dateStr, paintSubtitle, centerAlign, 4f))

        // Calculate total height and draw
        val totalHeight = sections.sumOf { it.height.toDouble() }.toInt() + 30
        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 0f
        for (section in sections) {
            section.draw(canvas, y)
            y += section.height
        }

        // Clean up plant bitmap
        plantBitmap?.recycle()

        return bitmap
    }

    /**
     * Create a separator line section.
     */
    private fun makeSeparatorSection(paint: Paint): Section {
        return Section(8f) { canvas, y ->
            canvas.drawLine(MARGIN.toFloat(), y, (WIDTH - MARGIN).toFloat(), y, paint)
        }
    }

    /**
     * Create a section that draws text using StaticLayout (supports RTL + word wrap).
     */
    private fun makeStaticSection(
        text: String,
        paint: TextPaint,
        alignment: Layout.Alignment,
        bottomPadding: Float,
    ): Section {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, TEXT_WIDTH)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
        val height = layout.height.toFloat() + bottomPadding
        return Section(height) { canvas, y ->
            canvas.save()
            canvas.translate(MARGIN.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Create a label:value section (e.g., "Plant: Rose").
     */
    private fun makeLabelValueSection(
        text: String,
        @Suppress("UNUSED_PARAMETER") labelPaint: TextPaint,
        bodyPaint: TextPaint,
        alignment: Layout.Alignment,
    ): Section {
        // Use body paint for the whole line; label is included in the text string
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, bodyPaint, TEXT_WIDTH)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
        val height = layout.height.toFloat() + 4f
        return Section(height) { canvas, y ->
            canvas.save()
            canvas.translate(MARGIN.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun getSeverityEmoji(severity: String?): String {
        if (severity.isNullOrBlank()) return ""
        val lower = severity.lowercase()
        return when {
            lower.contains("low") || lower.contains("منخفضة") -> "🟢"
            lower.contains("moderate") || lower.contains("متوسطة") -> "🟡"
            lower.contains("severe") || lower.contains("شديدة") -> "🟠"
            lower.contains("critical") || lower.contains("حرجة") -> "🔴"
            else -> "⚪"
        }
    }

    private fun isArabicLocale(context: Context): Boolean {
        val locale = context.resources.configuration.locales[0]
        return locale.language == "ar"
    }

    private fun loadBitmap(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) { null }
    }

    private class Section(
        val height: Float,
        val draw: (Canvas, Float) -> Unit,
    )
}
