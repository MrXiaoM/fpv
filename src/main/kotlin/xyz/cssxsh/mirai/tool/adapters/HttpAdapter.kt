package xyz.cssxsh.mirai.tool.adapters

import kotlinx.serialization.builtins.*
import net.mamoe.mirai.internal.spi.*
import net.mamoe.mirai.utils.*
import org.asynchttpclient.*
import xyz.cssxsh.mirai.tool.NetworkServiceFactory
import java.time.Duration
import kotlin.coroutines.*

import xyz.cssxsh.mirai.tool.NetworkServiceFactory.Companion.json

public class QsignHttpAdapter(
    private val server: String,
    private val key: String,
    private val ver: String,
    private val qua: String,
    coroutineContext: CoroutineContext
): AbstractAdapter(coroutineContext), EncryptService {

    private val client = Dsl.asyncHttpClient(
        DefaultAsyncHttpClientConfig.Builder()
            .setKeepAlive(true)
            .setMaxRequestRetry(3)
            .setUserAgent(NetworkServiceFactory.userAgent)
            .setRequestTimeout(Duration.ofSeconds(90))
            .setConnectTimeout(Duration.ofSeconds(30))
            .setReadTimeout(Duration.ofSeconds(180))
    )

    private fun BoundRequestBuilder.applyHeader(): BoundRequestBuilder = apply {
        NetworkServiceFactory.headers.forEach(::setHeader)
    }

    override fun initialize(context: EncryptServiceContext) {
        val device = context.extraArgs[EncryptServiceContext.KEY_DEVICE_INFO]
        val qimei36 = context.extraArgs[EncryptServiceContext.KEY_QIMEI36]
        val channel = context.extraArgs[EncryptServiceContext.KEY_CHANNEL_PROXY]

        logger.info("Bot(${context.id}) initialize by $server")

        initialize(context.id, device, qimei36, channel)
    }

    override fun register(uin: Long, androidId: String, guid: String, qimei36: String) {
        val response = client.prepareGet("${server}/register")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .addQueryParam("android_id", androidId)
            .addQueryParam("guid", guid)
            .addQueryParam("qimei36", qimei36)
            .addQueryParam("key", key)
            .execute().get()
        val body = json.decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.info("Bot(${uin}) register, ${body.message}")
    }

    override fun destroy(uin: Long) {
        val response = client.prepareGet("${server}/destroy")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .addQueryParam("key", key)
            .execute().get()
        if (response.statusCode == 404) return
        val body = json.decodeFromString(DataWrapper.serializer(), response.responseBody)

        logger.info("Bot(${uin}) destroy, ${body.message}")
    }

    override fun encryptTlv(context: EncryptServiceContext, tlvType: Int, payload: ByteArray): ByteArray? {
        if (tlvType != 0x544) return null
        val command = context.extraArgs[EncryptServiceContext.KEY_COMMAND_STR]

        val data = customEnergy(uin = context.id, salt = payload, data = command)

        return data.hexToBytes()
    }

    override fun customEnergy(uin: Long, salt: ByteArray, data: String): String {
        val response = client.prepareGet("${server}/custom_energy")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .addQueryParam("salt", salt.toUHexString(""))
            .addQueryParam("data", data)
            .execute().get()
        val body = json.decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) custom_energy ${data}, ${body.message}")

        return json.decodeFromJsonElement(String.serializer(), body.data)
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

    override fun sign(uin: Long, cmd: String, seq: Int, buffer: ByteArray): SignResult {
        val response = client.preparePost("${server}/sign")
            .applyHeader()
            .addFormParam("uin", uin.toString())
            .addFormParam("ver", ver)
            .addFormParam("qua", qua)
            .addFormParam("cmd", cmd)
            .addFormParam("seq", seq.toString())
            .addFormParam("buffer", buffer.toUHexString(""))
            .execute().get()
        val body = json.decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) sign ${cmd}, ${body.message}")

        return json.decodeFromJsonElement(SignResult.serializer(), body.data)
    }

    override fun requestToken(uin: Long): List<RequestCallback> {
        val response = client.prepareGet("${server}/request_token")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .execute().get()
        val body = json.decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.info("Bot(${uin}) request_token, ${body.message}")

        return json.decodeFromJsonElement(ListSerializer(RequestCallback.serializer()), body.data)
    }

    override fun submit(uin: Long, cmd: String, callbackId: Long, buffer: ByteArray) {
        val response = client.prepareGet("${server}/submit")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .addQueryParam("cmd", cmd)
            .addQueryParam("callback_id", callbackId.toString())
            .addQueryParam("buffer", buffer.toUHexString(""))
            .execute().get()
        val body = json.decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) submit ${cmd}, ${body.message}")
    }

    override fun toString(): String {
        return "UnidbgFetchQsign(server=${server}, uin=${token})"
    }

    public companion object {
        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(QsignHttpAdapter::class, "trpgbot.adapter.http")
    }
}
