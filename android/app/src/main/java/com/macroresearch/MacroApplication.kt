package com.macroresearch

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.local.MacroDatabase
import com.macroresearch.data.remote.MacroApi
import com.macroresearch.data.remote.MacroSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MacroApplication : Application() {
    lateinit var repository: MacroRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val gson = Gson()
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MacroApi::class.java)
        val database = Room.databaseBuilder(this, MacroDatabase::class.java, "macro.db")
            .addMigrations(MacroDatabase.MIGRATION_1_2)
            .build()
        val socket = MacroSocket(http, BuildConfig.WS_URL, gson)
        repository = MacroRepository(api, database.eventDao(), socket)
        repository.connectSocket()

        val notifications = NotificationCenter(this)
        applicationScope.launch {
            repository.socketEvents.collect { event ->
                if (repository.isFollowed(event.eventId)) notifications.show(event)
            }
        }
    }
}
