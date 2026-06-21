package com.abscafe.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class Table(
    val id: Int,
    val number: Int,
    val status: String
)

data class Category(
    val id: Int,
    val name: String,
    @SerializedName("sort_order") val sortOrder: Int
)

data class MenuItem(
    val id: Int,
    val name: String,
    val description: String = "",
    val price: Double,
    @SerializedName("category_id") val categoryId: Int,
    val available: Boolean = true
)

data class Order(
    val id: Int,
    @SerializedName("table_id") val tableId: Int,
    @SerializedName("table_number") val tableNumber: Int = 0,
    @SerializedName("waiter_id") val waiterId: Int,
    @SerializedName("waiter_name") val waiterName: String = "",
    val status: String,
    val total: Double = 0.0,
    @SerializedName("is_takeaway") val isTakeaway: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    val items: List<OrderItem> = emptyList()
)

data class OrderItem(
    val id: Int,
    @SerializedName("menu_item_id") val menuItemId: Int,
    val name: String = "",
    val quantity: Int,
    val price: Double,
    val notes: String = "",
    val status: String = "pending",
    val addons: List<OrderAddon> = emptyList()
)

data class OrderAddon(
    val id: Int = 0,
    val name: String,
    val price: Double
)

data class OrderItemInput(
    @SerializedName("menu_item_id") val menuItemId: Int,
    val quantity: Int,
    val price: Double,
    val notes: String = "",
    val addons: List<OrderAddon> = emptyList()
)

data class CreateOrderRequest(
    @SerializedName("table_id") val tableId: Int,
    val items: List<OrderItemInput>,
    @SerializedName("is_takeaway") val isTakeaway: Boolean = false
)

data class AddItemsRequest(
    val items: List<OrderItemInput>
)

data class PaymentRequest(
    val method: String
)

data class PaymentResponse(
    val success: Boolean,
    val message: String
)

data class ApiResponse(
    val success: Boolean? = null,
    val error: String? = null
)

data class SalesReport(
    val period: String,
    val sales: SalesSummary,
    @SerializedName("by_payment_method") val byPaymentMethod: List<MethodTotal>,
    @SerializedName("top_items") val topItems: List<ItemSales>,
    @SerializedName("by_table") val byTable: List<TableSales>
)

data class SalesSummary(
    @SerializedName("total_sales") val totalSales: Double,
    @SerializedName("total_orders") val totalOrders: Int,
    @SerializedName("avg_order_value") val avgOrderValue: Double
)

data class MethodTotal(
    val method: String,
    val total: Double
)

data class ItemSales(
    val name: String,
    val quantity: Int,
    val revenue: Double
)

data class TableSales(
    @SerializedName("table_number") val tableNumber: Int,
    val orders: Int,
    val revenue: Double
)

data class StatusUpdate(
    val status: String
)

data class MenuItemRequest(
    val name: String,
    val price: Double,
    @SerializedName("category_id") val categoryId: Int,
    val description: String = "",
    val available: Boolean = true
)

data class CartItem(
    val menuItem: MenuItem,
    var quantity: Int = 1,
    val notes: String = "",
    val addExtraCheese: Boolean = false
) {
    val totalPrice: Double
        get() {
            var price = menuItem.price * quantity
            if (addExtraCheese) price += 25.0 * quantity
            return price
        }

    fun toOrderItemInput(): OrderItemInput {
        val addons = mutableListOf<OrderAddon>()
        if (addExtraCheese) addons.add(OrderAddon(name = "Extra Cheese", price = 25.0))
        return OrderItemInput(
            menuItemId = menuItem.id,
            quantity = quantity,
            price = menuItem.price,
            notes = notes,
            addons = addons
        )
    }
}
