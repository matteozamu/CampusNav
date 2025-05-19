package com.tech4all.idt.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import androidx.core.graphics.scale

/**
 * The [Detector] class loads a TensorFlow Lite model and performs
 * object detection on input Bitmaps using YOLOv8 format.
 *
 * @param context Android context
 * @param modelPath Asset path to the TFLite model
 * @param labelPath Asset path to the class labels
 * @param detectorListener Callback interface for detection results
 */
class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {
    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    // Image processor: normalize and cast image input
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    private var selectedCategories = setOf<String>()

    /**
     * Loads the TFLite model, reads label file, and extracts input/output shapes.
     */
    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        // Read class labels from assets
        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (!line.isNullOrBlank()) {
                labels.add(line)
                line = reader.readLine()
            }
            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Clears interpreter memory.
     */
    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    /**
     * Runs inference on the input [frame] and reports results to listener.
     *
     * @param frame The input bitmap to be processed.
     */
    fun detect(frame: Bitmap) {
        interpreter ?: return
        if (tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) return

        var inferenceTime = SystemClock.uptimeMillis()

        // Resize image to model's input size
        val resizedBitmap = frame.scale(tensorWidth, tensorHeight, false)

        // Preprocess bitmap into TensorImage
        val tensorImage = TensorImage(DataType.FLOAT32).apply {
            load(resizedBitmap)
        }
        val processedImage = imageProcessor.process(tensorImage)

        // Prepare output buffer
        val output = TensorBuffer.createFixedSize(
            intArrayOf(1, numChannel, numElements),
            OUTPUT_IMAGE_TYPE
        )

        // Run the model
        interpreter?.run(processedImage.buffer, output.buffer)

        // Extract top results
        val bestBoxes = bestBox(output.floatArray)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        // Notify listener
        if (bestBoxes == null) {
            detectorListener.onEmptyDetect()
        } else {
            detectorListener.onDetect(bestBoxes, inferenceTime)
        }
    }

    /**
     * Parses raw output tensor and returns top bounding boxes above confidence threshold.
     */
    /**
     * Parses raw output tensor and returns top bounding boxes above confidence threshold,
     * filtered by user-selected categories.
     */
    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()

        for (c in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j

            // Find the class with the highest confidence score
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            // Filter out low-confidence detections
            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxIdx]

                // ❗ Filter out classes not selected by the user
                if (selectedCategories.isNotEmpty() && !selectedCategories.contains(clsName.replace(" ", "_").lowercase())) continue


                // Extract center and size of the bounding box
                val cx = array[c]
                val cy = array[c + numElements]
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]

                // Convert center coordinates to box corners
                val x1 = cx - (w / 2F)
                val y1 = cy - (h / 2F)
                val x2 = cx + (w / 2F)
                val y2 = cy + (h / 2F)

                // Ignore boxes with invalid coordinates
                if (x1 !in 0F..1F || y1 !in 0F..1F || x2 !in 0F..1F || y2 !in 0F..1F) continue

                // Add valid and selected bounding box to the list
                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = maxConf, cls = maxIdx, clsName = clsName
                    )
                )
            }
        }

        // Return boxes after applying Non-Maximum Suppression
        return if (boundingBoxes.isEmpty()) null else applyNMS(boundingBoxes)
    }


    /**
     * Applies Non-Maximum Suppression to remove overlapping boxes.
     */
    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                if (calculateIoU(first, nextBox) >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    /**
     * Calculates the Intersection over Union (IoU) between two bounding boxes.
     */
    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    /**
     * Interface for delivering detection results or failure notice.
     */
    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.45F
        private const val IOU_THRESHOLD = 0.6F
    }

    fun updateSelectedCategories(categories: Set<String>) {
        selectedCategories = categories.map {
            it.replace(" ", "_").lowercase()
        }.toSet()
    }



}
