package com.bopis.associate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.bopis.associate.ui.agent.ProactiveAgentOverlay
import com.bopis.associate.ui.agent.ProactiveAgentViewModel
import com.bopis.associate.ui.assistant.AssistantViewModel
import com.bopis.associate.ui.notifications.NotificationsScreen
import com.bopis.associate.ui.notifications.NotificationsViewModel
import com.bopis.associate.ui.orders.OrderDetailScreen
import com.bopis.associate.ui.orders.OrderDetailViewModel
import com.bopis.associate.ui.orders.OrdersListScreen
import com.bopis.associate.ui.orders.OrdersViewModel
import com.bopis.associate.ui.shelf.ShelfActionScreen
import com.bopis.associate.ui.shelf.ShelfActionViewModel
import com.bopis.associate.ui.theme.BopisTheme

private object Routes {
    const val ORDERS = "orders"
    const val NOTIFICATIONS = "notifications"
    const val ORDER_DETAIL = "orderDetail/{orderId}"
    const val SHELF_ACTION = "shelf/{orderId}/{itemId}/{qr}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Demo runs on a device left mirroring/on a counter for minutes at a time -
        // don't let the system dim/lock the screen mid-demo.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val repository = (application as BopisApp).repository

        setContent {
            BopisTheme {
                BopisApp(repository)
            }
        }
    }
}

// Text-based wordmark badge, not the literal SAP logo asset - keeps the SAP-inspired palette
// without shipping a trademarked image file.
@androidx.compose.runtime.Composable
private fun BrandBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = Color.White) {
                    Text(
                        "SAP",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "  BOPIS Associate",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@androidx.compose.runtime.Composable
private fun BopisApp(repository: BopisRepository) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val agentViewModel: ProactiveAgentViewModel = viewModel(factory = viewModelFactory {
        initializer { ProactiveAgentViewModel(repository, context.applicationContext) }
    })
    // Shared across the whole app (not per-route) since the chat window is now only reachable
    // from the always-visible corner bubble, not a NavHost destination.
    val assistantViewModel: AssistantViewModel = viewModel(factory = viewModelFactory {
        initializer { AssistantViewModel(repository, context.applicationContext) }
    })
    val tabs = listOf(
        Routes.ORDERS to "Orders",
        Routes.NOTIFICATIONS to "Alerts",
    )

    Scaffold(
        topBar = { BrandBar() },
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
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.ORDERS,
                modifier = Modifier.fillMaxSize(),
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
            }
            ProactiveAgentOverlay(agentViewModel, assistantViewModel)
        }
    }
}

