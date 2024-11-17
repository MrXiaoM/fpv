package xyz.cssxsh.mirai.tool.adapters

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.toUHexString
import xyz.cssxsh.mirai.tool.NetworkServiceFactory.Companion.json
import kotlin.coroutines.CoroutineContext

public class QsignWebSocketAdapter(
    private val server: String,
    private val ver: String,
    private val qua: String,
    public val client: Client,
    coroutineContext: CoroutineContext,
): AbstractAdapter(server, logger, coroutineContext) {

    private fun params(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("qua", qua)
        put("ver", ver)
        block(this)
    }

    override fun register(uin: Long, androidId: String, guid: String, qimei36: String) {
        /*
        val resp = client.send("register", params {
            put("uin", uin.toString())
            put("android_id", androidId)
            put("guid", guid)
            put("qimei36", qimei36)
        }) ?: throw IllegalStateException("签名服务请求超时或回调失败")
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)
        body.check(uin = uin)

        QsignHttpAdapter.logger.info("Bot(${uin}) register, ${body.message}")
         */
    }

    override fun destroy(uin: Long) {
        /*
        val resp = client.send("destroy", params {
            put("uin", uin.toString())
        }) ?: throw IllegalStateException("签名服务请求超时或回调失败")
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)

        logger.info("Bot(${uin}) destroy, ${body.message}")
         */
    }

    override fun customEnergy(uin: Long, salt: ByteArray, data: String): String {
        val resp = client.send("custom_energy", params {
            put("uin", uin.toString())
            put("salt", salt.toUHexString(""))
            put("data", data)
        }) ?: throw IllegalStateException("签名服务请求超时或回调失败")
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) custom_energy ${data}, ${body.message}")

        return json.decodeFromJsonElement(String.serializer(), body.data)
    }

    override fun sign(uin: Long, cmd: String, seq: Int, buffer: ByteArray): SignResult {
        val resp = client.send("sign", params {
            put("uin", uin.toString())
            put("cmd", cmd)
            put("seq", seq.toString())
            put("buffer", buffer.toUHexString(""))
        }) ?: throw IllegalStateException("签名服务请求超时或回调失败")
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) sign ${cmd}, ${body.message}")

        return json.decodeFromJsonElement(SignResult.serializer(), body.data)
    }

    override fun requestToken(uin: Long): List<RequestCallback> {
        val resp = client.send("request_token", params {
            put("uin", uin.toString())
        }) ?: throw IllegalStateException("签名服务请求超时或回调失败")
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)
        body.check(uin = uin)

        logger.info("Bot(${uin}) request_token, ${body.message}")

        return json.decodeFromJsonElement(ListSerializer(RequestCallback.serializer()), body.data)
    }

    override fun submit(uin: Long, cmd: String, callbackId: Long, buffer: ByteArray) {
        val resp = client.send("submit", params {
            put("uin", uin.toString())
            put("cmd", cmd)
            put("callback_id", callbackId.toString())
            put("buffer", buffer.toUHexString(""))
        }) ?: throw IllegalStateException("签名服务请求超时或回调失败")
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)
        body.check(uin = uin)

        logger.debug("Bot(${uin}) submit ${cmd}, ${body.message}")
    }

    override fun getCmdWhitelist(uin: Long): List<String> {
        val resp = client.send("cmd_whitelist", params {
            put("uin", uin.toString())
        }) ?: return listOf()
        val body = json.decodeFromJsonElement(DataWrapper.serializer(), resp)
        body.check(uin = uin)

        return runCatching {
            body.data.jsonObject["list"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf()
        }.getOrElse { listOf() }
    }

    override fun toString(): String {
        return "QsignWebSocketAdapter(server=${server}, uin=${token})"
    }

    public companion object {
        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(QsignWebSocketAdapter::class, "trpgbot.adapter.ws")
    }
}
