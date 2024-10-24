package xyz.cssxsh.mirai.tool

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.util.AnsiMessageBuilder
import net.mamoe.mirai.console.util.sendAnsiMessage
import net.mamoe.mirai.utils.*
import java.io.File

@PublishedApi
internal object FixProtocolVersionPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "trpgbot.qsign",
        name = "trpgbot",
        version = "1.13.2"
    ) {
        author("cssxsh & MrXiaoM")
    }
) {
    override fun PluginComponentStorage.onLoad() {
        logger.info("协议版本检查更新...")
        try {
            FixProtocolVersion.update()
            for (protocol in BotConfiguration.MiraiProtocol.values()) {
                val file = File("${protocol.name.lowercase()}.json")
                if (file.exists()) {
                    logger.info("$protocol load from \n ${file.toPath().toUri()}")
                    FixProtocolVersion.load(protocol, file)
                }
            }
        } catch (cause: Throwable) {
            logger.error("协议版本升级失败", cause)
        }
        logger.info("注册服务...")
        try {
            NetworkServiceFactory.install()
            with(File(System.getProperty(NetworkServiceFactory.CONFIG_PATH_PROPERTY, "network.json"))) {
                if (exists().not()) {
                    writeText(NetworkServiceFactory.DEFAULT_CONFIG)
                }
                logger.info("服务配置文件: \n ${toPath().toUri()}")
            }
            logger.warning("您正在使用远程签名服务，数据包将经过签名服务器，请勿添加不可信源")
            val factory = NetworkServiceFactory.inst ?: throw IllegalStateException("当前使用的签名服务并非 trpgbot")
            logger.info("正在检查可用的签名服务器")

            val (about, _) = factory.networkConfig.tryServers(true)
            factory.checkProtocolUpdate(BotConfiguration.MiraiProtocol.ANDROID_PAD, about)

        } catch (_: NoClassDefFoundError) {
            logger.warning("注册服务失败，请在 2.15.0-dev-105 或更高版本下运行")
        } catch (cause: Throwable) {
            logger.error("注册服务失败", cause)
        }
    }

    override fun onEnable() {
        runBlocking {
            logger.info {
                buildString {
                    appendLine("当前各登录协议版本日期: ")
                    for ((_, info) in FixProtocolVersion.info()) {
                        appendLine(info)
                    }
                    appendLine()
                }
            }
            ConsoleCommandSender.sendAnsiMessage {
                val support = AnsiMessageBuilder.isAnsiSupported(ConsoleCommandSender)
                val escape = '\u001b'
                append("        ")
                if (support) append("$escape[1;91m")
                append(" !!! ")
                reset().append(" ")
                if (support) append("$escape[1;93;45m")
                append("  请使用 ANDROID_PAD 协议  ")
                reset().append(" ")
                if (support) append("$escape[1;91m")
                append(" !!! ").reset()
                appendLine().append("            ").append("如需使用其他协议，请手动升级协议版本")
                appendLine().appendLine()
            }
            FixProtocolVersionCommand.register()
        }
    }

    override fun onDisable() {
        FixProtocolVersionCommand.unregister()
    }
}
