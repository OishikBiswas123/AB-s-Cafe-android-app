package com.abscafe.data.repository

import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.model.LoginRequest
import com.abscafe.data.model.User

class AuthRepository {

    suspend fun login(email: String, password: String): Result<Triple<String, User, String>> {
        return try {
            val response = RetrofitClient.apiService.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                RetrofitClient.setToken(body.token)
                Result.success(Triple(body.token, body.user, body.user.role))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Login failed"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        RetrofitClient.setToken(null)
    }
}
