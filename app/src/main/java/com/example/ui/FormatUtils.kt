package com.example.ui

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    private val decFormat = DecimalFormat("#,##0.00")
    private val singleDecFormat = DecimalFormat("#,##0.0")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 ميجابايت"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> "${singleDecFormat.format(gb)} جيجابايت"
            mb >= 1.0 -> "${singleDecFormat.format(mb)} ميجابايت"
            else -> "${singleDecFormat.format(kb)} كيلوبايت"
        }
    }

    fun formatBytesShort(bytes: Long): Pair<String, String> {
        if (bytes <= 0) return Pair("0", "ميجابايت")
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> Pair(decFormat.format(gb), "جيجابايت")
            mb >= 1.0 -> Pair(decFormat.format(mb), "ميجابايت")
            else -> Pair(decFormat.format(kb), "كيلوبايت")
        }
    }

    fun formatSpeed(kbps: Long): String {
        return if (kbps >= 1024) {
            val mbps = kbps / 1024.0
            "${singleDecFormat.format(mbps)} ميغابت/ث"
        } else {
            "$kbps كيلوبت/ث"
        }
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "لم يتم التحديث بعد"
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale("ar"))
        return sdf.format(Date(timestamp))
    }
}
