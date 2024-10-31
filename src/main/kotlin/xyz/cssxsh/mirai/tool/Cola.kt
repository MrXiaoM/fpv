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
            val pair = tryServer(parentJob, scope, s, false, startup)
            if (pair != null) return pair
        }
        val pair = tryServer(parentJob, scope, main, true, startup)
        if (pair != null) return pair
        if (!tryCdnFirst) for (s in cdn) {
            val pair1 = tryServer(parentJob, scope, s, false, startup)
            if (pair1 != null) return pair1
        }
        throw RuntimeException("请检查 trpgbot 的可用性")
    }
    private fun tryServer(parentJob: Job?, scope: CoroutineScope, s: Cola, main: Boolean, startup: Boolean): Pair<String, Cola>? {
        if (s.base.startsWith("ws")) {
            return runBlocking {
                if (Client.connectionPool.containsKey(s.base)) {
                    NetworkServiceFactory.logger.info("正在复用连接 ${s.base}")
                } else {
                    if (main) {
                        NetworkServiceFactory.logger.info("正在尝试连接 主服务器 ${s.base}")
                    } else {
                        NetworkServiceFactory.logger.info("正在尝试连接 CDN ${s.base}")
                    }
                }
                val conn = Client.connect(s.base, parentJob, scope)
                val packet = conn.send("index", buildJsonObject {  })
                if (packet == null) {
                    NetworkServiceFactory.logger.warning("访问 ${s.base} 获取信息出错: 未接收到回调包")
                    return@runBlocking null
                }
                val aboutString = packet.toString()
                NetworkServiceFactory.logger.info("服务器 ${s.base} 可用")
                return@runBlocking aboutString to s
            }
        } else {
            if (main) {
                NetworkServiceFactory.logger.info("正在尝试连接 主服务器 ${s.base}")
            } else {
                NetworkServiceFactory.logger.info("正在尝试连接 CDN ${s.base}")
            }
            return tryHttp(s, main, startup)
        }
    }

    private fun tryHttp(s: Cola, main: Boolean, startup: Boolean): Pair<String, Cola>? {
        try {
            val about = readText(s.base, startup)
            NetworkServiceFactory.json.parseToJsonElement(about)
            NetworkServiceFactory.logger.info("服务器 ${s.base} 可用")
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
