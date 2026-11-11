package app.atthedoor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Venue

/**
 * 지도 대신 쓰는 계획 도식: 차량 접근 지점 → 주차·승하차 구역 → 보행 동선 → 건물 입구.
 * 지도 SDK 사용 가능 여부가 확인되지 않아, 판단에 필요한 네 구간만 구분해 보여 준다.
 */
@Composable
fun PlanDiagram(venue: Venue, candidate: Candidate, modifier: Modifier = Modifier) {
    val entrance = venue.entrance(candidate.walk.toEntranceId)
    val stairs = candidate.walk.stairs
    val walkLabel = buildString {
        append(
            when {
                !stairs.known -> "계단 여부 미확인"
                stairs.value == true -> "계단 있음"
                else -> "계단 없음"
            },
        )
        if (candidate.walk.distanceM.known) append(" · 약 ${candidate.walk.distanceM.value}m")
    }
    val nodes = listOf(
        "차량 접근" to candidate.accessPoint.approach,
        (if (candidate.type == CandidateType.DROPOFF) "승하차" else "주차") to candidate.name,
        "보행 동선" to walkLabel,
        "건물 입구" to (entrance?.name ?: "입구 정보 없음"),
    )
    val warn = !stairs.known || stairs.value == true
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = nodes.joinToString(", 다음 ") { "${it.first} ${it.second}" } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        nodes.forEachIndexed { i, (title, value) ->
            DiagramNode(title, value, highlight = i == 2 && warn, modifier = Modifier.weight(1f))
            if (i < nodes.lastIndex) {
                Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiagramNode(title: String, value: String, highlight: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) cs.tertiaryContainer else cs.surfaceVariant)
            .border(if (highlight) 2.dp else 0.dp, if (highlight) cs.tertiary else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, color = if (highlight) cs.onTertiaryContainer else cs.onSurface)
    }
}

/** 마이크 아이콘(기본 아이콘 세트에 없어 직접 그린다) */
val MicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            moveTo(17f, 11f)
            curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
            curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.92f)
            curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
            close()
        }
    }.build()
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
}

@Composable
fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
}
