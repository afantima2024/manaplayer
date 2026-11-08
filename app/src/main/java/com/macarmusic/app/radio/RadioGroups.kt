package com.macarmusic.app.radio

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import com.macarmusic.app.R
import java.util.Locale

enum class RadioSection(@StringRes val label: Int) {
    CHINA(R.string.radio_section_china),
    INTERNATIONAL(R.string.radio_section_international),
    OTHER(R.string.radio_section_other),
}

data class RadioGroup(val id: String, @StringRes val label: Int = 0, val imported: String = "",
    val section: RadioSection = RadioSection.OTHER, val order: Int = 0, val countryCode: String = "") {
    fun title(context: Context): String = when {
        label != 0 -> context.getString(label)
        countryCode.isNotEmpty() -> Locale("", countryCode).getDisplayCountry(
            ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault())
        else -> imported
    }

    companion object {
        fun validId(value: String) = value.isNotBlank() && value.length <= 272 && value.none(Char::isISOControl)
    }
}

object RadioGroups {
    const val CCTV_TELEVISION = "CCTV Television"
    private data class Region(val id: String, val label: Int, val names: List<String>)
    private fun region(id: String, label: Int, names: String) = Region(id, label, names.split('|') + id)
    private val regions = listOf(
        region("beijing", R.string.radio_region_beijing, "北京"),
        region("tianjin", R.string.radio_region_tianjin, "天津"),
        region("hebei", R.string.radio_region_hebei, "河北|石家庄|唐山|保定|秦皇岛|张家口|承德|沧州|廊坊|邯郸|邢台|衡水"),
        region("shanxi", R.string.radio_region_shanxi, "山西|太原|大同|运城|晋中|临汾|长治|阳泉"),
        region("inner mongolia", R.string.radio_region_neimenggu, "内蒙古|呼和浩特|包头|赤峰|鄂尔多斯|呼伦贝尔|蒙古语综合"),
        region("liaoning", R.string.radio_region_liaoning, "辽宁|沈阳|沉阳|大连|鞍山|抚顺|锦州|丹东"),
        region("jilin", R.string.radio_region_jilin, "吉林|长春|延边|四平"),
        region("heilongjiang", R.string.radio_region_heilongjiang, "黑龙江|哈尔滨|齐齐哈尔|大庆|牡丹江|佳木斯|龙广"),
        region("shanghai", R.string.radio_region_shanghai, "上海|浦东|第一财经|东广"),
        region("jiangsu", R.string.radio_region_jiangsu, "江苏|南京|苏州|无锡|常州|徐州|南通|扬州|镇江|盐城|连云港|淮安|宿迁|泰州"),
        region("zhejiang", R.string.radio_region_zhejiang, "浙江|杭州|宁波|温州|嘉兴|湖州|绍兴|金华|台州|丽水|衢州|舟山"),
        region("anhui", R.string.radio_region_anhui, "安徽|合肥|芜湖|蚌埠|安庆|马鞍山|阜阳|滁州"),
        region("fujian", R.string.radio_region_fujian, "福建|福州|厦门|泉州|漳州|莆田|龙岩|三明|南平|宁德"),
        region("jiangxi", R.string.radio_region_jiangxi, "江西|南昌|九江|赣州|上饶|景德镇|宜春"),
        region("shandong", R.string.radio_region_shandong, "山东|济南|青岛|烟台|潍坊|淄博|威海|临沂|济宁|德州|聊城|泰安|东营|日照|滨州|菏泽|枣庄|阳信"),
        region("henan", R.string.radio_region_henan, "河南|郑州|洛阳|开封|南阳|新乡|安阳|信阳|商丘|鹤壁"),
        region("hubei", R.string.radio_region_hubei, "湖北|武汉|宜昌|襄阳|荆州|十堰|黄冈|荆门|楚天"),
        region("hunan", R.string.radio_region_hunan, "湖南|长沙|株洲|湘潭|衡阳|岳阳|常德|郴州"),
        region("guangdong", R.string.radio_region_guangdong, "广东|广州|深圳|珠海|佛山|东莞|中山|惠州|江门|肇庆|汕头|湛江|茂名|韶关|清远|梅州|揭阳|潮州|鹤山|南方生活|羊城|珠江"),
        region("guangxi", R.string.radio_region_guangxi, "广西|南宁|柳州|桂林|梧州|北海|玉林"),
        region("hainan", R.string.radio_region_hainan, "海南|海口|三亚"),
        region("chongqing", R.string.radio_region_chongqing, "重庆|巴渝|万盛|万州"),
        region("sichuan", R.string.radio_region_sichuan, "四川|成都|绵阳|德阳|乐山|南充|宜宾|泸州|自贡"),
        region("guizhou", R.string.radio_region_guizhou, "贵州|贵阳|遵义|六盘水|安顺|黔西南"),
        region("yunnan", R.string.radio_region_yunnan, "云南|昆明|曲靖|丽江|玉溪|普洱|大理|保山|香格里拉"),
        region("tibet", R.string.radio_region_xizang, "西藏|拉萨|日喀则"),
        region("shaanxi", R.string.radio_region_shaanxi, "陕西|西安|咸阳|宝鸡|渭南|延安|汉中|榆林"),
        region("gansu", R.string.radio_region_gansu, "甘肃|兰州|天水|酒泉|张掖"),
        region("qinghai", R.string.radio_region_qinghai, "青海|西宁"),
        region("ningxia", R.string.radio_region_ningxia, "宁夏|银川|石嘴山"),
        region("xinjiang", R.string.radio_region_xinjiang, "新疆|乌鲁木齐|克拉玛依|喀什|兵团"),
        region("hong kong", R.string.radio_region_hongkong, "香港|Hong Kong|RTHK|DRHK"),
        region("macao", R.string.radio_region_macao, "澳门|澳門|Macau"),
        region("taiwan", R.string.radio_region_taiwan, "台湾|台灣|台北|臺北|高雄|台中|臺中|中广|中廣|ICRT"),
        region("singapore", R.string.radio_region_singapore, "新加坡|Capital958|Class95|CNA938"),
        region("united states", R.string.radio_region_usa, "美国|美國|KEXP|SomaFM|MSNBC|NPR News|KVSP|Power FM"),
        region("united kingdom", R.string.radio_region_uk, "英国|英國|BBC|Classic FM|LBC|Times Radio"),
    )
    private val nationalNames = listOf("CNR", "CRI ", "China Plus Radio", "EasyFM", "HitFM", "NEWSPlus",
        "华语环球", "世界华声", "南海之声", "轻松调频")
    private val singaporeNames = setOf("capital fm", "class fm", "gold fm", "hao fm")
    val national = RadioGroup("national", R.string.radio_group_national, section = RadioSection.CHINA)
    val cctvTelevision = RadioGroup("cctv television", R.string.radio_group_cctv_television,
        section = RadioSection.CHINA, order = 1)
    private val china = RadioGroup("china", R.string.radio_region_china, section = RadioSection.CHINA, order = 2)
    val other = RadioGroup("other", R.string.radio_group_other)
    private val regionGroups = regions.mapIndexed { index, region ->
        val international = region.id in setOf("singapore", "united states", "united kingdom")
        RadioGroup(region.id, region.label,
            section = if (international) RadioSection.INTERNATIONAL else RadioSection.CHINA,
            order = if (international) when (region.id) { "united states" -> 0; "united kingdom" -> 1; else -> 2 }
                else index + 3)
    }.associateBy { it.id }
    private val importedRegions by lazy {
        buildMap {
            for (region in regions) {
                val group = regionGroups.getValue(region.id)
                region.names.forEach { put(RadioList.key(it), group) }
            }
            putAliases("CN|China|中国|中國|中国大陆|中國大陸", china)
            putAliases("CNR|CRI|National|National stations|全国|全国电台|中央电台|央广", national)
            putAliases("CCTV|CCTV Television|CCTV 电视|CCTV 電視|央视电视|央視電視|央视电视伴音|央視電視伴音", cctvTelevision)
            putAliases("US|USA|United States of America", regionGroups.getValue("united states"))
            putAliases("UK|GB|Great Britain", regionGroups.getValue("united kingdom"))
            putAliases("HK|Hong Kong SAR China|香港特别行政区", regionGroups.getValue("hong kong"))
            putAliases("TW|Taiwan (Province of China)", regionGroups.getValue("taiwan"))
            putAliases("MO|Macau SAR China|Macao SAR China", regionGroups.getValue("macao"))
        }
    }
    private fun MutableMap<String, RadioGroup>.putAliases(names: String, group: RadioGroup) {
        // Avoid large constant reference arrays: Android 6 x86 AOT miscompiles their write barrier after R8 lambda merging.
        names.split('|').forEach { put(RadioList.key(it), group) }
    }
    private val countries by lazy {
        buildMap {
            for (code in Locale.getISOCountries()) {
                val locale = Locale("", code)
                val group = when (code) {
                    "CN" -> china
                    "HK" -> regionGroups.getValue("hong kong")
                    "MO" -> regionGroups.getValue("macao")
                    "TW" -> regionGroups.getValue("taiwan")
                    "US" -> regionGroups.getValue("united states")
                    "GB" -> regionGroups.getValue("united kingdom")
                    "SG" -> regionGroups.getValue("singapore")
                    else -> RadioGroup("country:${code.lowercase(Locale.ROOT)}", countryCode = code,
                        section = RadioSection.INTERNATIONAL, order = 2)
                }
                for (language in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE))
                    put(RadioList.key(locale.getDisplayCountry(language)), group)
            }
        }
    }
    val order = compareBy<RadioGroup>({ it.section.ordinal }, { it.order }, { it.id })

    val defaultVisibleIds = setOf("united states", "united kingdom", "singapore", "national", "cctv television", "china",
        "beijing", "sichuan", "taiwan", "hong kong", "macao", "guangdong")

    fun defaultHiddenGroups(stations: List<RadioProgram>): Set<String> =
        (regionGroups.keys + countries.values.map { it.id } + stations.map { of(it).id } + other.id)
            .toSet() - defaultVisibleIds

    fun grouped(stations: List<RadioProgram>): List<Map.Entry<RadioGroup, List<RadioProgram>>> =
        stations.groupBy(::of).entries.sortedWith { a, b -> order.compare(a.key, b.key) }

    fun named(id: String): RadioGroup = when (id) {
        national.id -> national
        cctvTelevision.id -> cctvTelevision
        china.id -> china
        other.id -> other
        else -> regionGroups[id] ?: if (id.startsWith("country:") && id.substringAfter(':').uppercase(Locale.ROOT) in Locale.getISOCountries())
            RadioGroup(id, countryCode = id.substringAfter(':').uppercase(Locale.ROOT),
                section = RadioSection.INTERNATIONAL, order = 2)
        else RadioGroup(id, imported = id.removePrefix("import:"))
    }

    private fun regionOf(name: String) = regions.firstOrNull {
        it.names.any { alias -> name.contains(alias, ignoreCase = true) }
    }

    private fun isCctv(station: RadioProgram) =
        station.id == RadioDefaults.CCTV_ID || station.sources.any(RadioSubscriptions::isCctvAudio)

    fun of(station: RadioProgram): RadioGroup {
        if (station.group.isNotBlank()) {
            val key = RadioList.key(station.group)
            importedRegions[key]?.let {
                // Older cached CCTV feeds used National; regroup without rewriting station IDs or user caches.
                return if ((it == national || it == china) && isCctv(station)) cctvTelevision else it
            }
            countries[key]?.let { return it }
            val region = regionOf(key)?.let { regionGroups.getValue(it.id) }
            return RadioGroup("import:$key", imported = station.group,
                section = region?.section ?: RadioSection.OTHER, order = 100)
        }
        val name = RadioList.key(station.name)
        if (station.id == RadioDefaults.CCTV_ID || station.builtin == null && isCctv(station)) return cctvTelevision
        if (station.builtin != null || nationalNames.any { name.contains(it, ignoreCase = true) }) return national
        if (name in singaporeNames) return regionGroups.getValue("singapore")
        return regionOf(name)?.let { regionGroups.getValue(it.id) } ?: other
    }
}
