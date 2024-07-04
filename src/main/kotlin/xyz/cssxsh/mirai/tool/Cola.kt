package xyz.cssxsh.mirai.tool

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.ConnectException
import java.net.URL

@Serializable
internal data class NetworkConfig(
    @SerialName("main")
    val main: Cola,
    @SerialName("try_cdn_first")
    val tryCdnFirst: Boolean,
    @SerialName("cdn")
    val cdnList: List<Cola>,
) {
    fun tryServers(): Pair<String, Cola> {
        if (tryCdnFirst) {
            for (s in cdnList) {
                KFCFactory.logger.info("trying cdn ${s.base}")
                try {
                    val about = URL(s.base).readText()
                    return about to s
                } catch (cause: ConnectException) {
                    KFCFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用 ${cause.message}")
                }
            }
        }
        KFCFactory.logger.info("trying main server ${main.base}")
        try {
            val about = URL(main.base).readText()
            return about to main
        } catch (cause: ConnectException) {
            KFCFactory.logger.warning("trpgbot main by ${main.base} 暂不可用 ${cause.message}")
            if (tryCdnFirst) {
                throw RuntimeException("请检查 trpgbot 的可用性")
            }
        }
        for (s in cdnList) {
            KFCFactory.logger.info("trying cdn ${s.base}")
            try {
                val about = URL(s.base).readText()
                return about to s
            } catch (cause: ConnectException) {
                KFCFactory.logger.warning("trpgbot CDN by ${s.base} 暂不可用 ${cause.message}")
            }
        }
        throw RuntimeException("请检查 trpgbot 的可用性")
    }
}
@Serializable
internal data class Cola(
    @SerialName("base_url")
    val base: String,
    @SerialName("key")
    val key: String = "",
)
