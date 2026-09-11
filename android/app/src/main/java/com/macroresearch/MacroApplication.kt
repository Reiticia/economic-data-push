package com.macroresearch

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import com.google.gson.Gson
import com.macroresearch.data.CountryPreferences
import com.macroresearch.data.LocalAnalysisEngine
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.MarketPreferences
import com.macroresearch.data.TranslationPreferences
import com.macroresearch.data.local.MacroDatabase
import com.macroresearch.data.remote.AiAnalysisClient
import com.macroresearch.data.remote.DirectMarketClient
import com.macroresearch.data.remote.EconomicCalendarClient
import com.macroresearch.data.remote.TranslationClient
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class MacroApplication : Application() {
    lateinit var repository: MacroRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val translationPreferences = TranslationPreferences(this)
        if (!translationPreferences.settings.value.configured) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }

        val gson = Gson()
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val http = OkHttpClient.Builder()
            .cache(Cache(cacheDir.resolve("http"), 20L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "MacroResearch/0.2 (Android; personal research)")
                        .build(),
                )
            }
            .addInterceptor(logging)
            .build()
        val translationHttp = http.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .build()
        val database = Room.databaseBuilder(this, MacroDatabase::class.java, "macro.db")
            .addMigrations(MacroDatabase.MIGRATION_1_2, MacroDatabase.MIGRATION_2_3, MacroDatabase.MIGRATION_3_4)
            .build()
        repository = MacroRepository(
            calendarClient = EconomicCalendarClient(http),
            marketClient = DirectMarketClient(http),
            translationClient = TranslationClient(translationHttp, gson),
            aiAnalysisClient = AiAnalysisClient(translationHttp, gson),
            analysisEngine = LocalAnalysisEngine(),
            dao = database.eventDao(),
            analysisDao = database.analysisDao(),
            countryPreferences = CountryPreferences(this),
            marketPreferences = MarketPreferences(this),
            translationPreferences = translationPreferences,
        )
    }
}
