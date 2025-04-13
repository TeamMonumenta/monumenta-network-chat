rootProject.name = "monumenta-network-chat"

pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://maven.playmonumenta.com/releases/")
		// TODO: don't merge unless this is removed
		maven("https://maven.playmonumenta.com/snapshots/")
	}
}
