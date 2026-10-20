package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WeatherDay
import com.shuiyu.octoberrain.core.common.WeatherHour
import com.shuiyu.octoberrain.core.common.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 天气数据客户端。
 *
 * 优先使用配置中的经纬度，缺失时依次尝试三个 IP 定位服务；随后调用 Open-Meteo
 * 获取实时天气、逐时温度与七日预报。请求带超时，全部失败时回退到六小时内的
 * 缓存结果或 null。
 */
class WeatherClient {
    companion object {
        private const val TAG = "天气"
        private const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        private const val CACHE_TTL_MS = 30L * 60L * 1000L
        private const val STALE_TTL_MS = 6L * 60L * 60L * 1000L
        private const val LOCATE_TIMEOUT_MS = 8000L
        private const val REQUEST_TIMEOUT_MS = 6000
        private const val HOURLY_COUNT = 24

        private val LOCATION_PROVIDERS =
            listOf(
                "https://ipwho.is/",
                "https://ipapi.co/json/",
                "https://ip.sb/geoip",
            )
    }

    private var cachedAt = 0L
    private var cachedSnapshot: WeatherSnapshot? = null

    /** 清除天气缓存。 */
    fun invalidate() {
        cachedAt = 0L
        cachedSnapshot = null
    }

    /** 是否启用天气服务。 */
    fun isEnabled(): Boolean = ContentPaths.integrations().weatherEnabled

