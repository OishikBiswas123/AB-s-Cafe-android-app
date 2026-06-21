package com.abscafe.ui.waiter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abscafe.data.api.SocketClient
import com.abscafe.data.model.*
import com.abscafe.data.repository.MenuRepository
import com.abscafe.data.repository.OrderRepository
import com.abscafe.data.repository.TableRepository
import com.abscafe.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiterScreen(
    menuRepo: MenuRepository,
    orderRepo: OrderRepository,
    tableRepo: TableRepository,
    socketClient: SocketClient,
    onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("tables") }
    var selectedTable by remember { mutableStateOf<Table?>(null) }
    var tables by remember { mutableStateOf<List<Table>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var activeOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var isTakeaway by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var addItemMode by remember { mutableStateOf(false) }
    var selectedOrderForAdd by remember { mutableStateOf<Order?>(null) }
    var showAddItemMenu by remember { mutableStateOf(false) }
    var addItemCart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadData() {
        scope.launch {
            loading = true
            tableRepo.getTables().onSuccess { tables = it }
            menuRepo.getCategories().onSuccess { categories = it }
            menuRepo.getMenuItems().onSuccess { menuItems = it }
            orderRepo.getOrders().onSuccess { activeOrders = it }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
        socketClient.on("menu:updated") { loadData() }
        socketClient.on("order:update") { loadData() }
    }

    DisposableEffect(Unit) {
        onDispose {
            socketClient.off("menu:updated")
            socketClient.off("order:update")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            addItemMode -> "Add Items - Table ${selectedOrderForAdd?.tableNumber}"
                            showCart -> "Cart - Table ${selectedTable?.number}"
                            currentScreen == "tables" -> "AB's Cafe"
                            else -> "Table ${selectedTable?.number}"
                        }
                    )
                },
                navigationIcon = {
                    if (currentScreen != "tables" || showCart || addItemMode) {
                        IconButton(onClick = {
                            if (addItemMode) { addItemMode = false; showAddItemMenu = false; addItemCart = emptyList() }
                            else if (showCart) { showCart = false; cart = emptyList() }
                            else if (currentScreen == "menu") { currentScreen = "tables" }
                        }) { Icon(Icons.Default.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (currentScreen == "tables") {
                        IconButton(onClick = { showLogoutDialog = true }) { Icon(Icons.Default.Logout, "Logout") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                addItemMode -> AddItemMenuScreen(
                    categories = categories,
                    menuItems = menuItems,
                    selectedCategory = selectedCategory,
                    cart = addItemCart,
                    onSelectCategory = { selectedCategory = it },
                    onQuantityChange = { item, delta ->
                        addItemCart = addItemCart.mapNotNull { if (it.menuItem.id == item.id) { val nq = it.quantity + delta; if (nq <= 0) null else it.copy(quantity = nq) } else it }
                        if (addItemCart.none { it.menuItem.id == item.id }) {
                            addItemCart = addItemCart + CartItem(item)
                        }
                    },
                    onAddCheese = { item ->
                        addItemCart = addItemCart.map { if (it.menuItem.id == item.id) it.copy(addExtraCheese = !it.addExtraCheese) else it }
                    },
                    onSubmit = {
                        scope.launch {
                            val items = addItemCart.map { it.toOrderItemInput() }
                            orderRepo.addItems(selectedOrderForAdd!!.id, items).onSuccess { order ->
                                val json = JSONObject().apply {
                                    put("order_id", order.id)
                                    put("table_id", order.tableId)
                                    put("items", JSONArray())
                                }
                                socketClient.emit("order:add-items", json)
                                snackbarHostState.showSnackbar("Items added!")
                                addItemMode = false; addItemCart = emptyList(); showAddItemMenu = false
                                loadData()
                            }.onFailure { snackbarHostState.showSnackbar("Failed to add items") }
                        }
                    },
                    onCancel = { addItemMode = false; addItemCart = emptyList() }
                )

                showCart -> CartScreen(
                    cart = cart,
                    isTakeaway = isTakeaway,
                    onQuantityChange = { itemId, delta ->
                        cart = cart.mapNotNull { if (it.menuItem.id == itemId) { val nq = it.quantity + delta; if (nq <= 0) null else it.copy(quantity = nq) } else it }
                    },
                    onRemoveItem = { itemId -> cart = cart.filterNot { it.menuItem.id == itemId } },
                    onToggleCheese = { itemId ->
                        cart = cart.map { if (it.menuItem.id == itemId) it.copy(addExtraCheese = !it.addExtraCheese) else it }
                    },
                    onToggleTakeaway = { isTakeaway = !isTakeaway },
                    onPlaceOrder = {
                        scope.launch {
                            val items = cart.map { it.toOrderItemInput() }
                            orderRepo.createOrder(selectedTable!!.id, items, isTakeaway).onSuccess { order ->
                                val json = JSONObject().apply {
                                    put("order_id", order.id)
                                    put("table_id", order.tableId)
                                    put("table_number", selectedTable?.number)
                                    put("items", JSONArray())
                                }
                                socketClient.emit("order:place", json)
                                snackbarHostState.showSnackbar("Order placed!")
                                showCart = false; cart = emptyList(); isTakeaway = false
                                currentScreen = "tables"
                                loadData()
                            }.onFailure { snackbarHostState.showSnackbar("Failed to place order") }
                        }
                    },
                    onBack = { showCart = false; cart = emptyList() }
                )

                currentScreen == "tables" -> TablesScreen(
                    tables = tables,
                    activeOrders = activeOrders,
                    loading = loading,
                    onTableSelect = { table ->
                        selectedTable = table
                        currentScreen = "menu"
                    },
                    onAddItems = { order ->
                        selectedOrderForAdd = order
                        selectedCategory = null
                        addItemCart = emptyList()
                        addItemMode = true
                    },
                    onRefresh = { loadData() }
                )

                currentScreen == "menu" -> MenuScreen(
                    categories = categories,
                    menuItems = menuItems,
                    selectedCategory = selectedCategory,
                    cart = cart,
                    onSelectCategory = { selectedCategory = it },
                    onAddToCart = { item ->
                        cart = cart + CartItem(item)
                    },
                    onQuantityChange = { itemId, delta ->
                        cart = cart.mapNotNull { if (it.menuItem.id == itemId) { val nq = it.quantity + delta; if (nq <= 0) null else it.copy(quantity = nq) } else it }
                    },
                    onViewCart = { showCart = true }
                )
            }
        }
    }
}

@Composable
fun TablesScreen(
    tables: List<Table>,
    activeOrders: List<Order>,
    loading: Boolean,
    onTableSelect: (Table) -> Unit,
    onAddItems: (Order) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select Table", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tables) { table ->
                    val order = activeOrders.find { it.tableId == table.id && it.status != "paid" }
                    val isOccupied = table.status == "occupied"
                    TableCard(
                        table = table,
                        isOccupied = isOccupied,
                        order = order,
                        onClick = { if (!isOccupied) onTableSelect(table) },
                        onAddItems = { if (order != null) onAddItems(order) }
                    )
                }
            }
        }
    }
}

