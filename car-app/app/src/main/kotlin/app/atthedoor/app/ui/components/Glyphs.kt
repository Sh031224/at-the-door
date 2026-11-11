package app.atthedoor.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 순정 화면처럼 얇은 선 아이콘. 기본 세트에 없는 것은 24×24 격자에 1.8 굵기 선으로 그린다. */
object Glyph {
    val Check = Icons.Outlined.Check
    val Close = Icons.Outlined.Close
    val Info = Icons.Outlined.Info
    val Warning = Icons.Outlined.Warning
    val Chevron = Icons.Outlined.KeyboardArrowRight
    val Edit = Icons.Outlined.Edit
    val Refresh = Icons.Outlined.Refresh
    val Arrow = Icons.Outlined.ArrowForward
    val Phone = Icons.Outlined.Phone
    val Person = Icons.Outlined.Person

    private fun line(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()

    val Question: ImageVector by lazy {
        line("Question") {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close()
            moveTo(9.5f, 9.5f); curveTo(9.5f, 6.5f, 14.5f, 6.5f, 14.5f, 9.5f); curveTo(14.5f, 11.5f, 12f, 11.5f, 12f, 13.8f)
            moveTo(12f, 16.8f); lineTo(12f, 17.2f)
        }
    }
    val Block: ImageVector by lazy {
        line("Block") { moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); close(); moveTo(5.6f, 5.6f); lineTo(18.4f, 18.4f) }
    }
    val More: ImageVector by lazy {
        ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                listOf(5f, 12f, 19f).forEach { x -> moveTo(x + 1.8f, 12f); arcTo(1.8f, 1.8f, 0f, true, true, x + 1.79f, 12f); close() }
            }
        }.build()
    }
    val Mic: ImageVector by lazy {
        line("Mic") {
            moveTo(9f, 6f); arcTo(3f, 3f, 0f, false, true, 15f, 6f); lineTo(15f, 11f); arcTo(3f, 3f, 0f, false, true, 9f, 11f); close()
            moveTo(5.5f, 11f); curveTo(5.5f, 18.5f, 18.5f, 18.5f, 18.5f, 11f); moveTo(12f, 17f); lineTo(12f, 21f)
        }
    }
    val Car: ImageVector by lazy {
        line("Car") {
            moveTo(3.5f, 16f); lineTo(3.5f, 12.5f); lineTo(5.8f, 7.5f); lineTo(18.2f, 7.5f); lineTo(20.5f, 12.5f); lineTo(20.5f, 16f); close()
            moveTo(3.5f, 12.5f); lineTo(20.5f, 12.5f)
            moveTo(6f, 16f); lineTo(6f, 18.5f); moveTo(18f, 16f); lineTo(18f, 18.5f)
        }
    }
    val Parking: ImageVector by lazy {
        line("Parking") {
            moveTo(4f, 3.5f); lineTo(20f, 3.5f); lineTo(20f, 20.5f); lineTo(4f, 20.5f); close()
            moveTo(9.5f, 17f); lineTo(9.5f, 7f); lineTo(13f, 7f); curveTo(16.5f, 7f, 16.5f, 12.5f, 13f, 12.5f); lineTo(9.5f, 12.5f)
        }
    }
    val Door: ImageVector by lazy {
        line("Door") {
            moveTo(6f, 20.5f); lineTo(6f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 20.5f)
            moveTo(3.5f, 20.5f); lineTo(20.5f, 20.5f); moveTo(14.5f, 12f); lineTo(14.6f, 12f)
        }
    }
    val Walk: ImageVector by lazy {
        line("Walk") {
            moveTo(13.5f, 4.5f); arcTo(1.6f, 1.6f, 0f, true, true, 13.49f, 4.5f); close()
            moveTo(10f, 21f); lineTo(12f, 15f); lineTo(14.5f, 17.5f); lineTo(15.5f, 21f)
            moveTo(12f, 15f); lineTo(12.5f, 9f); lineTo(9f, 11f); lineTo(8f, 14f); moveTo(12.5f, 9f); lineTo(15f, 12f); lineTo(17.5f, 12.5f)
        }
    }
    val Pair: ImageVector by lazy {
        line("Pair") {
            moveTo(8.5f, 7f); arcTo(2.5f, 2.5f, 0f, true, true, 8.49f, 7f); close()
            moveTo(3.5f, 19.5f); curveTo(3.5f, 12.5f, 13.5f, 12.5f, 13.5f, 19.5f)
            moveTo(16f, 8f); arcTo(2.2f, 2.2f, 0f, true, true, 15.99f, 8f); close()
            moveTo(14.8f, 13.4f); curveTo(18.5f, 12.5f, 21f, 15f, 21f, 19.5f)
        }
    }
    val Group: ImageVector by lazy {
        line("Group") {
            moveTo(12f, 6.5f); arcTo(2.3f, 2.3f, 0f, true, true, 11.99f, 6.5f); close()
            moveTo(7.5f, 19.5f); curveTo(7.5f, 13f, 16.5f, 13f, 16.5f, 19.5f)
            moveTo(5.5f, 9f); arcTo(1.8f, 1.8f, 0f, true, true, 5.49f, 9f); close(); moveTo(1.8f, 18f); curveTo(1.8f, 14.5f, 4.5f, 13.3f, 7f, 14f)
            moveTo(18.5f, 9f); arcTo(1.8f, 1.8f, 0f, true, true, 18.49f, 9f); close(); moveTo(22.2f, 18f); curveTo(22.2f, 14.5f, 19.5f, 13.3f, 17f, 14f)
        }
    }
    val Qr: ImageVector by lazy {
        line("Qr") {
            listOf(3.5f to 3.5f, 14.5f to 3.5f, 3.5f to 14.5f).forEach { (x, y) -> moveTo(x, y); lineTo(x + 6f, y); lineTo(x + 6f, y + 6f); lineTo(x, y + 6f); close() }
            moveTo(14.5f, 14.5f); lineTo(16.5f, 14.5f); moveTo(19f, 14.5f); lineTo(20.5f, 14.5f); moveTo(14.5f, 18f); lineTo(14.5f, 20.5f); moveTo(17.5f, 17.5f); lineTo(20.5f, 20.5f)
        }
    }
    val Navigate: ImageVector by lazy {
        line("Navigate") { moveTo(12f, 3f); lineTo(19f, 20f); lineTo(12f, 16.2f); lineTo(5f, 20f); close() }
    }
}
