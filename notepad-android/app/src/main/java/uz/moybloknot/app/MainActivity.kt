package uz.moybloknot.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*

data class Note(val id: Long, var title: String, var body: String, var updated: Long)

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("notes_storage", MODE_PRIVATE) }
    private val notes = mutableListOf<Note>()
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private lateinit var search: EditText
    private val purple = Color.rgb(88, 83, 205)
    private val ink = Color.rgb(35, 35, 42)
    private val muted = Color.rgb(120, 120, 130)
    private val page = Color.rgb(247, 246, 242)
    private var newestFirst = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        loadNotes()
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(label: String, action: () -> Unit, primary: Boolean = false): TextView =
        text(label, 15f, if (primary) Color.WHITE else ink, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded(if (primary) purple else Color.rgb(235, 233, 227), 13)
            setOnClickListener { action() }
        }

    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            setBackgroundColor(page)
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("Мой блокнот", 28f, ink, true))
        count = text("", 13f, muted)
        heading.addView(count)
        root.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })

        search = EditText(this).apply {
            hint = "Поиск по заметкам…"
            textSize = 15f
            singleLine = true
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(Color.WHITE, 14, Color.rgb(230, 228, 220))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(14) })
        val sortRow = LinearLayout(this).apply {
            gravity = Gravity.END
            addView(button("По дате ↓", {
                newestFirst = !newestFirst
                (getChildAt(0) as TextView).text = if (newestFirst) "По дате ↓" else "По дате ↑"
                renderNotes()
            }))
        }
        root.addView(sortRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val add = button("+  Новая заметка", { editNote(null) }, true).apply { textSize = 17f }
        root.addView(add, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderNotes()
            override fun afterTextChanged(s: Editable?) {}
        })
        renderNotes()
    }

    private fun renderNotes() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val q = search.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = notes.filter { it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }
            .sortedWith(if (newestFirst) compareByDescending { it.updated } else compareBy { it.updated })
        count.text = when (notes.size % 10) {
            1 -> if (notes.size % 100 == 11) "${notes.size} заметок" else "${notes.size} заметка"
            2, 3, 4 -> if (notes.size % 100 in 12..14) "${notes.size} заметок" else "${notes.size} заметки"
            else -> "${notes.size} заметок"
        }
        if (filtered.isEmpty()) {
            val empty = text(if (q.isEmpty()) "Пока нет заметок\nНажмите «Новая заметка», чтобы начать." else "Ничего не найдено", 16f, muted)
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(12), dp(48), dp(12), dp(48))
            list.addView(empty)
        }
        filtered.forEach { note ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = rounded(Color.WHITE, 17, Color.rgb(233, 231, 224))
                isClickable = true
                isFocusable = true
                setOnClickListener { editNote(note) }
            }
            card.addView(text(note.title.ifBlank { "Без названия" }, 18f, ink, true))
            val preview = note.body.ifBlank { "Пустая заметка" }.let { if (it.length > 180) it.take(180) + "…" else it }
            card.addView(text(preview, 14f, muted).apply { setPadding(0, dp(7), 0, dp(10)) })
            card.addView(text(java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale("ru")).format(java.util.Date(note.updated)), 12f, muted))
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun editNote(note: Note?) {
        val titleInput = EditText(this).apply {
            hint = "Название заметки"
            setSingleLine(true)
            textSize = 18f
            setText(note?.title.orEmpty())
        }
        val bodyInput = EditText(this).apply {
            hint = "Начните писать…"
            gravity = Gravity.TOP or Gravity.START
            minLines = 7
            maxLines = 14
            textSize = 16f
            setText(note?.body.orEmpty())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(titleInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            addView(bodyInput, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (note == null) "Новая заметка" else "Редактировать заметку")
            .setView(content)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .apply { if (note != null) setNeutralButton("Удалить", null) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                if (title.isEmpty() && body.isEmpty()) {
                    titleInput.error = "Введите название или текст"
                    return@setOnClickListener
                }
                val now = System.currentTimeMillis()
                if (note == null) notes.add(Note(now, title, body, now))
                else {
                    note.title = title; note.body = body; note.updated = now
                }
                persistNotes()
                dialog.dismiss()
                renderNotes()
            }
            if (note != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Удалить эту заметку?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить") { _, _ ->
                        notes.removeAll { it.id == note.id }
                        persistNotes()
                        dialog.dismiss()
                        renderNotes()
                    }.show()
            }
        }
        dialog.show()
    }

    private fun loadNotes() {
        val raw = prefs.getString("items", "[]") ?: "[]"
        try {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                notes.add(Note(o.optLong("id"), o.optString("title"), o.optString("body"), o.optLong("updated")))
            }
        } catch (_: Exception) { notes.clear() }
    }

    private fun persistNotes() {
        val array = org.json.JSONArray()
        notes.forEach { n ->
            array.put(org.json.JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("updated", n.updated))
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
