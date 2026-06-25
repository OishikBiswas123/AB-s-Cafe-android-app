package com.abscafe.ui.owner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
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
import com.abscafe.data.repository.MenuRepository
import com.abscafe.data.repository.OrderRepository
import com.abscafe.data.repository.TableRepository
import com.abscafe.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerScreen(
    menuRepo: MenuRepository,
    orderRepo: OrderRepository,
    tableRepo: TableRepository,
    onLogout: () -> Unit,
    socketClient: SocketClient? = null
) {
    var currentTab by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val tabs = listOf("Dashboard", "Menu", "Tables", "Users")

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = { Button(onClick = { showLogoutDialog = false; onLogout() }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("No") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Dashboard") },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) { Icon(Icons.Default.Logout, "Logout") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = currentTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (currentTab) {
                0 -> DashboardTab(menuRepo)
                1 -> MenuManagementTab(menuRepo, socketClient)
                2 -> TableManagementTab(tableRepo)
                3 -> UserManagementTab()
            }
        }
    }
}

@Composable
fun DashboardTab(menuRepo: MenuRepository) {
    var report by remember { mutableStateOf<SalesReport?>(null) }
    var selectedPeriod by remember { mutableStateOf("daily") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadReport(period: String) {
        scope.launch {
            loading = true
            try {
                val response = RetrofitClient.apiService.getReport(period)
                if (response.isSuccessful) report = response.body()
            } catch (_: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(selectedPeriod) { loadReport(selectedPeriod) }

    var showClearData by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("daily", "weekly", "monthly").forEach { period ->
                    item {
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = { Text(period.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        if (loading) {
            item { Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (report != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Summary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem("Total Sales", "₹${report!!.sales.totalSales}", Primary)
                            StatItem("Orders", "${report!!.sales.totalOrders}", Secondary)
                            StatItem("Avg Order", "₹${report!!.sales.avgOrderValue}", Available)
                        }
                    }
                }
            }

            if (report!!.topItems.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Top Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            report!!.topItems.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.name}")
                                    Text("${item.quantity}x - ₹${item.revenue}", fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (report!!.byTable.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("By Table", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            report!!.byTable.forEach { t ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Table ${t.tableNumber}")
                                    Text("${t.orders} orders - ₹${t.revenue}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showClearData = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Clear All Data") }
        }
    }

    if (showClearData) {
        AlertDialog(
            onDismissRequest = { showClearData = false },
            title = { Text("Clear All Data") },
            text = { Text("This will permanently delete all orders and payments. This action cannot be undone. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearData = false
                        scope.launch {
                            try { RetrofitClient.apiService.clearAllData() } catch (_: Exception) {}
                            loadReport(selectedPeriod)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Yes, Clear Everything") }
            },
            dismissButton = { TextButton(onClick = { showClearData = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun MenuManagementTab(menuRepo: MenuRepository, socketClient: SocketClient? = null) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun load() {
        scope.launch {
            loading = true
            menuRepo.getCategories().onSuccess { categories = it }
            menuRepo.getMenuItems().onSuccess { menuItems = it }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                                                    menuItems = menuItems.map {
                                                        if (it.id == item.id) it.copy(available = newAvailable)
                                                        else it
                                                    }
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
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            var name by remember { mutableStateOf("") }
            var price by remember { mutableStateOf("") }
            var catId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 1) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Menu Item") },
                text = {
                    Column {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        categories.forEach { cat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = catId == cat.id, onClick = { catId = cat.id })
                                Text(cat.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            try {
                                val resp = RetrofitClient.apiService.createMenuItem(
                                    MenuItemRequest(
                                        name = name,
                                        price = price.toDoubleOrNull() ?: 0.0,
                                        categoryId = catId
                                    )
                                )
                                if (resp.isSuccessful) {
                                    showAddDialog = false
                                    load()
                                } else {
                                    snackbarHostState.showSnackbar("Failed to add item")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Network error")
                            }
                        }
                    }) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
fun TableManagementTab(tableRepo: TableRepository) {
    var tables by remember { mutableStateOf<List<Table>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newTableNumber by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun load() {
        scope.launch { loading = true; tableRepo.getTables().onSuccess { tables = it }; loading = false }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true; newTableNumber = "" }) {
                Icon(Icons.Default.Add, "Add Table")
            }
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tables) { table ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.size(40.dp), shape = MaterialTheme.shapes.small, color = if (table.status == "available") Available.copy(alpha = 0.2f) else Occupied.copy(alpha = 0.2f)) {
                                    Box(contentAlignment = Alignment.Center) { Text("T${table.number}", fontWeight = FontWeight.Bold, color = if (table.status == "available") Available else Occupied) }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(table.status.replaceFirstChar { it.uppercase() })
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    RetrofitClient.apiService.deleteTable(table.id)
                                    load()
                                }
                            }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Table") },
                text = {
                    Column {
                        Text("Add 5 tables (next available numbers)", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val maxNum = tables.maxOfOrNull { it.number } ?: 0
                                    for (i in 1..5) {
                                        try {
                                            RetrofitClient.apiService.createTable(mapOf("number" to (maxNum + i)))
                                        } catch (_: Exception) {}
                                    }
                                    showAddDialog = false
                                    load()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add 5 Tables") }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Or add a custom table number:", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(value = newTableNumber, onValueChange = { newTableNumber = it }, label = { Text("Table Number") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val num = newTableNumber.toIntOrNull()
                        if (num == null || num <= 0) {
                            scope.launch { snackbarHostState.showSnackbar("Enter a valid table number") }
                        } else {
                            scope.launch {
                                try {
                                    val resp = RetrofitClient.apiService.createTable(mapOf("number" to num))
                                    if (resp.isSuccessful) {
                                        showAddDialog = false
                                        load()
                                    } else {
                                        val err = resp.errorBody()?.string() ?: "Table may already exist"
                                        snackbarHostState.showSnackbar("Failed: $err")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Network error")
                                }
                            }
                        }
                    }) { Text("Add Table") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
fun UserManagementTab() {
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }
    var editName by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }
    var editRole by remember { mutableStateOf("waiter") }
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("staff123") }
    var newRole by remember { mutableStateOf("waiter") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun load() {
        scope.launch {
            loading = true
            try { val r = RetrofitClient.apiService.getUsers(); if (r.isSuccessful) users = r.body() ?: emptyList() } catch (_: Exception) {}
            loading = false
        }
    }

    fun openEdit(user: User) {
        editingUser = user
        editName = user.name
        editEmail = user.email
        editPassword = ""
        editRole = user.role
        showEditDialog = true
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.PersonAdd, "Add User") }
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users) { user ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Medium)
                                Text(user.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                    Text(user.role.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            val isDefaultUser = user.email in listOf("owner@abscafe.com", "waiter@abscafe.com", "chef@abscafe.com", "cashier@abscafe.com", "drinks@abscafe.com")
                            Row {
                                IconButton(onClick = { openEdit(user) }) { Icon(Icons.Default.Edit, "Edit") }
                                if (!isDefaultUser) {
                                    var showDeleteConfirm by remember { mutableStateOf(false) }
                                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                                    if (showDeleteConfirm) {
                                        AlertDialog(
                                            onDismissRequest = { showDeleteConfirm = false },
                                            title = { Text("Delete User?") },
                                            text = { Text("Are you sure you want to delete ${user.name} (${user.email})?") },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    showDeleteConfirm = false
                                                    scope.launch {
                                                        RetrofitClient.apiService.deleteUser(user.id)
                                                        load()
                                                    }
                                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add User") },
                text = {
                    Column {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newEmail, onValueChange = { newEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf("waiter", "chef", "cashier").forEach { role ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = newRole == role, onClick = { newRole = role })
                                Text(role.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            try {
                                val resp = RetrofitClient.apiService.createUser(mapOf("name" to newName, "email" to newEmail, "password" to newPassword, "role" to newRole))
                                if (resp.isSuccessful) {
                                    showAddDialog = false
                                    load()
                                } else {
                                    val err = resp.errorBody()?.string() ?: "Failed to add user"
                                    snackbarHostState.showSnackbar("Error: $err")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Network error")
                            }
                        }
                    }) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
            )
        }

        if (showEditDialog && editingUser != null) {
            val user = editingUser!!
            AlertDialog(
                onDismissRequest = { showEditDialog = false; editingUser = null },
                title = { Text("Edit User") },
                text = {
                    Column {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = editEmail, onValueChange = { editEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = editPassword, onValueChange = { editPassword = it }, label = { Text("New Password (leave blank to keep)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf("owner", "waiter", "chef", "drinks_chef", "cashier").forEach { role ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = editRole == role, onClick = { editRole = role })
                                Text(role.replace("_", " ").replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            try {
                                val body = mutableMapOf("name" to editName, "email" to editEmail, "role" to editRole)
                                if (editPassword.isNotBlank()) body["password"] = editPassword
                                val resp = RetrofitClient.apiService.updateUser(user.id, body)
                                if (resp.isSuccessful) {
                                    showEditDialog = false
                                    editingUser = null
                                    snackbarHostState.showSnackbar("User updated!")
                                    load()
                                } else {
                                    val err = resp.errorBody()?.string() ?: "Failed to update user"
                                    snackbarHostState.showSnackbar("Error: $err")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Network error")
                            }
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showEditDialog = false; editingUser = null }) { Text("Cancel") } }
            )
        }
    }
}
