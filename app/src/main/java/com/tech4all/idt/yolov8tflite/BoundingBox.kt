package com.tech4all.idt.yolov8tflite

/**
 * Data class representing a bounding box in an object detection model.
 *
 * This class stores information about the coordinates and properties of a detected bounding box
 * from an object detection model (such as YOLOv8). It includes both the original bounding box coordinates
 * and derived attributes like center, width, and height.
 *
 * @property x1 The x-coordinate of the top-left corner of the bounding box.
 * @property y1 The y-coordinate of the top-left corner of the bounding box.
 * @property x2 The x-coordinate of the bottom-right corner of the bounding box.
 * @property y2 The y-coordinate of the bottom-right corner of the bounding box.
 * @property cx The x-coordinate of the center of the bounding box.
 * @property cy The y-coordinate of the center of the bounding box.
 * @property w The width of the bounding box.
 * @property h The height of the bounding box.
 * @property cnf The confidence score of the detection (probability of the object being correctly detected).
 * @property cls The class index representing the type of object detected.
 * @property clsName The name of the class detected (e.g., "dog", "cat").
 */
data class BoundingBox(
    val x1: Float,   // X coordinate of the top-left corner of the bounding box
    val y1: Float,   // Y coordinate of the top-left corner of the bounding box
    val x2: Float,   // X coordinate of the bottom-right corner of the bounding box
    val y2: Float,   // Y coordinate of the bottom-right corner of the bounding box
    val cx: Float,   // X coordinate of the center of the bounding box
    val cy: Float,   // Y coordinate of the center of the bounding box
    val w: Float,    // Width of the bounding box
    val h: Float,    // Height of the bounding box
    val cnf: Float,  // Confidence score (probability of detection accuracy)
    val cls: Int,    // Class index representing the object type (e.g., 0 for "person", 1 for "cat")
    val clsName: String // Name of the class (e.g., "person", "dog", "car")
)
