package com.kash.app.ui.report

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kash.insights.MonthlyReport
import com.kash.parser.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the monthly report as an A4 PDF (with the same charts as the app) and saves it to
 * Downloads. Uses Android's built-in PdfDocument: no extra library, works offline.
 */
object ReportPdf {

    private const val W = 595f   // A4 in points
    private const val H = 842f
    private const val M = 40f    // margin

    private val INK = Color.rgb(23, 23, 26)
    private val MUTED = Color.rgb(94, 94, 102)
    private val BAR = Color.rgb(185, 70, 31)       // deeper orange: 5.3:1 on white
    private val ACCENT = Color.rgb(248, 105, 57)
    private val TRACK = Color.rgb(238, 236, 232)
    private val RULE = Color.rgb(226, 224, 220)

    fun saveToDownloads(context: Context, report: MonthlyReport): Pair<Uri, String> {
        val name = "Kash-${report.month}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Couldn't create the file in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { out -> render(report).apply { writeTo(out); close() } }
                ?: throw java.io.IOException("Couldn't write the file")
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return uri to name
    }

    fun render(r: MonthlyReport): PdfDocument {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
        var c = page.canvas
        var y = M

        fun newPageIfNeeded(space: Float) {
            if (y + space > H - M - 30) {
                footer(c, pageNo); doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
                c = page.canvas; y = M
            }
        }

        // Header
        val monthName = r.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        c.drawRoundRect(RectF(M, y, M + 28, y + 28), 7f, 7f, fill(ACCENT))
        text(c, "K", M + 8, y + 21, 18f, INK, bold = true)
        text(c, "Kash", M + 38, y + 20, 20f, INK, bold = true)
        text(c, "Monthly summary · $monthName", M + 38, y + 36, 11f, MUTED)
        y += 60

        // Totals
        text(c, "Spent", M, y, 11f, MUTED); y += 26
        text(c, r.spent.pdf(), M, y, 26f, INK, bold = true); y += 24
        val col = (W - 2 * M) / 3
        listOf("Money in" to r.moneyIn, "Saved" to r.saved, "Transaction costs" to r.fees).forEachIndexed { i, (l, v) ->
            text(c, l, M + i * col, y, 10f, MUTED)
            text(c, v.pdf(), M + i * col, y + 16, 13f, INK, bold = true)
        }
        y += 34
        rule(c, y); y += 20

        // Highlights
        if (r.highlights.isNotEmpty()) {
            text(c, "What stood out", M, y, 13f, INK, bold = true); y += 8
            r.highlights.take(5).forEach { h ->
                val layout = wrap("•  $h", 10.5f, INK, W - 2 * M)
                newPageIfNeeded(layout.height + 6f)
                c.save(); c.translate(M, y + 4); layout.draw(c); c.restore()
                y += layout.height + 6f
            }
            y += 10; rule(c, y); y += 20
        }

        // Categories: horizontal bars, single hue, value labels
        newPageIfNeeded(60f)
        text(c, "Where your money went", M, y, 13f, INK, bold = true); y += 16
        val max = r.byCategory.maxOfOrNull { it.amount.cents }?.coerceAtLeast(1) ?: 1
        val labelW = 150f
        val valueW = 110f
        val barW = W - 2 * M - labelW - valueW
        r.byCategory.take(10).forEach { t ->
            newPageIfNeeded(22f)
            text(c, t.category?.label ?: "Not sorted yet", M, y + 10, 10.5f, INK)
            c.drawRoundRect(RectF(M + labelW, y + 2, M + labelW + barW, y + 12), 4f, 4f, fill(TRACK))
            val bw = (t.amount.cents.toFloat() / max * barW).coerceAtLeast(3f)
            c.drawRoundRect(RectF(M + labelW, y + 2, M + labelW + bw, y + 12), 4f, 4f, fill(BAR))
            text(c, "${t.amount.pdf()}  ${Math.round(t.share * 100)}%", W - M - valueW + 8, y + 11, 10f, MUTED)
            y += 22
        }
        y += 8; rule(c, y); y += 20

        // Daily bars
        newPageIfNeeded(140f)
        text(c, "Spending by day", M, y, 13f, INK, bold = true); y += 8
        r.biggestDay?.let {
            text(c, "Biggest day: ${it.first.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}, ${it.second.pdf()}", M, y + 12, 10f, MUTED)
        }
        y += 22
        val chartH = 80f
        val n = r.daily.size
        val gap = 2f
        val bw = (W - 2 * M - gap * (n - 1)) / n
        val dmax = r.daily.maxOfOrNull { it.cents }?.coerceAtLeast(1) ?: 1
        r.daily.forEachIndexed { i, v ->
            val x = M + i * (bw + gap)
            c.drawRoundRect(RectF(x, y, x + bw, y + chartH), 2f, 2f, fill(TRACK))
            if (v.cents > 0) {
                val h = (v.cents.toFloat() / dmax * chartH).coerceAtLeast(2f)
                c.drawRoundRect(RectF(x, y + chartH - h, x + bw, y + chartH), 2f, 2f, fill(BAR))
            }
        }
        y += chartH + 12
        text(c, "1", M, y, 9f, MUTED)
        text(c, "$n", W - M - 10, y, 9f, MUTED)
        y += 14; rule(c, y); y += 20

        // Top payees
        if (r.topPayees.isNotEmpty()) {
            newPageIfNeeded(40f + r.topPayees.size * 18f)
            text(c, "Where you paid most", M, y, 13f, INK, bold = true); y += 18
            r.topPayees.forEachIndexed { i, p ->
                text(c, "${i + 1}.  ${p.payee.take(48)}", M, y, 10.5f, INK)
                text(c, "${p.count}×", W - M - 160, y, 10f, MUTED)
                text(c, p.amount.pdf(), W - M - 100, y, 10.5f, INK, bold = true)
                y += 18
            }
        }

        footer(c, pageNo)
        doc.finishPage(page)
        return doc
    }

    private fun footer(c: Canvas, page: Int) {
        val generated = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        text(c, "made by T.I · Created on your phone on $generated. Kash never uploads your data.", M, H - M + 10, 8.5f, MUTED)
        text(c, "Page $page", W - M - 36, H - M + 10, 8.5f, MUTED)
    }

    private fun rule(c: Canvas, y: Float) = c.drawLine(M, y, W - M, y, Paint().apply { color = RULE; strokeWidth = 0.8f })

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        })
    }

    private fun wrap(s: String, size: Float, color: Int, width: Float): StaticLayout {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size }
        return StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
    }

    private fun Money.pdf(): String {
        val abs = Math.abs(cents)
        val whole = String.format(Locale.US, "%,d", abs / 100)
        val frac = abs % 100
        return (if (cents < 0) "−" else "") + "KSh $whole" + if (frac == 0L) "" else ".${frac.toString().padStart(2, '0')}"
    }
}
