package app.kanata.anilist

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.kanata.loadIsMAL
import app.kanata.loadMedia
import app.kanata.logError
import app.kanata.startMainActivity

class UrlMedia : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data: Uri? = intent?.data
        if (data?.host != "anilist.co") loadIsMAL = true
        try {
            if (data?.pathSegments?.get(1) != null) loadMedia = data.pathSegments?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            logError(e)
        }
        startMainActivity(this)
    }
}