group = "de.hanno.kotlin.plugins"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.3.40"
    id("java-gradle-plugin")
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(gradleApi())
    implementation("io.github.classgraph:classgraph:4.8.41")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.squareup:kotlinpoet:1.3.0")
}

gradlePlugin {
    plugins {
        create("StaticReceivers") {
            id = "de.hanno.kotlin.plugins.staticreceivers"
            implementationClass = "de.hanno.kotlin.plugins.StaticReceiversPlugin"
        }
    }
}