package xyz.cssxsh.mirai.tool

import net.mamoe.mirai.internal.network.handler.selector.*

@PublishedApi
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
internal class NetworkServiceStateException(message: String) : NetworkException(message = message, recoverable = true)