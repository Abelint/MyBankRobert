package com.robert.mybank.server

import retrofit2.http.*

interface ApiService {

    @POST("register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("confirm")
    suspend fun confirm(@Body body: ConfirmRequest): ConfirmResponse

    @POST("login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("categories")
    suspend fun getCategories(@Header("Authorization") bearer: String): CategoriesResponse

    @POST("categories")
    suspend fun createCategory(
        @Header("Authorization") bearer: String,
        @Body body: CreateCategoryRequest
    ): CreateCategoryResponse

    // "отвязать" категорию от пользователя
    @DELETE("categories/{id}")
    suspend fun unlinkCategory(
        @Header("Authorization") bearer: String,
        @Path("id") id: Int
    ): ApiOkResponse

    @GET("records")
    suspend fun getRecords(
        @Header("Authorization") bearer: String,
        @Query("date") date: String
    ): RecordsResponse

    @POST("records")
    suspend fun createRecords(
        @Header("Authorization") bearer: String,
        @Body body: CreateRecordsRequest
    ): CreateRecordsResponse

}
