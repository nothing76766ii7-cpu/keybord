package com.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.keyboard.engine.glide.*

/**
 * KeyboardView with GlideTypingComponent integration strictly following
 * PRD Section 2.2 specifications.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Key(
        val char: Char,
        val drawRect: RectF = RectF()
    )

    private val keyList = mutableListOf<Key>()
    private val keyPositions = mutableMapOf<Char, KeyPosition>()
    private val touchPoints = mutableListOf<TouchPoint>()
    private var lastTapTimestamp = 0L

    private val lexicon = TrieLexicon().apply {
        loadFromAssets(context)
    }

    // GlideDecoder instantiated once (PRD 2.2b)
    private var glideDecoder = GlideDecoder(
        keyPositions = keyPositions,
        lexicon = lexicon
    )

    var overlayView: GestureOverlayView? = null
    var suggestionStrip: SuggestionStrip? = null
    var onKeyTapListener: ((Char) -> Unit)? = null
    var onWordCommittedListener: ((String) -> Unit)? = null
    var onGlideCandidatesListener: ((List<GlideCandidate>) -> Unit)? = null

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#313244")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    private val rows = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredHeightPx = (220f * density).toInt()
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val heightPx = resolveSize(desiredHeightPx, heightMeasureSpec)
        setMeasuredDimension(widthPx, heightPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeys(w, h)
    }

    // PRD 2.2a / FIX D: After key layout is measured, rebuild keyPositions map and refresh decoder
    private fun recomputeKeys(width: Int, height: Int) {
        keyList.clear()
        keyPositions.clear()

        val rowHeight = height.toFloat() / rows.size
        for ((rowIndex, rowChars) in rows.withIndex()) {
            val keyWidth = width.toFloat() / rowChars.length
            val top = rowIndex * rowHeight
            val bottom = top + rowHeight

            for ((colIndex, ch) in rowChars.withIndex()) {
                val left = colIndex * keyWidth
                val right = left + keyWidth
                val rect = RectF(left + 4f, top + 4f, right - 4f, bottom - 4f)
                val key = Key(ch, rect)
                keyList.add(key)
                keyPositions[ch.lowercaseChar()] = KeyPosition(
                    character = ch.lowercaseChar(),
                    bounds = rect
                )
            }
        }
        glideDecoder.updateKeyPositions(HashMap(keyPositions))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#181825"))

        for (k in keyList) {
            canvas.drawRoundRect(k.drawRect, 12f, 12f, keyPaint)
            val textY = k.drawRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(k.char.uppercase(), k.drawRect.centerX(), textY, textPaint)
        }
    }

    // PRD 2.2c / FIX A: Touch handling with gesture trail and ACTION_UP decode
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = System.currentTimeMillis()
        val point = TouchPoint(event.x, event.y, now, event.pressure)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchPoints.clear()
                touchPoints.add(point)
                overlayView?.clearTrail()
                overlayView?.addPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchPoints.add(point)
                overlayView?.addPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchPoints.add(point)
                val candidates = glideDecoder.decodeGesture(touchPoints, lastTapTimestamp)
                if (candidates.isNotEmpty()) {
                    val listener = onGlideCandidatesListener
                    if (listener != null) {
                        listener.invoke(candidates)
                    } else {
                        val topWord = candidates.first().word
                        suggestionStrip?.setCandidates(candidates.map { it.word })
                        commitTopCandidate(topWord)
                    }
                } else {
                    val keyAtPoint = findKeyAt(event.x, event.y)
                    if (keyAtPoint != null) {
                        handleTap(keyAtPoint.char)
                    }
                }
                overlayView?.clearTrail()
                touchPoints.clear()
                lastTapTimestamp = now
                glideDecoder.notifyTap(now)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                overlayView?.clearTrail()
                touchPoints.clear()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): Key? {
        return keyList.find { it.drawRect.contains(x, y) }
    }

    private fun handleTap(char: Char) {
        onKeyTapListener?.invoke(char)
    }

    private fun commitTopCandidate(word: String) {
        onWordCommittedListener?.invoke(word)
    }
}
