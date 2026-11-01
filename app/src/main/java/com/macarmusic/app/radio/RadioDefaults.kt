package com.macarmusic.app.radio

import com.macarmusic.app.R
import java.io.IOException

object RadioDefaults {
    const val CRI_ID = "radio:cri:905"
    const val MAX_PINS = 10
    val stations = CnrRadio.stations + RadioStation(
        905, "905", R.string.radio_cri_news,
        "https://p1crires.cri.cn/photoAlbum/cri/templet/common/DEPA1629939674468309/hqzx_logo.png",
        network = "cri", address = "https://sk.cri.cn/905.m3u8",
    )
    val corePrograms = stations.map { it.program }
    val bundled: List<RadioProgram> by lazy {
        var saved = emptyList<RadioProgram>()
        for (resource in listOf("/radio/sample-stations.m3u", "/radio/international-stations.m3u")) {
            val parsed = (RadioDefaults::class.java.getResourceAsStream(resource)
                ?: throw IOException("Missing bundled radio list: $resource")).use { RadioList.parse(it) }
            if (parsed.invalid != 0 || parsed.entries.isEmpty())
                throw IOException("Invalid bundled radio list: $resource")
            saved = RadioList.merge(saved, parsed.entries, emptyMap(), RadioImportMode.APPEND).programs
        }
        RadioList.catalog(saved).map { if (it.builtin != null) it.copy(name = "") else it }
    }
    private val byId = stations.associateBy { it.id }
    val pins = listOf(
        "radio:cnr:zgzs", "radio:cnr:jjzs", "radio:cnr:yyzs", "radio:cnr:gsgljtgb",
        CRI_ID, "radio:cnr:xczs", "radio:cnr:wyzs", "radio:cnr:dszs",
    )
    fun find(id: String?) = byId[id]
    fun addressId(uri: String): String? = CnrRadio.manifestStation(uri)?.id
        ?: RadioAddress.parse(uri)?.takeIf {
            it.host.equals("sk.cri.cn", true) && it.path == "/905.m3u8"
        }?.let { CRI_ID }
}
