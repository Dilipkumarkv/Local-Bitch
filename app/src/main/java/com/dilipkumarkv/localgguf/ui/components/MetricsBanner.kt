package com.dilipkumarkv.localgguf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dilipkumarkv.localgguf.data.model.GenerationStats
import java.util.Locale

@Composable
fun MetricsBanner(
    stats: GenerationStats,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricItem(label = "SPEED", value = String.format(Locale.US, "%.1f tok/s", stats.tokensPerSecond))
            MetricItem(label = "TOKENS", value = "${stats.generatedTokens}")
            MetricItem(label = "PREFILL", value = "${stats.promptEvalMs}ms")
            MetricItem(label = "GEN TIME", value = String.format(Locale.US, "%.1fs", stats.generationMs / 1000.0))
            if (stats.kvCacheReusedTokens > 0) {
                MetricItem(label = "KV HIT", value = "+${stats.kvCacheReusedTokens}")
            } else if (stats.contextTokensUsed > 0) {
                MetricItem(label = "CONTEXT", value = "${stats.contextTokensUsed}")
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
