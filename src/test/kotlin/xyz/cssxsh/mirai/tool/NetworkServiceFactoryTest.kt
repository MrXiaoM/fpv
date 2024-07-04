package xyz.cssxsh.mirai.tool

import net.mamoe.mirai.internal.spi.*
import kotlin.test.*

internal class NetworkServiceFactoryTest {

    @Test
    fun service() {
        @Suppress("INVISIBLE_MEMBER")
        assertIs<NetworkServiceFactory>(EncryptService.Companion.factory)
    }
}