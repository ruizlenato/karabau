package com.ruizlenato.karabau.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ruizlenato.karabau.MainActivity

class OpenInKarabauShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_SHARED_URL, selectedText)
            }
        )
        finish()
    }
}
