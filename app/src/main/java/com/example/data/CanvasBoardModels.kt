package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data structures representing elements on the infinite Dotted Canvas Board.
 */
data class CanvasPoint(
    val x: Float,
    val y: Float
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("x", x.toDouble())
            put("y", y.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CanvasPoint {
            return CanvasPoint(
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat()
            )
        }
    }
}

data class CanvasStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<CanvasPoint> = emptyList(),
    val colorHex: String = "#FFCC00",
    val strokeWidth: Float = 4f,
    val isEraser: Boolean = false
) {
    fun toJson(): JSONObject {
        val pointsArray = JSONArray()
        points.forEach { pointsArray.put(it.toJson()) }
        return JSONObject().apply {
            put("id", id)
            put("points", pointsArray)
            put("colorHex", colorHex)
            put("strokeWidth", strokeWidth.toDouble())
            put("isEraser", isEraser)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CanvasStroke {
            val pts = mutableListOf<CanvasPoint>()
            val arr = json.optJSONArray("points")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val ptObj = arr.optJSONObject(i)
                    if (ptObj != null) {
                        pts.add(CanvasPoint.fromJson(ptObj))
                    }
                }
            }
            return CanvasStroke(
                id = json.optString("id", UUID.randomUUID().toString()),
                points = pts,
                colorHex = json.optString("colorHex", "#FFCC00"),
                strokeWidth = json.optDouble("strokeWidth", 4.0).toFloat(),
                isEraser = json.optBoolean("isEraser", false)
            )
        }
    }
}

data class CanvasTextBlock(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val text: String,
    val colorHex: String = "#FFFFFF",
    val fontSize: Float = 16f,
    val width: Float = 260f,
    val isPrimaryNote: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("text", text)
            put("colorHex", colorHex)
            put("fontSize", fontSize.toDouble())
            put("width", width.toDouble())
            put("isPrimaryNote", isPrimaryNote)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CanvasTextBlock {
            return CanvasTextBlock(
                id = json.optString("id", UUID.randomUUID().toString()),
                x = json.optDouble("x", 100.0).toFloat(),
                y = json.optDouble("y", 100.0).toFloat(),
                text = json.optString("text", ""),
                colorHex = json.optString("colorHex", "#FFFFFF"),
                fontSize = json.optDouble("fontSize", 16.0).toFloat(),
                width = json.optDouble("width", 260.0).toFloat(),
                isPrimaryNote = json.optBoolean("isPrimaryNote", false)
            )
        }
    }
}

data class CanvasImageBlock(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val width: Float = 220f,
    val height: Float = 220f,
    val imagePathOrUri: String,
    val rotation: Float = 0f
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("width", width.toDouble())
            put("height", height.toDouble())
            put("imagePathOrUri", imagePathOrUri)
            put("rotation", rotation.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CanvasImageBlock {
            return CanvasImageBlock(
                id = json.optString("id", UUID.randomUUID().toString()),
                x = json.optDouble("x", 100.0).toFloat(),
                y = json.optDouble("y", 100.0).toFloat(),
                width = json.optDouble("width", 220.0).toFloat(),
                height = json.optDouble("height", 220.0).toFloat(),
                imagePathOrUri = json.optString("imagePathOrUri", ""),
                rotation = json.optDouble("rotation", 0.0).toFloat()
            )
        }
    }
}

/**
 * Full state of the interactive Canvas Board.
 */
data class CanvasBoardState(
    val strokes: List<CanvasStroke> = emptyList(),
    val textBlocks: List<CanvasTextBlock> = emptyList(),
    val images: List<CanvasImageBlock> = emptyList(),
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoomScale: Float = 1f
) {
    fun toJson(): String {
        val root = JSONObject()
        val strokesArr = JSONArray()
        strokes.forEach { strokesArr.put(it.toJson()) }
        root.put("strokes", strokesArr)

        val textArr = JSONArray()
        textBlocks.forEach { textArr.put(it.toJson()) }
        root.put("textBlocks", textArr)

        val imagesArr = JSONArray()
        images.forEach { imagesArr.put(it.toJson()) }
        root.put("images", imagesArr)

        root.put("panX", panX.toDouble())
        root.put("panY", panY.toDouble())
        root.put("zoomScale", zoomScale.toDouble())
        root.put("version", 1)

        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?, initialNoteContent: String? = null): CanvasBoardState {
            if (jsonStr.isNullOrBlank()) {
                val initialTexts = if (!initialNoteContent.isNullOrBlank()) {
                    listOf(
                        CanvasTextBlock(
                            x = 60f,
                            y = 80f,
                            text = initialNoteContent,
                            isPrimaryNote = true
                        )
                    )
                } else {
                    emptyList()
                }
                return CanvasBoardState(textBlocks = initialTexts)
            }

            return try {
                val root = JSONObject(jsonStr)
                val strokes = mutableListOf<CanvasStroke>()
                val strokesArr = root.optJSONArray("strokes")
                if (strokesArr != null) {
                    for (i in 0 until strokesArr.length()) {
                        val sObj = strokesArr.optJSONObject(i)
                        if (sObj != null) strokes.add(CanvasStroke.fromJson(sObj))
                    }
                }

                val textBlocks = mutableListOf<CanvasTextBlock>()
                val textArr = root.optJSONArray("textBlocks")
                if (textArr != null) {
                    for (i in 0 until textArr.length()) {
                        val tObj = textArr.optJSONObject(i)
                        if (tObj != null) textBlocks.add(CanvasTextBlock.fromJson(tObj))
                    }
                }

                val images = mutableListOf<CanvasImageBlock>()
                val imgArr = root.optJSONArray("images")
                if (imgArr != null) {
                    for (i in 0 until imgArr.length()) {
                        val iObj = imgArr.optJSONObject(i)
                        if (iObj != null) images.add(CanvasImageBlock.fromJson(iObj))
                    }
                }

                // If empty textBlocks and initialNoteContent provided, insert it
                if (textBlocks.isEmpty() && strokes.isEmpty() && images.isEmpty() && !initialNoteContent.isNullOrBlank()) {
                    textBlocks.add(
                        CanvasTextBlock(
                            x = 60f,
                            y = 80f,
                            text = initialNoteContent,
                            isPrimaryNote = true
                        )
                    )
                }

                CanvasBoardState(
                    strokes = strokes,
                    textBlocks = textBlocks,
                    images = images,
                    panX = root.optDouble("panX", 0.0).toFloat(),
                    panY = root.optDouble("panY", 0.0).toFloat(),
                    zoomScale = root.optDouble("zoomScale", 1.0).toFloat().coerceIn(0.25f, 4.0f)
                )
            } catch (e: Exception) {
                CanvasBoardState()
            }
        }
    }
}
