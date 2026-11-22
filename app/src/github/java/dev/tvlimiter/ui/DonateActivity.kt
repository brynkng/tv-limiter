package dev.tvlimiter.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.tvlimiter.BuildConfig
import dev.tvlimiter.R

/** Shows a QR code for the Venmo donation link, since a TV can't open the link itself. */
class DonateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = column(Gravity.CENTER_HORIZONTAL)
        content.addView(label("Support TV Limiter", sizeSp = 34f, bold = true))
        content.addView(
            label(
                "TV Limiter is free and open source. If it helps your family, you can leave a tip on Venmo. Scan the code with your phone's camera.",
                sizeSp = 18f,
                colorId = R.color.muted,
            ).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(640), -2).apply { topMargin = dp(12) },
        )
        val size = dp(280)
        content.addView(
            ImageView(this).apply {
                setImageDrawable(BitmapDrawable(resources, qr(BuildConfig.DONATE_URL, size)).apply { setFilterBitmap(false) })
                background = rounded(R.color.text, radiusDp = 16)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            },
            LinearLayout.LayoutParams(size + dp(24), size + dp(24)).apply { topMargin = dp(28) },
        )
        content.addView(label("venmo.com/u/bk.ceramics", sizeSp = 18f, weight = Weight.MEDIUM).margins(this, top = 16))
        content.addView(tvButton("Done") { finish() }.margins(this, top = 28))
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(color(R.color.bg))
                addView(content, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            },
        )
    }

    private fun qr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        val dark = color(R.color.bg)
        val light = color(R.color.text)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) dark else light }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
