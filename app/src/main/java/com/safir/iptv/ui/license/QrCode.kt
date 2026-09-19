package com.safir.iptv.ui.license

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The square somebody points a telephone at.
 *
 * Drawn rather than turned into a bitmap: a QR code is a grid of squares, and
 * letting Compose draw them keeps it crisp at whatever size the television happens
 * to be, with no image to allocate and no memory to give back.
 *
 * On a white card with a margin around it, because that is not decoration — a
 * scanner needs the quiet zone and the contrast, and a code drawn straight onto a
 * dark screen is a code that takes four tries to read from a sofa.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp
) {
    val matrix = remember(content) { encode(content) }

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        if (matrix == null) return@Box
        Canvas(Modifier.size(size - 24.dp)) {
            val cells = matrix.width
            if (cells <= 0) return@Canvas
            // Whole pixels per cell, or the rounding shows up as a moiré that some
            // phone cameras refuse outright.
            val cell = kotlin.math.floor(this.size.minDimension / cells)
            if (cell <= 0f) return@Canvas
            val drawn = cell * cells
            val left = (this.size.width - drawn) / 2f
            val top = (this.size.height - drawn) / 2f

            for (y in 0 until cells) {
                for (x in 0 until cells) {
                    if (!matrix.get(x, y)) continue
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(left + x * cell, top + y * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}

/**
 * @return the grid, or null when the text simply cannot be encoded. The screen it
 *   sits on shows the address in writing as well, so a missing square is a nuisance
 *   rather than a dead end.
 */
private fun encode(content: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        SIZE,
        SIZE,
        mapOf(
            EncodeHintType.MARGIN to 0,
            // A television screen can be dusty, reflective, and looked at from an
            // angle. The middle correction level recovers from a good deal of that
            // and costs only a slightly denser grid.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
}.getOrNull()

/**
 * The requested size in modules, not pixels: ZXing rounds up to whatever the content
 * needs, and the drawing above scales the result to the space it is given.
 */
private const val SIZE = 256
