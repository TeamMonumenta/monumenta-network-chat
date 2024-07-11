import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.lang.Closure
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.errorprone.CheckSeverity

plugins {
    java
    `maven-publish`
    id("com.palantir.git-version") version "0.12.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.ltgt.errorprone") version "2.0.2"
    id("net.ltgt.nullaway") version "1.3.0"
	id("com.playmonumenta.deployment") version "1.2"
    checkstyle
    pmd
}

repositories {
    mavenLocal()
	mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
	maven("https://oss.sonatype.org/content/repositories/snapshots/")
	maven("https://libraries.minecraft.net")
	maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	maven("https://repo.dmulloy2.net/repository/public/")
	maven("https://repo.maven.apache.org/maven2/")
	maven("https://repo.codemc.org/repository/maven-public/")
	maven("https://maven.playmonumenta.com/releases/")
}

dependencies {
    implementation("net.kyori:adventure-text-minimessage:4.11.0")
    implementation("org.apache.commons:commons-text:1.3")
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-bukkit-core:9.4.1")
    compileOnly("com.playmonumenta:monumenta-network-relay:2.7")
    compileOnly("com.playmonumenta:redissync:4.1:all")
    compileOnly("io.lettuce:lettuce-core:5.3.5.RELEASE")
    compileOnly("me.clip:placeholderapi:2.10.9")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.0.0")
    errorprone("com.google.errorprone:error_prone_core:2.10.0")
    errorprone("com.uber.nullaway:nullaway:0.9.5")
}

group = "com.playmonumenta"
val gitVersion: Closure<String> by extra
version = gitVersion()
description = "MonumentaNetworkChat"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

// Configure plugin.yml generation
bukkit {
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
    name = "MonumentaNetworkChat"
    main = "com.playmonumenta.networkchat.NetworkChatPlugin"
    apiVersion = "1.13"
    authors = listOf("NickNackGus")
    depend = listOf(
        "CommandAPI",
        "MonumentaNetworkRelay",
        "MonumentaRedisSync",
        "PlaceholderAPI",
        "ProtocolLib"
    )
    softDepend = listOf()
}

pmd {
    isConsoleOutput = true
    toolVersion = "6.41.0"
    ruleSets = listOf("$rootDir/pmd-ruleset.xml")
	isIgnoreFailures = true
}

java {
	withJavadocJar()
	withSourcesJar()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}
	repositories {
		maven {
			name = "MonumentaMaven"
			url = when (version.toString().endsWith("SNAPSHOT")) {
				true -> uri("https://maven.playmonumenta.com/snapshots")
				false -> uri("https://maven.playmonumenta.com/releases")
			}

			credentials {
				username = System.getenv("USERNAME")
				password = System.getenv("TOKEN")
			}
		}
	}
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xmaxwarns")
    options.compilerArgs.add("10000")

    options.compilerArgs.add("-Xlint:deprecation")

    options.errorprone {
        option("NullAway:AnnotatedPackages", "com.playmonumenta")

        allErrorsAsWarnings.set(true)

        /*** Disabled checks ***/
        // These we almost certainly don't want
        check("CatchAndPrintStackTrace", CheckSeverity.OFF) // This is the primary way a lot of exceptions are handled
        check("FutureReturnValueIgnored", CheckSeverity.OFF) // This one is dumb and doesn't let you check return values with .whenComplete()
        check("ImmutableEnumChecker", CheckSeverity.OFF) // Would like to turn this on but we'd have to annotate a bunch of base classes
        check("LockNotBeforeTry", CheckSeverity.OFF) // Very few locks in our code, those that we have are simple and refactoring like this would be ugly
        check("StaticAssignmentInConstructor", CheckSeverity.OFF) // We have tons of these on purpose
        check("StringSplitter", CheckSeverity.OFF) // We have a lot of string splits too which are fine for this use
        check("MutablePublicArray", CheckSeverity.OFF) // These are bad practice but annoying to refactor and low risk of actual bugs
        check("InlineMeSuggester", CheckSeverity.OFF) // This seems way overkill
    }
}

ssh.easySetup(tasks.shadowJar.get(), "NetworkChat")
