package xyz.cssxsh.mirai.tool

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.command.SystemCommandSender
import net.mamoe.mirai.console.util.AnsiMessageBuilder
import net.mamoe.mirai.console.util.sendAnsiMessage
import net.mamoe.mirai.internal.spi.*
import net.mamoe.mirai.internal.utils.*
import net.mamoe.mirai.utils.*
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Dsl
import java.io.File
import java.net.ConnectException
import java.net.URL
import java.time.Duration

public class NetworkServiceFactory(
    private val config: File
) : EncryptService.Factory {
    override val priority: Int = -1
    internal lateinit var networkConfig: NetworkConfig
    internal val protocolsFolder = File(config.parentFile, "protocols")
    public constructor() : this(config = File(System.getProperty(CONFIG_PATH_PROPERTY, "network.json")))

    public companion object {
        internal val json = Json {
            ignoreUnknownKeys = true
        }
        internal val jsonPretty = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
        internal val userAgent by lazy {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
            runCatching {
                @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                val ver = net.mamoe.mirai.console.internal.MiraiConsoleBuildConstants.versionConst
                "$ua mirai/$ver"
            }.getOrElse { ua }
        }

        internal val client = Dsl.asyncHttpClient(
            DefaultAsyncHttpClientConfig.Builder()
                .setKeepAlive(true)
                .setUserAgent(userAgent)
                .setRequestTimeout(Duration.ofSeconds(30))
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(30))
        )

        public val inst: NetworkServiceFactory?
            @Suppress("INVISIBLE_MEMBER")
            get() = EncryptService.Companion.factory as? NetworkServiceFactory

        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(NetworkServiceFactory::class)
        internal val headers = mapOf(
            "User-Agent" to userAgent
        )
        @JvmStatic
        public fun install() {
            Services.register(
                EncryptService.Factory::class.qualifiedName!!,
                NetworkServiceFactory::class.qualifiedName!!,
                ::NetworkServiceFactory
            )
        }

        @JvmStatic
        public val CONFIG_PATH_PROPERTY: String = "trpgbot.sign.config"

        @JvmStatic
        public val DEFAULT_CONFIG: String = """
            {
                "main": { "base_url": "https://qsign.trpgbot.com", "key": "miraibbs" },
                "try_cdn_first": true,
                "cdn": [
                    { "base_url": "https://qsign-v4.trpgbot.com", "key": "miraibbs" },
                    { "base_url": "https://qsign.chahuyun.cn", "key": "selfshare" },
                    { "base_url": "http://qsign.w1.luyouxia.net", key: "miraibbs" },
                    { "base_url": "http://qsign-v3.trpgbot.com", "key": "selfshare" }
                ]
            }
        """.trimIndent()

        @JvmStatic
        internal val created: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }

    init {
        protocolsFolder.mkdirs()
        reload()
    }

    public fun reload() {
        with(config) {
            if (exists().not()) {
                writeText(DEFAULT_CONFIG)
            }
            networkConfig = Json.decodeFromString(NetworkConfig.serializer(), readText())
        }
    }

    override fun createForBot(context: EncryptServiceContext, serviceSubScope: CoroutineScope): EncryptService {
        if (created.add(context.id).not()) {
            throw UnsupportedOperationException("重复创建 EncryptService(id=${context.id})")
        }
        serviceSubScope.coroutineContext.job.invokeOnCompletion {
            created.remove(context.id)
        }
        try {
            org.asynchttpclient.Dsl.config()
        } catch (cause: NoClassDefFoundError) {
            throw RuntimeException("请参照 https://search.maven.org/artifact/org.asynchttpclient/async-http-client/3.0.0.Beta2/jar 添加依赖", cause)
        }
        return when (val protocol = context.extraArgs[EncryptServiceContext.KEY_BOT_PROTOCOL]) {
            BotConfiguration.MiraiProtocol.ANDROID_PHONE, BotConfiguration.MiraiProtocol.ANDROID_PAD -> {
                @Suppress("INVISIBLE_MEMBER")
                val version = MiraiProtocolInternal[protocol].ver
                logger.info(
                    "创建 EncryptService(id=${context.id}), protocol=${protocol}(${version}) from ${
                        config.toPath().toUri()
                    }"
                )

                val (about, server) = networkConfig.tryServers()

                checkSignServerAvailability(protocol, version, server, about)

                UnidbgFetchQsign(
                    server = server.base,
                    key = server.key,
                    coroutineContext = serviceSubScope.coroutineContext
                )
            }
            BotConfiguration.MiraiProtocol.ANDROID_WATCH,
            BotConfiguration.MiraiProtocol.IPAD,
            BotConfiguration.MiraiProtocol.MACOS -> throw UnsupportedOperationException(protocol.name)
        }
    }

    @Suppress("INVISIBLE_MEMBER")
    internal fun checkProtocolUpdate(
        protocol: BotConfiguration.MiraiProtocol,
        about: String
    ) {
        val data = json.parseToJsonElement(about)
        val targetVer = runCatching {
            val jsonData = data.jsonObject["data"]!!.jsonObject
            if (jsonData.containsKey("protocol")) {
                jsonData["protocol"]!!.jsonObject["version"]!!.jsonPrimitive.content
            } else if (jsonData.containsKey("qua") && jsonData.containsKey("version")) {
                jsonData["version"]!!.jsonPrimitive.content
            } else {
                ""
            }
        }.getOrElse { "" }

        if (targetVer.isEmpty()) logger.warning("无法从 trpgbot 回应中获得协议版本，放弃自动更换版本")
        else {
            val file = File(protocolsFolder, "${protocol.name.lowercase()}_$targetVer.json")

            try {
                if (file.exists()) {
                    FixProtocolVersion.load(protocol, file)
                    val buildVer = MiraiProtocolInternal[protocol].buildVer
                    logger.info("已将 $protocol 从本地配置自动升级至 $buildVer")
                } else {
                    val result = runCatching {
                        FixProtocolVersion.fetchWithResult(protocol, targetVer)
                    }.fold(
                        onSuccess = { it },
                        onFailure = {
                            if (it is IllegalStateException) {
                                return@fold fetchVersionInfoFromGuide(targetVer)
                                    ?: throw IllegalStateException(
                                        "从 protocol-versions 及 qsign-guide 下载协议失败"
                                    )
                            }
                            throw it
                        }
                    )
                    val buildVer = MiraiProtocolInternal[protocol].buildVer
                    file.writeText(jsonPretty.encodeToString(result))
                    logger.info("已将 $protocol 从网络配置自动升级至 $buildVer，并保存配置到本地")
                }
            } catch (e: Exception) {
                logger.warning("协议自动升级失败", e)
            }
        }
    }

    private fun fetchVersionInfoFromGuide(targetVer: String): JsonObject? {
        val text = URL("https://qsign-guide.trpgbot.com/").readText()
        return runCatching {
            var flag1 = false
            val s = StringBuilder()
            for (c in text) {
                if (!flag1 && c == '{') {
                    flag1 = true
                }
                if (flag1) s.append(c)
                if (flag1 && c == '}') {
                    if (targetVer in s) {
                        break
                    }
                    s.clear()
                    flag1 = false
                }
            }
            json.parseToJsonElement(s.toString())
        }.getOrNull()?.jsonObject
    }

    private fun checkSignServerAvailability(
        protocol: BotConfiguration.MiraiProtocol,
        version: String,
        server: Cola,
        about: String
    ) {
        try {
            logger.info("trpgbot from ${server.base} about \n" + about)
            when {
                version !in about -> {
                    throw IllegalStateException("trpgbot by ${server.base} 与协议 ${protocol}(${version}) 似乎不匹配")
                }
                "IAA" !in about -> {
                    logger.error("请确认服务类型为 trpgbot")
                }
            }
        } catch (cause: ConnectException) {
            throw RuntimeException("请检查 trpgbot from ${server.base} 的可用性", cause)
        } catch (cause: java.io.FileNotFoundException) {
            throw RuntimeException("请检查 trpgbot from ${server.base} 的可用性", cause)
        }
        if (server.key.isEmpty()) {
            logger.warning("trpgbot key is empty")
        }
    }

    override fun toString(): String {
        return "NetworkServiceFactory(config=${config.toPath().toUri()})"
    }
}
