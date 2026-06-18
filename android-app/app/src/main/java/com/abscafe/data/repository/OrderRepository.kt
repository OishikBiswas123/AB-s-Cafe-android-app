package com.abscafe.data.repository

import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.model.*

class OrderRepository {

    suspend fun createOrder(tableId: Int, items: List<OrderItemInput>, isTakeaway: Boolean = false): Result<Order> {
        return try {
            val response = RetrofitClient.apiService.createOrder(
                CreateOrderRequest(tableId, items, isTakeaway)
            )
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to create order"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrders(status: String? = null): Result<List<Order>> {
        return try {
            val response = RetrofitClient.apiService.getOrders(status = status)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load orders"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addItems(orderId: Int, items: List<OrderItemInput>): Result<Order> {
        return try {
            val response = RetrofitClient.apiService.addOrderItems(orderId, AddItemsRequest(items))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to add items"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(orderId: Int, status: String): Result<Order> {
        return try {
            val response = RetrofitClient.apiService.updateOrderStatus(orderId, StatusUpdate(status))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to update status"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateItemStatus(itemId: Int, status: String): Result<ApiResponse> {
        return try {
            val response = RetrofitClient.apiService.updateOrderItemStatus(itemId, StatusUpdate(status))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to update item status"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
