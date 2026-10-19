package com.macarmusic.app.lyrics

data class LyricProviderDefinition(
    val origin: LyricOrigin,
    val routes: List<LyricSearchRoute>,
    val hosts: Set<String>,
    val enabled: Boolean = true,
)

/** Build-time provider configuration. New protocols also need a route adapter in OnlineLyricsClient. */
object LyricProviderConfig {
    val providers = listOf(
        LyricProviderDefinition(LyricOrigin.KUGOU,
            listOf(LyricSearchRoute.KUGOU_DIRECT, LyricSearchRoute.KUGOU_MOBILE),
            setOf("lyrics.kugou.com", "mobilecdn.kugou.com")),
        LyricProviderDefinition(LyricOrigin.QQ, listOf(LyricSearchRoute.QQ), setOf("c.y.qq.com")),
        LyricProviderDefinition(LyricOrigin.NETEASE, listOf(LyricSearchRoute.NETEASE), setOf("music.163.com")),
        LyricProviderDefinition(LyricOrigin.KUWO, listOf(LyricSearchRoute.KUWO),
            setOf("search.kuwo.cn", "www.kuwo.cn")),
    )

    init {
        require(providers.map { it.origin }.distinct().size == providers.size)
        require(providers.flatMap { it.routes }.distinct().size == providers.sumOf { it.routes.size })
        for (provider in providers) {
            require(provider.routes.isNotEmpty() && provider.routes.all { it.origin == provider.origin })
            require(provider.hosts.isNotEmpty() && provider.hosts.all { Regex("[a-z0-9.-]+").matches(it) })
        }
    }

    val routes = providers.filter { it.enabled }.flatMap { it.routes }
    val allowedHosts = providers.filter { it.enabled }.flatMap { it.hosts }.toSet()
}
