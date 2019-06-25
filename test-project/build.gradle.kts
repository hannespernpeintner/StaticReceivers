plugins {
    kotlin("jvm") version "1.3.40"
    id("de.hanno.kotlin.plugins.staticreceivers")
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
//    implementation("commons-lang:commons-lang:20030203.000129")
//    implementation("com.google.guava:guava:28.0-jre")
    implementation("commons-collections:commons-collections:3.2.2")
}