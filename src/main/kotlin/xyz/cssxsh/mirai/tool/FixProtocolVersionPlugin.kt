package xyz.cssxsh.mirai.tool

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.*
import java.io.File

@PublishedApi
internal object FixProtocolVersionPlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "trpgbot.qsign",
        name = "trpgbot",
        version = "1.13.1"
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

            val (about, _) = factory.networkConfig.tryServers()
            factory.checkProtocolUpdate(BotConfiguration.MiraiProtocol.ANDROID_PAD, about)

        } catch (_: NoClassDefFoundError) {
            logger.warning("注册服务失败，请在 2.15.0-dev-105 或更高版本下运行")
        } catch (cause: Throwable) {
            logger.error("注册服务失败", cause)
        }
    }

    override fun onEnable() {
        logger.info {
            buildString {
                appendLine("当前各登录协议版本日期: ")
                for ((_, info) in FixProtocolVersion.info()) {
                    appendLine(info)
                }
                appendLine()
                appendLine("请使用 ANDROID_PAD 协议登录")
            }
        }
        FixProtocolVersionCommand.register()
    }

    override fun onDisable() {
        FixProtocolVersionCommand.unregister()
    }
}
