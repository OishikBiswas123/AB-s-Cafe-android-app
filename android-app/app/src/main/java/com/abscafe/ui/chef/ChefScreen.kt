package com.abscafe.ui.chef

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.api.SocketClient
import com.abscafe.data.model.*
import com.abscafe.data.repository.OrderRepository
import com.abscafe.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefScreen(
    orderRepo: OrderRepository,
    socketClient: SocketClient,
    onLogout: () -> Unit
) {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadOrders() {
        scope.launch {
            loading = true
            orderRepo.getOrders().onSuccess { orders = it }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadOrders()

        socketClient.on("order:new") { loadOrders() }
        socketClient.on("order:modified") { loadOrders() }
        socketClient.on("menu:updated") { loadOrders() }
    }

    DisposableEffect(Unit) {
        onDispose {
            socketClient.off("order:new")
            socketClient.off("order:modified")
            socketClient.off("menu:updated")
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = { Button(onClick = { showLogoutDialog = false; onLogout() }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("No") } }
        )
    }

    val filteredOrders = when (filterStatus) {
        null -> orders.filter { it.status in listOf("pending", "preparing", "ready") }
        else -> orders.filter { it.status == filterStatus }
    }

    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf("Orders", "Menu")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentTab == 0) "Kitchen Orders" else "Menu Items") },
                actions = {
                    IconButton(onClick = { loadOrders() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { showLogoutDialog = true }) { Icon(Icons.Default.Logout, "Logout") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = currentTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = currentTab == index, onClick = { currentTab = index }, text = { Text(title) })
                }
            }

            if (currentTab == 1) {
                ChefMenuManagementTab(snackbarHostState, socketClient)
            } else {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = filterStatus == null, onClick = { filterStatus = null }, label = { Text("All") })
                    }
                    listOf("pending", "preparing", "ready").forEach { status ->
                        item {
                            FilterChip(
                                selected = filterStatus == status,
                                onClick = { filterStatus = status },
                                label = { Text(status.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Fastfood, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No orders", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredOrders) { order ->
                            KitchenOrderCard(
                                order = order,
                                onUpdateOrderStatus = { status ->
                                    scope.launch {
                                        orderRepo.updateOrderStatus(order.id, status).onSuccess {
                                            val json = JSONObject().apply {
                                                put("order_id", order.id)
                                                put("status", status)
                                                put("table_id", order.tableId)
                                            }
                                            socketClient.emit("order:status", json)
                                            loadOrders()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KitchenOrderCard(
    order: Order,
    onUpdateOrderStatus: (String) -> Unit
) {
    val statusColor = when (order.status) {
        "pending" -> PendingColor
        "preparing" -> PreparingColor
        "ready" -> ReadyColor
        else -> PaidColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Order #${order.id} - Table ${order.tableNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        order.status.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (order.isTakeaway) {
                Text("🥡 Takeaway", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.quantity}x ${item.name}", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        if (item.addons.isNotEmpty()) {
                            Text(item.addons.joinToString(", ") { it.name }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        if (item.notes.isNotBlank()) {
                            Text("Note: ${item.notes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                    Surface(
                        color = when (item.status) {
                            "pending" -> PendingColor.copy(alpha = 0.2f)
                            "preparing" -> PreparingColor.copy(alpha = 0.2f)
                            else -> ReadyColor.copy(alpha = 0.2f)
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            item.status.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (item.status) {
                                "pending" -> PendingColor
                                "preparing" -> PreparingColor
                                else -> ReadyColor
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (order.items.any { it.status != "ready" }) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onUpdateOrderStatus("preparing") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PreparingColor)
                ) { Text("Mark All Preparing", color = OnPrimary) }
            } else if (order.status != "ready") {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onUpdateOrderStatus("ready") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ReadyColor)
                ) { Text("Ready to Serve", color = OnPrimary) }
            }
        }
    }
}

@Composable
fun ChefMenuManagementTab(snackbarHostState: SnackbarHostState, socketClient: com.abscafe.data.api.SocketClient? = null) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            try {
                val catResponse = RetrofitClient.apiService.getCategories()
                if (catResponse.isSuccessful) categories = catResponse.body() ?: emptyList()
                val itemResponse = RetrofitClient.apiService.getMenuItems()
                if (itemResponse.isSuccessful) menuItems = itemResponse.body() ?: emptyList()
            } catch (_: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
        socketClient?.on("menu:updated") { load() }
    }

    DisposableEffect(Unit) {
        onDispose { socketClient?.off("menu:updated") }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All") }) }
            items(categories) { cat -> FilterChip(selected = selectedCategory == cat.id, onClick = { selectedCategory = cat.id }, label = { Text(cat.name) }) }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = if (selectedCategory != null) menuItems.filter { it.categoryId == selectedCategory } else menuItems
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                Text("₹${item.price} · ${categories.find { it.id == item.categoryId }?.name ?: ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Switch(
                                checked = item.available,
                                onCheckedChange = { newAvailable ->
                                    scope.launch {
                                        try {
                                            val resp = RetrofitClient.apiService.updateMenuItem(
                                                item.id,
                                                MenuItemRequest(
                                                    name = item.name,
                                                    price = item.price,
                                                    categoryId = item.categoryId,
                                                    description = item.description,
                                                    available = newAvailable
                                                )
                                            )
                                            if (resp.isSuccessful) {
                                                menuItems = menuItems.map { if (it.id == item.id) it.copy(available = newAvailable) else it }
                                                socketClient?.emit("menu:updated", org.json.JSONObject().apply { put("itemId", item.id); put("available", newAvailable) })
                                            } else {
                                                val errBody = resp.errorBody()?.string() ?: "Unknown"
                                                snackbarHostState.showSnackbar("API error: ${resp.code()} - $errBody")
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Exception: ${e.message}")
                                        }
                                    }
                                }
                            )
                        }
                        if (!item.available) {
                            Text("Currently Unavailable", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                }
                    }
                }
            }
            }
        }
    }
