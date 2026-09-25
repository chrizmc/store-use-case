package com.bopis.associate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.ui.assistant.AssistantScreen
import com.bopis.associate.ui.assistant.AssistantViewModel
import com.bopis.associate.ui.notifications.NotificationsScreen
import com.bopis.associate.ui.notifications.NotificationsViewModel
import com.bopis.associate.ui.orders.OrderDetailScreen
import com.bopis.associate.ui.orders.OrderDetailViewModel
import com.bopis.associate.ui.orders.OrdersListScreen
import com.bopis.associate.ui.orders.OrdersViewModel
import com.bopis.associate.ui.shelf.ShelfActionScreen
import com.bopis.associate.ui.shelf.ShelfActionViewModel

private object Routes {
    const val ORDERS = "orders"
    const val NOTIFICATIONS = "notifications"
    const val ASSISTANT = "assistant"
    const val ORDER_DETAIL = "orderDetail/{orderId}"
    const val SHELF_ACTION = "shelf/{orderId}/{itemId}/{qr}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as BopisApp).repository

        setContent {
            MaterialTheme {
                BopisApp(repository)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BopisApp(repository: BopisRepository) {
    val navController = rememberNavController()
    val tabs = listOf(
        Routes.ORDERS to "Orders",
        Routes.NOTIFICATIONS to "Alerts",
        Routes.ASSISTANT to "Assistant",
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                tabs.forEach { (route, label) ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            // Deliberately NOT using the standard saveState/restoreState
                            // recipe: OrderDetail/ShelfAction are flat destinations (not a
                            // nested graph scoped to the Orders tab), so restoreState was
                            // resurrecting whatever screen the associate had drilled into
                            // (e.g. a Shelf screen) instead of the tab's root - tapping
                            // "Orders" could silently show a stale Shelf screen rather than
                            // the orders list. Clearing the whole back stack guarantees each
                            // tab tap always lands on that tab's root screen with a fresh load.
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = {},
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ORDERS,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Routes.ORDERS) {
                val vm: OrdersViewModel = viewModel(factory = viewModelFactory {
                    initializer { OrdersViewModel(repository) }
                })
                OrdersListScreen(vm) { orderId ->
                    navController.navigate("orderDetail/$orderId")
                }
            }
            composable(Routes.ORDER_DETAIL) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
                val vm: OrderDetailViewModel = viewModel(factory = viewModelFactory {
                    initializer { OrderDetailViewModel(repository, orderId) }
                })
                OrderDetailScreen(vm) { itemId, qrCode ->
                    navController.navigate("shelf/$orderId/$itemId/$qrCode")
                }
            }
            composable(Routes.SHELF_ACTION) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId")
                val itemId = backStackEntry.arguments?.getString("itemId")
                val qr = backStackEntry.arguments?.getString("qr").orEmpty()
                val vm: ShelfActionViewModel = viewModel(factory = viewModelFactory {
                    initializer { ShelfActionViewModel(repository, qr, orderId, itemId) }
                })
                ShelfActionScreen(vm) { navController.popBackStack() }
            }
            composable(Routes.NOTIFICATIONS) {
                val vm: NotificationsViewModel = viewModel(factory = viewModelFactory {
                    initializer { NotificationsViewModel(repository) }
                })
                NotificationsScreen(vm)
            }
            composable(Routes.ASSISTANT) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val vm: AssistantViewModel = viewModel(factory = viewModelFactory {
                    initializer { AssistantViewModel(repository, context.applicationContext) }
                })
                AssistantScreen(vm)
            }
        }
    }
}
