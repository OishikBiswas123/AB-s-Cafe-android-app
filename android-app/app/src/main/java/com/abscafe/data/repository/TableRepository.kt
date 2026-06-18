package com.abscafe.data.repository

import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.model.Table

class TableRepository {

    suspend fun getTables(): Result<List<Table>> {
        return try {
            val response = RetrofitClient.apiService.getTables()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load tables"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTableStatus(tableId: Int, status: String): Result<Unit> {
        return try {
            val response = RetrofitClient.apiService.updateTableStatus(tableId, mapOf("status" to status))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update table"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
