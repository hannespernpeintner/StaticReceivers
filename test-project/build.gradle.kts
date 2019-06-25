import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("jvm") version "1.3.40"
    id("de.hanno.kotlin.plugins.staticreceivers").version("1.0-SNAPSHOT")
}

repositories {
    mavenCentral()
    jcenter()
}

sourceSets["main"].withConvention(KotlinSourceSet::class) {
    kotlin.srcDir(buildDir.resolve("staticmethods"))
}

tasks.getByName("assemble").dependsOn("generateReceiverFunctions")

dependencies {
    implementation(kotlin("stdlib"))
//    implementation("commons-lang:commons-lang:20030203.000129")
//    implementation("com.google.guava:guava:28.0-jre")
    implementation("commons-collections:commons-collections:3.2.2")
}