@Composable
fun TableCard(
    table: Table,
    isOccupied: Boolean,
    order: Order?,
    onClick: () -> Unit,
    onAddItems: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isOccupied) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isOccupied) Occupied.copy(alpha = 0.15f) else Available.copy(alpha = 0.1f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 2.dp,
            brush = androidx.compose.ui.graphics.SolidColor(if (isOccupied) Occupied else Available)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "T${table.number}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOccupied) Occupied else Available
            )
            Text(
                text = if (isOccupied) "Occupied" else "Available",
                fontSize = 12.sp,
                color = if (isOccupied) Occupied else Available
            )
            if (isOccupied && order != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val statusColor = when (order.status) {
                    "pending" -> PendingColor
                    "preparing" -> PreparingColor
                    "ready" -> ReadyColor
                    else -> Available
                }
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        order.status.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("₹${order.total}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onAddItems,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) { Text("+ Items", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun MenuScreen(
    categories: List<Category>,
    menuItems: List<MenuItem>,
    selectedCategory: Int?,
    cart: List<CartItem>,
    onSelectCategory: (Int?) -> Unit,
    onAddToCart: (MenuItem) -> Unit,
    onQuantityChange: (Int, Int) -> Unit,
    onViewCart: () -> Unit
) {
    val filteredItems = (if (selectedCategory != null) menuItems.filter { it.categoryId == selectedCategory }
    else menuItems).filter { it.available }
    val cartCount = cart.sumOf { it.quantity }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All") }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(cat.name) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems) { item ->
                val cartItem = cart.find { it.menuItem.id == item.id }
                MenuItemCard(
                    item = item,
                    quantity = cartItem?.quantity ?: 0,
                    onAdd = { onAddToCart(item) },
                    onIncrement = { onQuantityChange(item.id, 1) },
                    onDecrement = { onQuantityChange(item.id, -1) }
                )
            }
        }

        if (cartCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.primary
            ) {
                TextButton(
                    onClick = onViewCart,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Text(
                        "View Cart ($cartCount items) - ₹${cart.sumOf { it.totalPrice }}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MenuItemCard(
    item: MenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("₹${item.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (quantity > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = onDecrement, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Remove, "Decrease", modifier = Modifier.size(20.dp))
                    }
                    Text(" $quantity ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    FilledIconButton(onClick = onIncrement, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, "Increase", modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                FilledIconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, "Add", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun CartScreen(
    cart: List<CartItem>,
    isTakeaway: Boolean,
    onQuantityChange: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onToggleCheese: (Int) -> Unit,
    onToggleTakeaway: () -> Unit,
    onPlaceOrder: () -> Unit,
    onBack: () -> Unit
) {
    val total = cart.sumOf { it.totalPrice } + if (isTakeaway) 10.0 else 0.0

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cart) { cartItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cartItem.menuItem.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveItem(cartItem.menuItem.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text("₹${cartItem.menuItem.price} each", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Qty:", fontSize = 14.sp)
                            IconButton(onClick = { onQuantityChange(cartItem.menuItem.id, -1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Remove, "Decrease", modifier = Modifier.size(18.dp))
                            }
                            Text("${cartItem.quantity}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { onQuantityChange(cartItem.menuItem.id, 1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, "Increase", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = cartItem.addExtraCheese, onCheckedChange = { onToggleCheese(cartItem.menuItem.id) })
                            Text("Extra Cheese (+₹25)", fontSize = 13.sp)
                        }
                        Text("Subtotal: ₹${cartItem.totalPrice}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Takeaway", fontSize = 14.sp)
                    Switch(checked = isTakeaway, onCheckedChange = { onToggleTakeaway() })
                }
                if (isTakeaway) {
                    Text("+₹10 parcel charge", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total:", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("₹${total}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = onPlaceOrder, modifier = Modifier.weight(1f)) { Text("Place Order") }
                }
            }
        }
    }
}

@Composable
fun AddItemMenuScreen(
    categories: List<Category>,
    menuItems: List<MenuItem>,
    selectedCategory: Int?,
    cart: List<CartItem>,
    onSelectCategory: (Int?) -> Unit,
    onQuantityChange: (MenuItem, Int) -> Unit,
    onAddCheese: (MenuItem) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val filteredItems = (if (selectedCategory != null) menuItems.filter { it.categoryId == selectedCategory }
    else menuItems).filter { it.available }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(selected = selectedCategory == null, onClick = { onSelectCategory(null) }, label = { Text("All") })
            }
            items(categories) { cat ->
                FilterChip(selected = selectedCategory == cat.id, onClick = { onSelectCategory(cat.id) }, label = { Text(cat.name) })
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems) { item ->
                val cartItem = cart.find { it.menuItem.id == item.id }
                val qty = cartItem?.quantity ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                Text("₹${item.price}", color = MaterialTheme.colorScheme.primary)
                            }
                            if (qty > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onQuantityChange(item, -1) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Remove, "", modifier = Modifier.size(18.dp))
                                    }
                                    Text("$qty", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { onQuantityChange(item, 1) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Add, "", modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else {
                                FilledIconButton(onClick = { onQuantityChange(item, 1) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Add, "", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        if (qty > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = cartItem?.addExtraCheese ?: false, onCheckedChange = { onAddCheese(item) })
                                Text("Extra Cheese (+₹25)", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onSubmit, modifier = Modifier.weight(1f), enabled = cart.isNotEmpty()) { Text("Add Items (${cart.size})") }
            }
        }
    }
}
