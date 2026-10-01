package com.lucky.floattranslate

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.Future

object Langs {
    val ALL = listOf(
        "English", "Hinglish", "Hindi", "Urdu", "Arabic", "Chinese", "Japanese", "Korean",
        "Russian", "Spanish", "Portuguese", "French", "German", "Swiss German", "Italian",
        "Turkish", "Dutch", "Swedish", "Norwegian", "Danish", "Polish", "Greek", "Indonesian",
        "Malay", "Bengali", "Punjabi", "Persian", "Thai", "Vietnamese", "Ukrainian",
    )
    val TONES = listOf("Natural", "Casual", "Gen Z", "Slang", "Match Original", "Friendly", "Flirty", "Formal")
    val OUTPUTS = listOf("Native", "Romanized", "Both")
}

object Pal {
    const val BG = 0xFF111114.toInt()
    const val SHEET = 0xFF17171C.toInt()
    const val SURFACE = 0xFF212128.toInt()
    const val SURFACE2 = 0xFF2A2A33.toInt()
    const val ACCENT = 0xFF7C6CF2.toInt()
    const val ACCENT_SOFT = 0x337C6CF2
    const val TEXT = 0xFFF2F2F5.toInt()
    const val MUTED = 0xFF9A9AA6.toInt()
    const val ERROR = 0xFFFF7A7A.toInt()
    const val LINE = 0x14FFFFFF
}

class Prefs(c: Context) {
    private val sp = c.applicationContext.getSharedPreferences("float_translate", Context.MODE_PRIVATE)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var defaultFrom: String get() = s("default_from", "Hinglish"); set(v) = put("default_from", v)
    var defaultTo: String get() = s("default_to", "English"); set(v) = put("default_to", v)
    var lastFrom: String get() = s("last_from", defaultFrom); set(v) = put("last_from", v)
    var lastTo: String get() = s("last_to", defaultTo); set(v) = put("last_to", v)
    var output: String get() = s("output", "Native"); set(v) = put("output", v)
    var tone: String get() = s("tone", "Natural"); set(v) = put("tone", v)
    var bubbleEnabled: Boolean get() = b("bubble_enabled", false); set(v) = putB("bubble_enabled", v)
    var bubbleSide: String get() = s("bubble_side", "Right"); set(v) = put("bubble_side", v)
    var bubbleSize: String get() = s("bubble_size", "Medium"); set(v) = put("bubble_size", v)
    var bubbleY: Float get() = sp.getFloat("bubble_y", 0.4f); set(v) = sp.edit().putFloat("bubble_y", v).apply()
    var autoFocus: Boolean get() = b("auto_focus", true); set(v) = putB("auto_focus", v)
    var closeAfterCopy: Boolean get() = b("close_after_copy", false); set(v) = putB("close_after_copy", v)
    var rememberLangs: Boolean get() = b("remember_langs", true); set(v) = putB("remember_langs", v)

    val bubbleSizeDp: Int get() = when (bubbleSize) { "Small" -> 46; "Large" -> 64; else -> 54 }
    fun startFrom() = if (rememberLangs) lastFrom else defaultFrom
    fun startTo() = if (rememberLangs) lastTo else defaultTo
}

/** Calls the server-side translation endpoint. No API keys live in the app. */
object TranslateClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun translate(body: JSONObject, cb: (Result<String>) -> Unit): Future<*> = executor.submit {
        val result: Result<String> = try {
            val conn = URL(BuildConfig.TRANSLATE_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 90000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code in 200..299) {
                    val t = JSONObject(text).optString("translation").trim()
                    if (t.isEmpty()) Result.failure(Exception("Got an empty translation. Try again."))
                    else Result.success(t)
                } else {
                    val msg = try { JSONObject(text).optString("error") } catch (_: JSONException) { "" }
                    Result.failure(Exception(when {
                        msg.isNotBlank() -> msg
                        code == 429 -> "Too many requests. Wait a moment and try again."
                        code == 404 -> "Translation service not found. Publish the web app first."
                        else -> "Translation service error ($code). Try again."
                    }))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: UnknownHostException) {
            Result.failure(Exception("No internet connection."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("The request timed out. Try again."))
        } catch (e: IOException) {
            Result.failure(Exception("Network error. Check your connection."))
        } catch (e: JSONException) {
            Result.failure(Exception("Invalid response from the server."))
        } catch (e: Exception) {
            Result.failure(Exception("Something went wrong. Try again."))
        }
        main.post { cb(result) }
    }
}

// ---------- UI helpers ----------

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Int): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, strokeColor)
    }

fun ripple(base: Drawable): Drawable =
    RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), base, null)

val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

fun Context.label(s: String, size: Float = 15f, color: Int = Pal.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = MEDIUM
    }

fun Context.caps(s: String, color: Int = Pal.MUTED): TextView =
    label(s.uppercase(), 10.5f, color, true).apply { letterSpacing = 0.12f }

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.primaryButton(text: String): TextView = label(text, 15.5f, Pal.TEXT, true).apply {
    gravity = Gravity.CENTER
    background = ripple(rounded(Pal.ACCENT, dpf(18)))
    minHeight = dp(52)
    isClickable = true
}

/** Searchable, scrollable list used by the overlay sheet and the settings screen. */
fun Context.pickerList(items: List<String>, selected: String, searchable: Boolean, onPick: (String) -> Unit): LinearLayout {
    val ctx = this
    val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun render(q: String) {
        list.removeAllViews()
        val f = items.filter { it.contains(q.trim(), ignoreCase = true) }
        if (f.isEmpty()) list.addView(label("No match", 14f, Pal.MUTED).apply { setPadding(dp(16), dp(16), dp(16), dp(16)) })
        f.forEach { item ->
            val sel = item == selected
            val row = label(if (sel) "$item   ✓" else item, 15.5f, if (sel) Pal.ACCENT else Pal.TEXT, sel).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = ripple(rounded(0x00000000, dpf(14)))
                isClickable = true
                setOnClickListener { onPick(item) }
            }
            list.addView(row, lp(MATCH, WRAP))
        }
    }
    if (searchable) {
        val search = EditText(ctx).apply {
            hint = "Search languages"
            setHintTextColor(Pal.MUTED)
            setTextColor(Pal.TEXT)
            textSize = 15f
            isSingleLine = true
            background = rounded(Pal.SURFACE, dpf(14))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = render(s?.toString() ?: "")
            })
        }
        wrap.addView(search, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })
    }
    val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(list) }
    wrap.addView(scroll, lp(MATCH, 0, 1f))
    render("")
    wrap.tag = scroll
    return wrap
}

fun View.gone() { visibility = View.GONE }
fun View.show() { visibility = View.VISIBLE }
