package com.agro.interviewer.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

data class TokenApiRequest(
    val channelName: String,
    val uid: Int = 0,
    val role: String = "publisher"
)

data class TokenApiResponse(
    val token: String,
    val appId: String? = null,
    val channelName: String,
    val uid: Int,
    val expiresAt: String? = null,
    val expiresIn: Int? = 3600
)

interface TokenApiService {

    @POST("api/agora/token")
    suspend fun getToken(
        @Body request: TokenApiRequest
    ): TokenApiResponse
}
