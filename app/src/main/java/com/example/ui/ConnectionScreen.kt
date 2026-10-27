package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ConnectionStatus
import com.example.model.ServerConfig
import com.example.network.RemoteLinkWebSocketManager
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberEmerald

@Composable
fun ConnectionScreen(
    connectionStatus: ConnectionStatus,
    activeUrl: String,
    activeCode: String,
    logs: List<String>,
    onConnect: (ServerConfig) -> Unit,
    onDisconnect: () -> Unit,
    onGrantConsent: (Boolean) -> Unit,
    onOpenQrScanner: () -> Unit
) {
    val context = LocalContext.current
    var hostInput by remember { mutableStateOf("192.168.1.5") }
    var portInput by remember { mutableStateOf("8080") }
    var codeInput by remember { mutableStateOf("") }
    var isWss by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Visual Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hero_pairing_banner),
                        contentDescription = "Hero cyber pairing banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text(
                                text = "RemoteLink Device Companion",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                text = "Secure, private peer link with explicit user consent",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Live Link Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        when (connectionStatus) {
                            ConnectionStatus.CONNECTED_AND_APPROVED -> CyberEmerald.copy(alpha = 0.6f)
                            ConnectionStatus.WAITING_CONSENT -> CyberAmber.copy(alpha = 0.6f)
                            ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("connection_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
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
                                text = when (connectionStatus) {
                                    ConnectionStatus.CONNECTED_AND_APPROVED -> "Active Link Established"
                                    ConnectionStatus.WAITING_CONSENT -> "Awaiting User Consent"
                                    ConnectionStatus.CONNECTING -> "Connecting to Relay..."
                                    ConnectionStatus.ERROR -> "Connection Failed"
                                    ConnectionStatus.DISCONNECTED -> "Device Disconnected"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (connectionStatus == ConnectionStatus.CONNECTED_AND_APPROVED ||
                            connectionStatus == ConnectionStatus.WAITING_CONSENT ||
                            connectionStatus == ConnectionStatus.CONNECTING
                        ) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("disconnect_button")
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }
                    }

                    if (connectionStatus == ConnectionStatus.CONNECTED_AND_APPROVED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "URL: $activeUrl",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Code: $activeCode • Encrypted streaming & File transfer ready",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (connectionStatus == ConnectionStatus.WAITING_CONSENT) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Pairing requested from Web Panel. Do you allow access?",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onGrantConsent(false) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Deny", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { onGrantConsent(true) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberEmerald,
                                            contentColor = androidx.compose.ui.graphics.Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Connect Form Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Connect to Web Panel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scan the QR code on your PC panel or enter the details manually.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    Button(
                        onClick = onOpenQrScanner,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_qr_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code with Camera", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "OR ENTER DETAILS MANUALLY",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("Server IP / Host") },
                            placeholder = { Text("192.168.1.5 or 10.0.2.2") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("server_host_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("server_port_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        label = { Text("Pairing Code") },
                        placeholder = { Text("e.g. 9F3A1C7B") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pairing_code_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Secure SSL (wss://)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isWss,
                            onCheckedChange = { isWss = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val port = portInput.toIntOrNull() ?: 8080
                                onConnect(ServerConfig(hostInput, port, codeInput, isWss))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_relay_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                RemoteLinkWebSocketManager.startSimulatedDemo(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_demo_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Demo Mode", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Connection Logs
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connection Events Log",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${logs.size} events",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            if (logs.isEmpty()) {
                                item {
                                    Text(
                                        text = "No events yet. Ready to connect.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                items(logs) { line ->
                                    Text(
                                        text = line,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
