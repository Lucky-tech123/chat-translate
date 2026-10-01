package com.lucky.floattranslate

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Permission explanation + settings screen. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private var askedPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(Pal.BG); isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        scroll.addView(body)
        setContentView(scroll)
        askedPermission = savedInstanceState?.getBoolean("asked") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("asked", askedPermission)
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlay() && prefs.bubbleEnabled && !BubbleService.running) BubbleService.start(this)
        render()
    }

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun requestOverlay() {
        askedPermission = true
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    // ---------------- render ----------------

    private fun render() {
        body.removeAllViews()
        body.addView(label("Float Translate", 26f, Pal.TEXT, true))
        body.addView(label("Natural chat translation over any app.", 14f, Pal.MUTED), lp(MATCH, WRAP).apply { topMargin = dp(4); bottomMargin = dp(20) })

        if (!hasOverlay()) {
            val c = card()
            c.addView(label("Allow display over other apps", 17f, Pal.TEXT, true))
            c.addView(label(
                "The translator floats as a small bubble on top of WhatsApp, Telegram, Instagram and other apps, so you never have to switch away. Android needs your permission to show it.\n\nNothing else is accessed — no contacts, messages, microphone or files.",
                14f, Pal.MUTED,
            ).apply { setLineSpacing(0f, 1.2f) }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
            if (askedPermission) {
                c.addView(label("Permission not granted yet. Open settings, find Float Translate and turn on \"Allow display over other apps\".", 13.5f, Pal.ERROR),
                    lp(MATCH, WRAP).apply { topMargin = dp(12) })
            }
            c.addView(primaryButton(if (askedPermission) "Open settings" else "Grant permission").apply {
                setOnClickListener { requestOverlay() }
            }, lp(MATCH, WRAP).apply { topMargin = dp(16) })
            body.addView(c, lp(MATCH, WRAP).apply { bottomMargin = dp(16) })
        } else {
            val c = card()
            c.addView(toggleRow("Floating bubble", if (BubbleService.running) "Active — tap it in any app" else "Off", prefs.bubbleEnabled) { on ->
                prefs.bubbleEnabled = on
                if (on) BubbleService.start(this) else BubbleService.stop(this)
                body.postDelayed({ render() }, 250)
            })
            c.addView(primaryButton("Open translator now").apply {
                setOnClickListener {
                    prefs.bubbleEnabled = true
                    BubbleService.start(this@MainActivity, BubbleService.ACTION_OPEN)
                    moveTaskToBack(true)
                }
            }, lp(MATCH, WRAP).apply { topMargin = dp(12) })
            body.addView(c, lp(MATCH, WRAP).apply { bottomMargin = dp(16) })
        }

        section("Translation")
        val t = card()
        t.addView(selectRow("Default source language", prefs.defaultFrom) { pick("Default source", Langs.ALL, prefs.defaultFrom, true) { prefs.defaultFrom = it; prefs.lastFrom = it } })
        t.addView(divider())
        t.addView(selectRow("Default target language", prefs.defaultTo) { pick("Default target", Langs.ALL, prefs.defaultTo, true) { prefs.defaultTo = it; prefs.lastTo = it } })
        t.addView(divider())
        t.addView(label("Translation Output", 15f, Pal.TEXT), lp(MATCH, WRAP).apply { topMargin = dp(12) })
        t.addView(label("How non-Latin scripts (Korean, Japanese, Chinese, Hindi…) appear", 12.5f, Pal.MUTED), lp(MATCH, WRAP).apply { topMargin = dp(2) })
        t.addView(segmented(Langs.OUTPUTS, prefs.output) { prefs.output = it; render() }, lp(MATCH, WRAP).apply { topMargin = dp(10); bottomMargin = dp(12) })
        t.addView(divider())
        t.addView(selectRow("Default tone", prefs.tone) { pick("Default tone", Langs.TONES, prefs.tone, false) { prefs.tone = it } })
        body.addView(t, lp(MATCH, WRAP).apply { bottomMargin = dp(16) })

        section("Floating Bubble")
        val b = card()
        b.addView(label("Bubble position", 15f, Pal.TEXT), lp(MATCH, WRAP).apply { topMargin = dp(12) })
        b.addView(segmented(listOf("Left", "Right"), prefs.bubbleSide) { prefs.bubbleSide = it; refreshBubble(); render() }, lp(MATCH, WRAP).apply { topMargin = dp(10); bottomMargin = dp(12) })
        b.addView(divider())
        b.addView(label("Bubble size", 15f, Pal.TEXT), lp(MATCH, WRAP).apply { topMargin = dp(12) })
        b.addView(segmented(listOf("Small", "Medium", "Large"), prefs.bubbleSize) { prefs.bubbleSize = it; refreshBubble(); render() }, lp(MATCH, WRAP).apply { topMargin = dp(10); bottomMargin = dp(12) })
        b.addView(label("Tip: drag the bubble onto the ✕ at the bottom to remove it.", 12.5f, Pal.MUTED), lp(MATCH, WRAP).apply { bottomMargin = dp(12) })
        body.addView(b, lp(MATCH, WRAP).apply { bottomMargin = dp(16) })

        section("Behavior")
        val h = card()
        h.addView(toggleRow("Auto-focus input", "Keyboard opens with the translator", prefs.autoFocus) { prefs.autoFocus = it })
        h.addView(divider())
        h.addView(toggleRow("Close after copying", "Hide the translator once copied", prefs.closeAfterCopy) { prefs.closeAfterCopy = it })
        h.addView(divider())
        h.addView(toggleRow("Remember selected languages", "Reuse your last language pair", prefs.rememberLangs) { prefs.rememberLangs = it })
        body.addView(h, lp(MATCH, WRAP))
    }

    private fun refreshBubble() {
        if (BubbleService.running) BubbleService.start(this, BubbleService.ACTION_REFRESH)
    }

    // ---------------- widgets ----------------

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Pal.SHEET, dpf(22), dp(1), Pal.LINE)
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    private fun section(title: String) {
        body.addView(caps(title, Pal.ACCENT), lp(MATCH, WRAP).apply { leftMargin = dp(6); bottomMargin = dp(8) })
    }

    private fun divider() = View(this).apply { setBackgroundColor(Pal.LINE) }.also { it.layoutParams = lp(MATCH, dp(1)) }

    private fun toggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 15f, Pal.TEXT))
        texts.addView(label(sub, 12.5f, Pal.MUTED), lp(MATCH, WRAP).apply { topMargin = dp(2) })
        row.addView(texts, lp(0, WRAP, 1f))
        val sw = Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, v -> onChange(v) } }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    private fun selectRow(title: String, value: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            background = ripple(rounded(0, dpf(12)))
            setOnClickListener { onClick() }
        }
        row.addView(label(title, 15f, Pal.TEXT), lp(0, WRAP, 1f))
        row.addView(label("$value  ›", 14.5f, Pal.ACCENT, true))
        return row
    }

    private fun segmented(options: List<String>, selected: String, onPick: (String) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Pal.SURFACE, dpf(14))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        options.forEach { o ->
            val sel = o == selected
            val tv = TextView(this).apply {
                text = o
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = MEDIUM
                setTextColor(if (sel) Pal.TEXT else Pal.MUTED)
                background = if (sel) rounded(Pal.ACCENT, dpf(11)) else ripple(rounded(0, dpf(11)))
                setOnClickListener { if (!sel) onPick(o) }
            }
            row.addView(tv, lp(0, dp(38), 1f))
        }
        return row
    }

    private fun pick(title: String, items: List<String>, selected: String, searchable: Boolean, onPick: (String) -> Unit) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.SHEET, dpf(24))
            setPadding(dp(16), dp(18), dp(16), dp(12))
        }
        wrap.addView(label(title, 17f, Pal.TEXT, true), lp(MATCH, WRAP).apply { bottomMargin = dp(12); leftMargin = dp(4) })
        var dialog: AlertDialog? = null
        val list = pickerList(items, selected, searchable) { onPick(it); dialog?.dismiss(); render() }
        wrap.addView(list, lp(MATCH, (resources.displayMetrics.heightPixels * 0.55f).toInt()))
        dialog = AlertDialog.Builder(this).setView(wrap).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        dialog.show()
    }
}
