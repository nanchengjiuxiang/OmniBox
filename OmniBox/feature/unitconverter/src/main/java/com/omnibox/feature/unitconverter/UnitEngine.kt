package com.omnibox.feature.unitconverter

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** 物理量枚举，对应 unit_converter/code.html 顶部 7 个分类。 */
enum class UnitKind(val label: String, val icon: String) {
    LENGTH("长度", "straighten"),
    WEIGHT("重量", "fitness_center"),
    TEMPERATURE("温度", "device_thermostat"),
    AREA("面积", "crop_square"),
    VOLUME("体积", "science"),
    DATA("数据存储", "hard_drive"),
    EXCHANGE("离线汇率", "currency_exchange")
}

/**
 * 线性换算模型：base = value * factor + offset。
 * 温度用 offset 表达摄氏/华氏/开尔文的偏移；其余单位 offset 恒为 0。
 */
data class UnitDef(
    val id: String,
    val label: String,
    val factor: BigDecimal,
    val offset: BigDecimal = BigDecimal.ZERO,
    val hint: String = ""
)

object UnitEngine {
    private fun bd(v: String) = BigDecimal(v)
    private val MC = MathContext.DECIMAL128

    val units: Map<UnitKind, List<UnitDef>> = mapOf(
        UnitKind.LENGTH to listOf(
            UnitDef("m", "米 (m)", bd("1"), hint = "国际公制基础单位"),
            UnitDef("km", "千米 (km)", bd("1000")),
            UnitDef("cm", "厘米 (cm)", bd("0.01")),
            UnitDef("mm", "毫米 (mm)", bd("0.001")),
            UnitDef("ft", "英尺 (ft)", bd("0.3048"), hint = "英制长度单位 (Foot)"),
            UnitDef("in", "英寸 (in)", bd("0.0254")),
            UnitDef("mi", "英里 (mi)", bd("1609.344")),
            UnitDef("yd", "码 (yd)", bd("0.9144"))
        ),
        UnitKind.WEIGHT to listOf(
            UnitDef("kg", "千克 (kg)", bd("1")),
            UnitDef("g", "克 (g)", bd("0.001")),
            UnitDef("mg", "毫克 (mg)", bd("0.000001")),
            UnitDef("t", "吨 (t)", bd("1000")),
            UnitDef("lb", "磅 (lb)", bd("0.45359237")),
            UnitDef("oz", "盎司 (oz)", bd("0.028349523125"))
        ),
        UnitKind.TEMPERATURE to listOf(
            UnitDef("c", "摄氏度 (°C)", bd("1")),
            UnitDef("f", "华氏度 (°F)", bd("5").divide(bd("9"), MC), bd("-160").divide(bd("9"), MC)),
            UnitDef("k", "开尔文 (K)", bd("1"), bd("-273.15"))
        ),
        UnitKind.AREA to listOf(
            UnitDef("m2", "平方米 (m²)", bd("1")),
            UnitDef("km2", "平方千米 (km²)", bd("1000000")),
            UnitDef("cm2", "平方厘米 (cm²)", bd("0.0001")),
            UnitDef("ha", "公顷 (ha)", bd("10000")),
            UnitDef("ft2", "平方英尺 (ft²)", bd("0.09290304"))
        ),
        UnitKind.VOLUME to listOf(
            UnitDef("l", "升 (L)", bd("1")),
            UnitDef("ml", "毫升 (mL)", bd("0.001")),
            UnitDef("m3", "立方米 (m³)", bd("1000")),
            UnitDef("gal", "美制加仑 (gal)", bd("3.785411784"))
        ),
        UnitKind.DATA to listOf(
            UnitDef("b", "字节 (B)", bd("1")),
            UnitDef("kb", "千字节 (KB)", bd("1024")),
            UnitDef("mb", "兆字节 (MB)", bd("1048576")),
            UnitDef("gb", "吉字节 (GB)", bd("1073741824")),
            UnitDef("tb", "太字节 (TB)", bd("1099511627776")),
            UnitDef("bit", "比特 (bit)", bd("0.125"))
        ),
        // 内置离线汇率快照（相对 CNY），不联网更新；界面必须标注为快照值
        UnitKind.EXCHANGE to listOf(
            UnitDef("cny", "人民币 (CNY)", bd("1")),
            UnitDef("usd", "美元 (USD)", bd("7.12")),
            UnitDef("eur", "欧元 (EUR)", bd("7.74")),
            UnitDef("jpy", "日元 (JPY)", bd("0.047")),
            UnitDef("hkd", "港元 (HKD)", bd("0.91"))
        )
    )

    fun defaultFrom(kind: UnitKind): UnitDef = units.getValue(kind).first()
    fun defaultTo(kind: UnitKind): UnitDef = units.getValue(kind).getOrElse(1) { units.getValue(kind).first() }

    /** BigDecimal 无损换算：base = value*factor + offset，目标 = (base - offset)/factor。 */
    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef): BigDecimal? = runCatching {
        val base = value.multiply(from.factor).add(from.offset)
        base.subtract(to.offset).divide(to.factor, MC)
    }.getOrNull()

    fun format(value: BigDecimal?, scale: Int): String = value?.let {
        it.setScale(scale, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
    } ?: "—"

    /** 常用换算基准速查：全部基于当前单位表实时计算，不写死设计稿的 3.28084。 */
    fun references(kind: UnitKind, scale: Int = 5): List<Triple<String, String, String>> {
        val list = units.getValue(kind)
        val from = list.first()
        return list.drop(1).take(4).map { to ->
            Triple("1 ${from.label} =", format(convert(BigDecimal.ONE, from, to), scale), to.label)
        }
    }

    /** 两级比例尺度透视：源量级比例 / 目标单位扩展比。 */
    fun ratio(from: UnitDef, to: UnitDef): String =
        format(from.factor.divide(to.factor, MC), 5)
}
