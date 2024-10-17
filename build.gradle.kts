plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"

    id("net.mamoe.mirai-console") version "2.16.0"
}

group = "xyz.cssxsh.mirai"
version = "1.13.2"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    //
    implementation(platform("net.mamoe:mirai-bom:2.16.0"))
    compileOnly("net.mamoe:mirai-core")
    compileOnly("net.mamoe:mirai-core-utils")
    compileOnly("net.mamoe:mirai-console-compiler-common")
    testImplementation("net.mamoe:mirai-core-mock")
    testImplementation("net.mamoe:mirai-logging-slf4j")
    //
    implementation("org.asynchttpclient:async-http-client:3.0.0.Beta3")
    //
    implementation(platform("org.slf4j:slf4j-parent:2.0.7"))
    testImplementation("org.slf4j:slf4j-simple")
    //
    implementation(platform("io.netty:netty-bom:4.1.96.Final"))
}

kotlin {
    explicitApi()
}

mirai {
    coreVersion = "2.16.0"
    consoleVersion = "2.16.0"
    jvmTarget = JavaVersion.VERSION_11
}

tasks {
    test {
        useJUnitPlatform()
    }
}
