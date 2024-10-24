package xyz.cssxsh.mirai.tool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL

@Serializable
internal data class NetworkConfig(
    @SerialName("protocol_source")
    val protocolSource: String = "MrXiaoM/protocol-versions",
    @SerialName("protocol_version")
    val protocolVersion: String = "latest",
    @SerialName("main")
    val main: Cola,
    @SerialName("try_cdn_first")
    val tryCdnFirst: Boolean,
    @SerialName("cdn")
    val cdnList: MutableList<Cola>,
) {
    fun tryServers(startup: Boolean = false): Pair<String, Cola> {
        val cdn = cdnList.toList()
        if (tryCdnFirst) {
            for (s in cdn) {
                NetworkServiceFactory.logger.info("正在尝试连接 CDN ${s.base}")
                try {
                    val about = readText(s.base, startup)
                    NetworkServiceFactory.json.parseToJsonElement(about)
                    return about to s
                } catch (cause: Exception) {
                    NetworkServiceFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用，下次重载配置前将不连接该 CDN. ${cause.message}")
                    cdnList.removeIf { it.base == s.base }
                }
            }
        }
        NetworkServiceFactory.logger.info("正在尝试连接 主服务器 ${main.base}")
        try {
            val about = readText(main.base, startup)
            NetworkServiceFactory.json.parseToJsonElement(about)
            return about to main
        } catch (cause: Exception) {
            NetworkServiceFactory.logger.warning("trpgbot 主服务器 by ${main.base} 暂不可用 ${cause.message}")
            if (tryCdnFirst) {
                throw RuntimeException("请检查 trpgbot 的可用性")
            }
        }
        for (s in cdn) {
            NetworkServiceFactory.logger.info("正在尝试连接 CDN ${s.base}")
            try {
                val about = readText(s.base, startup)
                NetworkServiceFactory.json.parseToJsonElement(about)
                return about to s
            } catch (cause: Exception) {
                NetworkServiceFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用，下次重载配置前将不连接该 CDN. ${cause.message}")
                cdnList.removeIf { it.base == s.base }
            }
        }
        throw RuntimeException("请检查 trpgbot 的可用性")
    }
    private fun readText(url: String, startup: Boolean): String {
        val conn = URL(url).openConnection()
        conn.connectTimeout = 30 * 1000
        conn.readTimeout = 30 * 1000
        NetworkServiceFactory.headers.forEach(conn::setRequestProperty)
        if (startup) conn.setRequestProperty("X-Mirai-Startup", "!0");
        return conn.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
@Serializable
internal data class Cola(
    @SerialName("base_url")
    val base: String,
    @SerialName("key")
    val key: String = "",
)
