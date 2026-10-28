package org.nivora.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Panel = Color(0xCC252D3B)
private val Cyan = Color(0xFF66D9E8)

@Composable
internal fun AppTile(
    app: LauncherApp,
    onClick: () -> Unit,
    showLabel: Boolean = true,
    onIconConversionMeasured: ((String, Long) -> Unit)? = null
) {
    val iconResult = remember(app.icon) {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val bitmap = app.icon.toImageBitmap()
        bitmap to (SystemClock.elapsedRealtimeNanos() - startedAt)
    }
    val iconBitmap = iconResult.first

    LaunchedEffect(app.packageName, iconResult.second, onIconConversionMeasured) {
        onIconConversionMeasured?.invoke(app.packageName, iconResult.second)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Panel),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1), color = Cyan, fontSize = 22.sp)
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun Drawable.toImageBitmap(): ImageBitmap? {
    return try {
        val width = intrinsicWidth.coerceIn(1, 192)
        val height = intrinsicHeight.coerceIn(1, 192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
