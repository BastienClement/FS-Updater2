package fs.updater

import com.mashape.unirest.http.Unirest
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.zip.ZipFile

object Repository {
	private val lock = new Semaphore(1)

	def schedule(task: => Unit): Unit = {
		new Thread(() => {
			lock.acquire()
			try {
				task
			} finally {
				lock.release()
			}
		}).start()
	}

	private val addons = new PropertyFile(ConfRoot / "addons.properties")

	def addonsFolder: File = Option(LocalSettings.get("addons.folder")).map(new File(_)).orNull

	def addonsFolderValid: Boolean = addonsFolderValid(addonsFolder)

	def addonsFolderValid(folder: File): Boolean =
		folder != null && folder.isDirectory && (folder.getParentFile.getParentFile / "WoW.mfil").isFile

	def setAddonsFolder(folder: File): Unit =
		if (addonsFolderValid(folder)) LocalSettings.set("addons.folder", folder.getAbsolutePath)

	private def addonManifest(name: String): File = ConfRoot / s"manifest-$name.properties"

	def addonEnabled(name: String): Boolean = currentRevision(name) != null
	def addonPresent(name: String): Boolean = (addonsFolder / name).exists
	def currentRevision(name: String): String = addons.get(name)

	def install(addon: Addon): Unit = {
		UpdaterStatusInstance.start(s"Downloading: ${ addon.name }")
		val source = Unirest.get(s"https://addons.fromscratch.gg/${ addon.file }")
				.header("Authorization", s"Bearer ${ OAuth.token.get }")
				.asBinary()

		if (source.getStatus == 403) OAuth.failure()
		else if (source.getStatus != 200) {
			UpdaterStatusInstance.stop(s"Failed to install ${ addon.name }")
			addon.forceChangeEnable(false)
			return
		}

		val archive = File.createTempFile(addon.name, ".zip", ConfRoot)
		archive.delete()
		Files.copy(source.getBody, archive.toPath)

		UpdaterStatusInstance.start(s"Unpacking: ${ addon.name }")
		val zip = new ZipFile(archive)
		var folders = Set.empty[String]
		zip.stream().forEach { entry =>
			if (entry.isDirectory) {
				folders += entry.getName.split("/|\\\\")(0)
			} else {
				val file = addonsFolder / entry.getName
				file.getParentFile.mkdirs()
				if (file.exists) file.delete()
				Files.copy(zip.getInputStream(entry), file.toPath)
			}
		}
		zip.close()

		val manifest = new PropertyFile(addonManifest(addon.name))
		folders.foreach(manifest.set(_, ""))

		archive.delete()
		addons.set(addon.name, addon.rev)
		UpdaterStatusInstance.stop(s"Installed: ${ addon.name }")
		updateRepositoryData()
	}

	def uninstall(addon: Addon): Unit = {
		UpdaterStatusInstance.start(s"Uninstalling: ${ addon.name }")
		val manifestFile = addonManifest(addon.name)
		val manifest = new PropertyFile(manifestFile)
		for (root <- manifest.keys) {
			Files.walk((addonsFolder / root).toPath).sorted(Comparator.reverseOrder()).forEachOrdered(Files.delete _)
		}
		manifestFile.delete()
		addons.set(addon.name, null)
		UpdaterStatusInstance.stop(s"Uninstalled: ${ addon.name }")
		updateRepositoryData()
	}

	def update(addon: Addon): Unit = {
		uninstall(addon)
		install(addon)
		UpdaterStatusInstance.stop(s"Updated: ${ addon.name }")
	}

	def reset(name: String): Unit = {
		addons.set(name, null)
		val manifest = addonManifest(name)
		if (manifest.exists) manifest.delete()
		updateRepositoryData()
	}

	private def updateRepositoryData(): Unit = {
		if (addonEnabled("FS_UpdaterStatus")) {
			val out = new StringBuilder
			out.append("FS_UPDATER_ADDONS = {\n")
			addons.keys.map { addon =>
				"\t[\"%s\"] = \"%s\",\n".format(addon, addons.get(addon))
			}.foreach(out.append)
			out.append("}\n")
			Files.write((addonsFolder / "FS_UpdaterStatus/AddonsList.lua").toPath, out.toString.getBytes)
		}
	}
}
