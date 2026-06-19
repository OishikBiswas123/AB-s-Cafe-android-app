package com.abscafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        setContent {
            var loading by remember { mutableStateOf(true) }
            var savedToken by remember { mutableStateOf<String?>(null) }
            var savedRole by remember { mutableStateOf<String?>(null) }
            val tokenLoaded = remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val token = tokenManager.tokenFlow.first()
                val role = tokenManager.userRoleFlow.first()
                savedToken = token
                savedRole = role
                if (token != null) {
                    RetrofitClient.setToken(token)
                }
                tokenLoaded.value = true
                loading = false
            }

            ABsCafeTheme {
                if (loading) {
                    SplashScreen()
                } else {
                    val finalToken = savedToken
                    val finalRole = savedRole
                    val navController = rememberNavController()
                    val startDest = if (finalRole != null) {
                        val dest = when (finalRole) {
                            "waiter" -> "waiter"
                            "chef" -> "chef"
                            "cashier" -> "cashier"
                            "owner" -> "owner"
                            else -> "login"
                        }
                        if (finalToken != null) socketClient.connect(finalToken)
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
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.abs_cafe_logo),
                contentDescription = "AB's Cafe Logo",
                modifier = Modifier.size(180.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(modifier = Modifier.width(200.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "made by oishik biswas",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                textAlign = TextAlign.Center
            )
        }
    }
}
