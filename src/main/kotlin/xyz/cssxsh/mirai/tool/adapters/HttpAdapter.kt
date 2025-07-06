package xyz.cssxsh.mirai.tool.adapters

import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
): AbstractAdapter(server, logger, coroutineContext) {

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
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)
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
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)

        logger.info("Bot(${uin}) destroy, ${body.message}")
    }

    override fun customEnergy(uin: Long, salt: ByteArray, data: String): String {
        val response = client.prepareGet("${server}/custom_energy")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .addQueryParam("salt", salt.toUHexString(""))
            .addQueryParam("data", data)
            .addQueryParam("key", key)
            .execute().get()
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) custom_energy ${data}, ${body.message}")

        return json.decodeFromJsonElement(String.serializer(), body.data)
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
            .addFormParam("key", key)
            .execute().get()
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)
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
            .addQueryParam("key", key)
            .execute().get()
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)
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
            .addQueryParam("key", key)
            .execute().get()
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) submit ${cmd}, ${body.message}")
    }

    override fun getCmdWhitelist(uin: Long): List<String> {
        val response = client.prepareGet("${server}/cmd_whitelist")
            .applyHeader()
            .addQueryParam("uin", uin.toString())
            .addQueryParam("ver", ver)
            .addQueryParam("qua", qua)
            .addQueryParam("key", key)
            .execute().get()
        val body = decodeFromString(DataWrapper.serializer(), response.responseBody)
        body.check(uin = uin)

        return runCatching {
            body.data.jsonObject["list"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf()
        }.getOrElse { listOf() }
    }

    override fun toString(): String {
        return "QsignHttpAdapter(server=${server}, uin=${token})"
    }

    public companion object {
        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(QsignHttpAdapter::class, "trpgbot.adapter.http")
    }
}
