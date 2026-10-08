/*
 * Copyright 2026 Alexandr <aleks.spv@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adm.lite.ui.nav

import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.adm.lite.ldap.LdapSession
import com.adm.lite.model.ConnectionState
import com.adm.lite.ui.connect.ConnectScreen
import com.adm.lite.ui.servers.ServersScreen
import com.adm.lite.ui.users.UsersScreen
import com.adm.lite.ui.settings.SettingsScreen

sealed class Route(val path: String) {
    data object Servers : Route("servers")
    data object ServerEditor : Route("server/{id}") {
        fun path(id: Long) = "server/$id"
    }
    data object Connect : Route("connect/{id}") {
        fun path(id: Long) = "connect/$id"
    }
    data object Users : Route("users")
    data object Settings : Route("settings")
}

@Composable
fun AdmNavGraph(
    navController: NavHostController,
    factory: ViewModelProvider.Factory,
    ldapSession: LdapSession,
    onNavigate: (String) -> Unit = { navController.navigate(it) }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionState by ldapSession.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    DisposableEffect(lifecycleOwner, ldapSession) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                val activity = context.findActivity()
                if (activity != null && !activity.isChangingConfigurations) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                        .launch { ldapSession.disconnect() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var previousState by remember { mutableStateOf<ConnectionState>(ConnectionState.Disconnected) }
    LaunchedEffect(sessionState) {
        val wasConnected = previousState is ConnectionState.Connected
        val isNowDisconnected = sessionState is ConnectionState.Disconnected
        previousState = sessionState
        if (wasConnected && isNowDisconnected) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != null && current != "connect/{id}" && ldapSession.currentServerId != 0L) {
                navController.navigate("connect/${ldapSession.currentServerId}") {
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Route.Servers.path) {
        // ── Servers list ──
        composable(Route.Servers.path) {
            ServersScreen(
                factory = factory,
                onServerClick = { serverId ->
                    onNavigate(Route.Connect.path(serverId))
                },
                onSettingsClick = {
                    onNavigate(Route.Settings.path)
                }
            )
        }

        // ── Connect to server ──
        composable(
            Route.Connect.path,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("id") ?: 0L
            ConnectScreen(
                serverId = serverId,
                factory = factory,
                onConnected = {
                    onNavigate(Route.Users.path)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ── Users ──
        composable(Route.Users.path) {
            UsersScreen(
                onBack = { navController.popBackStack() },
                onNavigate = onNavigate,
                factory = factory
            )
        }

        // ── Settings ──
        composable(Route.Settings.path) {
            SettingsScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
