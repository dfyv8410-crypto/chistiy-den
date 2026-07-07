package com.chistiyen.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object NaRussiaApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Fetch JFT (Just For Today) daily meditation page */
    suspend fun fetchJft(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://na-russia.org/moskva")
                .header("User-Agent", "ChistiyDen/1.0")
                .build()
            client.newCall(request).execute().body?.string()
        } catch (e: Exception) {
            null
        }
    }

    /** Fetch group schedule */
    suspend fun fetchGroupSchedule(): String? = withContext(Dispatchers.IO) {
        try {
            // The actual API endpoint — might need adjustment
            val request = Request.Builder()
                .url("https://na-russia.org/moskva/groups")
                .header("User-Agent", "ChistiyDen/1.0")
                .build()
            client.newCall(request).execute().body?.string()
        } catch (e: Exception) {
            null
        }
    }
}
