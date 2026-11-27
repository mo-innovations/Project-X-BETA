package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ultra-lightweight inline vector icons so we do NOT bundle the ~20 MB material-icons-extended artifact.
 */
object XboxIcons {

    val Console: ImageVector by lazy {
        ImageVector.Builder(
            name = "Console",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 4f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 6f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 18f, 20f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 18f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 6f, 4f)
                close()
                moveTo(9f, 9f)
                horizontalLineTo(15f)
                moveTo(8f, 15.5f)
                horizontalLineTo(10f)
            }
        }.build()
    }

    val Gamepad: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gamepad",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.5f, 7f)
                horizontalLineTo(17.5f)
                curveTo(19.8f, 7f, 21.5f, 9f, 21.5f, 12.2f)
                lineTo(20.6f, 16.8f)
                curveTo(20.3f, 18.1f, 19f, 18.8f, 17.8f, 18f)
                lineTo(15.2f, 15.8f)
                horizontalLineTo(8.8f)
                lineTo(6.2f, 18f)
                curveTo(5f, 18.8f, 3.7f, 18.1f, 3.4f, 16.8f)
                lineTo(2.5f, 12.2f)
                curveTo(2.5f, 9f, 4.2f, 7f, 6.5f, 7f)
                close()
                moveTo(8f, 10.5f)
                verticalLineTo(13.5f)
                moveTo(6.5f, 12f)
                horizontalLineTo(9.5f)
                moveTo(15.2f, 11.2f)
                horizontalLineTo(15.3f)
                moveTo(17.2f, 12.8f)
                horizontalLineTo(17.3f)
            }
        }.build()
    }

    val Power: ImageVector by lazy {
        ImageVector.Builder(
            name = "Power",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 3f)
                verticalLineTo(11f)
                moveTo(17.6f, 6.6f)
                arcTo(8f, 8f, 0f, true, true, 6.4f, 6.6f)
            }
        }.build()
    }

    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 16.5f)
                arcTo(8.5f, 8.5f, 0f, true, true, 19.5f, 16.5f)
                moveTo(12f, 14f)
                lineTo(16.2f, 9.8f)
            }
        }.build()
    }

    val VisibilityOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "VisibilityOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(10.6f, 10.6f)
                arcTo(2f, 2f, 0f, false, false, 13.4f, 13.4f)
                moveTo(9.9f, 5.1f)
                curveTo(10.6f, 5f, 11.3f, 5f, 12f, 5f)
                curveTo(16.5f, 5f, 20f, 8.5f, 21.5f, 12f)
                curveTo(21f, 13.2f, 20.1f, 14.5f, 19f, 15.6f)
                moveTo(6.2f, 7.6f)
                curveTo(4.5f, 8.8f, 3.3f, 10.4f, 2.5f, 12f)
                curveTo(4f, 15.5f, 7.5f, 19f, 12f, 19f)
                curveTo(13.6f, 19f, 15.1f, 18.5f, 16.4f, 17.8f)
            }
        }.build()
    }

    val Wifi: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wifi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3f, 8.5f)
                curveTo(8f, 4f, 16f, 4f, 21f, 8.5f)
                moveTo(6.5f, 12.2f)
                curveTo(9.5f, 9.5f, 14.5f, 9.5f, 17.5f, 12.2f)
                moveTo(10f, 16f)
                curveTo(11.1f, 15f, 12.9f, 15f, 14f, 16f)
                moveTo(12f, 19.2f)
                horizontalLineTo(12.1f)
            }
        }.build()
    }
}
