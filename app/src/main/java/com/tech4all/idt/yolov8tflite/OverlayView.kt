package com.tech4all.idt.yolov8tflite

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.tech4all.idt.R
import kotlin.math.max

/**
 * A custom View used to draw bounding boxes and class labels over a camera preview.
 * It overlays object detection results on the screen.
 *
 * @constructor Creates an OverlayView instance with context and optional attributes.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()

    // Paint objects for bounding box and label text
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var bounds = Rect()

    init {
        initPaints()
    }

    /**
     * Clears the overlay and resets paint objects.
     */
    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    /**
     * Initializes paint styles for bounding box and text.
     */
    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        // Set bounding box color from resources
        boxPaint.color = ContextCompat.getColor(context ?: return, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    /**
     * Draws all bounding boxes and labels onto the canvas.
     *
     * @param canvas The canvas on which to draw.
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        results.forEach {
            // Convert normalized coordinates to actual view dimensions
            val left = it.x1 * width
            val top = it.y1 * height
            val right = it.x2 * width
            val bottom = it.y2 * height

            // Draw bounding box rectangle
            canvas.drawRect(left, top, right, bottom, boxPaint)

            val drawableText = it.clsName

            // Measure text size for background box
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()

            // Draw background for label
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Draw label text
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    /**
     * Sets the list of bounding boxes to be displayed and triggers a redraw.
     *
     * @param boundingBoxes List of detected BoundingBox results.
     */
    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
