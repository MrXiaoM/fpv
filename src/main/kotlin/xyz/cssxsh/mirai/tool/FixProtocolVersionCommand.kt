package xyz.cssxsh.mirai.tool

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.BotConfiguration
import xyz.cssxsh.mirai.tool.adapters.Client
import java.io.File

@PublishedApi
@OptIn(ConsoleExperimentalApi::class)
internal object FixProtocolVersionCommand : CompositeCommand(
    owner = FixProtocolVersionPlugin,
    "trpgBotSign",
    "tbs",
    description = "Plugin Command"
) {
    @SubCommand("fetch", "sync")
    @Description("从远程仓库下载协议版本信息配置文件")
    suspend fun CommandSender.fetch(@Name("协议类型") protocol: BotConfiguration.MiraiProtocol, @Name("版本号") version: String = "latest") {
        try {
            FixProtocolVersion.fetch(protocol, version)
            sendMessage(FixProtocolVersion.info()[protocol] ?: "找不到协议信息")
        } catch (cause: Throwable) {
            FixProtocolVersionPlugin.logger.warning(cause)
            sendMessage("出现错误")
        }
    }

    @SubCommand
    @Description("查看可使用 load 命令加载的协议版本列表")
    suspend fun CommandSender.list(@Name("协议类型") protocol: BotConfiguration.MiraiProtocol) {
        val factory = NetworkServiceFactory.inst
        if (factory == null) {
            sendMessage("当前已有其它签名服务优先级高于 trpgbot 签名服务")
            return
        }
        val files = factory.protocolsFolder.listFiles { _, name -> name.startsWith(protocol.name + "_", true) && name.endsWith(".json") }
        sendMessage(buildString {
            appendLine("协议版本列表如下:")
            if (files != null) for (file in files) {
                appendLine("- " + file.nameWithoutExtension)
            }
        })
    }

    @SubCommand
    @Description("从本地加载协议版本信息配置文件，不填写版本使用 \"./协议类型.json\"；填写版本使用 \"./protocols/协议类型_协议版本.json\"")
    suspend fun CommandSender.load(@Name("协议类型") protocol: BotConfiguration.MiraiProtocol, @Name("版本号") version: String? = null) {
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
    @Description("查看各协议版本信息")
    suspend fun CommandSender.info() {
        sendMessage(buildString {
            appendLine("当前各协议版本日期: ")
            for ((_, info) in FixProtocolVersion.info()) {
                appendLine(info)
            }
        })
    }

    @SubCommand("websocket", "ws")
    @Description("查看ws连接列表")
    suspend fun CommandSender.websocket() {
        sendMessage(buildString {
            appendLine("当前 WebSocket 连接池情况：")
            for ((address, client) in Client.connectionPool) {
                val status = if (client.isOpen) "已连接" else "未连接"
                appendLine("- $status $address")
            }
        })
    }

    @SubCommand
    @Description("断开ws连接。你不应该在该链接被某Bot使用时执行该操作")
    suspend fun CommandSender.disconnect(@Name("地址") address: String) {
        val client = Client.connectionPool[address]
        if (client == null) {
            sendMessage("未在 WebSocket 连接池中找到该连接")
        } else if (!client.isOpen) {
            sendMessage("该连接早已断开")
        } else {
            NetworkServiceFactory.created
            client.closeBlocking(1000, "用户请求关闭")
            sendMessage("已成功断开连接")
        }
    }

    @SubCommand
    @Description("重载配置文件 network.json")
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