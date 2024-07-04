package xyz.cssxsh.mirai.tool

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.internal.spi.EncryptService
import net.mamoe.mirai.utils.*
import java.io.File

@PublishedApi
internal object FixProtocolVersionCommand : CompositeCommand(
    owner = FixProtocolVersionPlugin,
    "trpgBotSign",
    "tbs",
    description = "Plugin Command"
) {
    @SubCommand("fetch", "sync")
    suspend fun CommandSender.fetch(protocol: BotConfiguration.MiraiProtocol, version: String = "latest") {
        try {
            FixProtocolVersion.fetch(protocol, version)
            sendMessage(FixProtocolVersion.info()[protocol] ?: "找不到协议信息")
        } catch (cause: Throwable) {
            FixProtocolVersionPlugin.logger.warning(cause)
            sendMessage("出现错误")
        }
    }

    @SubCommand
    suspend fun CommandSender.load(protocol: BotConfiguration.MiraiProtocol, version: String? = null) {
        try {
            if (version != null) {
                val factory = NetworkServiceFactory.inst
                if (factory == null) {
                    sendMessage("当前已有其它签名服务优先级高于 trpgbot 签名服务")
                    return
                }
                val file = File(factory.protocolsFolder, "${protocol.name.lowercase()}_$version.json")
                if (!file.exists()) {
                    sendMessage("无法找到文件 ${file.name}")
                    return
                }
                FixProtocolVersion.load(protocol, file)
            } else {
                FixProtocolVersion.load(protocol)
            }
            sendMessage(FixProtocolVersion.info()[protocol] ?: "找不到协议信息")
        } catch (cause: Throwable) {
            FixProtocolVersionPlugin.logger.warning(cause)
            sendMessage("出现错误")
        }
    }

    @SubCommand
    suspend fun CommandSender.info() {
        sendMessage(buildString {
            appendLine("当前各协议版本日期: ")
            for ((_, info) in FixProtocolVersion.info()) {
                appendLine(info)
            }
        })
    }
    @SubCommand
    suspend fun CommandSender.reload() {
        val factory = NetworkServiceFactory.inst
        if (factory == null) {
            sendMessage("当前已有其它签名服务优先级高于 trpgbot 签名服务，无法重载")
            return
        }
        factory.reload()
        sendMessage("配置文件 network.json 已重载")
    }
}