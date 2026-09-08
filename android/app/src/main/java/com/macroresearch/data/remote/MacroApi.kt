package com.macroresearch.data.remote

import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.EventDetailResponse
import com.macroresearch.data.model.MarketResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MacroApi {
    @GET("api/v1/events/upcoming")
    suspend fun upcoming(@Query("days") days: Int = 7): List<EconomicEvent>

    @GET("api/v1/calendar")
    suspend fun calendar(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("country") country: String? = null,
        @Query("minimum_importance") minimumImportance: Int? = null,
    ): List<EconomicEvent>

    @GET("api/v1/events/{id}")
    suspend fun event(@Path("id") id: Long): EventDetailResponse

    @GET("api/v1/events/{id}/analysis")
    suspend fun analysis(@Path("id") id: Long): AnalysisReport

    @GET("api/v1/events/{id}/market")
    suspend fun market(@Path("id") id: Long): MarketResponse

    @GET("api/v1/events/history")
    suspend fun history(
        @Query("country") country: String? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<EconomicEvent>
}

