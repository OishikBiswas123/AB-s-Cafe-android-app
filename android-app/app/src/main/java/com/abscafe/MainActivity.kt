package com.abscafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.api.SocketClient
import com.abscafe.navigation.AppNavGraph
import com.abscafe.ui.theme.ABsCafeTheme
import com.abscafe.util.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenManager = TokenManager(applicationContext)
        val socketClient = SocketClient()

        val savedToken = runBlocking { tokenManager.tokenFlow.first() }
        if (savedToken != null) {
            RetrofitClient.setToken(savedToken)
        }

        setContent {
            ABsCafeTheme {
                val navController = rememberNavController()

                val savedRole = runBlocking { tokenManager.userRoleFlow.first() }
                val startDest = if (savedRole != null) {
                    val dest = when (savedRole) {
                        "waiter" -> "waiter"
                        "chef" -> "chef"
                        "cashier" -> "cashier"
                        "owner" -> "owner"
                        else -> "login"
                    }
                    if (savedToken != null) socketClient.connect(savedToken)
                    dest
                } else "login"

                AppNavGraph(
                    navController = navController,
                    tokenManager = tokenManager,
                    socketClient = socketClient,
                    startDestination = startDest
                )
            }
        }
    }
}
