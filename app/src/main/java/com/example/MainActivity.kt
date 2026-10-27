package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.configureAppCheck
import com.example.model.ConnectionStatus
import com.example.model.ServerConfig
import com.example.network.RemoteLinkWebSocketManager
import com.example.ui.ConnectionScreen
import com.example.ui.ConsentDialog
import com.example.ui.FileOfferDialog
import com.example.ui.FileTransferScreen
import com.example.ui.GeminiAiScreen
import com.example.ui.NotificationMirrorScreen
import com.example.ui.QrScannerDialog
import com.example.ui.ServerHubScreen
import com.example.ui.ThemeSettingsDialog
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager

data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val testTag: String)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        ThemeManager.init(this)
        RemoteLinkWebSocketManager.initDatabase(this)
        com.example.service.AppNotificationFilterManager.init(this)
        enableEdgeToEdge()

        setContent {
            val themeMode by ThemeManager.themeMode.collectAsState()
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsState()

            MyApplicationTheme(
                themeMode = themeMode,
                dynamicColor = isDynamicColor
            ) {
                RemoteLinkApp(
                    themeMode = themeMode,
                    isDynamicColor = isDynamicColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteLinkApp(
    themeMode: AppThemeMode,
    isDynamicColor: Boolean
) {
    val localContext = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val connectionStatus by RemoteLinkWebSocketManager.connectionStatus.collectAsState()
    val activeUrl by RemoteLinkWebSocketManager.activeServerUrl.collectAsState()
    val activeCode by RemoteLinkWebSocketManager.activePairingCode.collectAsState()
    val incomingOffer by RemoteLinkWebSocketManager.incomingOffer.collectAsState()
    val transfers by RemoteLinkWebSocketManager.transfers.collectAsState()
    val mirroredNotifications by RemoteLinkWebSocketManager.mirroredNotifications.collectAsState()
    val logs by RemoteLinkWebSocketManager.logs.collectAsState()

    // Dialog 1: Consent confirmation when waiting for authorization
    if (connectionStatus == ConnectionStatus.WAITING_CONSENT) {
        ConsentDialog(
            serverUrl = activeUrl,
            pairingCode = activeCode,
            onAllow = { RemoteLinkWebSocketManager.grantConsent(true) },
            onDeny = { RemoteLinkWebSocketManager.grantConsent(false) }
        )
    }

    // Dialog 2: Incoming file transfer offer from PC
    incomingOffer?.let { offer ->
        FileOfferDialog(
            offer = offer,
            onAccept = {
                RemoteLinkWebSocketManager.acceptIncomingFile(offer, localContext)
                Toast.makeText(localContext, "Receiving ${offer.name} into Downloads...", Toast.LENGTH_SHORT).show()
            },
            onReject = {
                RemoteLinkWebSocketManager.rejectIncomingFile(offer)
            }
        )
    }

    // Dialog 3: QR Scanner
    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrDetected = { host, port, code ->
                showQrScanner = false
                RemoteLinkWebSocketManager.connect(ServerConfig(host = host, port = port, code = code))
            }
        )
    }

    // Dialog 4: Theme & Appearance Settings
    if (showThemeDialog) {
        ThemeSettingsDialog(
            themeMode = themeMode,
            isDynamicColor = isDynamicColor,
            onDismiss = { showThemeDialog = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (connectionStatus) {
                                        ConnectionStatus.CONNECTED_AND_APPROVED -> CyberEmerald
                                        ConnectionStatus.WAITING_CONSENT -> CyberAmber
                                        ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RemoteLink",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Theme Switcher Button
                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = when (themeMode) {
                                AppThemeMode.DARK -> Icons.Default.DarkMode
                                AppThemeMode.LIGHT -> Icons.Default.LightMode
                                AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            },
                            contentDescription = "Theme & Appearance",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Connection Badge
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(
                                when (connectionStatus) {
                                    ConnectionStatus.CONNECTED_AND_APPROVED -> CyberEmerald.copy(alpha = 0.15f)
                                    ConnectionStatus.WAITING_CONSENT -> CyberAmber.copy(alpha = 0.15f)
                                    ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.CONNECTED_AND_APPROVED -> "Linked"
                                ConnectionStatus.WAITING_CONSENT -> "Consent Needed"
                                ConnectionStatus.CONNECTING -> "Connecting"
                                else -> "Offline"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (connectionStatus) {
                                ConnectionStatus.CONNECTED_AND_APPROVED -> CyberEmerald
                                ConnectionStatus.WAITING_CONSENT -> CyberAmber
                                ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val navItems = listOf(
                    NavItem("Link", Icons.Default.Link, "nav_link"),
                    NavItem("Files", Icons.Default.FolderShared, "nav_files"),
                    NavItem("Mirror", Icons.Default.Notifications, "nav_mirror"),
                    NavItem("Gemini AI", Icons.Default.AutoAwesome, "nav_gemini"),
                    NavItem("Server", Icons.Default.Terminal, "nav_server")
                )

                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        modifier = Modifier.testTag(item.testTag),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ConnectionScreen(
                    connectionStatus = connectionStatus,
                    activeUrl = activeUrl,
                    activeCode = activeCode,
                    logs = logs,
                    onConnect = { config -> RemoteLinkWebSocketManager.connect(config) },
                    onDisconnect = { RemoteLinkWebSocketManager.disconnect() },
                    onGrantConsent = { granted -> RemoteLinkWebSocketManager.grantConsent(granted) },
                    onOpenQrScanner = { showQrScanner = true }
                )

                1 -> FileTransferScreen(
                    connectionStatus = connectionStatus,
                    transfers = transfers,
                    onSendFile = { uri -> RemoteLinkWebSocketManager.sendFile(uri, localContext) },
                    onSimulateIncomingFile = { RemoteLinkWebSocketManager.simulateIncomingOfferFromPanel() }
                )

                2 -> NotificationMirrorScreen(
                    notifications = mirroredNotifications,
                    onTestNotification = {
                        RemoteLinkWebSocketManager.sendNotification(
                            appName = "WhatsApp",
                            title = "Alex Vance",
                            text = "Hey! Files are ready on the PC panel."
                        )
                        Toast.makeText(localContext, "Test notification dispatched to mirror stream!", Toast.LENGTH_SHORT).show()
                    }
                )

                3 -> GeminiAiScreen(
                    notifications = mirroredNotifications,
                    transfers = transfers
                )

                4 -> ServerHubScreen()
            }
        }
    }
}
