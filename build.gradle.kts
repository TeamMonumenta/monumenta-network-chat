import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "3.+"
}

repositories {
	mavenCentral()
	maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	maven("https://repo.viaversion.com")
}

dependencies {
	implementation(libs.commons)
	compileOnly(libs.commandapi)
	compileOnly(libs.networkrelay)
	compileOnly(libs.redissync) {
		artifact {
			classifier = "all"
		}
	}
	compileOnly(libs.placeholderapi)
	compileOnly(libs.protocollib)
	compileOnly(libs.viaversion)
}

monumenta {
	id("MonumentaNetworkChat")
	name("NetworkChat")
	paper(
		"com.playmonumenta.networkchat.NetworkChatPlugin", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf(
			"CommandAPI",
			"MonumentaNetworkRelay",
			"MonumentaRedisSync",
			"PlaceholderAPI",
			"ProtocolLib"
		),
		softDepends = listOf("ViaVersion"),
		apiJarVersion = "1.20.4-R0.1-SNAPSHOT"
	)
}
