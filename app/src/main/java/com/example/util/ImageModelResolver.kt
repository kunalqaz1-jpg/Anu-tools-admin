package com.example.util

object ImageModelResolver {
    fun resolveImageModel(model: Any?): Any? {
        if (model == null) return null
        if (model is String) {
            val trimmed = model.trim()
            if (trimmed.isEmpty()) return null
            return trimmed
        }
        return model
    }
}
