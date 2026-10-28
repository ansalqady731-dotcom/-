package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DeviceEntity
import com.example.ui.FormatUtils
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SuccessEmerald

enum class DeviceSortOption(val titleArabic: String) {
    CONSUMPTION("حسب الاستهلاك"),
    ACTIVE("حسب الحالة"),
    NAME("حسب الاسم")
}

@Composable
fun DevicesScreen(
    devices: List<DeviceEntity>,
    onUpdateNickname: (mac: String, nickname: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(DeviceSortOption.CONSUMPTION) }
    var deviceToEdit by remember { mutableStateOf<DeviceEntity?>(null) }

    val filteredDevices = remember(devices, searchQuery, selectedSort) {
        val query = searchQuery.trim().lowercase()
        val list = devices.filter { dev ->
            query.isEmpty() ||
            dev.hostName.lowercase().contains(query) ||
            dev.customNickname.lowercase().contains(query) ||
            dev.ipAddress.contains(query) ||
            dev.macAddress.lowercase().contains(query)
        }

        when (selectedSort) {
            DeviceSortOption.CONSUMPTION -> list.sortedByDescending { it.dataUsedBytes }
            DeviceSortOption.ACTIVE -> list.sortedByDescending { it.isConnected }
            DeviceSortOption.NAME -> list.sortedBy { (it.customNickname.ifBlank { it.hostName }).lowercase() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("device_search_input"),
            placeholder = { Text("البحث بالاسم أو عنوان IP أو MAC…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث"
                )
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Technical Notice Card
        TechnicalDisclaimerCard()

        Spacer(modifier = Modifier.height(10.dp))

        // Sort Options Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "ترتيب",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            DeviceSortOption.values().forEach { option ->
                FilterChip(
                    selected = selectedSort == option,
                    onClick = { selectedSort = option },
                    label = { Text(option.titleArabic, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Devices List
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "لا توجد أجهزة مطابقة للبحث" else "لم يتم العثور على أجهزة متصلة بالمودم حاليًا",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDevices, key = { it.macAddress }) { device ->
                    DeviceCardItem(
                        device = device,
                        onEditClicked = { deviceToEdit = device }
                    )
                }
            }
        }
    }

    // Edit Nickname Dialog
    deviceToEdit?.let { dev ->
        EditNicknameDialog(
            device = dev,
            onDismiss = { deviceToEdit = null },
            onConfirm = { newName ->
                onUpdateNickname(dev.macAddress, newName)
                deviceToEdit = null
            }
        )
    }
}

@Composable
private fun TechnicalDisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "معلومات",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "ملاحظة: تظهر هذه القائمة الأجهزة المتصلة بمودم ZTE MF293N. يتم فرز الاستهلاك وفق العدادات المسجلة أو المكتشفة من المودم دون أرقام تقديرية مضللة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceCardItem(
    device: DeviceEntity,
    onEditClicked: () -> Unit
) {
    val displayName = device.customNickname.ifBlank { device.hostName }
    val isCustomized = device.customNickname.isNotBlank()
    val statusColor = if (device.isConnected) SuccessEmerald else MaterialTheme.colorScheme.outline
    val icon = chooseDeviceIcon(device.hostName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_item_${device.macAddress}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Type Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = displayName,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCustomized) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${device.hostName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "IP: ${device.ipAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (device.isConnected) "متصل الآن" else "غير متصل",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                    if (device.dataUsedBytes > 0) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "الاستهلاك: ${FormatUtils.formatBytes(device.dataUsedBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberCyan
                        )
                    }
                }
            }

            // Edit Name Button
            IconButton(
                onClick = onEditClicked,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "تعديل اسم الجهاز",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun chooseDeviceIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("tv") || lower.contains("smart") -> Icons.Default.Tv
        lower.contains("phone") || lower.contains("galaxy") || lower.contains("iphone") || lower.contains("redmi") || lower.contains("pixel") -> Icons.Default.PhoneAndroid
        lower.contains("mac") || lower.contains("pc") || lower.contains("laptop") || lower.contains("desktop") -> Icons.Default.Computer
        else -> Icons.Default.Wifi
    }
}

@Composable
private fun EditNicknameDialog(
    device: DeviceEntity,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var text by remember { mutableStateOf(device.customNickname.ifBlank { device.hostName }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسمية الجهاز") },
        text = {
            Column {
                Text(
                    text = "عيّن اسمًا مألوفًا للجهاز (${device.macAddress}):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("اسم الجهاز") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
