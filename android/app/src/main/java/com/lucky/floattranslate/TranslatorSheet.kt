package com.lucky.floattranslate

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.math.min

/** Compact bottom sheet drawn over the current app via WindowManager. */
class TranslatorSheet(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onClosed: () -> Unit,
) {
    private val prefs = Prefs(ctx)
    private val handler = Handler(Looper.getMainLooper())

    private var from = prefs.startFrom()
    private var to = prefs.startTo()
    private var tone = prefs.tone

    private val root = object : FrameLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    if (picker != null) closePicker() else dismiss()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
    private val card = LinearLayout(ctx)
    private val content = FrameLayout(ctx)
    private lateinit var mainView: ScrollView
    private var picker: View? = null

    private lateinit var input: EditText
    private lateinit var fromValue: TextView
    private lateinit var toValue: TextView
    private lateinit var toneValue: TextView
    private lateinit var counter: TextView
    private lateinit var clearBtn: TextView
    private lateinit var translateBtn: TextView
    private lateinit var progress: ProgressBar
    private lateinit var translateLabel: TextView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultLabel: TextView
    private lateinit var resultText: TextView
    private lateinit var copyBtn: TextView

    private var pending: Future<*>? = null
    private var requestId = 0
    private var attached = false
    private var closing = false
    private var hasResult = false
    private val params = WindowManager.LayoutParams()

    private fun sheetHeight(): Int {
        val h = ctx.resources.displayMetrics.heightPixels
        return min((h * 0.9f).toInt(), max((h * 0.56f).toInt(), ctx.dp(400)))
    }

    fun show() {
        build()
        params.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = sheetHeight()
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                if (prefs.autoFocus) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                else WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        }
        try {
            wm.addView(root, params)
            attached = true
        } catch (e: Exception) {
            onClosed(); return
        }
        card.translationY = params.height.toFloat()
        card.animate().translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator(2f)).start()
        if (prefs.autoFocus) {
            handler.postDelayed({
                if (!attached) return@postDelayed
                input.requestFocus()
                ctx.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }

    fun onConfigChanged() {
        if (!attached) return
        params.height = sheetHeight()
        try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    fun dismiss() {
        if (!attached || closing) return
        closing = true
        hideKeyboard()
        card.animate().translationY(card.height.toFloat().coerceAtLeast(1f)).setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = dismissNow()
            }).start()
    }

    fun dismissNow() {
        if (!attached) return
        attached = false
        requestId++
        pending?.cancel(true)
        pending = null
        handler.removeCallbacksAndMessages(null)
        card.animate().cancel()
        try { wm.removeViewImmediate(root) } catch (_: Exception) {}
        onClosed()
    }

    private fun hideKeyboard() {
        ctx.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(root.windowToken, 0)
    }

    // ---------------- UI ----------------

    private fun build() = with(ctx) {
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawableTop(Pal.SHEET, dpf(28))
        card.elevation = dpf(16)
        card.setPadding(dp(18), dp(8), dp(18), 0)
        root.addView(card, FrameLayout.LayoutParams(MATCH, MATCH))

        // Drag handle + header (swipe-down area)
        val dragArea = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val handleBar = View(ctx).apply { background = rounded(Pal.SURFACE2, dpf(3)) }
        dragArea.addView(handleBar, lp(dp(40), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4); bottomMargin = dp(10) })
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Float Translate", 17f, Pal.TEXT, true), lp(0, WRAP, 1f))
        val close = label("✕", 15f, Pal.MUTED).apply {
            gravity = Gravity.CENTER
            background = ripple(rounded(Pal.SURFACE, dpf(18)))
            setOnClickListener { dismiss() }
            contentDescription = "Close"
        }
        header.addView(close, lp(dp(36), dp(36)))
        dragArea.addView(header, lp(MATCH, WRAP).apply { bottomMargin = dp(12) })
        attachSwipe(dragArea)
        card.addView(dragArea, lp(MATCH, WRAP))
        card.addView(content, lp(MATCH, 0, 1f))

        mainView = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(18)) }
        mainView.addView(body)
        content.addView(mainView, FrameLayout.LayoutParams(MATCH, MATCH))

        // Language row
        val langRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val (fromChip, fv) = chip("From", from) { openLangPicker(true) }
        val (toChip, tv) = chip("To", to) { openLangPicker(false) }
        fromValue = fv; toValue = tv
        val swap = label("⇄", 18f, Pal.ACCENT, true).apply {
            gravity = Gravity.CENTER
            background = ripple(rounded(Pal.ACCENT_SOFT, dpf(22)))
            contentDescription = "Swap languages"
            setOnClickListener { swapLangs() }
        }
        langRow.addView(fromChip, lp(0, WRAP, 1f))
        langRow.addView(swap, lp(dp(44), dp(44)).apply { leftMargin = dp(8); rightMargin = dp(8) })
        langRow.addView(toChip, lp(0, WRAP, 1f))
        body.addView(langRow, lp(MATCH, WRAP))

        // Input
        val inputBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.SURFACE, dpf(20))
            setPadding(dp(16), dp(12), dp(12), dp(8))
        }
        input = EditText(ctx).apply {
            hint = "Type or paste your message..."
            setHintTextColor(Pal.MUTED)
            setTextColor(Pal.TEXT)
            textSize = 16f
            background = null
            setPadding(0, 0, 0, 0)
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = refreshInputState()
            })
        }
        inputBox.addView(input, lp(MATCH, WRAP))
        val inputFoot = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        counter = label("0/2000", 11.5f, Pal.MUTED)
        clearBtn = label("Clear", 12.5f, Pal.MUTED, true).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = ripple(rounded(0, dpf(12)))
            setOnClickListener { input.setText(""); hideResult(); input.requestFocus() }
        }
        inputFoot.addView(counter, lp(0, WRAP, 1f))
        inputFoot.addView(clearBtn, lp(WRAP, WRAP))
        inputBox.addView(inputFoot, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        body.addView(inputBox, lp(MATCH, WRAP).apply { topMargin = dp(12) })

        // Tone + Translate row
        val actionRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val (toneChip, tnv) = chip("Tone", tone) { openTonePicker() }
        toneValue = tnv
        actionRow.addView(toneChip, lp(0, WRAP, 1f))

        val tFrame = FrameLayout(ctx).apply {
            background = ripple(rounded(Pal.ACCENT, dpf(18)))
            isClickable = true
            setOnClickListener { doTranslate() }
        }
        translateBtn = label("", 1f).apply { gone() } // placeholder for enabled state tracking
        val tInner = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        progress = ProgressBar(ctx).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Pal.TEXT)
            gone()
        }
        translateLabel = label("Translate", 15.5f, Pal.TEXT, true)
        tInner.addView(progress, lp(dp(18), dp(18)).apply { rightMargin = dp(8) })
        tInner.addView(translateLabel, lp(WRAP, WRAP))
        tFrame.addView(tInner, FrameLayout.LayoutParams(MATCH, MATCH))
        tFrame.tag = "translate"
        actionRow.addView(tFrame, lp(0, dp(56), 1.15f).apply { leftMargin = dp(10) })
        body.addView(actionRow, lp(MATCH, WRAP).apply { topMargin = dp(12) })
        translateFrame = tFrame

        // Result card
        resultCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.SURFACE, dpf(20), dp(1), Pal.LINE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gone()
        }
        resultLabel = caps("Translation", Pal.ACCENT)
        resultText = label("", 16.5f, Pal.TEXT).apply {
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
        }
        copyBtn = label("Copy", 15f, Pal.TEXT, true).apply {
            gravity = Gravity.CENTER
            background = ripple(rounded(Pal.ACCENT, dpf(16)))
            setOnClickListener { copy() }
        }
        resultCard.addView(resultLabel, lp(WRAP, WRAP))
        resultCard.addView(resultText, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        resultCard.addView(copyBtn, lp(MATCH, dp(48)).apply { topMargin = dp(12) })
        body.addView(resultCard, lp(MATCH, WRAP).apply { topMargin = dp(12) })

        refreshInputState()
    }

    private var translateFrame: View? = null

    private fun chip(title: String, value: String, onClick: () -> Unit): Pair<LinearLayout, TextView> = with(ctx) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ripple(rounded(Pal.SURFACE, dpf(18)))
            setPadding(dp(14), dp(9), dp(12), dp(9))
            isClickable = true
            setOnClickListener { onClick() }
            minimumHeight = dp(56)
        }
        box.addView(caps(title))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val v = label(value, 15f, Pal.TEXT, true).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END }
        row.addView(v, lp(0, WRAP, 1f))
        row.addView(label("▾", 13f, Pal.MUTED))
        box.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(2) })
        box to v
    }

    private fun attachSwipe(area: View) {
        var startY = 0f
        var dy = 0f
        area.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; dy = 0f; true }
                MotionEvent.ACTION_MOVE -> {
                    dy = max(0f, e.rawY - startY)
                    card.translationY = dy
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dy > card.height * 0.22f) dismiss()
                    else card.animate().translationY(0f).setDuration(160).start()
                    true
                }
                else -> false
            }
        }
    }

    // ---------------- pickers ----------------

    private fun openLangPicker(isFrom: Boolean) {
        openPicker(if (isFrom) "From language" else "To language", Langs.ALL, if (isFrom) from else to, true) { pick ->
            if (isFrom) from = pick else to = pick
            saveLangs()
            fromValue.text = from; toValue.text = to
        }
    }

    private fun openTonePicker() {
        openPicker("Tone", Langs.TONES, tone, false) { pick ->
            tone = pick
            toneValue.text = pick
        }
    }

    private fun openPicker(title: String, items: List<String>, selected: String, searchable: Boolean, onPick: (String) -> Unit) = with(ctx) {
        hideKeyboard()
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(12)) }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = label("‹", 22f, Pal.TEXT).apply {
            gravity = Gravity.CENTER
            background = ripple(rounded(Pal.SURFACE, dpf(18)))
            setOnClickListener { closePicker() }
            contentDescription = "Back"
        }
        head.addView(back, lp(dp(36), dp(36)))
        head.addView(label(title, 15.5f, Pal.TEXT, true), lp(0, WRAP, 1f).apply { leftMargin = dp(12) })
        wrap.addView(head, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
        wrap.addView(pickerList(items, selected, searchable) { onPick(it); closePicker() }, lp(MATCH, 0, 1f))
        mainView.gone()
        picker = wrap
        content.addView(wrap, FrameLayout.LayoutParams(MATCH, MATCH))
        wrap.alpha = 0f
        wrap.animate().alpha(1f).setDuration(140).start()
    }

    private fun closePicker() {
        picker?.let { content.removeView(it) }
        picker = null
        mainView.show()
        hideKeyboard()
    }

    // ---------------- actions ----------------

    private fun saveLangs() {
        if (prefs.rememberLangs) { prefs.lastFrom = from; prefs.lastTo = to }
    }

    private fun swapLangs() {
        val t = from; from = to; to = t
        fromValue.text = from; toValue.text = to
        saveLangs()
        if (hasResult) {
            input.setText(resultText.text.toString())
            input.setSelection(input.text.length)
            hideResult()
        }
    }

    private fun refreshInputState() {
        val len = input.text.length
        counter.text = "$len/2000"
        clearBtn.visibility = if (len > 0) View.VISIBLE else View.INVISIBLE
        val enabled = input.text.isNotBlank() && pending == null
        translateFrame?.isEnabled = enabled
        translateFrame?.alpha = if (input.text.isNotBlank()) 1f else 0.4f
    }

    private fun setLoading(loading: Boolean) {
        if (loading) { progress.show(); translateLabel.text = "Translating" }
        else { progress.gone(); translateLabel.text = "Translate" }
        refreshInputState()
    }

    private fun doTranslate() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) { showError("Type something to translate."); return }
        if (pending != null) return
        val id = ++requestId
        val body = JSONObject()
            .put("text", text).put("source", from).put("target", to)
            .put("tone", tone).put("output", prefs.output)
        pending = TranslateClient.translate(body) { res ->
            if (!attached || id != requestId) return@translate
            pending = null
            setLoading(false)
            res.fold({ showResult(it) }, { showError(it.message ?: "Something went wrong.") })
        }
        setLoading(true)
    }

    private fun showResult(t: String) {
        hasResult = true
        resultLabel.text = "TRANSLATION"
        resultLabel.setTextColor(Pal.ACCENT)
        resultText.text = t
        resultText.setTextColor(Pal.TEXT)
        copyBtn.show()
        copyBtn.text = "Copy"
        resultCard.show()
        mainView.post { mainView.smoothScrollTo(0, resultCard.top) }
    }

    private fun showError(msg: String) {
        hasResult = false
        resultLabel.text = "COULDN'T TRANSLATE"
        resultLabel.setTextColor(Pal.ERROR)
        resultText.text = msg
        resultText.setTextColor(Pal.MUTED)
        copyBtn.gone()
        resultCard.show()
    }

    private fun hideResult() {
        hasResult = false
        resultCard.gone()
    }

    private fun copy() {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Translation", resultText.text.toString()))
        copyBtn.text = "Copied ✓"
        handler.postDelayed({ if (attached) copyBtn.text = "Copy" }, 1500)
        if (prefs.closeAfterCopy) handler.postDelayed({ dismiss() }, 450)
    }
}

/** Rounded-top-only sheet background. */
class GradientDrawableTop(color: Int, r: Float) : android.graphics.drawable.GradientDrawable() {
    init {
        setColor(color)
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }
}
