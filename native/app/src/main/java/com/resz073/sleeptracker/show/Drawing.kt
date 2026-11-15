package com.resz073.sleeptracker.show

import com.resz073.sleeptracker.engine.Js

/** One shape of a chart, with the attributes version 1 gives its SVG element */
class Shape(val tag: String, val attrs: Map<String, String>, val text: String? = null, val title: String? = null) {
    operator fun get(k: String): String? = attrs[k]
    fun num(k: String, default: Float = 0f): Float = attrs[k]?.toFloatOrNull() ?: default
}

/** A chart: shapes in its own coordinates ([viewBox]), clip paths by id, and a label for screen readers */
class Drawing(val viewBox: String, val label: String?, val clips: Map<String, String>, val shapes: List<Shape>) {
    private val box = viewBox.split(' ').map { it.toFloat() }
    val width: Float get() = box[2]
    val height: Float get() = box[3]
}

class DrawingBuilder {
    private val shapes = ArrayList<Shape>()
    private val clips = LinkedHashMap<String, String>()

    private fun attrs(a: Array<out Pair<String, Any?>>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for ((k, v) in a) when (v) {
            null -> {}
            is Double -> m[k] = Js.num(v)
            is Float -> m[k] = Js.num(v.toDouble())
            is Int -> m[k] = v.toString()
            is Long -> m[k] = v.toString()
            else -> m[k] = v.toString()
        }
        return m
    }

    fun rect(vararg a: Pair<String, Any?>, title: String? = null) { shapes += Shape("rect", attrs(a), null, title) }
    fun line(vararg a: Pair<String, Any?>) { shapes += Shape("line", attrs(a)) }
    fun path(vararg a: Pair<String, Any?>) { shapes += Shape("path", attrs(a)) }
    fun circle(vararg a: Pair<String, Any?>) { shapes += Shape("circle", attrs(a)) }
    fun text(text: String, vararg a: Pair<String, Any?>) { shapes += Shape("text", attrs(a), text) }
    fun clip(id: String, d: String) { clips[id] = d }
    fun build(viewBox: String, label: String?) = Drawing(viewBox, label, clips.toMap(), shapes.toList())
}
