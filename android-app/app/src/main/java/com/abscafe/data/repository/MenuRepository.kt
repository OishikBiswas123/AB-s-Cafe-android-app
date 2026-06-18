package com.abscafe.data.repository

import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.model.Category
import com.abscafe.data.model.MenuItem

class MenuRepository {

    suspend fun getCategories(): Result<List<Category>> {
        return try {
            val response = RetrofitClient.apiService.getCategories()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load categories"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMenuItems(categoryId: Int? = null): Result<List<MenuItem>> {
        return try {
            val response = RetrofitClient.apiService.getMenuItems(categoryId)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load menu"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