    /** 读取天气，未启用、定位失败且无缓存时返回 null。 */
    suspend fun current(): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            if (!isEnabled()) return@withContext null
            val config = ContentPaths.integrations()
            val configuredCity = config.weatherCity.trim()
            val configuredLatitude = config.weatherLat?.takeIf { isValidLatitude(it) }
            val configuredLongitude = config.weatherLon?.takeIf { isValidLongitude(it) }
            if (configuredLatitude != null &&
                configuredLongitude != null &&
                !(configuredLatitude == 0.0 && configuredLongitude == 0.0)
            ) {
                if (cachedSnapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                    return@withContext cachedSnapshot
                }
                val snapshot = fetch(configuredLatitude, configuredLongitude, configuredCity)
                if (snapshot != null) {
                    cache(snapshot)
                    return@withContext snapshot
                }
                return@withContext fallbackSnapshot()
            }
            if (cachedSnapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return@withContext cachedSnapshot
            }
            val located = locate()
            if (located == null) {
                InkLog.w(TAG, "定位失败，返回缓存或空结果")
                return@withContext fallbackSnapshot()
            }
            val city = configuredCity.ifBlank { located.city }
            val snapshot = fetch(located.latitude, located.longitude, city)
            if (snapshot != null) {
                cache(snapshot)
                return@withContext snapshot
            }
            fallbackSnapshot()
        }

    /** 返回可接受的缓存快照，超过六小时视为过期。 */
    private fun fallbackSnapshot(): WeatherSnapshot? {
        val snapshot = cachedSnapshot ?: return null
        return if (System.currentTimeMillis() - cachedAt <= STALE_TTL_MS) snapshot else null
    }

    /** 记录成功结果。 */
    private fun cache(snapshot: WeatherSnapshot) {
        cachedSnapshot = snapshot
        cachedAt = System.currentTimeMillis()
    }

    /** IP 定位结果。 */
    private class Location(
        val latitude: Double,
        val longitude: Double,
        val city: String,
    )

    /** 依次尝试定位服务，累计超时不超过八秒。 */
    private fun locate(): Location? {
        val deadline = System.currentTimeMillis() + LOCATE_TIMEOUT_MS
        for (provider in LOCATION_PROVIDERS) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining < 800L) break
            val timeout = minOf(remaining, 5000L).toInt()
            val payload = getJson(provider, timeout) ?: continue
            val location = parseLocation(payload)
            if (location != null) return location
        }
        return null
    }

    /** 解析定位响应中的经纬度与城市，兼容字符串与数值两种字段类型。 */
    private fun parseLocation(payload: JSONObject): Location? {
        if (payload.has("success") && !payload.optBoolean("success", true)) return null
        val latitude = readDouble(payload, "latitude")
        val longitude = readDouble(payload, "longitude")
        if (latitude == null || longitude == null) return null
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        val city =
            payload
                .optString("city")
                .ifBlank { payload.optString("region") }
                .ifBlank { payload.optString("country_name") }
        return Location(latitude, longitude, city)
    }

    /** 读取数值字段，字符串形式同样接受。 */
    private fun readDouble(
        payload: JSONObject,
        key: String,
    ): Double? {
        if (!payload.has(key)) return null
        return payload.optDouble(key, Double.NaN).takeIf { !it.isNaN() }
            ?: payload.optString(key).toDoubleOrNull()
    }

    /** 纬度取值范围校验。 */
    private fun isValidLatitude(value: Double): Boolean = value in -90.0..90.0

    /** 经度取值范围校验。 */
    private fun isValidLongitude(value: Double): Boolean = value in -180.0..180.0

    /** 请求 Open-Meteo 并组装天气快照。 */
    private fun fetch(
        latitude: Double,
        longitude: Double,
        city: String,
    ): WeatherSnapshot? {
        val url =
            buildString {
                append(FORECAST_ENDPOINT)
                append("?latitude=").append(String.format(Locale.US, "%.3f", latitude))
                append("&longitude=").append(String.format(Locale.US, "%.3f", longitude))
                append("&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature")
                append("&hourly=temperature_2m,weather_code")
                append("&daily=weather_code,temperature_2m_max,temperature_2m_min")
                append("&forecast_days=7&timezone=auto")
            }
        val payload = getJson(url, REQUEST_TIMEOUT_MS) ?: return null
        val current = payload.optJSONObject("current") ?: return null
        val currentCode = current.optInt("weather_code", 0)
        val temperature = current.optDouble("temperature_2m", Double.NaN)
        if (temperature.isNaN()) return null
        val currentTime = current.optString("time")
        val hourly = parseHourly(payload, currentTime)
        val days = parseDaily(payload)
        val today = days.firstOrNull()
        val humidity = current.optInt("relative_humidity_2m", -1)
        return WeatherSnapshot(
            city = city.ifBlank { "当前位置" },
            temperature = temperature.toInt(),
            condition =
                describeWeatherCode(currentCode).let { description ->
                    if (humidity in 0..100) "$description 湿度 $humidity%" else description
                },
            code = currentCode.toString(),
            high = today?.high ?: temperature.toInt(),
            low = today?.low ?: temperature.toInt(),
            updatedAt = System.currentTimeMillis(),
            hourly = hourly,
            daily = days,
        )
    }

    /** 解析逐时温度与天气代码，从当前小时起保留 24 条。 */
    private fun parseHourly(
        payload: JSONObject,
        currentTime: String,
    ): List<WeatherHour> {
        val hourly = payload.optJSONObject("hourly") ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temperatures = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code")
        val startIndex =
            if (currentTime.isBlank()) {
                0
            } else {
                (0 until times.length()).firstOrNull { times.optString(it) >= currentTime } ?: 0
            }
        val result = ArrayList<WeatherHour>(HOURLY_COUNT)
        var index = startIndex
        while (index < times.length() && result.size < HOURLY_COUNT) {
            val temperature = temperatures.optDouble(index, Double.NaN)
            if (!temperature.isNaN()) {
                result.add(
                    WeatherHour(
                        hourLabel = formatHour(times.optString(index)),
                        temperature = temperature.toInt(),
                        code = codes?.optInt(index, 0)?.toString().orEmpty(),
                    ),
                )
            }
            index += 1
        }
        return result
    }

    /** 解析七日预报。 */
    private fun parseDaily(payload: JSONObject): List<WeatherDay> {
        val daily = payload.optJSONObject("daily") ?: return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val highs = daily.optJSONArray("temperature_2m_max") ?: return emptyList()
        val lows = daily.optJSONArray("temperature_2m_min") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val result = ArrayList<WeatherDay>(times.length())
        for (index in 0 until times.length()) {
            val high = highs.optDouble(index, Double.NaN)
            val low = lows.optDouble(index, Double.NaN)
            if (high.isNaN() || low.isNaN()) continue
            result.add(
                WeatherDay(
                    label = formatDay(times.optString(index)),
                    high = high.toInt(),
                    low = low.toInt(),
                    code = codes?.optInt(index, 0)?.toString().orEmpty(),
                ),
            )
        }
        return result
    }

    /** 把 ISO 时间转换为小时标签。 */
    private fun formatHour(isoTime: String): String {
        val hour = isoTime.substringAfter('T', "").take(2)
        return if (hour.toIntOrNull() != null) "${hour.toInt()}时" else isoTime
    }

    /** 把 ISO 日期转换为月日标签。 */
    private fun formatDay(isoDate: String): String {
        val parts = isoDate.split('-')
        if (parts.size != 3) return isoDate
        val month = parts[1].toIntOrNull() ?: return isoDate
        val day = parts[2].toIntOrNull() ?: return isoDate
        return "$month/$day"
    }

    /** 天气代码转中文描述。 */
    private fun describeWeatherCode(code: Int): String =
        when (code) {
            0 -> "晴"
            1 -> "大部晴朗"
            2 -> "多云"
            3 -> "阴"
            45, 48 -> "雾"
            51, 53, 55 -> "毛毛雨"
            56, 57 -> "冻毛毛雨"
            61 -> "小雨"
            63 -> "中雨"
            65 -> "大雨"
            66, 67 -> "冻雨"
            71 -> "小雪"
            73 -> "中雪"
            75 -> "大雪"
            77 -> "雪粒"
            80 -> "小阵雨"
            81 -> "阵雨"
            82 -> "强阵雨"
            85 -> "小阵雪"
            86 -> "强阵雪"
            95 -> "雷阵雨"
            96, 99 -> "雷阵雨伴冰雹"
            else -> "未知天气"
        }

    /** 发起 GET 请求并解析 JSON，异常返回 null。 */
    private fun getJson(
        url: String,
        timeoutMs: Int,
    ): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "OctoberLauncher/1.0")
                }
            val status = connection.responseCode
            if (status !in 200..299) {
                InkLog.w(TAG, "请求 $url 返回 HTTP $status")
                null
            } else {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    JSONObject(reader.readText())
                }
            }
        } catch (error: IOException) {
            InkLog.w(TAG, "请求 $url 失败：${error.message}")
            null
        } catch (error: JSONException) {
            InkLog.w(TAG, "响应无法解析：${error.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
