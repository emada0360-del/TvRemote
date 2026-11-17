package com.example.tvremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var ir: ConsumerIrManager
    private lateinit var info: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("tv", Context.MODE_PRIVATE) }
    private val btns = HashMap<String, Button>()

    private val addr = 0x01
    private var cur = -1
    private var scanning = false
    private var learn = false

    private val keys = listOf(
        "Power" to "باور", "Mute" to "كتم", "Source" to "المصدر",
        "Vol+" to "صوت +", "Vol-" to "صوت -", "CH+" to "قناة +",
        "CH-" to "قناة -", "Up" to "↑", "Home" to "Home",
        "Left" to "←", "OK" to "OK", "Right" to "→",
        "Back" to "رجوع", "Down" to "↓", "Menu" to "قائمة"
    )

    private fun nec(a: Int, c: Int): IntArray {
        val p = ArrayList<Int>()
        p += 9000; p += 4500
        val data = (a and 0xFF) or ((a.inv() and 0xFF) shl 8) or
                ((c and 0xFF) shl 16) or ((c.inv() and 0xFF) shl 24)
        for (i in 0 until 32) {
            p += 560
            p += if (((data shr i) and 1) == 1) 1690 else 560
        }
        p += 560
        return p.toIntArray()
    }

    private fun send(c: Int) {
        if (ir.hasIrEmitter()) ir.transmit(38000, nec(addr, c))
    }

    private fun show() { info.text = "address=0x01  command=0x%02X".format(cur.coerceAtLeast(0)) }

    private fun saved(id: String) = prefs.getInt("k_$id", if (id == "Power") 0x10 else -1)

    private fun refresh(id: String, ar: String) {
        val s = saved(id)
        btns[id]?.text = if (s >= 0) "$ar\n0x%02X".format(s) else ar
    }

    private val scanStep = object : Runnable {
        override fun run() {
            if (!scanning) return
            if (cur >= 255) { scanning = false; info.text = "انتهى الفحص"; return }
            cur++; send(cur); show()
            handler.postDelayed(this, 600)
        }
    }

    private fun tool(t: String, f: () -> Unit) = Button(this).apply {
        text = t; setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun row(vararg v: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 40)
        }
        info = TextView(this).apply { textSize = 20f; text = "جاهز" }
        root.addView(info)

        // أدوات الفحص
        root.addView(row(
            tool("فحص") { if (!scanning) { scanning = true; handler.post(scanStep) } },
            tool("إيقاف") { scanning = false },
            tool("السابق") { scanning = false; cur = (cur - 1).coerceAtLeast(0); send(cur); show() },
            tool("التالي") { scanning = false; cur = (cur + 1).coerceAtMost(255); send(cur); show() }
        ))

        val learnBtn = tool("وضع التعلّم: مطفي") {}
        learnBtn.setOnClickListener {
            learn = !learn
            learnBtn.text = if (learn) "وضع التعلّم: شغّال" else "وضع التعلّم: مطفي"
        }
        val power5 = tool("باور x5") {
            val c = saved("Power")
            for (i in 0 until 5) handler.postDelayed({ send(c) }, i * 350L)
        }
        root.addView(row(learnBtn, power5))

        // شبكة الأزرار
        keys.chunked(3).forEach { r ->
            val bs = r.map { (id, ar) ->
                tool(ar) {
                    if (learn) {
                        if (cur < 0) { Toast.makeText(this, "افحص أولاً", Toast.LENGTH_SHORT).show() }
                        else { prefs.edit().putInt("k_$id", cur).apply(); refresh(id, ar) }
                    } else {
                        val s = saved(id)
                        if (s >= 0) send(s) else Toast.makeText(this, "غير محفوظ", Toast.LENGTH_SHORT).show()
                    }
                }.also { btns[id] = it }
            }
            root.addView(row(*bs.toTypedArray()))
        }
        keys.forEach { (id, ar) -> refresh(id, ar) }

        setContentView(ScrollView(this).apply { addView(root) })
        if (!ir.hasIrEmitter()) info.text = "الموبايل ما بيه IR"
    }

    override fun onDestroy() { scanning = false; super.onDestroy() }
}
