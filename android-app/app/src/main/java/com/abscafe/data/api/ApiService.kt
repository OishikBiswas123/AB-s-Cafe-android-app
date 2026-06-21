package com.abscafe.data.api

import com.abscafe.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/tables")
    suspend fun getTables(): Response<List<Table>>

    @PATCH("api/tables/{id}/status")
    suspend fun updateTableStatus(@Path("id") id: Int, @Body body: Map<String, String>): Response<ApiResponse>

    @POST("api/tables")
    suspend fun createTable(@Body body: Map<String, Int>): Response<ApiResponse>

    @DELETE("api/tables/{id}")
    suspend fun deleteTable(@Path("id") id: Int): Response<ApiResponse>

    @GET("api/menu/categories")
    suspend fun getCategories(): Response<List<Category>>

    @GET("api/menu/items")
    suspend fun getMenuItems(@Query("category_id") categoryId: Int? = null): Response<List<MenuItem>>

    @POST("api/menu/items")
    suspend fun createMenuItem(@Body body: MenuItemRequest): Response<ApiResponse>

    @PUT("api/menu/items/{id}")
    suspend fun updateMenuItem(@Path("id") id: Int, @Body body: MenuItemRequest): Response<ApiResponse>

    @DELETE("api/menu/items/{id}")
    suspend fun deleteMenuItem(@Path("id") id: Int): Response<ApiResponse>

    @POST("api/orders")
    suspend fun createOrder(@Body request: CreateOrderRequest): Response<Order>

    @GET("api/orders")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("table_id") tableId: Int? = null
    ): Response<List<Order>>

    @GET("api/orders/{id}")
    suspend fun getOrder(@Path("id") id: Int): Response<Order>

    @POST("api/orders/{id}/items")
    suspend fun addOrderItems(@Path("id") id: Int, @Body request: AddItemsRequest): Response<Order>

    @PATCH("api/orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Int, @Body body: StatusUpdate): Response<Order>

    @PATCH("api/orders/items/{itemId}/status")
    suspend fun updateOrderItemStatus(@Path("itemId") itemId: Int, @Body body: StatusUpdate): Response<ApiResponse>

    @POST("api/payments/{orderId}/pay")
    suspend fun payOrder(@Path("orderId") orderId: Int, @Body request: PaymentRequest): Response<PaymentResponse>

    @GET("api/reports/{period}")
    suspend fun getReport(@Path("period") period: String): Response<SalesReport>

    @GET("api/admin/users")
    suspend fun getUsers(): Response<List<User>>

    @POST("api/admin/users")
    suspend fun createUser(@Body body: Map<String, String>): Response<ApiResponse>

    @DELETE("api/admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<ApiResponse>

    @PATCH("api/admin/users/{id}/reset-password")
    suspend fun resetPassword(@Path("id") id: Int, @Body body: Map<String, String>): Response<ApiResponse>

    @DELETE("api/admin/clear-data")
    suspend fun clearAllData(): Response<ApiResponse>
}
