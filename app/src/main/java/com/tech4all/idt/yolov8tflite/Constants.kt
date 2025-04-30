package com.tech4all.idt.yolov8tflite

/**
 * Object holding constant values related to the YOLOv8 TensorFlow Lite model.
 *
 * This object contains the paths for the TensorFlow Lite model file and its corresponding labels file.
 */
object Constants {

    /**
     * Path to the TensorFlow Lite model file.
     * This is the file used for running inference on the model.
     */
    const val MODEL_PATH = "model.tflite"

    /**
     * Path to the labels file that corresponds to the TensorFlow Lite model.
     * This file contains the class labels used for object detection in the model.
     */
    const val LABELS_PATH = "labels.txt"
}
