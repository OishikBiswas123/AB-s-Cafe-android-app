package com.abscafe.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.api.SocketClient
import com.abscafe.data.repository.*
import com.abscafe.ui.cashier.CashierScreen
import com.abscafe.ui.chef.ChefScreen
import com.abscafe.ui.login.LoginScreen
import com.abscafe.ui.owner.OwnerScreen
import com.abscafe.ui.waiter.WaiterScreen
import com.abscafe.util.TokenManager

object Routes {
    const val LOGIN = "login"
    const val WAITER = "waiter"
    const val CHEF = "chef"
    const val CASHIER = "cashier"
    const val OWNER = "owner"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    tokenManager: TokenManager,
    socketClient: SocketClient,
    startDestination: String = Routes.LOGIN
) {
    val authRepo = AuthRepository()
    val menuRepo = MenuRepository()
    val orderRepo = OrderRepository()
    val tableRepo = TableRepository()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                tokenManager = tokenManager,
                authRepo = authRepo,
                onLoginSuccess = { role, token ->
                    RetrofitClient.setToken(token)
                    socketClient.connect(token)
                    val destination = when (role) {
                        "waiter" -> Routes.WAITER
                        "chef" -> Routes.CHEF
                        "cashier" -> Routes.CASHIER
                        "owner" -> Routes.OWNER
                        else -> Routes.WAITER
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.WAITER) {
            WaiterScreen(
                menuRepo = menuRepo,
                orderRepo = orderRepo,
                tableRepo = tableRepo,
                socketClient = socketClient,
                onLogout = {
                    CoroutineScope(Dispatchers.Main).launch { tokenManager.clear() }
                    RetrofitClient.setToken(null)
                    socketClient.disconnect()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.CHEF) {
            ChefScreen(
                orderRepo = orderRepo,
                socketClient = socketClient,
                onLogout = {
                    CoroutineScope(Dispatchers.Main).launch { tokenManager.clear() }
                    RetrofitClient.setToken(null)
                    socketClient.disconnect()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.CASHIER) {
            CashierScreen(
                orderRepo = orderRepo,
                tableRepo = tableRepo,
                socketClient = socketClient,
                onLogout = {
                    CoroutineScope(Dispatchers.Main).launch { tokenManager.clear() }
                    RetrofitClient.setToken(null)
                    socketClient.disconnect()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.OWNER) {
            OwnerScreen(
                menuRepo = menuRepo,
                orderRepo = orderRepo,
                tableRepo = tableRepo,
                onLogout = {
                    CoroutineScope(Dispatchers.Main).launch { tokenManager.clear() }
                    RetrofitClient.setToken(null)
                    socketClient.disconnect()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
    }
}
