package eu.kanade.tachiyomi.animeextension.en.hstream

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

class HstreamUrlActivity : Activity() {
    private val tag = HstreamUrlActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        val intent = Intent().apply {
            action = "eu.kanade.tachiyomi.ANIMESEARCH"
            putExtra("query", getIntent().data.toString())
            putExtra("filter", packageName)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(tag, "Unable to launch activity", e)
        }
        finish()
    }
}
