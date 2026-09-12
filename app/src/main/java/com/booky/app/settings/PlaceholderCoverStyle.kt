package com.booky.app.settings

data class PlaceholderCoverStyle(
    val shapeId: String = SHAPE_AUTO,
    val font: PlaceholderFont = PlaceholderFont.Serif,
    val weight: Float = 600f,
) {
    companion object {
        const val SHAPE_AUTO = "auto"
        val Default = PlaceholderCoverStyle()
    }
}

enum class PlaceholderFont(val label: String) {
    Serif("Serif"),
    Sans("Sans serif"),
}

data class PlaceholderShapeOption(
    val id: String,
    val label: String,
)
