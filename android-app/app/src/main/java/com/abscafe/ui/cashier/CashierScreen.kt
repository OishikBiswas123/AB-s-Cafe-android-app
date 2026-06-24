package com.abscafe.ui.cashier

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.api.SocketClient
import com.abscafe.data.model.*
import com.abscafe.data.repository.MenuRepository
import com.abscafe.data.repository.OrderRepository
import com.abscafe.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    orderRepo: OrderRepository,
    menuRepo: MenuRepository,
    socketClient: SocketClient,
    onLogout: () -> Unit
) {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var processingPayment by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var excludedItemIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showAddItems by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showSmsDialog by remember { mutableStateOf(false) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var addItemCart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun loadOrders() {
        scope.launch {
            loading = true
            orderRepo.getOrders().onSuccess { orders = it.filter { o -> o.status != "paid" && o.status != "void" } }
            loading = false
        }
    }

    fun loadMenu() {
        scope.launch {
            val c = async { menuRepo.getCategories() }
            val m = async { menuRepo.getMenuItems() }
            c.await().onSuccess { categories = it }
            m.await().onSuccess { menuItems = it }
        }
    }

    LaunchedEffect(Unit) {
        loadOrders()
        loadMenu()
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

    if (showSplitDialog && selectedOrder != null) {
        SplitBillDialog(
            order = selectedOrder!!,
            onConfirm = { groups ->
                scope.launch {
                    orderRepo.splitOrder(selectedOrder!!.id, groups).onSuccess { response ->
                        val json = JSONObject().apply {
                            put("order_id", selectedOrder!!.id)
                            put("table_id", selectedOrder!!.tableId)
                        }
                        socketClient.emit("order:split", json)
                        snackbarHostState.showSnackbar("Order split successfully!")
                        showSplitDialog = false
                        if (response.orders.isNotEmpty()) selectedOrder = response.orders.first()
                        else selectedOrder = null
                        loadOrders()
                    }.onFailure {
                        snackbarHostState.showSnackbar("Split failed: ${it.message}")
                    }
                }
            },
            onDismiss = { showSplitDialog = false }
        )
    }

    if (showSmsDialog && selectedOrder != null) {
        SmsBillDialog(
            order = selectedOrder!!,
            onDismiss = { showSmsDialog = false },
            context = context
        )
    }

    if (showPaymentDialog && selectedOrder != null) {
        PaymentDialog(
            order = selectedOrder!!,
            processingPayment = processingPayment,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { method ->
                if (processingPayment) return@PaymentDialog
                processingPayment = true
                scope.launch {
                    val response = RetrofitClient.apiService.payOrder(selectedOrder!!.id, PaymentRequest(method))
                    if (response.isSuccessful) {
                        socketClient.emit("order:paid", JSONObject().apply {
                            put("order_id", selectedOrder!!.id)
                            put("table_id", selectedOrder!!.tableId)
                            put("total", selectedOrder!!.total)
                        })
                        snackbarHostState.showSnackbar("Payment successful!")
                        showPaymentDialog = false
                        selectedOrder = null
                        loadOrders()
                    } else {
                        snackbarHostState.showSnackbar("Payment failed")
                        showPaymentDialog = false
                    }
                    processingPayment = false
                }
            }
        )
    }

    val loadOrdersRef = { loadOrders() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing Counter") },
                actions = {
                    IconButton(onClick = loadOrdersRef) { Icon(Icons.Default.Refresh, "Refresh") }
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
        if (selectedOrder != null && !showAddItems) {
            BillDetailScreen(
                order = selectedOrder!!,
                editMode = editMode,
                excludedItemIds = excludedItemIds,
                onToggleItem = { itemId ->
                    excludedItemIds = if (itemId in excludedItemIds) excludedItemIds - itemId
                    else excludedItemIds + itemId
                },
                onSaveEdits = {
                    scope.launch {
                        for (itemId in excludedItemIds) {
                            orderRepo.deleteItem(itemId).onFailure {
                                snackbarHostState.showSnackbar("Failed to remove item")
                            }
                        }
                        excludedItemIds = emptySet()
                        editMode = false
                        orderRepo.getOrders().onSuccess { updated ->
                            val updatedOrder = updated.find { it.id == selectedOrder!!.id }
                            if (updatedOrder != null) {
                                selectedOrder = updatedOrder
                                if (updatedOrder.status == "void") {
                                    snackbarHostState.showSnackbar("All items removed, order voided")
                                    selectedOrder = null
                                }
                            } else {
                                selectedOrder = null
                            }
                            orders = updated.filter { o -> o.status != "paid" }
                        }
                    }
                },
                onCancelEdits = {
                    excludedItemIds = emptySet()
                    editMode = false
                },
                onBack = {
                    selectedOrder = null
                    editMode = false
                    excludedItemIds = emptySet()
                },
                onEdit = { editMode = !editMode; excludedItemIds = emptySet() },
                onAddItems = { showAddItems = true },
                onSplit = { showSplitDialog = true },
                onPrint = { printBill(context, selectedOrder!!) },
                onSms = { showSmsDialog = true },
                onPay = { showPaymentDialog = true }
            )
        } else if (showAddItems && selectedOrder != null) {
            CashierAddItemScreen(
                menuItems = menuItems.filter { it.available },
                categories = categories,
                selectedCategory = selectedCategory,
                cart = addItemCart,
                onSelectCategory = { selectedCategory = it },
                onQuantityChange = { item, delta ->
                    addItemCart = addItemCart.mapNotNull { if (it.menuItem.id == item.id) { val nq = it.quantity + delta; if (nq <= 0) null else it.copy(quantity = nq) } else it }
                    if (addItemCart.none { it.menuItem.id == item.id }) {
                        addItemCart = addItemCart + CartItem(item)
                    }
                },
                onSubmit = {
                    scope.launch {
                        val items = addItemCart.map { it.toOrderItemInput() }
                        orderRepo.addItems(selectedOrder!!.id, items).onSuccess { order ->
                            socketClient.emit("order:add-items", JSONObject().apply {
                                put("order_id", order.id)
                                put("table_id", order.tableId)
                            })
                            snackbarHostState.showSnackbar("Items added!")
                            selectedOrder = order
                            showAddItems = false
                            addItemCart = emptyList()
                            loadOrders()
                        }.onFailure { snackbarHostState.showSnackbar("Failed to add items") }
                    }
                },
                onCancel = { showAddItems = false; addItemCart = emptyList() }
            )
        } else if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No active bills", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders) { order ->
                    OccupiedTableBillCard(
                        order = order,
                        onClick = { selectedOrder = order; editMode = false; excludedItemIds = emptySet() }
                    )
                }
            }
        }
    }
}

