package com.booky.app.library

object MediaKinds {
    private val audio = setOf(
        "mp3", "m4b", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "aiff",
    )
    private val video = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi")
    private val image = setOf("jpg", "jpeg", "png", "webp")
    private val coverNames = setOf("cover", "folder", "front", "artwork")

    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun stem(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    fun isMedia(name: String): Boolean {
        val ext = extension(name)
        return ext in audio || ext in video
    }

    fun isCoverFile(name: String): Boolean {
        val ext = extension(name)
        if (ext !in image) return false
        return stem(name).lowercase() in coverNames
    }

    fun isImage(name: String): Boolean = extension(name) in image

    fun isMp4Container(name: String): Boolean {
        val ext = extension(name)
        return ext == "m4b" || ext == "m4a" || ext == "mp4" || ext == "m4v" || ext == "mov"
    }
}
