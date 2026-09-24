package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * High-fidelity, custom-crafted Lucide Icons mapping for Jetpack Compose.
 * All icons utilize a clean 1.5f stroke width, round caps, and round joints to match
 * the sleek minimalist Lucide design language with premium thin crisp stroke.
 */
object Lucide {

    val Home = ImageVector.Builder(
        name = "Lucide.Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White), // Default white, dynamically overwriteable by tint
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 9f)
        lineTo(12f, 2f)
        lineTo(21f, 9f)
        verticalLineTo(20f)
        curveTo(21f, 20.53f, 20.79f, 21.04f, 20.41f, 21.41f)
        curveTo(20.04f, 21.79f, 19.53f, 22f, 19f, 22f)
        horizontalLineTo(5f)
        curveTo(4.47f, 22f, 3.96f, 21.79f, 3.59f, 21.41f)
        curveTo(3.21f, 21.04f, 3f, 20.53f, 3f, 20f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(9f, 22f)
        verticalLineTo(12f)
        horizontalLineTo(15f)
        verticalLineTo(22f)
    }.build()

    val Sparkles = ImageVector.Builder(
        name = "Lucide.Sparkles",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 3f)
        lineTo(14.5f, 9.5f)
        lineTo(21f, 12f)
        lineTo(14.5f, 14.5f)
        lineTo(12f, 21f)
        lineTo(9.5f, 14.5f)
        lineTo(3f, 12f)
        lineTo(9.5f, 9.5f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(5f, 3f)
        lineTo(6f, 5f)
        lineTo(8f, 6f)
        lineTo(6f, 7f)
        lineTo(5f, 9f)
        lineTo(4f, 7f)
        lineTo(2f, 6f)
        lineTo(4f, 5f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(19f, 17f)
        lineTo(20f, 19f)
        lineTo(22f, 20f)
        lineTo(20f, 21f)
        lineTo(19f, 23f)
        lineTo(18f, 21f)
        lineTo(16f, 20f)
        lineTo(18f, 19f)
        close()
    }.build()

    val Memories = ImageVector.Builder(
        name = "Lucide.Memories",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Square Library Outer Boundary
        moveTo(5f, 3f)
        lineTo(19f, 3f)
        quadTo(21f, 3f, 21f, 5f)
        lineTo(21f, 19f)
        quadTo(21f, 21f, 19f, 21f)
        lineTo(5f, 21f)
        quadTo(3f, 21f, 3f, 19f)
        lineTo(3f, 5f)
        quadTo(3f, 3f, 5f, 3f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Book 1: vertical line at x = 7
        moveTo(7f, 7f)
        lineTo(7f, 17f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Book 2: vertical line at x = 11
        moveTo(11f, 7f)
        lineTo(11f, 17f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Book 3: slanted leaning line from (15, 7) to (17, 17)
        moveTo(15f, 7f)
        lineTo(17f, 17f)
    }.build()

    val Mic = ImageVector.Builder(
        name = "Lucide.Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 2f)
        curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
        verticalLineTo(13f)
        curveTo(9f, 14.66f, 10.34f, 16f, 12f, 16f)
        curveTo(13.66f, 16f, 15f, 14.66f, 15f, 13f)
        verticalLineTo(5f)
        curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(19f, 10f)
        verticalLineTo(11f)
        curveTo(19f, 14.87f, 15.87f, 18f, 12f, 18f)
        curveTo(8.13f, 18f, 5f, 14.87f, 5f, 11f)
        verticalLineTo(10f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 18f)
        verticalLineTo(22f)
        moveTo(8f, 22f)
        lineTo(16f, 22f)
    }.build()

    val Stop = ImageVector.Builder(
        name = "Lucide.Stop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 4f)
        horizontalLineTo(20f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        close()
    }.build()

    val Play = ImageVector.Builder(
        name = "Lucide.Play",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(5f, 3f)
        lineTo(19f, 12f)
        lineTo(5f, 21f)
        close()
    }.build()

    val Pause = ImageVector.Builder(
        name = "Lucide.Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(6f, 4f)
        horizontalLineTo(10f)
        verticalLineTo(20f)
        horizontalLineTo(6f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(14f, 4f)
        horizontalLineTo(18f)
        verticalLineTo(20f)
        horizontalLineTo(14f)
        close()
    }.build()

    val Trash = ImageVector.Builder(
        name = "Lucide.Trash",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 6f)
        horizontalLineTo(21f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(19f, 6f)
        verticalLineTo(20f)
        curveTo(19f, 20.53f, 18.79f, 21.04f, 18.41f, 21.41f)
        curveTo(18.04f, 21.79f, 17.53f, 22f, 17f, 22f)
        horizontalLineTo(7f)
        curveTo(6.47f, 22f, 5.96f, 21.79f, 5.59f, 21.41f)
        curveTo(5.21f, 21.04f, 5f, 20.53f, 5f, 20f)
        verticalLineTo(6f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(8f, 6f)
        verticalLineTo(4f)
        curveTo(8f, 3.47f, 8.21f, 2.96f, 8.59f, 2.59f)
        curveTo(8.96f, 2.21f, 9.47f, 2f, 10f, 2f)
        horizontalLineTo(14f)
        curveTo(14.53f, 2f, 15.04f, 2.21f, 15.41f, 2.59f)
        curveTo(15.79f, 2.96f, 16f, 3.47f, 16f, 4f)
        verticalLineTo(6f)
    }.build()

    val Edit = ImageVector.Builder(
        name = "Lucide.Edit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 20f)
        lineTo(21f, 20f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(16.5f, 3.5f)
        curveTo(17.328f, 2.672f, 18.672f, 2.672f, 19.5f, 3.5f)
        curveTo(20.328f, 4.328f, 20.328f, 5.672f, 19.5f, 6.5f)
        lineTo(7f, 19f)
        lineTo(3f, 19.5f)
        lineTo(3.5f, 15.5f)
        lineTo(16.5f, 3.5f)
        close()
    }.build()

    val Copy = ImageVector.Builder(
        name = "Lucide.Copy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(9f, 9f)
        horizontalLineTo(20f)
        verticalLineTo(20f)
        horizontalLineTo(9f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(5f, 15f)
        horizontalLineTo(4f)
        curveTo(2.9f, 15f, 2f, 14.1f, 2f, 13f)
        verticalLineTo(4f)
        curveTo(2f, 2.9f, 2.9f, 2f, 4f, 2f)
        horizontalLineTo(13f)
        curveTo(14.1f, 2f, 15f, 2.9f, 15f, 4f)
        verticalLineTo(5f)
    }.build()

    val Share = ImageVector.Builder(
        name = "Lucide.Share",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 12f)
        verticalLineTo(20f)
        curveTo(4f, 20.53f, 4.21f, 21.04f, 4.59f, 21.41f)
        curveTo(4.96f, 21.79f, 5.47f, 22f, 6f, 22f)
        horizontalLineTo(18f)
        curveTo(18.53f, 22f, 19.04f, 21.79f, 19.41f, 21.41f)
        curveTo(19.79f, 21.04f, 20f, 20.53f, 20f, 20f)
        verticalLineTo(12f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(16f, 6f)
        lineTo(12f, 2f)
        lineTo(8f, 6f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 2f)
        verticalLineTo(15f)
    }.build()

    val Calendar = ImageVector.Builder(
        name = "Lucide.Calendar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(19f, 4f)
        horizontalLineTo(5f)
        curveTo(3.9f, 4f, 3f, 4.9f, 3f, 6f)
        verticalLineTo(20f)
        curveTo(3f, 21.1f, 3.9f, 22f, 5f, 22f)
        horizontalLineTo(19f)
        curveTo(20.1f, 22f, 21f, 21.1f, 21f, 20f)
        verticalLineTo(6f)
        curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(16f, 2f)
        verticalLineTo(6f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(8f, 2f)
        verticalLineTo(6f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 10f)
        horizontalLineTo(21f)
    }.build()

    val Image = ImageVector.Builder(
        name = "Lucide.Image",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Star / Sun in the image
        moveTo(8.5f, 8.5f)
        curveTo(8.5f, 9.33f, 7.83f, 10f, 7f, 10f)
        curveTo(6.17f, 10f, 5.5f, 9.33f, 5.5f, 8.5f)
        curveTo(5.5f, 7.67f, 6.17f, 7f, 7f, 7f)
        curveTo(7.83f, 7f, 8.5f, 7.67f, 8.5f, 8.5f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Mountain peak line
        moveTo(21f, 15f)
        lineTo(16f, 10f)
        lineTo(5f, 21f)
    }.build()

    val FileText = ImageVector.Builder(
        name = "Lucide.FileText",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(15f, 2f)
        horizontalLineTo(6f)
        curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
        verticalLineTo(20f)
        curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
        horizontalLineTo(18f)
        curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
        verticalLineTo(7f)
        lineTo(15f, 2f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(14f, 2f)
        verticalLineTo(8f)
        horizontalLineTo(20f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(16f, 13f)
        horizontalLineTo(8f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(16f, 17f)
        horizontalLineTo(8f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(10f, 9f)
        horizontalLineTo(8f)
    }.build()

    val Check = ImageVector.Builder(
        name = "Lucide.Check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(20f, 6f)
        lineTo(9f, 17f)
        lineTo(4f, 12f)
    }.build()

    val Plus = ImageVector.Builder(
        name = "Lucide.Plus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 5f)
        verticalLineTo(19f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(5f, 12f)
        horizontalLineTo(19f)
    }.build()

    val Square = ImageVector.Builder(
        name = "Lucide.Square",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        close()
    }.build()

    val CheckSquare = ImageVector.Builder(
        name = "Lucide.CheckSquare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Outlined checkbox background box
        moveTo(3f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Inner Checkmark
        moveTo(9f, 11f)
        lineTo(12f, 14f)
        lineTo(17f, 8f)
    }.build()

    val Search = ImageVector.Builder(
        name = "Lucide.Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(11f, 19f)
        curveTo(15.4183f, 19f, 19f, 15.4183f, 19f, 11f)
        curveTo(19f, 6.58172f, 15.4183f, 3f, 11f, 3f)
        curveTo(6.58172f, 3f, 3f, 6.58172f, 3f, 11f)
        curveTo(3f, 15.4183f, 6.58172f, 19f, 11f, 19f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(21f, 21f)
        lineTo(16.65f, 16.65f)
    }.build()

    val Paperclip = ImageVector.Builder(
        name = "Lucide.Paperclip",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(21.44f, 11.05f)
        lineTo(12.12f, 20.37f)
        curveTo(10.17f, 22.32f, 7f, 22.32f, 5.05f, 20.37f)
        curveTo(3.1f, 18.42f, 3.1f, 15.25f, 5.05f, 13.3f)
        lineTo(14.37f, 3.98f)
        curveTo(15.67f, 2.68f, 17.78f, 2.68f, 19.08f, 3.98f)
        curveTo(20.38f, 5.28f, 20.38f, 7.39f, 19.08f, 8.69f)
        lineTo(9.75f, 18.01f)
        curveTo(9.1f, 18.66f, 8.05f, 18.66f, 7.4f, 18.01f)
        curveTo(6.75f, 17.36f, 6.75f, 16.31f, 7.4f, 15.66f)
        lineTo(16.12f, 6.94f)
    }.build()

    val Brain = ImageVector.Builder(
        name = "Lucide.Brain",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Left Hemisphere boundary
        moveTo(12f, 5f)
        curveTo(10f, 5f, 9.5f, 4f, 7.5f, 4f)
        curveTo(5f, 4f, 4f, 6f, 4f, 9f)
        curveTo(4f, 10.5f, 4.5f, 11f, 4.5f, 12f)
        curveTo(4.5f, 13f, 4f, 13.5f, 4f, 15f)
        curveTo(4f, 18f, 5.5f, 20f, 8f, 20f)
        curveTo(9.5f, 20f, 10.5f, 19f, 12f, 19f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Right Hemisphere boundary
        moveTo(12f, 5f)
        curveTo(14f, 5f, 14.5f, 4f, 16.5f, 4f)
        curveTo(19f, 4f, 20f, 6f, 20f, 9f)
        curveTo(20f, 10.5f, 19.5f, 11f, 19.5f, 12f)
        curveTo(19.5f, 13f, 20f, 13.5f, 20f, 15f)
        curveTo(20f, 18f, 18.5f, 20f, 16f, 20f)
        curveTo(14.5f, 20f, 13.5f, 19f, 12f, 19f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Center dividing fissure / crease
        moveTo(12f, 5f)
        verticalLineTo(19f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Left Hemisphere inside cerebral folds
        moveTo(8f, 9f)
        horizontalLineTo(4.5f)
        moveTo(9.5f, 12f)
        curveTo(8f, 12f, 7.5f, 13f, 6.5f, 13f)
        horizontalLineTo(4f)
        moveTo(8f, 16f)
        horizontalLineTo(5.5f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Right Hemisphere inside cerebral folds
        moveTo(16f, 9f)
        horizontalLineTo(19.5f)
        moveTo(14.5f, 12f)
        curveTo(16f, 12f, 16.5f, 13f, 17.5f, 13f)
        horizontalLineTo(20f)
        moveTo(16f, 16f)
        horizontalLineTo(18.5f)
    }.build()

    val Clock = ImageVector.Builder(
        name = "Lucide.Clock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Outer boundary circle
        moveTo(12f, 2f)
        curveTo(17.52f, 2f, 22f, 6.48f, 22f, 12f)
        curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
        curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
        curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
        close()
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Hour hand pointing upwards & Minute hand pointing right or angled
        moveTo(12f, 6f)
        lineTo(12f, 12f)
        lineTo(16f, 14f)
    }.build()

    val History = Clock

    val Menu = ImageVector.Builder(
        name = "Lucide.Menu",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        moveTo(4f, 18f)
        lineTo(20f, 18f)
    }.build()

    val Settings = ImageVector.Builder(
        name = "Lucide.Settings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Center Circle
        moveTo(12f, 15f)
        arcTo(3f, 3f, 0f, true, true, 12f, 9f)
        arcTo(3f, 3f, 0f, false, true, 12f, 15f)
        // Outer gear teeth & ring
        moveTo(19.4f, 15f)
        arcTo(1.65f, 1.65f, 0f, false, false, 19.73f, 16.82f)
        lineTo(19.79f, 16.88f)
        arcTo(2f, 2f, 0f, false, true, 19.79f, 19.71f)
        arcTo(2f, 2f, 0f, false, true, 16.96f, 19.71f)
        lineTo(16.9f, 19.65f)
        arcTo(1.65f, 1.65f, 0f, false, false, 15.08f, 19.32f)
        arcTo(1.65f, 1.65f, 0f, false, false, 14.08f, 20.83f)
        lineTo(14.08f, 21f)
        arcTo(2f, 2f, 0f, false, true, 12.08f, 23f)
        arcTo(2f, 2f, 0f, false, true, 10.08f, 21f)
        lineTo(10.08f, 20.91f)
        arcTo(1.65f, 1.65f, 0f, false, false, 9.08f, 19.4f)
        arcTo(1.65f, 1.65f, 0f, false, false, 7.26f, 19.73f)
        lineTo(7.2f, 19.79f)
        arcTo(2f, 2f, 0f, false, true, 4.37f, 19.79f)
        arcTo(2f, 2f, 0f, false, true, 4.37f, 16.96f)
        lineTo(4.43f, 16.9f)
        arcTo(1.65f, 1.65f, 0f, false, false, 4.76f, 15.08f)
        arcTo(1.65f, 1.65f, 0f, false, false, 3.25f, 14.08f)
        lineTo(3.25f, 14f)
        arcTo(2f, 2f, 0f, false, true, 1.25f, 12f)
        arcTo(2f, 2f, 0f, false, true, 3.25f, 10f)
        lineTo(3.34f, 10f)
        arcTo(1.65f, 1.65f, 0f, false, false, 4.85f, 9f)
        arcTo(1.65f, 1.65f, 0f, false, false, 4.52f, 7.18f)
        lineTo(4.46f, 7.12f)
        arcTo(2f, 2f, 0f, false, true, 4.46f, 4.29f)
        arcTo(2f, 2f, 0f, false, true, 7.29f, 4.29f)
        lineTo(7.35f, 4.35f)
        arcTo(1.65f, 1.65f, 0f, false, false, 9.17f, 4.68f)
        arcTo(1.65f, 1.65f, 0f, false, false, 10.17f, 3.17f)
        lineTo(10.17f, 3f)
        arcTo(2f, 2f, 0f, false, true, 12.17f, 1f)
        arcTo(2f, 2f, 0f, false, true, 14.17f, 3f)
        lineTo(14.17f, 3.09f)
        arcTo(1.65f, 1.65f, 0f, false, false, 15.17f, 4.6f)
        arcTo(1.65f, 1.65f, 0f, false, false, 16.99f, 4.27f)
        lineTo(17.05f, 4.21f)
        arcTo(2f, 2f, 0f, false, true, 19.88f, 4.21f)
        arcTo(2f, 2f, 0f, false, true, 19.88f, 7.04f)
        lineTo(19.82f, 7.1f)
        arcTo(1.65f, 1.65f, 0f, false, false, 19.49f, 8.92f)
        arcTo(1.65f, 1.65f, 0f, false, false, 21f, 9.92f)
        lineTo(21.08f, 9.92f)
        arcTo(2f, 2f, 0f, false, true, 23.08f, 11.92f)
        arcTo(2f, 2f, 0f, false, true, 21.08f, 13.92f)
        lineTo(20.99f, 13.92f)
        arcTo(1.65f, 1.65f, 0f, false, false, 19.48f, 14.92f)
    }.build()

    val Download = ImageVector.Builder(
        name = "Lucide.Download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(21f, 15f)
            lineTo(21f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 21f)
            lineTo(5f, 21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 3f, y1 = 19f)
            lineTo(3f, 15f)
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(12f, 15f)
            lineTo(12f, 3f)
        }
    }.build()

    val ArrowRight = ImageVector.Builder(
        name = "Lucide.ArrowRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
        moveTo(12f, 5f)
        lineTo(19f, 12f)
        lineTo(12f, 19f)
    }.build()

    val ArrowLeft = ImageVector.Builder(
        name = "Lucide.ArrowLeft",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(19f, 12f)
        lineTo(5f, 12f)
        moveTo(12f, 5f)
        lineTo(5f, 12f)
        lineTo(12f, 19f)
    }.build()

    val Waves = ImageVector.Builder(
        name = "Lucide.Waves",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 10f)
        lineTo(3f, 14f)
        moveTo(7f, 6f)
        lineTo(7f, 18f)
        moveTo(11f, 4f)
        lineTo(11f, 20f)
        moveTo(15f, 7f)
        lineTo(15f, 17f)
        moveTo(19f, 9f)
        lineTo(19f, 15f)
    }.build()

    val X = ImageVector.Builder(
        name = "Lucide.X",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(18f, 6f)
        lineTo(6f, 18f)
        moveTo(6f, 6f)
        lineTo(18f, 18f)
    }.build()

    val Scan = ImageVector.Builder(
        name = "Lucide.Scan",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Corners scanner outline
        moveTo(8f, 4f)
        lineTo(4f, 4f)
        lineTo(4f, 8f)
        
        moveTo(16f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 8f)
        
        moveTo(4f, 16f)
        lineTo(4f, 20f)
        lineTo(8f, 20f)
        
        moveTo(20f, 16f)
        lineTo(20f, 20f)
        lineTo(16f, 20f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Center box
        moveTo(8f, 8f)
        lineTo(16f, 8f)
        lineTo(16f, 16f)
        lineTo(8f, 16f)
        close()
    }.build()

    val Type = ImageVector.Builder(
        name = "Lucide.Type",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.0f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 7f)
        verticalLineTo(4f)
        horizontalLineTo(20f)
        verticalLineTo(7f)
        
        moveTo(9f, 20f)
        horizontalLineTo(15f)
        
        moveTo(12f, 4f)
        verticalLineTo(20f)
    }.build()

    val Upload = ImageVector.Builder(
        name = "Lucide.Upload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(21f, 15f)
        verticalLineTo(19f)
        curveTo(21f, 19.53f, 20.79f, 20.04f, 20.41f, 20.41f)
        curveTo(20.04f, 20.79f, 19.53f, 21f, 19f, 21f)
        horizontalLineTo(5f)
        curveTo(4.47f, 21f, 3.96f, 20.79f, 3.59f, 20.41f)
        curveTo(3.21f, 20.04f, 3f, 19.53f, 3f, 19f)
        verticalLineTo(15f)
        
        moveTo(17f, 8f)
        lineTo(12f, 3f)
        lineTo(7f, 8f)
        
        moveTo(12f, 3f)
        verticalLineTo(15f)
    }.build()

    val Cloud = ImageVector.Builder(
        name = "Lucide.Cloud",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(17.5f, 19f)
        horizontalLineTo(9f)
        curveTo(6.24f, 19f, 4f, 16.76f, 4f, 14f)
        curveTo(4f, 11.51f, 5.82f, 9.44f, 8.24f, 9.06f)
        curveTo(9.04f, 6.1f, 11.75f, 4f, 15f, 4f)
        curveTo(18.87f, 4f, 22f, 7.13f, 22f, 11f)
        curveTo(22f, 11.34f, 21.98f, 11.67f, 21.93f, 12f)
        curveTo(23.18f, 12.87f, 24f, 14.34f, 24f, 16f)
        curveTo(24f, 18.76f, 21.76f, 21f, 19f, 21f)
    }.build()

    val Pen = ImageVector.Builder(
        name = "Lucide.Pen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(17f, 3f)
        lineTo(21f, 7f)
        lineTo(7f, 21f)
        lineTo(3f, 21f)
        lineTo(3f, 17f)
        close()
    }.build()

    val Brush = ImageVector.Builder(
        name = "Lucide.Brush",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(9.06f, 11.9f)
        lineTo(19f, 2f)
        lineTo(22f, 5f)
        lineTo(12.1f, 14.94f)
        moveTo(9.06f, 11.9f)
        curveTo(8f, 13f, 7f, 14.5f, 7f, 16.5f)
        curveTo(7f, 19.5f, 4.5f, 21f, 2f, 21f)
        curveTo(4f, 18.5f, 4.5f, 16f, 6.5f, 14f)
        curveTo(7.5f, 13f, 8.5f, 12f, 9.06f, 11.9f)
    }.build()

    val Eraser = ImageVector.Builder(
        name = "Lucide.Eraser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(20f, 20f)
        horizontalLineTo(7f)
        lineTo(3f, 16f)
        curveTo(2.2f, 15.2f, 2.2f, 14f, 3f, 13.2f)
        lineTo(13.2f, 3f)
        curveTo(14f, 2.2f, 15.2f, 2.2f, 16f, 3f)
        lineTo(21f, 8f)
        curveTo(21.8f, 8.8f, 21.8f, 10f, 21f, 10.8f)
        lineTo(11f, 20f)
    }.build()

    val MousePointer = ImageVector.Builder(
        name = "Lucide.MousePointer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 3f)
        lineTo(10.07f, 19.97f)
        lineTo(12.58f, 12.58f)
        lineTo(19.97f, 10.07f)
        close()
    }.build()

    val Undo = ImageVector.Builder(
        name = "Lucide.Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 7f)
        verticalLineTo(13f)
        horizontalLineTo(9f)
        moveTo(3f, 13f)
        curveTo(5.5f, 8.5f, 10.5f, 6f, 16f, 7f)
        curveTo(20.5f, 8f, 22f, 12.5f, 22f, 16f)
    }.build()

    val Redo = ImageVector.Builder(
        name = "Lucide.Redo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(21f, 7f)
        verticalLineTo(13f)
        horizontalLineTo(15f)
        moveTo(21f, 13f)
        curveTo(18.5f, 8.5f, 13.5f, 6f, 8f, 7f)
        curveTo(3.5f, 8f, 2f, 12.5f, 2f, 16f)
    }.build()

    val ZoomIn = ImageVector.Builder(
        name = "Lucide.ZoomIn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(11f, 11f)
        horizontalLineTo(17f)
        moveTo(14f, 8f)
        verticalLineTo(14f)
        moveTo(21f, 21f)
        lineTo(16.65f, 16.65f)
        moveTo(19f, 11f)
        curveTo(19f, 15.418f, 15.418f, 19f, 11f, 19f)
        curveTo(6.582f, 19f, 3f, 15.418f, 3f, 11f)
        curveTo(3f, 6.582f, 6.582f, 3f, 11f, 3f)
        curveTo(15.418f, 3f, 19f, 6.582f, 19f, 11f)
        close()
    }.build()

    val RefreshCw = ImageVector.Builder(
        name = "Lucide.RefreshCw",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 12f)
        curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
        curveTo(15.5f, 3f, 18.5f, 5f, 20f, 8f)
        moveTo(21f, 3f)
        verticalLineTo(8f)
        horizontalLineTo(16f)
        moveTo(21f, 12f)
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        curveTo(8.5f, 21f, 5.5f, 19f, 4f, 16f)
        moveTo(3f, 21f)
        verticalLineTo(16f)
        horizontalLineTo(8f)
    }.build()

    val Grid = ImageVector.Builder(
        name = "Lucide.Grid",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 3f)
        horizontalLineTo(10f)
        verticalLineTo(10f)
        horizontalLineTo(3f)
        close()
        moveTo(14f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(10f)
        horizontalLineTo(14f)
        close()
        moveTo(14f, 14f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(14f)
        close()
        moveTo(3f, 14f)
        horizontalLineTo(10f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        close()
    }.build()

    val Trash2 = ImageVector.Builder(
        name = "Lucide.Trash2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 6f)
        horizontalLineTo(21f)
        moveTo(19f, 6f)
        verticalLineTo(20f)
        curveTo(19f, 21.1f, 18.1f, 22f, 17f, 22f)
        horizontalLineTo(7f)
        curveTo(5.9f, 22f, 5f, 21.1f, 5f, 20f)
        verticalLineTo(6f)
        moveTo(8f, 6f)
        verticalLineTo(4f)
        curveTo(8f, 2.9f, 8.9f, 2f, 10f, 2f)
        horizontalLineTo(14f)
        curveTo(15.1f, 2f, 16f, 2.9f, 16f, 4f)
        verticalLineTo(6f)
        moveTo(10f, 11f)
        verticalLineTo(17f)
        moveTo(14f, 11f)
        verticalLineTo(17f)
    }.build()

    val Maximize2 = ImageVector.Builder(
        name = "Lucide.Maximize2",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(15f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(9f)
        moveTo(9f, 21f)
        horizontalLineTo(3f)
        verticalLineTo(15f)
        moveTo(21f, 3f)
        lineTo(14f, 10f)
        moveTo(3f, 21f)
        lineTo(10f, 14f)
    }.build()

    val AlertCircle = ImageVector.Builder(
        name = "Lucide.AlertCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Circle
        moveTo(22f, 12f)
        arcTo(10f, 10f, 0f, true, true, 2f, 12f)
        arcTo(10f, 10f, 0f, false, true, 22f, 12f)
    }.path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Exclamation line
        moveTo(12f, 8f)
        lineTo(12f, 12f)
        // Dot
        moveTo(12f, 16f)
        lineTo(12.01f, 16f)
    }.build()

    val MoveHorizontal = ImageVector.Builder(
        name = "Lucide.MoveHorizontal",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(18f, 8f)
        lineTo(22f, 12f)
        lineTo(18f, 16f)
        moveTo(6f, 8f)
        lineTo(2f, 12f)
        lineTo(6f, 16f)
        moveTo(2f, 12f)
        horizontalLineTo(22f)
    }.build()
}
