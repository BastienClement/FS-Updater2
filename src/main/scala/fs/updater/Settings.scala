package fs.updater

import java.io.File

object Settings {
	private val settings = new PropertyFile(Launcher.ConfDir / "settings.properties")

	/**
	  * Gets the currently defined addons folder
	  */
	def addonsFolder: Option[File] = Option(settings.get("addons.folder")).map(path => new File(path))

	/**
	  * Checks if the currently defined addons folder is valid.
	  */
	def isAddonsFolderValid: Boolean = addonsFolder.exists(isAddonsFolderValid)

	/**
	  * Checks if the given addons folder is valid.
	  * @param folder the addons folder to check
	  */
	def isAddonsFolderValid(folder: File): Boolean =
		folder.isDirectory  && (folder.getParentFile.getParentFile / "WoW.mfil").isFile

	/**
	  * Defines a new addons folder.
	  */
	def setAddonsFolder(folder: File): Unit = settings.set("addons.folder", folder.getAbsolutePath)
}
