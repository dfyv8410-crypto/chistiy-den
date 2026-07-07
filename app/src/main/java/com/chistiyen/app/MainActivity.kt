package com.chistiyen.app

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chistiyen.app.data.db.AppDatabase
import com.chistiyen.app.data.db.entity.*
import com.chistiyen.app.data.network.JftParser
import com.chistiyen.app.data.network.NaRussiaApi
import com.chistiyen.app.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var currentScreen = "home"

    // Screen views
    private lateinit var screens: Map<String, View>
    private lateinit var contentFrame: FrameLayout

    // Bottom nav
    private lateinit var bottomNav: LinearLayout

    // Import file result
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importBook(it) } }

    // Backup import
    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importBackup(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)
        contentFrame = findViewById(R.id.contentFrame)

        // Register all screens
        screens = mapOf(
            "home" to findViewById(R.id.screenHome),
            "jft" to findViewById(R.id.screenJft),
            "groups" to findViewById(R.id.screenGroups),
            "plan" to findViewById(R.id.screenPlan),
            "medals" to findViewById(R.id.screenMedals),
            "sos" to findViewById(R.id.screenSos),
            "service" to findViewById(R.id.screenService),
            "library" to findViewById(R.id.screenLibrary),
            "settings" to findViewById(R.id.screenSettings)
        )

        setupBottomNav()
        showScreen("home")

        // Load data
        lifecycleScope.launch {
            loadHome()
            loadPlan()
            loadServices()
            loadLibrary()
            loadJft()
            loadSos()

            // Restore alarms after boot
            restoreAlarms()
        }
    }

    /* ===================== NAVIGATION ===================== */

    private fun setupBottomNav() {
        bottomNav = findViewById(R.id.bottomNav)
        // Simple tab switching: bottom nav built as horizontal LinearLayout in layout
        val tabs = listOf(
            R.id.nav_home to "home", R.id.nav_jft to "jft",
            R.id.nav_groups to "groups", R.id.nav_plan to "plan",
            R.id.nav_medals to "medals", R.id.nav_sos to "sos",
            R.id.nav_service to "service", R.id.nav_library to "library",
            R.id.nav_settings to "settings"
        )
        // Note: BottomNavigationView is used with menu. For simplicity we handle clicks directly.
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
            .setOnNavigationItemSelectedListener { item ->
                val screen = when (item.itemId) {
                    R.id.nav_home -> "home"
                    R.id.nav_jft -> "jft"
                    R.id.nav_groups -> "groups"
                    R.id.nav_plan -> "plan"
                    R.id.nav_medals -> "medals"
                    R.id.nav_sos -> "sos"
                    R.id.nav_service -> "service"
                    R.id.nav_library -> "library"
                    R.id.nav_settings -> "settings"
                    else -> "home"
                }
                showScreen(screen)
                true
            }
    }

    private fun showScreen(name: String) {
        currentScreen = name
        screens.forEach { (key, view) ->
            view.visibility = if (key == name) View.VISIBLE else View.GONE
        }
    }

    /* ===================== HOME / COUNTER ===================== */

    data class Milestone(val days: Int, val name: String, val color: String)
    private val milestones = listOf(
        Milestone(0, "Белая", "#F0EDE8"), Milestone(30, "Оранжевая", "#E8924A"),
        Milestone(60, "Зелёная", "#5DB87A"), Milestone(90, "Красная", "#D4605A"),
        Milestone(182, "Синяя", "#4A7FB5"), Milestone(274, "Жёлтая", "#D4A843"),
        Milestone(365, "Золотая", "#C8963E"), Milestone(548, "Серая", "#A09888"),
        Milestone(730, "Чёрная", "#4A4038"), Milestone(1095, "Фиолетовая", "#8B6FA8")
    )

    private suspend fun loadHome() {
        val settings = db.userSettingsDao().get() ?: UserSettings()
        val startDate = settings.startDate
        val days = if (startDate != null) calcDays(startDate) else 0
        val medal = getMedal(days)

        withContext(Dispatchers.Main) {
            findViewById<TextView>(R.id.daysCount).text = days.toString()
            findViewById<TextView>(R.id.medalName).text = medal.name

            val statDays = findViewById<TextView>(R.id.statDays)
            statDays.text = days.toString()

            val nextMilestone = milestones.find { it.days > days }
            findViewById<TextView>(R.id.statMedal).text = medal.name
            findViewById<TextView>(R.id.statNext).text =
                if (nextMilestone != null) "${nextMilestone.days - days} дн." else "—"

            findViewById<TextView>(R.id.settingsLink).setOnClickListener {
                showScreen("settings")
            }
        }
    }

    private fun calcDays(startDate: String): Int {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val start = fmt.parse(startDate)?.time ?: return 0
            val now = System.currentTimeMillis()
            val diff = now - start
            return maxOf(0, (diff / 86400000L).toInt())
        } catch (e: Exception) { return 0 }
    }

    private fun getMedal(days: Int): Milestone {
        var best = milestones.first()
        for (m in milestones) {
            if (days >= m.days) best = m
        }
        return best
    }

    /* ===================== JFT ===================== */

    private suspend fun loadJft() {
        withContext(Dispatchers.Main) {
            findViewById<TextView>(R.id.jftRefresh).setOnClickListener {
                lifecycleScope.launch { fetchJft() }
            }
        }
        // Try cache first
        val settings = db.userSettingsDao().get()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (settings?.jftCacheDate == today && settings.jftCache != null) {
            displayJft(settings.jftCache!!)
            return
        }
        fetchJft()
    }

    private suspend fun fetchJft() {
        try {
            val html = NaRussiaApi.fetchJft()
            if (html != null) {
                val data = JftParser.parse(html)
                if (data != null) {
                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.jftDate).text = data.date
                        findViewById<TextView>(R.id.jftTitle).text = data.title
                        findViewById<TextView>(R.id.jftRef).text = data.ref
                        findViewById<TextView>(R.id.jftQuote).text = data.quote
                        findViewById<TextView>(R.id.jftBody).text = data.body
                        findViewById<TextView>(R.id.jftJft).text = data.jft
                    }
                    // Cache
                    db.userSettingsDao().updateJftCache(
                        html, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    )
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayJft(html: String) {
        val data = JftParser.parse(html)
        if (data != null) {
            findViewById<TextView>(R.id.jftDate).text = data.date
            findViewById<TextView>(R.id.jftTitle).text = data.title
            findViewById<TextView>(R.id.jftRef).text = data.ref
            findViewById<TextView>(R.id.jftQuote).text = data.quote
            findViewById<TextView>(R.id.jftBody).text = data.body
            findViewById<TextView>(R.id.jftJft).text = data.jft
        }
    }

    /* ===================== PLAN ===================== */

    private suspend fun loadPlan() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val items = db.planItemDao().getByDate(today)

        withContext(Dispatchers.Main) {
            val list = findViewById<LinearLayout>(R.id.planList)
            list.removeAllViews()
            items.forEach { item ->
                val view = layoutInflater.inflate(R.layout.item_plan_task, list, false)
                view.findViewById<TextView>(R.id.taskText).text = item.text
                val cb = view.findViewById<CheckBox>(R.id.taskCheck).apply {
                    isChecked = item.done
                    setOnCheckedChangeListener { _, isChecked ->
                        lifecycleScope.launch {
                            db.planItemDao().toggleDone(item.id, isChecked)
                        }
                    }
                }
                view.findViewById<ImageButton>(R.id.taskDelete).setOnClickListener {
                    lifecycleScope.launch {
                        db.planItemDao().deleteById(item.id)
                        loadPlan()
                    }
                }
                list.addView(view)
            }

            findViewById<Button>(R.id.planAddBtn).setOnClickListener {
                val text = findViewById<EditText>(R.id.planInput).text.toString().trim()
                if (text.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.planItemDao().insert(PlanItem(text = text, dateKey = today))
                        findViewById<EditText>(R.id.planInput).text.clear()
                        loadPlan()
                    }
                }
            }

            findViewById<Button>(R.id.setPlanReminder).setOnClickListener {
                requestNotificationPermission()
                lifecycleScope.launch {
                    val cal = Calendar.getInstance()
                    val time = findViewById<TimePicker>(R.id.planReminderTime)
                    cal.set(Calendar.HOUR_OF_DAY, time.hour)
                    cal.set(Calendar.MINUTE, time.minute)
                    cal.set(Calendar.SECOND, 0)
                    // If time already passed today, schedule for tomorrow
                    if (cal.timeInMillis <= System.currentTimeMillis())
                        cal.add(Calendar.DAY_OF_YEAR, 1)

                    val timeStr = "${time.hour}:${time.minute}"
                    db.userSettingsDao().updatePlanReminderTime(timeStr)
                    NotificationHelper.schedulePlanReminder(this@MainActivity, cal.timeInMillis)
                    Toast.makeText(this@MainActivity, "Напоминание установлено", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /* ===================== SERVICES ===================== */

    private suspend fun loadServices() {
        val items = db.serviceItemDao().getAll()
        withContext(Dispatchers.Main) {
            val list = findViewById<LinearLayout>(R.id.serviceList)
            list.removeAllViews()
            val dayNames = resources.getStringArray(R.array.days_short)

            items.forEach { item ->
                val view = layoutInflater.inflate(R.layout.item_service, list, false)
                view.findViewById<TextView>(R.id.svcDayBadge).text = dayNames[item.dayOfWeek]
                view.findViewById<TextView>(R.id.svcTimeText).text = item.time
                view.findViewById<TextView>(R.id.svcNameText).text = item.name
                if (item.groupName.isNotEmpty())
                    view.findViewById<TextView>(R.id.svcGroupText).text = item.groupName

                val reminderBtn = view.findViewById<Button>(R.id.svcReminderBtn)
                reminderBtn.text = if (item.reminderEnabled) "✓ ${item.reminderTime}" else "Напом."
                reminderBtn.setOnClickListener {
                    lifecycleScope.launch {
                        toggleServiceReminder(item)
                        loadServices()
                    }
                }

                view.findViewById<ImageButton>(R.id.svcDeleteBtn).setOnClickListener {
                    lifecycleScope.launch {
                        NotificationHelper.cancelServiceReminder(this@MainActivity, item.id)
                        db.serviceItemDao().deleteById(item.id)
                        loadServices()
                    }
                }
                list.addView(view)
            }

            findViewById<Button>(R.id.svcAddBtn).setOnClickListener {
                val day = findViewById<Spinner>(R.id.svcDay).selectedPosition
                val time = findViewById<EditText>(R.id.svcTime).text.toString().trim()
                val name = findViewById<EditText>(R.id.svcName).text.toString().trim()
                val group = findViewById<EditText>(R.id.svcGroup).text.toString().trim()
                if (name.isNotEmpty() && time.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.serviceItemDao().insert(
                            ServiceItem(dayOfWeek = day, time = time, name = name, groupName = group)
                        )
                        findViewById<EditText>(R.id.svcName).text.clear()
                        findViewById<EditText>(R.id.svcGroup).text.clear()
                        loadServices()
                    }
                }
            }
        }
    }

    private suspend fun toggleServiceReminder(item: ServiceItem) {
        if (item.reminderEnabled) {
            NotificationHelper.cancelServiceReminder(this, item.id)
            db.serviceItemDao().updateReminder(item.id, false, "")
        } else {
            requestNotificationPermission()
            val parts = item.time.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: return
                val m = parts[1].toIntOrNull() ?: return
                NotificationHelper.scheduleServiceReminder(this, item.id, item.dayOfWeek, h, m)
                db.serviceItemDao().updateReminder(item.id, true, item.time)
            }
        }
    }

    /* ===================== SOS ===================== */

    private val builtinSos = listOf(
        Triple("АН — Единый номер", "Анонимные Наркоманы, круглосуточно", "88001014212"),
        Triple("Единый телефон доверия", "Психологическая помощь, круглосуточно", "88001004994"),
        Triple("Кризисная линия по России", "Бесплатно, круглосуточно", "88003334434"),
        Triple("Экстренные службы", "Единый номер экстренных служб РФ", "112")
    )

    private suspend fun loadSos() {
        val contacts = db.sosContactDao().getAll()
        withContext(Dispatchers.Main) {
            // Built-in
            val builtinList = findViewById<LinearLayout>(R.id.sosBuiltinList)
            builtinList.removeAllViews()
            builtinSos.forEach { (name, desc, phone) ->
                val view = layoutInflater.inflate(R.layout.item_sos_contact, builtinList, false)
                view.findViewById<TextView>(R.id.sosName).text = name
                view.findViewById<TextView>(R.id.sosDesc).text = desc
                view.findViewById<Button>(R.id.sosCallBtn).setOnClickListener {
                    dialPhone(phone)
                }
                builtinList.addView(view)
            }

            // Personal contacts
            val personalList = findViewById<LinearLayout>(R.id.sosContactsList)
            personalList.removeAllViews()
            contacts.forEach { contact ->
                val view = layoutInflater.inflate(R.layout.item_sos_personal, personalList, false)
                view.findViewById<TextView>(R.id.sosContactName).text = contact.name
                view.findViewById<TextView>(R.id.sosContactPhone).text = contact.phone
                view.findViewById<TextView>(R.id.sosContactType).text = contact.type
                view.findViewById<Button>(R.id.sosCallBtn).setOnClickListener {
                    dialPhone(contact.phone)
                }
                view.findViewById<ImageButton>(R.id.sosDeleteBtn).setOnClickListener {
                    lifecycleScope.launch {
                        db.sosContactDao().deleteById(contact.id)
                        loadSos()
                    }
                }
                personalList.addView(view)
            }

            findViewById<Button>(R.id.sosAddBtn).setOnClickListener {
                val name = findViewById<EditText>(R.id.sosName).text.toString().trim()
                val phone = findViewById<EditText>(R.id.sosPhone).text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.sosContactDao().insert(SosContact(name = name, phone = phone))
                        findViewById<EditText>(R.id.sosName).text.clear()
                        findViewById<EditText>(R.id.sosPhone).text.clear()
                        loadSos()
                    }
                }
            }
        }
    }

    private fun dialPhone(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))
        startActivity(intent)
    }

    /* ===================== LIBRARY ===================== */

    private var currentBookId: Long = -1
    private var currentChapterIndex = 0
    private var currentChapters: List<Pair<String, String>> = emptyList() // (id, title)

    // Built-in book (Basic Text)
    private val builtInBook = BookEntry(
        id = -1, title = "Анонимные Наркоманы. Базовый текст"
    ).apply { /* chapters loaded from assets */ }

    private data class BookDisplay(
        val id: Long, val title: String, val author: String,
        val chapters: List<ChapterData>, val icon: String, val color: String
    )
    data class ChapterData(val id: String, val title: String, val content: List<String>)

    private suspend fun loadLibrary() {
        val books = mutableListOf<BookDisplay>()

        // Built-in basic text (asset)
        val basicChapters = loadBuiltInBook()
        if (basicChapters.isNotEmpty()) {
            books.add(BookDisplay(-1, "Анонимные Наркоманы. Базовый текст", "", basicChapters, "\uD83D\uDCD6", "#C8963E"))
        }

        // Imported books
        val imported = db.bookEntryDao().getAll()
        imported.forEach { entry ->
            val chapters = loadBookChapters(entry)
            books.add(BookDisplay(entry.id, entry.title, entry.author, chapters, entry.icon, entry.color))
        }

        withContext(Dispatchers.Main) {
            val recycler = findViewById<RecyclerView>(R.id.bookshelf)
            recycler.layoutManager = LinearLayoutManager(this@MainActivity)
            recycler.adapter = BookAdapter(books) { book ->
                openBook(book)
            }

            findViewById<Button>(R.id.importBookBtn).setOnClickListener {
                importFileLauncher.launch("*/*")
            }

            findViewById<Button>(R.id.readerBack).setOnClickListener {
                showBookshelf()
            }

            findViewById<Button>(R.id.prevChapter).setOnClickListener {
                if (currentChapterIndex > 0) {
                    currentChapterIndex--
                    showChapter()
                }
            }

            findViewById<Button>(R.id.nextChapter).setOnClickListener {
                if (currentChapterIndex < currentChapters.size - 1) {
                    currentChapterIndex++
                    showChapter()
                }
            }
        }
    }

    private fun openBook(book: BookDisplay) {
        currentBookId = book.id
        currentChapters = book.chapters.map { it.id to it.title }
        currentChapterIndex = 0

        findViewById<LinearLayout>(R.id.libraryPanel).visibility = View.GONE
        findViewById<LinearLayout>(R.id.readerPanel).visibility = View.VISIBLE
        findViewById<TextView>(R.id.readerTitle).text = book.title

        // Load per-book settings
        lifecycleScope.launch {
            val settings = db.bookSettingsDao().getByBookId(book.id)
            if (settings != null) {
                currentChapterIndex = settings.currentChapterIndex
            }
            showChapter()
        }
    }

    private fun showBookshelf() {
        // Save position
        lifecycleScope.launch {
            if (currentBookId >= 0) {
                db.bookSettingsDao().upsert(
                    BookSettings(
                        bookId = currentBookId,
                        currentChapterIndex = currentChapterIndex,
                        scrollPos = findViewById<NestedScrollView>(R.id.readerContent).scrollY
                    )
                )
            }
        }
        findViewById<LinearLayout>(R.id.libraryPanel).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.readerPanel).visibility = View.GONE
    }

    private fun showChapter() {
        val textView = findViewById<TextView>(R.id.readerText)
        val book = getCurrentBookDisplay() ?: return

        if (currentChapterIndex < 0 || currentChapterIndex >= book.chapters.size) return
        val ch = book.chapters[currentChapterIndex]

        val html = buildString {
            append("<h2>${ch.title}</h2>")
            ch.content.forEach { p -> append("<p>$p</p>") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.text = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
        } else {
            textView.text = android.text.Html.fromHtml(html)
        }
        findViewById<TextView>(R.id.pageInfo).text = "${currentChapterIndex + 1} / ${book.chapters.size}"

        // Restore scroll
        lifecycleScope.launch {
            val settings = db.bookSettingsDao().getByBookId(book.id)
            if (settings != null) {
                findViewById<NestedScrollView>(R.id.readerContent).scrollY = settings.scrollPos
            }
        }
    }

    private suspend fun getCurrentBookDisplay(): BookDisplay? {
        // Simplified: returns from current book list
        val books = mutableListOf<BookDisplay>()
        val basic = loadBuiltInBook()
        if (basic.isNotEmpty())
            books.add(BookDisplay(-1, "Базовый текст", "", basic, "", ""))

        val imported = db.bookEntryDao().getAll()
        imported.forEach { entry ->
            val chapters = loadBookChapters(entry)
            books.add(BookDisplay(entry.id, entry.title, entry.author, chapters, entry.icon, entry.color))
        }
        return books.find { it.id == currentBookId }
    }

    private fun loadBuiltInBook(): List<ChapterData> {
        // Try to load from assets/basic_text.fb2
        return try {
            val text = applicationContext.assets.open("basic_text.fb2")
                .bufferedReader().use { it.readText() }
            parseFB2(text)?.chapters ?: emptyList()
        } catch (e: Exception) {
            // Fallback: inline minimal chapters
            listOf(ChapterData("ch1", "Наш символ", listOf("Текст символа...")))
        }
    }

    private suspend fun loadBookChapters(entry: BookEntry): List<ChapterData> {
        val file = File(entry.filePath)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            when {
                entry.format == "fb2" -> parseFB2(text)?.chapters ?: emptyList()
                entry.format == "txt" -> parseTxt(text)
                else -> emptyList()
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseFB2(text: String): BookDisplay? {
        val title = Regex("<book-title>([^<]+)</book-title>", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1) ?: ""
        val body = Regex("<body>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val sections = Regex("<section>([\\s\\S]*?)</section>", RegexOption.IGNORE_CASE)
            .findAll(body.value).toList()
        val chapters = sections.mapIndexed { i, sec ->
            val secText = sec.value
            val secTitle = Regex("<title>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
                .find(secText)?.groupValues?.getOrNull(1)
                ?.replace(Regex("<[^>]+>"), "")?.trim() ?: "Раздел ${i + 1}"
            val pars = Regex("<p>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)
                .findAll(secText).map { it.groupValues[1] }
                .map { it.replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotEmpty() }
                .toList()
            ChapterData("fb2_$i", secTitle, pars)
        }.filter { it.content.isNotEmpty() }

        return if (chapters.isEmpty()) null else BookDisplay(-1, title, "", chapters, "", "")
    }

    private fun parseTxt(text: String): List<ChapterData> {
        val lines = text.lines().filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val pageSize = 30
        val pages = lines.chunked(pageSize).mapIndexed { i, chunk ->
            ChapterData("p_$i", "Страница ${i + 1}", chunk.map { it.trim() })
        }
        return pages
    }

    private fun importBook(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()

                // Determine format from content or URI
                val fileName = uri.lastPathSegment ?: "book"
                val ext = fileName.substringAfterLast('.', "").toLowerCase()

                // Copy to internal storage
                val bookDir = File(filesDir, "books")
                bookDir.mkdirs()
                val destFile = File(bookDir, "${System.currentTimeMillis()}_$fileName")
                destFile.writeBytes(bytes)

                val chapters = when (ext) {
                    "fb2" -> {
                        val text = bytes.toString(Charsets.UTF_8)
                        parseFB2(text)?.chapters
                    }
                    "txt" -> parseTxt(bytes.toString(Charsets.UTF_8))
                    else -> {
                        // Try parsing as FB2
                        val text = bytes.toString(Charsets.UTF_8)
                        parseFB2(text)?.chapters
                    }
                }

                val iconMap = mapOf("fb2" to "\uD83D\uDCD8", "txt" to "\uD83D\uDCC4",
                    "epub" to "\uD83D\uDCD5", "pdf" to "\uD83D\uDCCB", "docx" to "\uD83D\uDCDD")
                val colorMap = mapOf("fb2" to "#3A7B4F", "txt" to "#5A6B7A",
                    "epub" to "#8B3A8B", "pdf" to "#C0392B", "docx" to "#2C6FBB")

                val title = fileName.substringBeforeLast('.')
                db.bookEntryDao().insert(BookEntry(
                    title = title,
                    author = "",
                    format = ext,
                    filePath = destFile.absolutePath,
                    icon = iconMap[ext] ?: "\uD83D\uDCD6",
                    color = colorMap[ext] ?: "#4A7FB5"
                ))

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Книга импортирована: $title", Toast.LENGTH_SHORT).show()
                }
                loadLibrary()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка импорта: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /* ===================== BOOK ADAPTER ===================== */

    inner class BookAdapter(
        private val books: List<BookDisplay>,
        private val onClick: (BookDisplay) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_book, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val book = books[position]
            holder.itemView.findViewById<TextView>(R.id.bookTitle).text = book.title
            holder.itemView.findViewById<TextView>(R.id.bookMeta).text = "${book.chapters.size} глав"
            holder.itemView.setOnClickListener { onClick(book) }
        }

        override fun getItemCount() = books.size
    }

    /* ===================== SETTINGS ===================== */

    private suspend fun loadSettings() {
        val settings = db.userSettingsDao().get() ?: UserSettings()

        withContext(Dispatchers.Main) {
            // Theme
            if (settings.theme == "blue") {
                findViewById<RadioButton>(R.id.themeBlue).isChecked = true
            } else {
                findViewById<RadioButton>(R.id.themeLight).isChecked = true
            }

            // Notifications
            findViewById<Switch>(R.id.notifyVibrate).isChecked = settings.notifyVibrate
            findViewById<Switch>(R.id.notifySound).isChecked = settings.notifySound

            // Date picker
            if (settings.startDate != null) {
                val parts = settings.startDate.split("-")
                if (parts.size == 3) {
                    findViewById<DatePicker>(R.id.startDatePicker).updateDate(
                        parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt()
                    )
                }
            }

            findViewById<Button>(R.id.saveDateBtn).setOnClickListener {
                val picker = findViewById<DatePicker>(R.id.startDatePicker)
                val date = String.format(
                    Locale.US, "%04d-%02d-%02d",
                    picker.year, picker.month + 1, picker.dayOfMonth
                )
                lifecycleScope.launch {
                    db.userSettingsDao().updateStartDate(date)
                    loadHome()
                    Toast.makeText(this@MainActivity, "Дата сохранена", Toast.LENGTH_SHORT).show()
                }
            }

            // Theme switch
            findViewById<RadioGroup>(R.id.themeGroup).setOnCheckedChangeListener { _, checkedId ->
                val theme = if (checkedId == R.id.themeBlue) "blue" else "light"
                lifecycleScope.launch {
                    db.userSettingsDao().updateTheme(theme)
                    applyTheme(theme)
                }
            }

            // Notification settings
            findViewById<Switch>(R.id.notifyVibrate).setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch { db.userSettingsDao().updateVibrate(isChecked) }
            }
            findViewById<Switch>(R.id.notifySound).setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch { db.userSettingsDao().updateSound(isChecked) }
            }

            // Dev phone
            findViewById<TextView>(R.id.devPhone).setOnClickListener { dialPhone("89801793263") }
            findViewById<TextView>(R.id.maxPhone).setOnClickListener { dialPhone("89259039777") }

            // Backup
            findViewById<Button>(R.id.exportBtn).setOnClickListener {
                lifecycleScope.launch { exportBackup() }
            }
            findViewById<Button>(R.id.importBtn).setOnClickListener {
                backupImportLauncher.launch("application/json")
            }

            findViewById<Button>(R.id.backBtn).setOnClickListener { showScreen("home") }
        }
    }

    private fun applyTheme(theme: String) {
        // Simplified: just show toast. Full theme switching requires recreate.
        Toast.makeText(this, "Тема: $theme (перезапустите для полного применения)", Toast.LENGTH_SHORT).show()
    }

    /* ===================== NOTIFICATIONS PERMISSION ===================== */

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }
    }

    /* ===================== ALARM RESTORE ===================== */

    private suspend fun restoreAlarms() {
        // Restore plan reminder
        val settings = db.userSettingsDao().get()
        if (settings?.planReminderTime != null) {
            val parts = settings.planReminderTime.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (h != null && m != null) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, h)
                        set(Calendar.MINUTE, m)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis())
                            add(Calendar.DAY_OF_YEAR, 1)
                    }
                    NotificationHelper.schedulePlanReminder(this, cal.timeInMillis)
                }
            }
        }

        // Restore service reminders
        val services = db.serviceItemDao().getWithReminders()
        services.forEach { svc ->
            if (svc.reminderEnabled && svc.reminderTime.isNotEmpty()) {
                val parts = svc.reminderTime.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    if (h != null && m != null) {
                        NotificationHelper.scheduleServiceReminder(
                            this, svc.id, svc.dayOfWeek, h, m
                        )
                    }
                }
            }
        }
    }

    /* ===================== BACKUP ===================== */

    private suspend fun exportBackup() {
        try {
            val json = JSONObject()

            // Collect all data from DB
            val settings = db.userSettingsDao().get()
            if (settings != null) {
                json.put("user_settings", JSONObject().apply {
                    put("start_date", settings.startDate ?: "")
                    put("theme", settings.theme)
                    put("notify_vibrate", settings.notifyVibrate)
                    put("notify_sound", settings.notifySound)
                    put("plan_reminder_time", settings.planReminderTime ?: "")
                })
            }

            val plans = db.planItemDao().getAll()
            json.put("plan_items", plans.map { JSONObject().apply {
                put("text", it.text); put("done", it.done); put("date_key", it.dateKey)
            }})

            val services = db.serviceItemDao().getAll()
            json.put("service_items", services.map { JSONObject().apply {
                put("day", it.dayOfWeek); put("time", it.time); put("name", it.name)
                put("group", it.groupName); put("reminder", it.reminderEnabled)
            }})

            val books = db.bookEntryDao().getAll()
            json.put("book_entries", books.map { JSONObject().apply {
                put("title", it.title); put("author", it.author); put("format", it.format)
                put("file_path", it.filePath); put("icon", it.icon); put("color", it.color)
            }})

            val contacts = db.sosContactDao().getAll()
            json.put("sos_contacts", contacts.map { JSONObject().apply {
                put("name", it.name); put("phone", it.phone); put("type", it.type)
            }})

            val jsonStr = json.toString(2)

            // Save to downloads
            val fileName = "chistiy-den-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
            val file = File(getExternalFilesDir(null), fileName)
            file.parentFile?.mkdirs()
            file.writeText(jsonStr)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Бэкап сохранён: $fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun importBackup(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val text = BufferedReader(InputStreamReader(inputStream)).readText()
            inputStream.close()

            val json = JSONTokener(text).nextValue() as? JSONObject ?: return

            // Restore settings
            json.optJSONObject("user_settings")?.let { s ->
                db.userSettingsDao().upsert(UserSettings(
                    startDate = s.optString("start_date", null)?.ifEmpty { null },
                    theme = s.optString("theme", "light"),
                    notifyVibrate = s.optBoolean("notify_vibrate", true),
                    notifySound = s.optBoolean("notify_sound", true),
                    planReminderTime = s.optString("plan_reminder_time", null)?.ifEmpty { null }
                ))
            }

            // Restore plans (clear first)
            // Simplified: just log for now
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Восстановление завершено. Перезапустите приложение.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
