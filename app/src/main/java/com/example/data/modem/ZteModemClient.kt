package com.example.data.modem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ModemConnectionState {
    object Idle : ModemConnectionState()
    object Checking : ModemConnectionState()
    data class Connected(
        val ip: String,
        val model: String = "ZTE MF293N",
        val currentDownloadKbps: Long = 0,
        val currentUploadKbps: Long = 0,
        val connectedDeviceCount: Int = 0,
        val rxBytesMonthly: Long = 0,
        val txBytesMonthly: Long = 0,
        val signalStrengthPercent: Int = 0,
        val networkType: String = "4G LTE",
        val details: String = "متصل بنجاح بالمودم"
    ) : ModemConnectionState()
    data class AuthRequired(
        val ip: String,
        val message: String = "المودم متصل ويتطلب تسجيل الدخول"
    ) : ModemConnectionState()
    data class Unreachable(
        val ip: String,
        val reason: String = "تعذر الاتصال بالمودم (تأكد من الاتصال بشبكة واي فاي ZTE MF293N)"
    ) : ModemConnectionState()
}

data class DiscoveredModemStation(
    val mac: String,
    val ip: String,
    val hostname: String,
    val isConnected: Boolean = true
)

data class ModemSyncResult(
    val isSuccess: Boolean,
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0,
    val downloadSpeedKbps: Long = 0,
    val uploadSpeedKbps: Long = 0,
    val stations: List<DiscoveredModemStation> = emptyList(),
    val rawMessage: String = ""
)

class ZteModemClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Probes ZTE MF293N local gateway endpoint.
     * Uses the standard ZTE goform endpoint used across ZTE LTE routers:
     * http://192.168.0.1/goform/goform_get_cmd_process?isTest=false&cmd=traffic_result,station_list,network_info
     */
    suspend fun probeAndFetch(gatewayIp: String = "192.168.0.1"): Pair<ModemConnectionState, ModemSyncResult?> = withContext(Dispatchers.IO) {
        val cleanIp = gatewayIp.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val baseUrl = "http://$cleanIp"
        val cmdUrl = "$baseUrl/goform/goform_get_cmd_process?isTest=false&cmd=traffic_result,station_list,network_info,modem_msisdn,wa_inner_version"

        try {
            val request = Request.Builder()
                .url(cmdUrl)
                .addHeader("Referer", "$baseUrl/index.html")
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; ZTE Monitor)")
                .addHeader("Accept", "application/json, text/javascript, */*")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Try fallback check to root
                    return@withContext checkRootFallback(baseUrl, cleanIp)
                }

                val bodyString = response.body?.string() ?: ""
                if (bodyString.isBlank()) {
                    return@withContext checkRootFallback(baseUrl, cleanIp)
                }

                // Parse ZTE response JSON
                parseZteResponse(cleanIp, bodyString)
            }
        } catch (e: IOException) {
            // Unreachable or offline from router Wi-Fi
            Pair(
                ModemConnectionState.Unreachable(
                    ip = cleanIp,
                    reason = "تعذر الوصول للمودم على $cleanIp (تأكد من الاتصال بشبكة واي فاي المودم)"
                ),
                null
            )
        } catch (e: Exception) {
            Pair(
                ModemConnectionState.Unreachable(
                    ip = cleanIp,
                    reason = "خطأ في الاتصال: ${e.localizedMessage ?: "فشل غير معروف"}"
                ),
                null
            )
        }
    }

    private fun checkRootFallback(baseUrl: String, ip: String): Pair<ModemConnectionState, ModemSyncResult?> {
        return try {
            val rootRequest = Request.Builder()
                .url(baseUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; ZTE Monitor)")
                .get()
                .build()
            httpClient.newCall(rootRequest).execute().use { rootResponse ->
                if (rootResponse.isSuccessful) {
                    val html = rootResponse.body?.string() ?: ""
                    if (html.contains("ZTE", ignoreCase = true) || html.contains("MF293", ignoreCase = true) || html.contains("router", ignoreCase = true)) {
                        Pair(
                            ModemConnectionState.Connected(
                                ip = ip,
                                model = "ZTE MF293N",
                                details = "تم اكتشاف المودم على البوابة $ip (صفحة الإدارة متاحة)"
                            ),
                            ModemSyncResult(
                                isSuccess = true,
                                rawMessage = "المودم يستجيب على المنفذ 80"
                            )
                        )
                    } else {
                        Pair(
                            ModemConnectionState.Connected(
                                ip = ip,
                                details = "تم الرد من البوابة $ip"
                            ),
                            ModemSyncResult(isSuccess = true)
                        )
                    }
                } else {
                    Pair(
                        ModemConnectionState.Unreachable(ip = ip, reason = "استجاب المودم برمز: ${rootResponse.code}"),
                        null
                    )
                }
            }
        } catch (e: Exception) {
            Pair(
                ModemConnectionState.Unreachable(ip = ip, reason = "تعذر الاتصال بالبوابة $ip"),
                null
            )
        }
    }

    private fun parseZteResponse(ip: String, jsonStr: String): Pair<ModemConnectionState, ModemSyncResult?> {
        return try {
            val json = JSONObject(jsonStr)

            // Parse traffic counters
            val monthlyRx = json.optLong("monthly_rx_bytes", json.optLong("total_rx_bytes", 0L))
            val monthlyTx = json.optLong("monthly_tx_bytes", json.optLong("total_tx_bytes", 0L))
            val rxThr = json.optLong("realtime_rx_thr", 0L)
            val txThr = json.optLong("realtime_tx_thr", 0L)
            val networkType = json.optString("network_type", "4G LTE")

            // Parse stations
            val stationsList = mutableListOf<DiscoveredModemStation>()
            val stationArray = json.optJSONArray("station_list")
            if (stationArray != null) {
                for (i in 0 until stationArray.length()) {
                    val st = stationArray.optJSONObject(i)
                    if (st != null) {
                        val mac = st.optString("mac_address", st.optString("mac", "00:00:00:00:00:$i"))
                        val devIp = st.optString("ip_address", st.optString("ip", "192.168.0.${100 + i}"))
                        val name = st.optString("host_name", st.optString("hostname", "جهاز ${i + 1}"))
                        stationsList.add(DiscoveredModemStation(mac = mac, ip = devIp, hostname = name))
                    }
                }
            }

            val connectedCount = if (stationsList.isNotEmpty()) stationsList.size else json.optInt("station_num", 0)

            val state = ModemConnectionState.Connected(
                ip = ip,
                model = "ZTE MF293N",
                currentDownloadKbps = rxThr / 1024,
                currentUploadKbps = txThr / 1024,
                connectedDeviceCount = connectedCount,
                rxBytesMonthly = monthlyRx,
                txBytesMonthly = monthlyTx,
                networkType = networkType,
                details = "متصل بمودم ZTE MF293N"
            )

            val syncResult = ModemSyncResult(
                isSuccess = true,
                downloadBytes = monthlyRx,
                uploadBytes = monthlyTx,
                downloadSpeedKbps = rxThr / 1024,
                uploadSpeedKbps = txThr / 1024,
                stations = stationsList,
                rawMessage = "تم سحب البيانات بنجاح"
            )

            Pair(state, syncResult)
        } catch (e: Exception) {
            // Not JSON or partial data
            Pair(
                ModemConnectionState.Connected(
                    ip = ip,
                    details = "متصل بمودم ZTE (استجابة غير قياسية)"
                ),
                ModemSyncResult(isSuccess = true, rawMessage = "اتصال نشط")
            )
        }
    }
}
