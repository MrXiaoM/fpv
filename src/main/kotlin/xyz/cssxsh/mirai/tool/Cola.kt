package xyz.cssxsh.mirai.tool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import xyz.cssxsh.mirai.tool.adapters.Client
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
    /**
     * @return 服务端信息 to 服务端地址
     */
    fun tryServers(parentJob: Job?, scope: CoroutineScope, startup: Boolean = false): Pair<String, Cola> {
        val cdn = cdnList.toList()
        if (tryCdnFirst) for (s in cdn) {
            tryServer(parentJob, scope, s, false, startup)?.also { return it }
        }
        tryServer(parentJob, scope, main, true, startup)?.also { return it }
        for (s in cdn) {
            tryServer(parentJob, scope, s, false, startup)?.also { return it }
        }
        throw RuntimeException("请检查 trpgbot 的可用性")
    }
    private fun tryServer(parentJob: Job?, scope: CoroutineScope, s: Cola, main: Boolean, startup: Boolean): Pair<String, Cola>? {
        if (main) {
            NetworkServiceFactory.logger.info("正在尝试连接 主服务器 ${s.base}")
        } else {
            NetworkServiceFactory.logger.info("正在尝试连接 CDN ${s.base}")
        }
        if (s.base.startsWith("ws")) {
            runBlocking {
                val conn = Client.connect(s.base, parentJob, scope)
            }
            throw RuntimeException("测试")
        }
        return tryHttp(s, main, startup)
    }

    private fun tryHttp(s: Cola, main: Boolean, startup: Boolean): Pair<String, Cola>? {
        try {
            val about = readText(s.base, startup)
            NetworkServiceFactory.json.parseToJsonElement(about)
            return about to s
        } catch (cause: Exception) {
            if (main) {
                NetworkServiceFactory.logger.warning("trpgbot 主服务器 by ${s.base} 暂不可用 ${cause.message}")
                if (tryCdnFirst) {
                    throw RuntimeException("请检查 trpgbot 的可用性")
                }
            } else {
                NetworkServiceFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用，下次重载配置前将不连接该 CDN. ${cause.message}")
                cdnList.removeIf { it.base == s.base }
            }
        }
        return null
    }

    private fun readText(url: String, startup: Boolean): String {
        val conn = URL(url).openConnection()
        conn.connectTimeout = 30 * 1000
        conn.readTimeout = 30 * 1000
        NetworkServiceFactory.headers.forEach(conn::setRequestProperty)
        if (startup) conn.setRequestProperty("X-Mirai-Startup", "!0")
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
