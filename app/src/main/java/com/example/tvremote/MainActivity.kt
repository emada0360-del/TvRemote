package com.example.tvremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var ir: ConsumerIrManager
    private lateinit var info: TextView
    private lateinit var saved: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("tv", Context.MODE_PRIVATE) }

    private var scanning = false
    private var addr = 0
    private var cmd = 0

    private fun nec(a: Int, c: Int): IntArray {
        val p = ArrayList<Int>()
        p += 9000; p += 4500
        val data = (a and 0xFF) or
                ((a.inv() and 0xFF) shl 8) or
                ((c and 0xFF) shl 16) or
                ((c.inv() and 0xFF) shl 24)
        for (i in 0 until 32) {
            p += 560
            p += if (((data shr i) and 1) == 1) 1690 else 560
        }
        p += 560
        return p.toIntArray()
    }

    private fun send(a: Int, c: Int) {
        if (ir.hasIrEmitter()) ir.transmit(38000, nec(a, c))
    }

    private fun label(a: Int, c: Int) = "address=0x%02X  command=0x%02X".format(a, c)

    private val scanStep = object : Runnable {
        override fun run() {
            if (!scanning) return
            send(addr, cmd)
            info.text = label(addr, cmd)
            cmd++
            if (cmd > 255) { cmd = 0; addr++ }
            if (addr > 255) { scanning = false; info.text = "انتهى الفحص"; return }
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 80, 40, 40)
        }

        info = TextView(this).apply { textSize = 20f; text = "جاهز" }
        saved = TextView(this).apply {
            textSize = 16f
            text = "المحفوظ: " + label(prefs.getInt("a", 0), prefs.getInt("c", 0))
        }

        fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { onClick() }
        }

        val start = btn("جرّب الأكواد") {
            if (!scanning) { scanning = true; handler.post(scanStep) }
        }
        val stop = btn("إيقاف") { scanning = false }
        val back = btn("السابق") {
            scanning = false
            cmd--
            if (cmd < 0) { cmd = 255; addr = (addr - 1).coerceAtLeast(0) }
            send(addr, cmd); info.text = label(addr, cmd)
        }
        val save = btn("حفظ هذا الكود") {
            var a = addr; var c = cmd - 1
            if (c < 0) { c = 255; a = (a - 1).coerceAtLeast(0) }
            prefs.edit().putInt("a", a).putInt("c", c).apply()
            saved.text = "المحفوظ: " + label(a, c)
        }
        val power = btn("Power (المحفوظ)") {
            send(prefs.getInt("a", 0), prefs.getInt("c", 0))
        }

        listOf(info, start, stop, back, save, power, saved).forEach { root.addView(it) }
        setContentView(root)

        if (!ir.hasIrEmitter()) info.text = "الموبايل ما بيه IR"
    }

    override fun onDestroy() {
        scanning = false
        super.onDestroy()
    }
}
