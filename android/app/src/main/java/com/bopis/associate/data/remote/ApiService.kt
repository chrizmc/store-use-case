package com.bopis.associate.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("orders")
    suspend fun getOrders(@Query("store_id") storeId: String? = null): List<OrderSummary>

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") orderId: String): OrderDetail

    @GET("orders/{id}/items/{itemId}/substitutes")
    suspend fun getSubstitutes(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
    ): List<SubstituteCandidate>

    @POST("orders/{id}/items/{itemId}/pick")
    suspend fun pickItem(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): OrderItem

    @POST("orders/{id}/items/{itemId}/mark-unavailable")
    suspend fun markUnavailable(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): OrderItem

    @POST("orders/{id}/items/{itemId}/substitute")
    suspend fun substituteItem(
        @Path("id") orderId: String,
        @Path("itemId") itemId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: SubstituteRequest,
    ): OrderItem

    @GET("shelves")
    suspend fun getShelves(@Query("storeId") storeId: String): List<Shelf>

    @GET("shelves/{qr}")
    suspend fun getShelf(@Path("qr") qrCode: String): Shelf

    @POST("shelves/{qr}/report")
    suspend fun reportShelf(
        @Path("qr") qrCode: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ShelfReportRequest,
    ): InventoryRow

    @GET("sync/pull")
    suspend fun syncPull(
        @Query("since") since: Long = 0,
        @Query("store_id") storeId: String? = null,
    ): SyncPullResponse

    @POST("assistant/query")
    suspend fun assistantQuery(@Body body: AssistantQueryRequest): AssistantResponse

    @Multipart
    @POST("assistant/voice")
    suspend fun assistantVoice(
        @Part audio: MultipartBody.Part,
        @Part("storeId") storeId: RequestBody,
    ): AssistantResponse
}
