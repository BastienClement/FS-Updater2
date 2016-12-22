package fs.updater

import com.mashape.unirest.http.Unirest
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.zip.ZipFile

/**
  * The addon repository manager.
  */
object Repository {
	/**
	  * The exclusive lock to the repository
	  */
	private val lock = new Semaphore(1)

	/**
	  * Shedules an exclusive task to run with the respository.
	  *
	  * @param task the task to execute
	  */
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

	/**
	  * The installed addons list
	  */
	private val addons = new PropertiesFile(ConfRoot / "addons.properties")

	/**
	  * The WoW addons folder
	  */
	def addonsFolder: File = Option(LocalSettings.get("addons.folder")).map(new File(_)).orNull

	/**
	  * Whether the defined addon folder is valid
	  */
	def addonsFolderValid: Boolean = addonsFolderValid(addonsFolder)

	/**
	  * Whether the given folder is a valid addons folder.
	  *
	  * @param folder the folder to check
	  */
	def addonsFolderValid(folder: File): Boolean =
		folder != null && folder.isDirectory && (folder.getParentFile.getParentFile / "WoW.mfil").isFile

	/**
	  * Defines the addons folder to use.
	  *
	  * If the given folder is not valid, it is ignored.
	  *
	  * @param folder the folder to use
	  */
	def setAddonsFolder(folder: File): Unit =
		if (addonsFolderValid(folder)) LocalSettings.set("addons.folder", folder.getAbsolutePath)

	/**
	  * The manifest file for a given addon.
	  *
	  * @param name the addon name
	  */
	private def addonManifest(name: String): File = ConfRoot / s"manifest-$name.properties"

	/**
	  * Checks if an addon is enabled.
	  *
	  * @param name the addon name
	  */
	def addonEnabled(name: String): Boolean = currentRevision(name) != null

	/**
	  * Checks if an addon is physically present in the addons folder.
	  *
	  * @param name the addon name
	  */
	def addonPresent(name: String): Boolean = (addonsFolder / name).exists

	/**
	  * The currently installed revision of an addon.
	  *
	  * @param name the addon name
	  */
	def currentRevision(name: String): String = addons.get(name)

	/**
	  * Installs an addon.
	  *
	  * @param addon the addon to install
	  */
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

		val manifest = new PropertiesFile(addonManifest(addon.name))
		folders.foreach(manifest.set(_, ""))

		archive.delete()
		addons.set(addon.name, addon.rev)
		UpdaterStatusInstance.stop(s"Installed: ${ addon.name }")
		updateRepositoryData()
	}

	/**
	  * Uninstalls an addon.
	  *
	  * @param addon the addon to uninstall
	  */
	def uninstall(addon: Addon): Unit = {
		UpdaterStatusInstance.start(s"Uninstalling: ${ addon.name }")
		val manifestFile = addonManifest(addon.name)
		val manifest = new PropertiesFile(manifestFile)
		for (root <- manifest.keys) {
			Files.walk((addonsFolder / root).toPath).sorted(Comparator.reverseOrder()).forEachOrdered(Files.delete _)
		}
		manifestFile.delete()
		addons.set(addon.name, null)
		UpdaterStatusInstance.stop(s"Uninstalled: ${ addon.name }")
		updateRepositoryData()
	}

	/**
	  * Updates an addon.
	  *
	  * @param addon the addon to update
	  */
	def update(addon: Addon): Unit = {
		uninstall(addon)
		install(addon)
		UpdaterStatusInstance.stop(s"Updated: ${ addon.name }")
	}

	/**
	  * Resets the cached state of an addon. Effectively unmarking it as installed.
	  *
	  * @param name the addon name
	  */
	def reset(name: String): Unit = {
		addons.set(name, null)
		val manifest = addonManifest(name)
		if (manifest.exists) manifest.delete()
		updateRepositoryData()
	}

	/**
	  * Updates FS_UpdaterStatus respository data.
	  */
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
