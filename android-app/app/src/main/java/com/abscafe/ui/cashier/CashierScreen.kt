package com.abscafe.ui.cashier

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abscafe.data.api.RetrofitClient
import com.abscafe.data.api.SocketClient
import com.abscafe.data.model.Order
import com.abscafe.data.repository.OrderRepository
import com.abscafe.data.repository.TableRepository
import com.abscafe.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    orderRepo: OrderRepository,
    tableRepo: TableRepository,
    socketClient: SocketClient,
    onLogout: () -> Unit
) {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadOrders() {
        scope.launch {
            loading = true
            orderRepo.getOrders().onSuccess { orders = it.filter { o -> o.status != "paid" } }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadOrders() }
    val loadOrdersRef = { loadOrders() }

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
        if (selectedOrder != null) {
            BillDetailScreen(
                order = selectedOrder!!,
                onBack = { selectedOrder = null },
                onPay = { showPaymentDialog = true }
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
                        onClick = { selectedOrder = order }
                    )
                }
            }
        }

        if (showPaymentDialog && selectedOrder != null) {
            PaymentDialog(
                order = selectedOrder!!,
                onDismiss = { showPaymentDialog = false },
                onConfirm = { method ->
                    scope.launch {
                        val response = RetrofitClient.apiService.payOrder(selectedOrder!!.id, com.abscafe.data.model.PaymentRequest(method))
                        if (response.isSuccessful) {
                            val json = JSONObject().apply {
                                put("order_id", selectedOrder!!.id)
                                put("table_id", selectedOrder!!.tableId)
                                put("total", selectedOrder!!.total)
                            }
                            socketClient.emit("order:paid", json)
                            snackbarHostState.showSnackbar("Payment successful! Table released.")
                            showPaymentDialog = false
                            selectedOrder = null
                            loadOrders()
                        } else {
                            snackbarHostState.showSnackbar("Payment failed")
                            showPaymentDialog = false
                        }
                    }
                }
            )
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
fun BillDetailScreen(order: Order, onBack: () -> Unit, onPay: () -> Unit) {
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
                    }
                }
            }

            item {
                Text("Items", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
            }

            items(order.items) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${item.quantity}x ${item.name}", fontWeight = FontWeight.Medium)
                            if (item.addons.isNotEmpty()) {
                                Text(item.addons.joinToString(", ") { "${it.name} (+₹${it.price})" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        val itemTotal = item.price * item.quantity + item.addons.sumOf { it.price * item.quantity }
                        Text("₹${itemTotal}", fontWeight = FontWeight.Bold)
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
                    Text("₹${order.total}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = onPay, modifier = Modifier.weight(1f)) { Text("Collect Payment") }
            }
        }
    }
}

@Composable
fun PaymentDialog(
    order: Order,
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
                        modifier = Modifier.weight(1f)
                    ) { Text("Cash") }
                    Button(
                        onClick = { onConfirm("qr") },
                        modifier = Modifier.weight(1f)
                    ) { Text("QR Code") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
            }
        }
    )
}
