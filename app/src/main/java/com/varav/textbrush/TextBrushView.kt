package com.varav.textbrush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.contains
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import kotlin.math.floor

private const val MIN_TRACK_DIST = 20
private const val SAMPLE_LENGTH = 170f
private const val MAX_ZOOM = 1.25f
private const val MIN_ZOOM = 0.25f
private const val ZOOM_FACTOR = 8f
private const val BOUNDS_SLOP = 7f

class TextBrushView(context: Context?) : View(context) {

    data class DrawTextContext(
        var text: String = "x",
        var scale: Float = 1f,
        var color: Int = Color.WHITE
    )

    data class DrawnText(
        val path: Path,
        val ctx: DrawTextContext,
        val bounds: RectF = RectF()
    ) {
        fun handleTransform(curTransform: Op.Transform, pan: PointF, zoom: Float) {
            val delta = (zoom - 1) * ZOOM_FACTOR
            ctx.scale = (ctx.scale + delta).coerceIn(MIN_ZOOM..MAX_ZOOM)
            curTransform.offset.x += (pan.x)
            curTransform.offset.y += (pan.y)
            path.offset(pan.x, pan.y)
            bounds.offset(pan.x, pan.y)
        }
    }

    sealed class Op {
        data class TextDrawn(val drawnText: DrawnText) : Op()

        data class Transform(
            val drawnText: DrawnText,
            val offset: PointF,
            val preScale: Float
        ) : Op()
    }

    var captureEvents = true

    private var isTracing = false
    private val curPath = Path()
    private val curDrawCtx = DrawTextContext()
    private val curPoint = PointF()
    private val lastPoint = PointF()

    private val drawnTexts = arrayListOf<DrawnText>()
    private var lastTracked : DrawnText? = null
    private var curTransform: Op.Transform? = null
    private val ops = arrayListOf<Op>()

    private val pathMeasure = PathMeasure()
    private val textPaint = TextPaint().apply {
        letterSpacing = 0.1f
    }

    // DEBUG!
    private val drawDebug = false
    private val debugPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
    }

    init {
        isFocusable = true;
        isFocusableInTouchMode = true;
    }

    override fun onDraw(canvas: Canvas?) {
        if (canvas != null) {
            canvas.drawColor(Color.BLACK)
            for (drawnText in drawnTexts) {
                canvas.drawTextPath(drawnText.path, drawnText.ctx)
            }
            if (isTracing) {
                canvas.drawTextPath(curPath, curDrawCtx)
            }
        }

    }

    private fun Canvas.drawTextPath(path: Path, ctx: DrawTextContext) {
        pathMeasure.setPath(path, false)
        textPaint.color = ctx.color
        textPaint.textSize = 80f * ctx.scale
        val div = pathMeasure.length / textPaint.measureText(ctx.text)
        val timesRepeat = div.floorInt()
        val chars = (ctx.text.length * (div % 1)).floorInt()
        val textContent = ctx.text.repeat(timesRepeat) + ctx.text.substring(0 until chars)
        drawTextOnPath(textContent, path, 0f, 0f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && captureEvents) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    curPath.moveTo(event.x, event.y)
                    isTracing = true
                    lastPoint.set(event.x, event.y)
                }

                MotionEvent.ACTION_MOVE -> {
                    curPoint.set(event.x, event.y)
                    val shouldTrack = (lastPoint - curPoint).length() > MIN_TRACK_DIST
                    if (shouldTrack) {
                        curPath.lineTo(event.x, event.y)
                        lastPoint.set(curPoint)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    finalizeDrawText()
                    curPath.reset()
                    isTracing = false
                }

                else -> return false
            }
            postInvalidate()
            return true
        }
        return false
    }

    private fun finalizeDrawText() {
        pathMeasure.setPath(curPath, false)
        val samplePoints = (pathMeasure.length / SAMPLE_LENGTH).floorInt()
        val pos = floatArrayOf(0f, 0f)
        val pointsList = Array(samplePoints + 1) {
            when (it) {
                samplePoints -> PointF(lastPoint)
                else -> {
                    pathMeasure.getPosTan(it * SAMPLE_LENGTH, pos, null)
                    PointF(pos[0], pos[1])
                }
            }
        }
        val path = when {
            (pointsList.size > 2) -> {
                val curve = BezierSplineUtil.computeControlPoints(pointsList)
                Path().apply {
                    moveTo(pointsList[0].x, pointsList[0].y)
                    for (i in curve.indices) {
                        cubicTo(
                            curve[i].first.x, curve[i].first.y,
                            curve[i].second.x, curve[i].second.y,
                            pointsList[i + 1].x, pointsList[i + 1].y
                        )
                    }
                }
            }

            else -> Path(curPath)
        }
        val drawnText = DrawnText(path, curDrawCtx.copy()).apply {
            path.computeBounds(bounds, true)
            bounds.expand(BOUNDS_SLOP)
        }
        drawnTexts.add(drawnText)
        ops.add(Op.TextDrawn(drawnText))
    }

    fun clear() {
        drawnTexts.clear()
        curPath.reset()
        postInvalidate()
    }

    fun setDrawText(drawText: String) {
        curDrawCtx.text = drawText
    }

    fun setDrawScale(scale: Float) {
        curDrawCtx.scale = scale
    }

    fun setDrawColor(color: Int) {
        curDrawCtx.color = color
    }

    fun undo() {
       ops.removeLastOrNull()?.also {
           when (it) {
               is Op.TextDrawn -> {
                   drawnTexts.remove(it.drawnText)
               }
               is Op.Transform -> {
                   it.drawnText.ctx.scale = it.preScale
                   it.drawnText.path.offset(-it.offset.x, -it.offset.y)
                   it.drawnText.bounds.offset(-it.offset.x, -it.offset.y)
                   if (curTransform == it) {
                       endTracking()
                   }
               }
           }
       }
        postInvalidate()
    }

    fun onGesture(center: PointF, pan: PointF, zoom: Float) {
        postInvalidate()
        lastTracked?.apply {
            if (bounds.contains(center)) {
                handleTransform(curTransform!!, pan, zoom)
                return
            }
        }
        // End tracking
        endTracking()
        for (drawnText in drawnTexts) {
            if (drawnText.bounds.contains(center)) {
                val transform = Op.Transform(drawnText, PointF(), drawnText.ctx.scale)
                ops.add(transform)
                drawnText.handleTransform(transform, pan, zoom)
                curTransform = transform
                lastTracked = drawnText
                return
            }
        }
    }

    private fun endTracking() {
        if (lastTracked != null) {
            lastTracked = null
            curTransform = null
        }
    }
}

private fun Float.floorInt() = floor(this).toInt()

private fun RectF.expand(amount: Float) {
    top -= amount
    left -= amount
    bottom += amount
    right += amount
}
