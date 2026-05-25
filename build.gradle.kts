plugins {
    kotlin("jvm") version "2.1.20"
    application
}

group = "com.anaxa"
version = "0.0.1"

application {
    mainClass.set("com.anaxa.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.1"
val logbackVersion = "1.5.16"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

kotlin {
    jvmToolchain(17)
}
