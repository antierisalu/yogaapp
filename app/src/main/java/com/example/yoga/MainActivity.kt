package com.example.yoga

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {
    private val TAG = "YogaApp"
    private val SERVER_URL = "https://yoga.matansa.ee"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resolveAndLaunchVideo()
    }

    private fun resolveAndLaunchVideo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(SERVER_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val html = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                Log.d(TAG, "Raw HTML Response:\n$html")

                val extractedUrl = parseRedirectUrl(html)
                    ?: throw Exception("No redirect URL found in HTML")

                // Decode HTML entities and URL encoding
                val decodedUrl = URLDecoder.decode(extractedUrl, "UTF-8")
                    .replace("&amp;", "&") // Fix common HTML entity issue

                Log.d(TAG, "Decoded URL: $decodedUrl")

                if (!isValidYouTubeUrl(decodedUrl)) {
                    throw Exception("Invalid YouTube URL format: $decodedUrl")
                }

                runOnUiThread { launchVideoPlayer(decodedUrl) }

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                runOnUiThread { showError(e.message ?: "Unknown error") }
            }
        }
    }

    private fun parseRedirectUrl(html: String): String? {
        // Enhanced regex to handle various formatting scenarios
        val regex = Regex("""window\.location\.replace\(['"]?([^'")]*)['"]?\)""")
        return regex.find(html)?.groups?.get(1)?.value
    }

    private fun isValidYouTubeUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            when (uri.host) {
                "www.youtube.com", "youtube.com", "youtu.be" ->
                    uri.path?.contains("/watch") == true || uri.path?.startsWith("/embed") == true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun launchVideoPlayer(url: String) {
        try {
            // Extract both video ID and playlist index
            val videoId = url.substringAfter("v=").substringBefore("&").take(11)
            val playlistIndex = if ("index=" in url) {
                url.substringAfter("index=").substringBefore("&").toIntOrNull() ?: 0
            } else 0

            // SmartTube-specific deep link format
            val smartTubeUri = Uri.parse("https://youtube.com/watch?v=$videoId").buildUpon()
                .appendQueryParameter("list", url.substringAfter("list=").substringBefore("&"))
                .appendQueryParameter("index", playlistIndex.toString())
                .appendQueryParameter("autoplay", "1")
                .build()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = smartTubeUri
                `package` = "com.liskovsoft.videomanager"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // SmartTube magic parameters
                putExtra("force_mode", "direct_play")
                putExtra("play_now", true)
                putExtra("playlist_start_index", playlistIndex)
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback with forced autoplay
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$url&autoplay=1")))
            }
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "SmartTube installation required!", Toast.LENGTH_LONG).show()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}