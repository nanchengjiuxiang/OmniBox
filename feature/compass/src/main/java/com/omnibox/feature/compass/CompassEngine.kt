package com.omnibox.feature.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class CompassReading(
    val azimuthDeg: Float? = null,      // 有磁力计时才是真实磁北方位角
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val fieldStrengthUt: Float? = null,
    val magneticAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
)

data class DirectionName(val chinese: String, val abbr: String)

/** 16 方位罗盘名称（角度 0=北，顺时针递增）。 */
val DIRECTIONS_16 = listOf(
    DirectionName("北", "N"),
    DirectionName("东北偏北", "NNE"),
    DirectionName("东北", "NE"),
    DirectionName("东北偏东", "ENE"),
    DirectionName("东", "E"),
    DirectionName("东南偏东", "ESE"),
    DirectionName("东南", "SE"),
    DirectionName("东南偏南", "SSE"),
    DirectionName("南", "S"),
    DirectionName("西南偏南", "SSW"),
    DirectionName("西南", "SW"),
    DirectionName("西南偏西", "WSW"),
    DirectionName("西", "W"),
    DirectionName("西北偏西", "WNW"),
    DirectionName("西北", "NW"),
    DirectionName("西北偏北", "NNW")
)

fun normalizeDeg(deg: Float): Float = ((deg % 360f) + 360f) % 360f

fun directionOf(azimuth: Float): DirectionName =
    DIRECTIONS_16[(azimuth / 22.5f).roundToInt() % 16]

fun accuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "高精度"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "中精度"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "低精度 · 建议八字校准"
    else -> "不可靠 · 需校准"
}

fun fieldAssessment(ut: Float): String = when {
    ut < 20f -> "磁场偏弱 · 可能受干扰"
    ut > 65f -> "磁场偏强 · 可能受干扰"
    else -> "环境磁场正常"
}

/** 真北校正结果：全部来自真实定位缓存与 GeomagneticField 计算。 */
data class TrueNorthInfo(
    val declinationDeg: Float,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?
)

fun formatDeclination(deg: Float): String {
    val a = abs(deg)
    val d = a.toInt()
    val m = ((a - d) * 60).toInt()
    val sign = if (deg >= 0) "+" else "-"
    val dir = if (deg >= 0) "偏东" else "偏西"
    return "$sign$d° $m' ($dir)"
}

fun formatDms(value: Double, positive: String, negative: String): String {
    val hemi = if (value >= 0) positive else negative
    val a = abs(value)
    val deg = a.toInt()
    val minFull = (a - deg) * 60.0
    val min = minFull.toInt()
    val sec = ((minFull - min) * 60).toInt()
    return "$deg° $min' $sec\" $hemi"
}

/**
 * 太阳方位角粗估：仅用当前本地时间做粗略推算，明确标注为估算，不冒充传感器读数。
 * 北半球规律：6:00 正东(90°)，12:00 正南(180°)，18:00 正西(270°)，每小时约 15°。
 */
object SolarAzimuth {
    fun estimateDeg(): Double {
        val cal = Calendar.getInstance() // 设备本地时间
        val hour = cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60.0 +
            cal.get(Calendar.SECOND) / 3600.0
        val deg = 90.0 + (hour - 6.0) * 15.0
        return ((deg % 360.0) + 360.0) % 360.0
    }
}

/**
 * 指南针平台封装：SensorManager 加速度计 + 磁力计（无磁力计时用 GRAVITY 降级），
 * getRotationMatrix + getOrientation 得方位角，低通滤波平滑；
 * 可选定位权限下用 GeomagneticField 计算磁偏角得到真北。
 */
class CompassEngine(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    val hasMagnetometer: Boolean get() = magnetometer != null

    private val _readings = MutableStateFlow<CompassReading?>(null)
    val readings: StateFlow<CompassReading?> = _readings.asStateFlow()

    private var handlerThread: HandlerThread? = null
    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothAzimuth = 0.0
    private var hasAzimuth = false

    @Volatile
    private var magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy = accuracy
        }

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY ->
                    gravityValues = lowPass(gravityValues, event.values)
                Sensor.TYPE_MAGNETIC_FIELD ->
                    geomagneticValues = lowPass(geomagneticValues, event.values)
            }
            compute()
        }
    }

    private fun lowPass(prev: FloatArray?, input: FloatArray): FloatArray {
        val out = prev?.copyOf() ?: input.copyOf()
        for (i in input.indices) {
            out[i] = out[i] * 0.85f + input[i] * 0.15f
        }
        return out
    }

    private fun compute() {
        val g = gravityValues ?: return
        val ax = g[0]; val ay = g[1]; val az = g[2]
        val norm = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(0.0001f)
        val gx = ax / norm; val gy = ay / norm; val gz = az / norm
        val roll = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2(-gy.toDouble(), sqrt((gx * gx + gz * gz).toDouble()))).toFloat()

        var azimuth: Float? = null
        var fieldUt: Float? = null
        if (hasMagnetometer) {
            val m = geomagneticValues
            if (m != null) {
                fieldUt = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
                if (SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val raw = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                    azimuth = smoothAngle(raw).toFloat()
                }
            }
        }
        _readings.value = CompassReading(
            azimuthDeg = azimuth,
            pitchDeg = pitch,
            rollDeg = roll,
            fieldStrengthUt = fieldUt,
            magneticAccuracy = magneticAccuracy
        )
    }

    /** 圆周量低通滤波：用 sin/cos 分量插值，避免 359°→0° 跳变。 */
    private fun smoothAngle(newDeg: Double): Double {
        val alpha = 0.15
        if (!hasAzimuth) {
            smoothAzimuth = newDeg
            hasAzimuth = true
            return newDeg
        }
        val cur = Math.toRadians(smoothAzimuth)
        val nxt = Math.toRadians(newDeg)
        val x = (1 - alpha) * cos(cur) + alpha * cos(nxt)
        val y = (1 - alpha) * sin(cur) + alpha * sin(nxt)
        smoothAzimuth = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return smoothAzimuth
    }

    /** 由页面 DisposableEffect 调用，注册监听；回调跑在独立 HandlerThread 上，不阻塞主线程。 */
    fun register() {
        if (handlerThread != null) return
        val thread = HandlerThread("CompassSensors").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI, handler)
        }
        if (hasMagnetometer) {
            magnetometer?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI, handler)
            }
        } else {
            gravitySensor?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI, handler)
            }
        }
    }

    /** 页面退出必须注销监听并退出后台线程。 */
    fun unregister() {
        sensorManager.unregisterListener(listener)
        handlerThread?.quitSafely()
        handlerThread = null
    }

    /** 重新扫描硬件传感器，返回当前是否可用磁力计。 */
    fun rescan(): Boolean {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        return hasMagnetometer
    }

    /**
     * 可选定位权限下的真实磁偏角。无权限、无定位缓存、SecurityException 时返回 null，
     * 由上层明确显示「仅磁北可用」，绝不伪造真北数值。调用方需放在 Dispatchers.IO。
     */
    fun computeTrueNorth(): TrueNorthInfo? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc: Location = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        } ?: return null
        val field = GeomagneticField(
            loc.latitude.toFloat(),
            loc.longitude.toFloat(),
            (if (loc.hasAltitude()) loc.altitude else 0.0).toFloat(),
            System.currentTimeMillis()
        )
        return TrueNorthInfo(
            declinationDeg = field.declination,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeM = if (loc.hasAltitude()) loc.altitude else null
        )
    }
}
