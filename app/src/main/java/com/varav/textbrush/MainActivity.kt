package com.varav.textbrush

import android.graphics.PointF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.varav.textbrush.ui.theme.TextBrushTheme

val drawTextColors = listOf(
    Pair(Color.White, android.graphics.Color.WHITE),
    Pair(Color.Red, android.graphics.Color.RED),
    Pair(Color.Blue, android.graphics.Color.BLUE),
    Pair(Color.Green, android.graphics.Color.GREEN),
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var textBrushView: TextBrushView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()
        setContent {
            TextBrushTheme {
                var drawText by remember { mutableStateOf("Hello") }
                var size by remember { mutableStateOf(0.5f) }
                var drawMode by remember { mutableStateOf(true) }
                var selectedColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
                var showStyleDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    textBrushView.setDrawText(drawText)
                    textBrushView.setDrawScale(size)
                    textBrushView.setDrawColor(selectedColor)
                    textBrushView.captureEvents = drawMode
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    AndroidView(
                        factory = {
                            TextBrushView(it).apply {
                                textBrushView = this
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(drawMode) {
                                if (!drawMode) {
                                    detectTransformGestures(
                                        onGesture = { centroid, pan, zoom, _ ->
                                            val center = PointF(centroid.x, centroid.y)
                                            if (pan.isSpecified || zoom != 1f) {
                                                val panF = PointF(pan.x, pan.y)
                                                textBrushView.onGesture(center, panF, zoom)
                                            }
                                        }
                                    )
                                }

                            }
                    )
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .background(color = Color.DarkGray, shape = RoundedCornerShape(20.dp))
                    ) {
                        IconToggleButton(
                            checked = drawMode,
                            onCheckedChange = {
                                drawMode = drawMode.not()
                                textBrushView.captureEvents = drawMode
                            }) {
                            Icon(
                                Icons.Default.Edit,
                                modifier = Modifier.size(40.dp).padding(5.dp),
                                contentDescription = "edit",
                                tint = if (drawMode) Color.White else Color.Gray
                            )
                        }
                        IconToggleButton(
                            checked = !drawMode,
                            onCheckedChange = {
                                drawMode = drawMode.not()
                                textBrushView.captureEvents = drawMode
                            }) {
                            Icon(
                                Icons.Default.PanTool,
                                modifier = Modifier.size(40.dp).padding(5.dp),
                                contentDescription = "pan",
                                tint = if (!drawMode) Color.White else Color.Gray
                            )
                        }
                        IconButton(onClick = { textBrushView.undo() }) {
                            Icon(
                                Icons.Default.Refresh,
                                modifier = Modifier.size(40.dp).padding(5.dp),
                                contentDescription = "undo",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { clearCanvas() }) {
                            Icon(
                                Icons.Default.Clear,
                                modifier = Modifier.size(40.dp).padding(5.dp),
                                contentDescription = "clear",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showStyleDialog = showStyleDialog.not() }) {
                            Icon(
                                Icons.Default.MoreVert,
                                modifier = Modifier.size(40.dp).padding(5.dp),
                                contentDescription = "style",
                                tint = Color.White
                            )
                        }
                    }
                    if (showStyleDialog) {
                        Dialog(
                            onDismissRequest = { showStyleDialog = false },
                        ) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(modifier = Modifier.padding(15.dp)) {
                                    Column {
                                        Text(
                                            text = "Edit Style",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.size(20.dp))
                                        TextField(
                                            value = drawText,
                                            onValueChange = {
                                                drawText = it
                                                textBrushView.setDrawText(it)
                                            },
                                            label = { Text("Draw Text") },
                                            singleLine = true,
                                        )
                                        Spacer(modifier = Modifier.size(20.dp))
                                        Text(
                                            "Draw Size",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Slider(value = size, onValueChange = {
                                            size = it
                                            textBrushView.setDrawScale(it)
                                        })
                                        Spacer(modifier = Modifier.size(20.dp))
                                        Text(
                                            "Draw Color",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(
                                            modifier = Modifier
                                                .padding(5.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            for (color in drawTextColors) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(color = color.first)
                                                        .clickable {
                                                            selectedColor = color.second
                                                            textBrushView.setDrawColor(
                                                                selectedColor
                                                            )
                                                        }
                                                ) {
                                                    if (selectedColor == color.second) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            modifier = Modifier.align(Alignment.Center),
                                                            contentDescription = "Check",
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun clearCanvas() {
        textBrushView.clear()
    }

    private fun hideSystemUi() {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            view.onApplyWindowInsets(windowInsets)
        }
    }
}