@Composable
fun OccupiedTableBillCard(order: Order, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
                color = Occupied.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("T${order.tableNumber}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Occupied)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Order #${order.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${order.items.size} items", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                if (order.isTakeaway) Text("Takeaway", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${order.total}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text(order.status.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = when (order.status) {
                    "ready" -> ReadyColor; "preparing" -> PreparingColor; else -> PendingColor
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    order: Order,
    editMode: Boolean,
    excludedItemIds: Set<Int>,
    onToggleItem: (Int) -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddItems: () -> Unit,
    onSplit: () -> Unit,
    onPrint: () -> Unit,
    onSms: () -> Unit,
    onPay: () -> Unit
) {
    val adjustedTotal = if (editMode) {
        order.items.filter { it.id !in excludedItemIds }.sumOf { item ->
            item.price * item.quantity + item.addons.sumOf { it.price * item.quantity }
        }
    } else order.total

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AB's Cafe", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Table ${order.tableNumber}", fontSize = 16.sp)
                        Text("Order #${order.id}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("Waiter: ${order.waiterName}", fontSize = 14.sp)
                        if (order.isTakeaway) Text("Takeaway", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        if (editMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Edit mode: uncheck items to exclude", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                Text("Items", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
            }

            items(order.items) { item ->
                val isExcluded = item.id in excludedItemIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExcluded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${item.quantity}x ${item.name}",
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (isExcluded) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (item.addons.isNotEmpty()) {
                                Text(
                                    item.addons.joinToString(", ") { "${it.name} (+₹${it.price})" },
                                    fontSize = 12.sp,
                                    textDecoration = if (isExcluded) TextDecoration.LineThrough else TextDecoration.None,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isExcluded) 0.3f else 0.6f
                                    )
                                )
                            }
                        }
                        val itemTotal = item.price * item.quantity + item.addons.sumOf { it.price * item.quantity }
                        if (editMode) {
                            Checkbox(
                                checked = !isExcluded,
                                onCheckedChange = { onToggleItem(item.id) }
                            )
                        } else {
                            Text(
                                "₹${itemTotal}",
                                fontWeight = FontWeight.Bold,
                                color = if (isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total:", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "₹${adjustedTotal}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (adjustedTotal != order.total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                if (editMode && adjustedTotal != order.total) {
                    Text("Original: ₹${order.total}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Scan to Pay", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            painter = painterResource(id = com.abscafe.R.drawable.qr_code),
                            contentDescription = "QR Code for Payment",
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Show this QR code to customer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }

        Surface(shadowElevation = 8.dp) {
            if (editMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancelEdits, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = onSaveEdits, modifier = Modifier.weight(1f)) { Text("Update Bill") }
                }
            } else {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                        OutlinedButton(onClick = onSplit, modifier = Modifier.weight(1f)) { Text("Split") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAddItems, modifier = Modifier.weight(1f)) { Text("+ Items") }
                        OutlinedButton(onClick = onPrint, modifier = Modifier.weight(1f)) { Text("Print") }
                        OutlinedButton(onClick = onSms, modifier = Modifier.weight(1f)) { Text("SMS") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) { Text("Collect Payment") }
                }
            }
        }
    }
}

@Composable
fun CashierAddItemScreen(
    menuItems: List<MenuItem>,
    categories: List<Category>,
    selectedCategory: Int?,
    cart: List<CartItem>,
    onSelectCategory: (Int?) -> Unit,
    onQuantityChange: (MenuItem, Int) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val filteredItems = if (selectedCategory != null) menuItems.filter { it.categoryId == selectedCategory }
    else menuItems

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

@Composable
fun SplitBillDialog(
    order: Order,
    onConfirm: (List<List<Int>>) -> Unit,
    onDismiss: () -> Unit
) {
    var splitAssignments by remember { mutableStateOf(order.items.associate { it.id to 1 }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Bill - Table ${order.tableNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Assign items to split groups:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                order.items.forEach { item ->
                    val currentSplit = splitAssignments[item.id] ?: 1
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.quantity}x ${item.name}", fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { splitAssignments = splitAssignments + (item.id to maxOf(1, currentSplit - 1)) },
                                modifier = Modifier.size(28.dp)
                            ) { Icon(Icons.Default.Remove, "", modifier = Modifier.size(16.dp)) }
                            Text("$currentSplit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(
                                onClick = { splitAssignments = splitAssignments + (item.id to currentSplit + 1) },
                                modifier = Modifier.size(28.dp)
                            ) { Icon(Icons.Default.Add, "", modifier = Modifier.size(16.dp)) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Preview:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                val groups = splitAssignments.entries
                    .groupBy({ it.value }, { it.key })
                    .entries.sortedBy { it.key }

                groups.forEach { (groupNum, itemIds) ->
                    val groupTotal = order.items.filter { it.id in itemIds }.sumOf { item ->
                        item.price * item.quantity + item.addons.sumOf { it.price * item.quantity }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Split $groupNum: ${itemIds.size} items", fontSize = 13.sp)
                            Text("₹$groupTotal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val groups = splitAssignments.entries
                        .groupBy({ it.value }, { it.key })
                        .entries.sortedBy { it.key }
                        .map { it.value }
                    onConfirm(groups)
                }) { Text("Confirm Split") }
            }
        }
    )
}

@Composable
fun SmsBillDialog(
    order: Order,
    onDismiss: () -> Unit,
    context: Context
) {
    var phoneNumber by remember { mutableStateOf("") }
    val billText = getBillText(order)

    fun isWhatsAppInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo("com.whatsapp", android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("com.whatsapp", 0)
            }
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    val waInstalled = remember { isWhatsAppInstalled() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter phone number:", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '+' }) phoneNumber = it },
                    label = { Text("Phone number") },
                    placeholder = { Text("+919876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Bill preview:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                ) {
                    Text(
                        billText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (phoneNumber.isBlank()) {
                                Toast.makeText(context, "Enter phone number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:$phoneNumber")
                                putExtra("sms_body", billText)
                            }
                            context.startActivity(smsIntent)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = phoneNumber.isNotBlank()
                    ) { Text("SMS") }
                    if (waInstalled) {
                        Button(
                            onClick = {
                                if (phoneNumber.isBlank()) {
                                    Toast.makeText(context, "Enter phone number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                try {
                                    val waIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, billText)
                                        putExtra("jid", "$phoneNumber@s.whatsapp.net")
                                        setPackage("com.whatsapp")
                                    }
                                    context.startActivity(waIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "WhatsApp not available", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = phoneNumber.isNotBlank()
                        ) { Text("WhatsApp") }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun PaymentDialog(
    order: Order,
    processingPayment: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collect Payment - Table ${order.tableNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Total Amount: ₹${order.total}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select payment method:", fontSize = 14.sp)
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConfirm("cash") },
                        modifier = Modifier.weight(1f),
                        enabled = !processingPayment
                    ) { Text("Cash") }
                    Button(
                        onClick = { onConfirm("qr") },
                        modifier = Modifier.weight(1f),
                        enabled = !processingPayment
                    ) { Text("QR Code") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
            }
        }
    )
}

fun getBillText(order: Order): String {
    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    val sb = StringBuilder()
    sb.appendLine("AB's Cafe")
    sb.appendLine("=================")
    sb.appendLine("Order #${order.id}")
    sb.appendLine("Table: ${order.tableNumber}")
    sb.appendLine("Date: ${dateFormat.format(Date())}")
    if (order.isTakeaway) sb.appendLine("Takeaway")
    sb.appendLine("-----------------")
    for (item in order.items) {
        val itemTotal = item.price * item.quantity + item.addons.sumOf { it.price * item.quantity }
        sb.appendLine("${item.quantity}x ${item.name}    ₹$itemTotal")
        if (item.addons.isNotEmpty()) {
            sb.appendLine("    + ${item.addons.joinToString(", ") { it.name }}")
        }
    }
    sb.appendLine("-----------------")
    sb.appendLine("Total:        ₹${order.total}")
    sb.appendLine("=================")
    sb.appendLine("Thank you!")
    return sb.toString()
}

fun printBill(context: Context, order: Order) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "ABsCafe_Order_${order.id}"

    printManager.print(jobName, object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                val pdfDocument = PrintedPdfDocument(context, PrintAttributes.Builder().build())
                val page = pdfDocument.startPage(0)
                val canvas: Canvas = page.canvas
                val pageWidth = canvas.width.toFloat()
                var y = 50f

                val titlePaint = Paint().apply {
                    textSize = 28f
                    color = android.graphics.Color.BLACK
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                val headerPaint = Paint().apply {
                    textSize = 16f
                    color = android.graphics.Color.DKGRAY
                    textAlign = Paint.Align.CENTER
                }
                val boldPaint = Paint().apply {
                    textSize = 14f
                    color = android.graphics.Color.BLACK
                    typeface = Typeface.DEFAULT_BOLD
                }
                val normalPaint = Paint().apply {
                    textSize = 14f
                    color = android.graphics.Color.BLACK
                }
                val smallPaint = Paint().apply {
                    textSize = 11f
                    color = android.graphics.Color.DKGRAY
                }

                // Title
                canvas.drawText("AB's Cafe", pageWidth / 2, y, titlePaint)
                y += 40f
                canvas.drawText("Order #${order.id} - Table ${order.tableNumber}", pageWidth / 2, y, headerPaint)
                y += 25f

                val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                canvas.drawText(dateFormat.format(Date()), pageWidth / 2, y, headerPaint)
                y += 25f

                if (order.isTakeaway) {
                    canvas.drawText("Takeaway", pageWidth / 2, y, headerPaint)
                    y += 25f
                }

                // Separator
                canvas.drawLine(50f, y, pageWidth - 50f, y, normalPaint)
                y += 15f

                // Column headers
                canvas.drawText("Item", 50f, y, boldPaint)
                canvas.drawText("Qty", pageWidth / 2 - 30f, y, boldPaint)
                canvas.drawText("Amount", pageWidth - 100f, y, boldPaint)
                y += 5f
                canvas.drawLine(50f, y, pageWidth - 50f, y, smallPaint)
                y += 20f

                // Items
                for (item in order.items) {
                    val itemTotal = item.price * item.quantity + item.addons.sumOf { it.price * item.quantity }
                    canvas.drawText("${item.quantity}x ${item.name}", 50f, y, normalPaint)
                    canvas.drawText("₹$itemTotal", pageWidth - 100f, y, boldPaint)
                    y += 20f

                    if (item.addons.isNotEmpty()) {
                        canvas.drawText("  + ${item.addons.joinToString(", ") { it.name }}", 70f, y, smallPaint)
                        y += 16f
                    }
                }

                // Separator
                y += 5f
                canvas.drawLine(50f, y, pageWidth - 50f, y, normalPaint)
                y += 25f

                // Total
                canvas.drawText("Total:", 50f, y, boldPaint)
                val totalPaint = Paint().apply {
                    textSize = 20f
                    color = android.graphics.Color.BLACK
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("₹${order.total}", pageWidth - 100f, y, totalPaint)
                y += 40f

                // Footer
                canvas.drawText("Thank you!", pageWidth / 2, y, Paint().apply {
                    textSize = 18f
                    color = android.graphics.Color.DKGRAY
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                })

                pdfDocument.finishPage(page)

                pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                pdfDocument.close()
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }, null)
}
