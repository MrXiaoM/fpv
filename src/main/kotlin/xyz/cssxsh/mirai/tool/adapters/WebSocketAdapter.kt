package xyz.cssxsh.mirai.tool.adapters

import kotlinx.coroutines.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import net.mamoe.mirai.utils.MiraiLogger
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import xyz.cssxsh.mirai.tool.NetworkServiceFactory
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

public class QsignWebSocketAdapter(
    private val server: String,
    private val ver: String,
    private val qua: String,
    parentJob: Job,
    coroutineContext: CoroutineContext,
): AbstractAdapter(server, coroutineContext) {
    public val client: Client = Client(server, parentJob, this,
        NetworkServiceFactory.headers.plus(mapOf(
            "X-Qsign-QUA" to qua,
            "X-Qsign-Ver" to ver,
        )))
    public class Client(
        server: String,
        parentJob: Job,
        private val scope: CoroutineScope,
        headers: Map<String, String>,
        private val retryTimes: Int = 5,
        private val retryWaitMills: Long = 5000L,
        private val retryRestMills: Long = 60000L,
    ) : WebSocketClient(URI(server), headers) {
        private val echos = JavaAtomicLong(0)
        private val futureMap: MutableMap<String, CompletableFuture<JsonObject>> = mutableMapOf()
        private var retryCount = 0
        private var scheduleClose = false
        @OptIn(InternalCoroutinesApi::class)
        private val connectDef = CompletableDeferred<Boolean>(parentJob).apply {
            invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true
            ) { close() }
        }
        public suspend fun connectSuspend(): Boolean {
            if (super.connectBlocking()) return true
            return connectDef.await()
        }
        override fun onOpen(handshakedata: ServerHandshake) {
            logger.info("已连接到签名服务器")
        }
        override fun connect() {
            scheduleClose = false
            super.connect()
        }
        override fun close() {
            scheduleClose = true
            super.close()
        }
        public fun send(type: String, params: JsonObject): JsonObject? {
            val echo = echos.getAndIncrement().toString()
            val future = CompletableFuture<JsonObject>()
            futureMap[echo] = future
            send(buildJsonObject {
                put("type", type)
                put("params", params)
                put("echo", echo)
            }.toString())
            return runCatching {
                future.get(15, TimeUnit.SECONDS)
            }.getOrNull()
        }
        override fun onMessage(message: String) {
            try {
                val json = jsonParser.parseToJsonElement(message).jsonObject
                val echo = json["echo"]?.jsonPrimitive?.content ?: return
                val payload = json["payload"]?.jsonObject ?: return
                val future = futureMap.remove(echo) ?: return
                if (future.isDone || future.isCancelled) return
                future.complete(payload)
            } catch (e: SerializationException) {
                logger.error("Json语法错误: $message")
            }
        }
        override fun onClose(code: Int, reason: String, remote: Boolean) {
            logger.info("签名服务器连接因 ${reason.ifEmpty { "未知原因" }} 已关闭 (关闭码: $code)")
            // 自动重连
            if (!scheduleClose) retry()
        }
        private fun retry() {
            if (retryTimes < 1 || retryWaitMills < 0) {
                logger.warning("连接失败，未开启自动重连，放弃连接")
                connectDef.complete(false)
                return
            }
            scope.launch {
                if (retryCount < retryTimes) {
                    retryCount++
                    logger.warning(
                        "等待 ${
                            String.format("%.1f", retryWaitMills / 1000.0F)
                        } 秒后重连 (第 $retryCount/$retryTimes 次)"
                    )
                    delay(retryWaitMills)
                } else {
                    retryCount = 0
                    if (retryRestMills < 0) {
                        logger.warning("重连次数耗尽... 放弃重试")
                        return@launch
                    }
                    logger.warning("重连次数耗尽... 休息 ${String.format("%.1f", retryRestMills / 1000.0F)} 秒后重试")
                    delay(retryRestMills)
                }
                logger.info("正在重连...")
                if (reconnectBlocking()) {
                    retryCount = 0
                    connectDef.complete(true)
                }
            }
        }
        override fun onError(ex: Exception) {
            logger.error("签名服务器连接出现错误 ${ex.localizedMessage} 或未连接")
        }
        public companion object {
            public val jsonParser: Json = Json {
                ignoreUnknownKeys = true
            }
        }
    }

    override fun register(uin: Long, androidId: String, guid: String, qimei36: String) {
        TODO("Not yet implemented")
    }

    override fun destroy(uin: Long) {
        TODO("Not yet implemented")
    }

    override fun customEnergy(uin: Long, salt: ByteArray, data: String): String {
        TODO("Not yet implemented")
    }

    override fun sign(uin: Long, cmd: String, seq: Int, buffer: ByteArray): SignResult {
        TODO("Not yet implemented")
    }

    override fun requestToken(uin: Long): List<RequestCallback> {
        TODO("Not yet implemented")
    }

    override fun submit(uin: Long, cmd: String, callbackId: Long, buffer: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return "QsignWebSocketAdapter(server=${server}, uin=${token})"
    }

    public companion object {
        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(QsignWebSocketAdapter::class, "trpgbot.adapter.ws")
    }
}
