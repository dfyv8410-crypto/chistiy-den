package com.chistiyen.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chistiyen.app.data.db.AppDatabase
import com.chistiyen.app.data.db.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File

class ReaderActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var bookId: Long = -1
    private var currentChapterIndex = 0
    private var currentFontSize = 16
    private var currentReaderTheme = "light"
    private var chapters: List<ChapterData> = emptyList()
    private var currentBookTitle = ""

    data class ChapterData(val id: String, val title: String, val content: List<String>)

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        db = AppDatabase.getInstance(this)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)

        setupUI()
        lifecycleScope.launch { loadBook() }
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.readerBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.readerBmBtn).setOnClickListener { togglePanel("bm") }
        findViewById<ImageButton>(R.id.readerNoteBtn).setOnClickListener { togglePanel("note") }
        findViewById<ImageButton>(R.id.readerSettingsBtn).setOnClickListener { togglePanel("settings") }
        findViewById<Button>(R.id.prevChapter).setOnClickListener {
            if (currentChapterIndex > 0) { currentChapterIndex--; showChapter() }
        }
        findViewById<Button>(R.id.nextChapter).setOnClickListener {
            if (currentChapterIndex < chapters.size - 1) { currentChapterIndex++; showChapter() }
        }
        findViewById<Button>(R.id.noteAddBtn).setOnClickListener { addNote() }
        findViewById<Button>(R.id.readerAddBmBtn).setOnClickListener { toggleBookmark() }
        findViewById<RadioGroup>(R.id.fontSizeGroup).setOnCheckedChangeListener { _, id ->
            currentFontSize = when (id) { R.id.fontSmall -> 14; R.id.fontLarge -> 20; else -> 16 }
            findViewById<TextView>(R.id.readerText).textSize = currentFontSize.toFloat()
            lifecycleScope.launch { saveSettings() }
        }
        findViewById<RadioGroup>(R.id.readerThemeGroup).setOnCheckedChangeListener { _, id ->
            currentReaderTheme = when (id) { R.id.readerThemeSepia -> "sepia"; R.id.readerThemeDark -> "dark"; else -> "light" }
            applyTheme()
            lifecycleScope.launch { saveSettings() }
        }
    }

    private suspend fun loadBook() {
        val entry = if (bookId == -1L) null else db.bookEntryDao().getById(bookId)
        currentBookTitle = entry?.title ?: "Базовый текст"

        chapters = if (entry != null) loadBookChapters(entry) else loadBuiltInBook()

        val settings = db.bookSettingsDao().getByBookId(bookId)
        if (settings != null) {
            currentChapterIndex = settings.currentChapterIndex.coerceIn(0, chapters.size - 1)
            currentFontSize = settings.fontSize
            currentReaderTheme = settings.theme
        }
        withContext(Dispatchers.Main) {
            findViewById<TextView>(R.id.readerTitle).text = currentBookTitle
            applyTheme()
            when (currentFontSize) { 14 -> findViewById<RadioButton>(R.id.fontSmall).isChecked = true; 20 -> findViewById<RadioButton>(R.id.fontLarge).isChecked = true; else -> findViewById<RadioButton>(R.id.fontMedium).isChecked = true }
            when (currentReaderTheme) { "sepia" -> findViewById<RadioButton>(R.id.readerThemeSepia).isChecked = true; "dark" -> findViewById<RadioButton>(R.id.readerThemeDark).isChecked = true; else -> findViewById<RadioButton>(R.id.readerThemeLight).isChecked = true }
            showChapter()
            renderBookmarks()
            renderNotes()
        }
    }

    private fun applyTheme() {
        val readerView = findViewById<View>(R.id.readerContent)
        val textView = findViewById<TextView>(R.id.readerText)
        val bg = findViewById<LinearLayout>(R.id.readerRoot)
        when (currentReaderTheme) {
            "sepia" -> { readerView.setBackgroundColor(getColor(R.color.sepia_bg)); textView.setTextColor(getColor(R.color.sepia_text)); bg.setBackgroundColor(getColor(R.color.sepia_bg)) }
            "dark" -> { readerView.setBackgroundColor(getColor(R.color.dark_bg)); textView.setTextColor(getColor(R.color.dark_text)); bg.setBackgroundColor(getColor(R.color.dark_bg)) }
            else -> { readerView.setBackgroundColor(getColor(R.color.white)); textView.setTextColor(getColor(R.color.cream)); bg.setBackgroundColor(getColor(R.color.white)) }
        }
    }

    private fun showChapter() {
        if (chapters.isEmpty()) return
        val ch = chapters[currentChapterIndex.coerceIn(0, chapters.size - 1)]
        val html = buildString { append("<h2>${ch.title}</h2>"); ch.content.forEach { append("<p>$it</p>") } }
        findViewById<TextView>(R.id.readerText).apply {
            textSize = currentFontSize.toFloat()
            text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT) else android.text.Html.fromHtml(html)
        }
        findViewById<TextView>(R.id.pageInfo).text = "${currentChapterIndex + 1} / ${chapters.size}"
    }

    private fun togglePanel(type: String) {
        listOf(R.id.readerBmPanel, R.id.readerNotePanel, R.id.readerSettingsPanel).forEach { findViewById<View>(it).visibility = View.GONE }
        val id = when (type) { "bm" -> R.id.readerBmPanel; "note" -> R.id.readerNotePanel; else -> R.id.readerSettingsPanel }
        findViewById<View>(id).visibility = View.VISIBLE
        if (type == "bm") renderBookmarks()
        if (type == "note") renderNotes()
    }

    private fun toggleBookmark() {
        lifecycleScope.launch {
            val existing = db.bookmarkDao().find(bookId, chapters.getOrNull(currentChapterIndex)?.id ?: "")
            if (existing != null) { db.bookmarkDao().deleteById(existing.id); Toast.makeText(this@ReaderActivity, "Закладка удалена", Toast.LENGTH_SHORT).show() }
            else { db.bookmarkDao().insert(Bookmark(bookId = bookId, chapterId = chapters.getOrNull(currentChapterIndex)?.id ?: "", chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "")); Toast.makeText(this@ReaderActivity, "Закладка добавлена", Toast.LENGTH_SHORT).show() }
            renderBookmarks()
        }
    }

    private fun renderBookmarks() {
        lifecycleScope.launch {
            val bms = db.bookmarkDao().getByBookId(bookId)
            val list = findViewById<LinearLayout>(R.id.bmList)
            list.removeAllViews()
            if (bms.isEmpty()) { list.addView(TextView(this@ReaderActivity).apply { text = "Нет закладок"; textSize = 12f; setTextColor(getColor(R.color.muted)) }); return@launch }
            bms.forEach { bm ->
                val row = LinearLayout(this@ReaderActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 4) }
                row.addView(TextView(this@ReaderActivity).apply { text = bm.chapterTitle.ifEmpty { bm.chapterId }; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 13f; setOnClickListener { currentChapterIndex = chapters.indexOfFirst { it.id == bm.chapterId }.coerceAtLeast(0); showChapter(); listOf(R.id.readerBmPanel, R.id.readerNotePanel, R.id.readerSettingsPanel).forEach { findViewById<View>(it).visibility = View.GONE } } })
                row.addView(ImageButton(this@ReaderActivity).apply { setImageResource(android.R.drawable.ic_menu_delete); setBackgroundColor(0); setOnClickListener { lifecycleScope.launch { db.bookmarkDao().deleteById(bm.id); renderBookmarks() } }; layoutParams = LinearLayout.LayoutParams(-2, -2) })
                list.addView(row)
            }
        }
    }

    private fun renderNotes() {
        lifecycleScope.launch {
            val notes = db.noteDao().getByBookId(bookId)
            val list = findViewById<LinearLayout>(R.id.noteList)
            list.removeAllViews()
            if (notes.isEmpty()) { list.addView(TextView(this@ReaderActivity).apply { text = "Нет заметок"; textSize = 12f; setTextColor(getColor(R.color.muted)) }); return@launch }
            notes.forEach { note ->
                val row = LinearLayout(this@ReaderActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 4) }
                row.addView(TextView(this@ReaderActivity).apply { text = "[${note.chapterId}] ${note.text}"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 12f; setOnClickListener { editNoteDialog(note) } })
                row.addView(ImageButton(this@ReaderActivity).apply { setImageResource(android.R.drawable.ic_menu_delete); setBackgroundColor(0); setOnClickListener { lifecycleScope.launch { db.noteDao().deleteById(note.id); renderNotes() } }; layoutParams = LinearLayout.LayoutParams(-2, -2) })
                list.addView(row)
            }
        }
    }

    private fun editNoteDialog(note: Note) {
        val et = EditText(this).apply { setText(note.text); setPadding(16, 16, 16, 16) }
        android.app.AlertDialog.Builder(this).setTitle("Редактировать заметку").setView(et)
            .setPositiveButton("Сохранить") { _, _ ->
                lifecycleScope.launch { db.noteDao().update(note.copy(text = et.text.toString().trim())); renderNotes() }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun addNote() {
        val text = findViewById<EditText>(R.id.noteInput).text.toString().trim()
        if (text.isEmpty() || chapters.isEmpty()) return
        lifecycleScope.launch {
            db.noteDao().insert(Note(bookId = bookId, chapterId = chapters[currentChapterIndex].id, text = text))
            findViewById<EditText>(R.id.noteInput).text.clear()
            renderNotes()
        }
    }

    private suspend fun saveSettings() {
        if (bookId < 0) return
        db.bookSettingsDao().upsert(BookSettings(bookId = bookId, fontSize = currentFontSize, theme = currentReaderTheme, currentChapterIndex = currentChapterIndex, scrollPos = findViewById<NestedScrollView>(R.id.readerContent).scrollY))
    }

    private suspend fun loadBookChapters(entry: BookEntry): List<ChapterData> {
        val file = File(entry.filePath)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            when { entry.format == "fb2" -> parseFB2(text); entry.format == "txt" -> parseTxt(text); else -> emptyList() }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun loadBuiltInBook(): List<ChapterData> {
        return try {
            val text = applicationContext.assets.open("basic_text.fb2").bufferedReader().use { it.readText() }
            parseFB2(text)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseFB2(text: String): List<ChapterData> {
        return try {
            val doc = Jsoup.parse(text, "", Parser.xmlParser())
            val body = doc.select("body").first() ?: return emptyList()
            body.select("section").mapNotNull { sec ->
                val title = sec.select("title").first()?.text()?.trim() ?: return@mapNotNull null
                val content = sec.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
                if (content.isEmpty()) null else ChapterData("fb2_${sec.siblingIndex}", title, content)
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseTxt(text: String): List<ChapterData> {
        val lines = text.lines().filter { it.trim().isNotEmpty() }
        return lines.chunked(30).mapIndexed { i, chunk -> ChapterData("p_$i", "Страница ${i + 1}", chunk.map { it.trim() }) }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch { saveSettings() }
    }
}
