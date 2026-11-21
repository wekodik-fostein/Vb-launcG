package com.vb.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

enum class CtrlKind { STICK, BUTTON }

data class CtrlItem(
    val id: String,
    val label: String,
    val kind: CtrlKind,
    val x: Float,       // центр, доля ширины экрана 0..1
    val y: Float,       // центр, доля высоты экрана 0..1
    val sizeDp: Float
)

/** Готовые кнопки для добавления в редакторе. */
val BUTTON_LIBRARY = listOf(
    "Бег" to "SPRINT", "Прыжок" to "JUMP", "Действие" to "USE", "Атака" to "ATTACK",
    "Прицел" to "AIM", "Укрытие" to "COVER", "Присесть" to "CROUCH", "Оружие" to "WEAPON",
    "Машина" to "VEHICLE", "Карта" to "MAP", "Пауза" to "PAUSE", "Телефон" to "PHONE"
)

class ControlsState(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vb_controls", Context.MODE_PRIVATE)
    val items = mutableStateListOf<CtrlItem>()

    /** id последней добавленной кнопки — редактор выделяет её сразу. */
    var selectedHint: String? = null

    init {
        val raw = prefs.getString("layout", null)
        val loaded = raw?.let { runCatching { parse(it) }.getOrNull() }
        items.addAll(if (loaded.isNullOrEmpty()) defaults() else loaded)
    }

    fun move(id: String, dx: Float, dy: Float) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = items[i]
        items[i] = c.copy(
            x = (c.x + dx).coerceIn(0.04f, 0.96f),
            y = (c.y + dy).coerceIn(0.20f, 0.94f)
        )
    }

    fun resize(id: String, sizeDp: Float) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(sizeDp = sizeDp.coerceIn(36f, 200f))
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
    }

    fun add(label: String, code: String) {
        var id = code
        var n = 2
        while (items.any { it.id == id }) id = "${code}_${n++}"
        items.add(CtrlItem(id, label, CtrlKind.BUTTON, 0.5f, 0.5f, 64f))
        selectedHint = id
    }

    fun reset() {
        items.clear()
        items.addAll(defaults())
    }

    fun save() {
        val arr = JSONArray()
        items.toList().forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("label", it.label).put("kind", it.kind.name)
                    .put("x", it.x.toDouble()).put("y", it.y.toDouble())
                    .put("size", it.sizeDp.toDouble())
            )
        }
        prefs.edit().putString("layout", arr.toString()).apply()
    }

    private fun parse(raw: String): List<CtrlItem> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            CtrlItem(
                o.getString("id"), o.getString("label"),
                CtrlKind.valueOf(o.getString("kind")),
                o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                o.getDouble("size").toFloat()
            )
        }
    }

    private fun defaults() = listOf(
        CtrlItem("MOVE", "Ходьба", CtrlKind.STICK, 0.13f, 0.70f, 140f),
        CtrlItem("LOOK", "Камера", CtrlKind.STICK, 0.62f, 0.72f, 120f),
        CtrlItem("ATTACK", "Атака", CtrlKind.BUTTON, 0.90f, 0.70f, 74f),
        CtrlItem("JUMP", "Прыжок", CtrlKind.BUTTON, 0.82f, 0.50f, 64f),
        CtrlItem("SPRINT", "Бег", CtrlKind.BUTTON, 0.78f, 0.84f, 64f),
        CtrlItem("USE", "Действие", CtrlKind.BUTTON, 0.92f, 0.46f, 60f),
        CtrlItem("AIM", "Прицел", CtrlKind.BUTTON, 0.70f, 0.58f, 56f),
        CtrlItem("VEHICLE", "Машина", CtrlKind.BUTTON, 0.92f, 0.28f, 56f),
        CtrlItem("PAUSE", "Пауза", CtrlKind.BUTTON, 0.50f, 0.22f, 48f)
    )
}
