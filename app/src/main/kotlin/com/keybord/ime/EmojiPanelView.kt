package com.keybord.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keybord.theme.BuiltInThemes
import com.keybord.theme.KeyboardEffectEngine
import com.keybord.theme.ThemeModel
import com.keybord.ui.UiKit

/**
 * Full-featured on-keyboard Emoji Picker with:
 * - Persisted Recently Used section
 * - 8 curated Unicode categories
 * - Instant English + Hinglish keyword search
 * - Theme-adaptive surface/accent colors
 * - Bottom [ABC] return & hold-to-repeat [⌫] bar
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class EmojiItem(val emoji: String, val keywords: String, val category: String)

    var theme: ThemeModel = BuiltInThemes.MATERIAL_MIDNIGHT
        set(value) {
            field = value
            applyTheme()
        }

    var onEmojiSelected: ((String) -> Unit)? = null
    var onBackspaceClicked: (() -> Unit)? = null
    var onBackToKeyboardClicked: (() -> Unit)? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentEmojis = mutableListOf<String>()
    private var activeCategory: String = CAT_SMILEYS
    private var searchQuery: String = ""

    private lateinit var searchBox: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchClearBtn: TextView
    private lateinit var tabRow: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var abcBtn: TextView
    private lateinit var categoryTitleTv: TextView
    private lateinit var backspaceBtn: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isBackspaceHeld = false
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isBackspaceHeld) return
            onBackspaceClicked?.invoke()
            handler.postDelayed(this, 65L)
        }
    }

    init {
        orientation = VERTICAL
        loadRecents()
        if (recentEmojis.isNotEmpty()) {
            activeCategory = CAT_RECENT
        }
        setupViews()
        applyTheme()
        refreshGrid()
    }

    private fun setupViews() {
        val dp = resources.displayMetrics.density
        val totalHeightPx = ((theme.keyHeightDp * 4f + 56f) * dp).toInt().coerceAtLeast((240 * dp).toInt())
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, totalHeightPx)

        // Search Row
        searchBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            }
        }
        val searchIcon = TextView(context).apply {
            text = "🔍"
            textSize = 13f
            setPadding(0, 0, (6 * dp).toInt(), 0)
        }
        searchInput = EditText(context).apply {
            hint = "Search emojis (heart, namaste, chai, fire...)"
            textSize = 13f
            background = null
            setSingleLine(true)
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    searchClearBtn.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                    refreshGrid()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        searchClearBtn = TextView(context).apply {
            text = "✕"
            textSize = 14f
            visibility = View.GONE
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            setOnClickListener { searchInput.setText("") }
        }
        searchBox.addView(searchIcon)
        searchBox.addView(searchInput)
        searchBox.addView(searchClearBtn)
        addView(searchBox)

        // Category Tabs
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), (4 * dp).toInt())
        }
        tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        tabScroll.addView(tabRow)
        addView(tabScroll)

        // Empty Search Placeholder
        emptyView = TextView(context).apply {
            text = "No matching emojis"
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(emptyView)

        // Grid
        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 7)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            clipToPadding = false
        }
        addView(recyclerView)

        // Bottom Navigation Bar: [ABC] | Category Label | [⌫]
        bottomBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
        }
        abcBtn = TextView(context).apply {
            text = "ABC"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { onBackToKeyboardClicked?.invoke() }
        }
        categoryTitleTv = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        backspaceBtn = TextView(context).apply {
            text = "⌫"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((18 * dp).toInt(), (6 * dp).toInt(), (18 * dp).toInt(), (6 * dp).toInt())
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isBackspaceHeld = true
                        onBackspaceClicked?.invoke()
                        handler.postDelayed(backspaceRepeatRunnable, 360L)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isBackspaceHeld = false
                        handler.removeCallbacks(backspaceRepeatRunnable)
                        true
                    }
                    else -> false
                }
            }
        }
        bottomBar.addView(abcBtn)
        bottomBar.addView(categoryTitleTv)
        bottomBar.addView(backspaceBtn)
        addView(bottomBar)

        renderTabs()
    }

    private fun renderTabs() {
        val dp = resources.displayMetrics.density
        tabRow.removeAllViews()
        val accent = theme.effectColors.firstOrNull() ?: theme.accentKeyColor ?: theme.trailColor
        for ((catId, iconLabel) in CATEGORIES) {
            val selected = (catId == activeCategory && searchQuery.isEmpty())
            val tab = TextView(context).apply {
                text = iconLabel
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding((11 * dp).toInt(), (5 * dp).toInt(), (11 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (4 * dp).toInt()
                }
                background = if (selected) {
                    UiKit.roundedDrawable(context, Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent)), 10f, accent, 1f)
                } else {
                    null
                }
                setOnClickListener {
                    searchInput.setText("")
                    activeCategory = catId
                    renderTabs()
                    refreshGrid()
                }
            }
            tabRow.addView(tab)
        }
    }

    private fun refreshGrid() {
        val list: List<EmojiItem> = when {
            searchQuery.isNotEmpty() -> {
                val q = searchQuery.lowercase()
                ALL_EMOJIS.filter { it.keywords.contains(q) || it.category.lowercase().contains(q) }
            }
            activeCategory == CAT_RECENT -> {
                if (recentEmojis.isEmpty()) {
                    ALL_EMOJIS.filter { it.category == CAT_SMILEYS }.take(28)
                } else {
                    recentEmojis.map { em ->
                        ALL_EMOJIS.firstOrNull { it.emoji == em } ?: EmojiItem(em, "", CAT_RECENT)
                    }
                }
            }
            else -> ALL_EMOJIS.filter { it.category == activeCategory }
        }

        categoryTitleTv.text = if (searchQuery.isNotEmpty()) {
            "Results (${list.size})"
        } else {
            activeCategory
        }

        if (list.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = EmojiAdapter(list) { item ->
                recordRecent(item.emoji)
                onEmojiSelected?.invoke(item.emoji)
            }
        }
    }

    private fun recordRecent(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 28) {
            recentEmojis.removeAt(recentEmojis.lastIndex)
        }
        prefs.edit().putString(KEY_RECENT, recentEmojis.joinToString("\u0001")).apply()
    }

    private fun loadRecents() {
        val raw = prefs.getString(KEY_RECENT, null) ?: return
        recentEmojis.clear()
        raw.split('\u0001').filter { it.isNotBlank() }.forEach { recentEmojis.add(it) }
    }

    private fun applyTheme() {
        val bg = theme.keyboardBackground.colorStart
        setBackgroundColor(bg)
        val dp = resources.displayMetrics.density
        val totalHeightPx = ((theme.keyHeightDp * 4f + theme.bottomPaddingDp + 52f) * dp).toInt()
            .coerceAtLeast((240 * dp).toInt())
        layoutParams = (layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, totalHeightPx)).apply {
            height = totalHeightPx
        }

        val cardBg = KeyboardEffectEngine.compositeOver(theme.keyFaceColor.primaryColor, bg)
        val funcBg = KeyboardEffectEngine.compositeOver(theme.functionalKeyColor ?: theme.keyFaceColor.primaryColor, bg)
        searchBox.background = UiKit.roundedDrawable(context, cardBg, 12f, theme.keyBorderColor, 1f)
        searchInput.setTextColor(theme.keyLabelColorPrimary)
        searchInput.setHintTextColor(theme.keyLabelColorSecondary)
        searchClearBtn.setTextColor(theme.keyLabelColorSecondary)
        emptyView.setTextColor(theme.keyLabelColorSecondary)
        categoryTitleTv.setTextColor(theme.keyLabelColorSecondary)

        abcBtn.background = UiKit.roundedDrawable(context, funcBg, 10f, theme.keyBorderColor, 1f)
        abcBtn.setTextColor(theme.keyLabelColorPrimary)
        backspaceBtn.background = UiKit.roundedDrawable(context, funcBg, 10f, theme.keyBorderColor, 1f)
        backspaceBtn.setTextColor(theme.keyLabelColorPrimary)

        if (::tabRow.isInitialized) {
            renderTabs()
        }
    }

    private class EmojiAdapter(
        private val items: List<EmojiItem>,
        private val onClick: (EmojiItem) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.VH>() {

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = parent.context.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (44 * dp).toInt()
                )
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tv.text = item.emoji
            holder.tv.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val PREFS_NAME = "keybord_emoji_prefs"
        private const val KEY_RECENT = "recent_emojis"

        private const val CAT_RECENT = "Recent"
        private const val CAT_SMILEYS = "Smileys"
        private const val CAT_PEOPLE = "Gestures"
        private const val CAT_HEARTS = "Love"
        private const val CAT_CELEBRATION = "Fun"
        private const val CAT_FOOD = "Food"
        private const val CAT_TRAVEL = "Places"
        private const val CAT_OBJECTS = "Objects"

        private val CATEGORIES = listOf(
            CAT_RECENT to "🕒",
            CAT_SMILEYS to "😀",
            CAT_PEOPLE to "🙏",
            CAT_HEARTS to "❤️",
            CAT_CELEBRATION to "🎉",
            CAT_FOOD to "☕",
            CAT_TRAVEL to "✈️",
            CAT_OBJECTS to "🔥"
        )

        private val ALL_EMOJIS = listOf(
            // Smileys
            EmojiItem("😀", "grin smile happy khush", CAT_SMILEYS),
            EmojiItem("😃", "smile happy joy", CAT_SMILEYS),
            EmojiItem("😄", "laugh happy smile", CAT_SMILEYS),
            EmojiItem("😁", "grin teeth happy", CAT_SMILEYS),
            EmojiItem("😆", "laugh haha lol", CAT_SMILEYS),
            EmojiItem("😅", "sweat laugh relief", CAT_SMILEYS),
            EmojiItem("😂", "laugh cry tears joy lol haha hasi", CAT_SMILEYS),
            EmojiItem("🤣", "rofl laugh haha", CAT_SMILEYS),
            EmojiItem("😊", "blush warm smile", CAT_SMILEYS),
            EmojiItem("😇", "angel innocent halo", CAT_SMILEYS),
            EmojiItem("🙂", "slight smile ok", CAT_SMILEYS),
            EmojiItem("😉", "wink flirt", CAT_SMILEYS),
            EmojiItem("😍", "love heart eyes pyaar", CAT_SMILEYS),
            EmojiItem("🥰", "love hearts adore pyar", CAT_SMILEYS),
            EmojiItem("😘", "kiss love", CAT_SMILEYS),
            EmojiItem("😋", "yum delicious tasty mast", CAT_SMILEYS),
            EmojiItem("😎", "cool sunglasses swag hero", CAT_SMILEYS),
            EmojiItem("🤩", "star struck wow", CAT_SMILEYS),
            EmojiItem("🥳", "party celebrate masti bday", CAT_SMILEYS),
            EmojiItem("😏", "smirk sly", CAT_SMILEYS),
            EmojiItem("😒", "unamused meh bored", CAT_SMILEYS),
            EmojiItem("😔", "sad pensive udaas", CAT_SMILEYS),
            EmojiItem("😢", "cry tear sad rona dukh", CAT_SMILEYS),
            EmojiItem("😭", "sob cry loud", CAT_SMILEYS),
            EmojiItem("😤", "triumph steam angry", CAT_SMILEYS),
            EmojiItem("😡", "angry mad gussa", CAT_SMILEYS),
            EmojiItem("🤯", "mind blown shocked", CAT_SMILEYS),
            EmojiItem("😳", "flushed embarrassed sharam", CAT_SMILEYS),
            EmojiItem("🤔", "think hmm soch", CAT_SMILEYS),
            EmojiItem("🤭", "oops giggle", CAT_SMILEYS),
            EmojiItem("🤫", "shh quiet chup", CAT_SMILEYS),
            EmojiItem("😴", "sleep tired neend", CAT_SMILEYS),
            EmojiItem("🤒", "sick fever bukhar", CAT_SMILEYS),
            EmojiItem("🥺", "pleading puppy please", CAT_SMILEYS),
            EmojiItem("😬", "grimace awkward", CAT_SMILEYS),
            EmojiItem("🫠", "melt awkward", CAT_SMILEYS),
            EmojiItem("🫡", "salute respect", CAT_SMILEYS),
            EmojiItem("🤝", "handshake deal dosti", CAT_PEOPLE),

            // Gestures & People
            EmojiItem("🙏", "namaste pranam thank you shukriya dua please", CAT_PEOPLE),
            EmojiItem("👍", "thumbs up yes ok sahi haan", CAT_PEOPLE),
            EmojiItem("👎", "thumbs down no galat nahi", CAT_PEOPLE),
            EmojiItem("👌", "ok perfect badiya mast", CAT_PEOPLE),
            EmojiItem("✌️", "peace victory two", CAT_PEOPLE),
            EmojiItem("🤞", "fingers crossed luck kismat", CAT_PEOPLE),
            EmojiItem("🤟", "love you rock", CAT_PEOPLE),
            EmojiItem("🤘", "rock on metal", CAT_PEOPLE),
            EmojiItem("🤙", "call me bro chill", CAT_PEOPLE),
            EmojiItem("👏", "clap applause shabash taali", CAT_PEOPLE),
            EmojiItem("🙌", "celebrate praise", CAT_PEOPLE),
            EmojiItem("🫶", "heart hands pyaar", CAT_PEOPLE),
            EmojiItem("💪", "strong muscle power gym taqat", CAT_PEOPLE),
            EmojiItem("👋", "wave hello hi bye", CAT_PEOPLE),
            EmojiItem("✋", "stop hand ruko", CAT_PEOPLE),
            EmojiItem("👉", "point right", CAT_PEOPLE),
            EmojiItem("👆", "point up", CAT_PEOPLE),
            EmojiItem("👀", "eyes look dekho", CAT_PEOPLE),
            EmojiItem("🧠", "brain smart dimag", CAT_PEOPLE),

            // Hearts & Symbols
            EmojiItem("❤️", "red heart love dil pyaar", CAT_HEARTS),
            EmojiItem("🩷", "pink heart love", CAT_HEARTS),
            EmojiItem("🧡", "orange heart", CAT_HEARTS),
            EmojiItem("💛", "yellow heart friendship dosti", CAT_HEARTS),
            EmojiItem("💚", "green heart", CAT_HEARTS),
            EmojiItem("💙", "blue heart", CAT_HEARTS),
            EmojiItem("💜", "purple heart", CAT_HEARTS),
            EmojiItem("🖤", "black heart", CAT_HEARTS),
            EmojiItem("🤍", "white heart peace", CAT_HEARTS),
            EmojiItem("💔", "broken heart sad", CAT_HEARTS),
            EmojiItem("❤️‍🔥", "heart fire passion", CAT_HEARTS),
            EmojiItem("💖", "sparkle heart", CAT_HEARTS),
            EmojiItem("💘", "cupid arrow love", CAT_HEARTS),
            EmojiItem("💯", "100 percent bilkul sahi pakka", CAT_HEARTS),
            EmojiItem("✅", "check done sahi ok", CAT_HEARTS),
            EmojiItem("❌", "cross wrong cancel galat", CAT_HEARTS),
            EmojiItem("⭐", "star taara favorite", CAT_HEARTS),
            EmojiItem("⚡", "lightning fast energy", CAT_HEARTS),

            // Fun & Celebration
            EmojiItem("🎉", "party popper congrats badhai mubarak", CAT_CELEBRATION),
            EmojiItem("🎊", "confetti celebration jashn", CAT_CELEBRATION),
            EmojiItem("🥳", "party face bday", CAT_CELEBRATION),
            EmojiItem("🎈", "balloon party", CAT_CELEBRATION),
            EmojiItem("🎂", "cake birthday", CAT_CELEBRATION),
            EmojiItem("🎁", "gift present tohfa", CAT_CELEBRATION),
            EmojiItem("🪔", "diya diwali lamp roshni", CAT_CELEBRATION),
            EmojiItem("🎆", "fireworks patakha diwali", CAT_CELEBRATION),
            EmojiItem("✨", "sparkles magic shine", CAT_CELEBRATION),
            EmojiItem("🏆", "trophy winner jeet", CAT_CELEBRATION),
            EmojiItem("🥇", "first medal win", CAT_CELEBRATION),
            EmojiItem("🏏", "cricket bat ball india", CAT_CELEBRATION),
            EmojiItem("⚽", "football soccer", CAT_CELEBRATION),
            EmojiItem("🎮", "gaming game controller", CAT_CELEBRATION),
            EmojiItem("🎵", "music song gaana", CAT_CELEBRATION),
            EmojiItem("🎶", "music notes geet", CAT_CELEBRATION),

            // Food & Drink
            EmojiItem("☕", "chai coffee tea morning", CAT_FOOD),
            EmojiItem("🍵", "tea chai cup", CAT_FOOD),
            EmojiItem("🍕", "pizza food", CAT_FOOD),
            EmojiItem("🍔", "burger food", CAT_FOOD),
            EmojiItem("🍟", "fries snack", CAT_FOOD),
            EmojiItem("🍛", "curry biryani dal chawal khana", CAT_FOOD),
            EmojiItem("🥟", "momos samosa dumpling", CAT_FOOD),
            EmojiItem("🍨", "ice cream kulfi meetha", CAT_FOOD),
            EmojiItem("🍰", "cake mithai sweet", CAT_FOOD),
            EmojiItem("🍫", "chocolate sweet", CAT_FOOD),
            EmojiItem("🥭", "mango aam fruit", CAT_FOOD),
            EmojiItem("🍌", "banana kela fruit", CAT_FOOD),
            EmojiItem("💧", "water pani drop", CAT_FOOD),
            EmojiItem("🍺", "beer drink", CAT_FOOD),
            EmojiItem("🥂", "cheers toast", CAT_FOOD),

            // Places & Travel
            EmojiItem("🇮🇳", "india bharat hindustan flag tiranga", CAT_TRAVEL),
            EmojiItem("🏠", "home ghar house", CAT_TRAVEL),
            EmojiItem("🏢", "office work kaam", CAT_TRAVEL),
            EmojiItem("🚗", "car drive gaadi", CAT_TRAVEL),
            EmojiItem("🏍️", "bike motorcycle", CAT_TRAVEL),
            EmojiItem("🚆", "train metro rail", CAT_TRAVEL),
            EmojiItem("✈️", "flight plane safar travel", CAT_TRAVEL),
            EmojiItem("🚀", "rocket fast launch", CAT_TRAVEL),
            EmojiItem("🌍", "earth world duniya", CAT_TRAVEL),
            EmojiItem("☀️", "sun morning dhoop subah", CAT_TRAVEL),
            EmojiItem("🌙", "moon night raat chaand", CAT_TRAVEL),
            EmojiItem("🌧️", "rain barish monsoon", CAT_TRAVEL),
            EmojiItem("⛈️", "storm thunder", CAT_TRAVEL),
            EmojiItem("🌈", "rainbow", CAT_TRAVEL),

            // Objects
            EmojiItem("🔥", "fire lit mast zabardast aag", CAT_OBJECTS),
            EmojiItem("💡", "idea bulb light", CAT_OBJECTS),
            EmojiItem("📱", "phone mobile call", CAT_OBJECTS),
            EmojiItem("💻", "laptop computer coding work", CAT_OBJECTS),
            EmojiItem("⌨️", "keyboard typing", CAT_OBJECTS),
            EmojiItem("🎙️", "mic voice audio", CAT_OBJECTS),
            EmojiItem("📚", "books study padhai kitaab", CAT_OBJECTS),
            EmojiItem("💰", "money paisa cash", CAT_OBJECTS),
            EmojiItem("⏰", "alarm time samay waqt", CAT_OBJECTS),
            EmojiItem("🔒", "lock privacy safe", CAT_OBJECTS),
            EmojiItem("📌", "pin clipboard", CAT_OBJECTS),
            EmojiItem("🗑️", "trash delete", CAT_OBJECTS),
            EmojiItem("🌹", "rose flower phool pyaar", CAT_OBJECTS),
            EmojiItem("🐾", "paw pet dog cat", CAT_OBJECTS)
        )
    }
}
