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

    @GET("income")
    suspend fun getIncome(
        @Header("Authorization") bearer: String,
        @Query("date") date: String
    ): IncomeGetResponse

    @POST("income")
    suspend fun createIncome(
        @Header("Authorization") bearer: String,
        @Body body: IncomeCreateRequest
    ): IncomeCreateResponse

    @PUT("income/{id}")
    suspend fun updateIncome(
        @Header("Authorization") bearer: String,
        @Path("id") id: Int,
        @Body body: IncomeUpdateRequest
    ): ApiOkResponse
    @GET("target")
    suspend fun getActiveTarget(@Header("Authorization") bearer: String): GetTargetResponse

    @POST("targets")
    suspend fun createTarget(
        @Header("Authorization") bearer: String,
        @Body body: CreateTargetRequest
    ): CreateTargetResponse

    @PUT("targets/{id}")
    suspend fun updateTarget(
        @Header("Authorization") bearer: String,
        @Path("id") id: Int,
        @Body body: UpdateTargetRequest
    ): ApiOkResponse

    @DELETE("targets/{id}")
    suspend fun deleteTarget(
        @Header("Authorization") bearer: String,
        @Path("id") id: Int
    ): ApiOkResponse

    @GET("targets/summary")
    suspend fun getTargetsSummary(
        @Header("Authorization") bearer: String,
        @Query("month") month: String
    ): TargetsSummaryResponse

    @GET("targets/current")
    suspend fun getTargetsCurrent(
        @Header("Authorization") bearer: String
    ): TargetsCurrentGetResponse

    @PUT("targets/current")
    suspend fun putTargetsCurrent(
        @Header("Authorization") bearer: String,
        @Body req: TargetsCurrentPutRequest
    ): TargetsCurrentPutResponse


}
