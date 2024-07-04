package xyz.cssxsh.mirai.tool

import kotlinx.serialization.*
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

@Serializable
internal data class NetworkConfig(
    @SerialName("main")
    val main: Cola,
    @SerialName("try_cdn_first")
    val tryCdnFirst: Boolean,
    @SerialName("cdn")
    val cdnList: MutableList<Cola>,
) {
    fun tryServers(): Pair<String, Cola> {
        val cdn = cdnList.toList()
        if (tryCdnFirst) {
            for (s in cdn) {
                NetworkServiceFactory.logger.info("trying CDN ${s.base}")
                try {
                    val about = readText(s.base)
                    NetworkServiceFactory.json.parseToJsonElement(about)
                    return about to s
                } catch (cause: Exception) {
                    NetworkServiceFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用，下次重载配置前将不连接该 CDN. ${cause.message}")
                    cdnList.removeIf { it.base == s.base }
                }
            }
        }
        NetworkServiceFactory.logger.info("trying Main Server ${main.base}")
        try {
            val about = readText(main.base)
            NetworkServiceFactory.json.parseToJsonElement(about)
            return about to main
        } catch (cause: Exception) {
            NetworkServiceFactory.logger.warning("trpgbot Main by ${main.base} 暂不可用 ${cause.message}")
            if (tryCdnFirst) {
                throw RuntimeException("请检查 trpgbot 的可用性")
            }
        }
        for (s in cdn) {
            NetworkServiceFactory.logger.info("trying CDN ${s.base}")
            try {
                val about = readText(s.base)
                NetworkServiceFactory.json.parseToJsonElement(about)
                return about to s
            } catch (cause: Exception) {
                NetworkServiceFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用，下次重载配置前将不连接该 CDN. ${cause.message}")
                cdnList.removeIf { it.base == s.base }
            }
        }
        throw RuntimeException("请检查 trpgbot 的可用性")
    }
    private fun readText(url: String): String {
        val conn = URL(url).openConnection()
        NetworkServiceFactory.headers.forEach(conn::setRequestProperty)
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
