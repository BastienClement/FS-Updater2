package fs.updater

import scalafx.beans.property.{BooleanProperty, StringProperty}

/**
  * An addon descriptor.
  *
  * @param name  the name of the addon
  * @param rev   the last revision of the addon
  * @param file  the remote archive of the addon
  * @param force whether this addon is forcefully installed
  */
case class Addon(name: String, rev: String, file: String, force: Boolean) {
	/**
	  * Local enabled status
	  */
	val enable = new BooleanProperty(this, "enable", false)

	/**
	  * Textual indication of the current update status
	  */
	val status = new StringProperty(this, "name", name)

	// Check the actual addon state
	if (Repository.addonEnabled(name)) {
		if (!Repository.addonPresent(name)) {
			Repository.reset(name)
		} else {
			enable.value = true
		}
	}

	// Update the status text
	updateStatus()

	// Track changes to the enabled flag
	private var skipChange = false
	enable.onChange((_, _, n: java.lang.Boolean) => if (!skipChange) {
		Repository.schedule {
			if (n) Repository.install(this)
			else if (!force) Repository.uninstall(this)
			else forceChangeEnable(true)
			updateStatus()
		}
	})

	/**
	  * Updates the addon enabled flag without executing observer actions.
	  *
	  * @param value the new value of the enabled flag
	  */
	def forceChangeEnable(value: Boolean): Unit = {
		skipChange = true
		enable.set(value)
		skipChange = false
	}

	/**
	  * Updates the status text.
	  */
	def updateStatus(): Unit = status.value = {
		val current = Repository.currentRevision(name)
		if (current == null) {
			if (Repository.addonPresent(name)) "Unmanaged"
			else "Not installed"
		} else if (current == rev) {
			"Up to date"
		} else {
			"Outdated"
		}
	}

	/**
	  * Whether this addon should auto update.
	  */
	def shouldAutoUpdate: Boolean = Repository.addonEnabled(name) && rev != Repository.currentRevision(name)
}
