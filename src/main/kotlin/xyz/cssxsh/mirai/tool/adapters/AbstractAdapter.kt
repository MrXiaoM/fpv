package xyz.cssxsh.mirai.tool.adapters

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.BotOfflineEvent
import net.mamoe.mirai.internal.spi.EncryptService
import net.mamoe.mirai.internal.spi.EncryptServiceContext
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.tool.NetworkServiceStateException
import kotlin.coroutines.CoroutineContext

public typealias JavaAtomicLong = java.util.concurrent.atomic.AtomicLong

public abstract class AbstractAdapter(
    private val serverName: String,
    coroutineContext: CoroutineContext
): CoroutineScope, EncryptService {

    override val coroutineContext: CoroutineContext =
        coroutineContext + SupervisorJob(coroutineContext[Job]) + CoroutineExceptionHandler { context, exception ->
            when (exception) {
                is CancellationException, is InterruptedException -> {
                    // ignored
                }
                is NetworkServiceStateException -> {
                    // ignored
                }
                else -> {
                    logger.warning({ "with ${context[CoroutineName]}" }, exception)
                }
            }
        }

    private var channel0: EncryptService.ChannelProxy? = null

    private val channel: EncryptService.ChannelProxy get() = channel0 ?: throw IllegalStateException("need initialize")

    protected val token: JavaAtomicLong = JavaAtomicLong(0)

    protected fun initialize(uin: Long, device: DeviceInfo, qimei36: String, channel: EncryptService.ChannelProxy) {
        channel0 = channel
        if (token.get() == 0L) {
            @OptIn(MiraiInternalApi::class)
            register(
                uin = uin,
                androidId = device.androidId.decodeToString(),
                guid = device.guid.toUHexString(),
                qimei36 = qimei36
            )
            coroutineContext.job.invokeOnCompletion {
                try {
                    destroy(uin = uin)
                } catch (cause: Throwable) {
                    logger.warning("Bot(${uin}) destroy", cause)
                } finally {
                    token.compareAndSet(uin, 0)
                }
            }
        }

        logger.info("Bot($uin) initialize complete")
    }
    protected abstract fun register(uin: Long, androidId: String, guid: String, qimei36: String)
    protected abstract fun destroy(uin: Long)
    protected abstract fun customEnergy(uin: Long, salt: ByteArray, data: String): String

    protected abstract fun sign(uin: Long, cmd: String, seq: Int, buffer: ByteArray): SignResult
    protected abstract fun requestToken(uin: Long): List<RequestCallback>
    protected abstract fun submit(uin: Long, cmd: String, callbackId: Long, buffer: ByteArray)
    protected fun DataWrapper.check(uin: Long) {
        if (code == 0) return
        token.compareAndSet(uin, 0)
        val cause = NetworkServiceStateException("trpgbot 服务异常, 请检查其日志, '$message'")
        launch(CoroutineName(name = "Dropped(${uin})")) {
            if (message !in RESET_SESSION) return@launch
            @OptIn(MiraiInternalApi::class)
            BotOfflineEvent.Dropped(
                bot = Bot.getInstance(qq = uin),
                cause = cause
            ).broadcast()
        }
        throw cause
    }
    protected fun signRegister(uin: Long) {
        if (token.compareAndSet(0, uin)) {
            launch(CoroutineName(name = "RequestToken")) {
                while (isActive) {
                    val interval = System.getProperty(REQUEST_TOKEN_INTERVAL, "2400000").toLong()
                    if (interval <= 0L) break
                    if (interval < 600_000) logger.warning("$REQUEST_TOKEN_INTERVAL=${interval} < 600_000 (ms)")
                    delay(interval)
                    val request = try {
                        requestToken(uin = uin)
                    } catch (cause: Throwable) {
                        logger.error(cause)
                        continue
                    }
                    callback(uin = uin, request = request)
                }
            }
        }
    }
    protected fun callback(uin: Long, request: List<RequestCallback>) {
        launch(CoroutineName(name = "SendMessage")) {
            for (callback in request) {
                logger.debug("Bot(${uin}) sendMessage ${callback.cmd} ")
                val result = try {
                    channel.sendMessage(
                        remark = "mobileqq.msf.security",
                        commandName = callback.cmd,
                        uin = 0,
                        data = callback.body.hexToBytes()
                    )
                } catch (cause: Throwable) {
                    throw RuntimeException("Bot(${uin}) callback ${callback.cmd}", cause)
                }
                if (result == null) {
                    logger.debug("Bot(${uin}) callback ${callback.cmd} ChannelResult is null")
                    continue
                }

                submit(uin = uin, cmd = result.cmd, callbackId = callback.id, buffer = result.data)
            }
        }
    }


    override fun initialize(context: EncryptServiceContext) {
        val device = context.extraArgs[EncryptServiceContext.KEY_DEVICE_INFO]
        val qimei36 = context.extraArgs[EncryptServiceContext.KEY_QIMEI36]
        val channel = context.extraArgs[EncryptServiceContext.KEY_CHANNEL_PROXY]

        QsignWebSocketAdapter.logger.info("Bot(${context.id}) initialize by $serverName")

        initialize(context.id, device, qimei36, channel)
    }

    override fun encryptTlv(context: EncryptServiceContext, tlvType: Int, payload: ByteArray): ByteArray? {
        if (tlvType != 0x544) return null
        val command = context.extraArgs[EncryptServiceContext.KEY_COMMAND_STR]

        val data = customEnergy(uin = context.id, salt = payload, data = command)

        return data.hexToBytes()
    }

    override fun qSecurityGetSign(
        context: EncryptServiceContext,
        sequenceId: Int,
        commandName: String,
        payload: ByteArray
    ): EncryptService.SignResult? {
        if (commandName == "StatSvc.register") {
            signRegister(context.id)
        }

        if (commandName !in CMD_WHITE_LIST) return null

        val data = sign(uin = context.id, cmd = commandName, seq = sequenceId, buffer = payload)

        callback(uin = context.id, request = data.request)

        return EncryptService.SignResult(
            sign = data.sign.hexToBytes(),
            token = data.token.hexToBytes(),
            extra = data.extra.hexToBytes()
        )
    }

    public companion object {
        @JvmStatic
        internal val CMD_WHITE_LIST = QsignHttpAdapter::class.java.getResource("cmd.txt")!!.readText().lines()

        @JvmStatic
        internal val RESET_SESSION = arrayOf(
            "Uin is not registered.",
            "First use must be submitted with android_id and guid."
        )

        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(AbstractAdapter::class, "trpgbot.adapter")

        @JvmStatic
        public val REQUEST_TOKEN_INTERVAL: String = "xyz.cssxsh.mirai.tool.UnidbgFetchQsign.token.interval"
    }
}

@Serializable
public data class DataWrapper(
    @SerialName("code")
    val code: Int = 0,
    @SerialName("msg")
    val message: String = "",
    @SerialName("data")
    val data: JsonElement
)

@Serializable
public data class SignResult(
    @SerialName("token")
    val token: String = "",
    @SerialName("extra")
    val extra: String = "",
    @SerialName("sign")
    val sign: String = "",
    @SerialName("o3did")
    val o3did: String = "",
    @SerialName("requestCallback")
    val request: List<RequestCallback> = emptyList()
)

@Serializable
public data class RequestCallback(
    @SerialName("body")
    val body: String,
    @SerialName("callback_id")
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("callbackId", "callback_id")
    val id: Long,
    @SerialName("cmd")
    val cmd: String
)
