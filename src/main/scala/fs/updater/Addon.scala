package fs.updater

import scalafx.beans.property.{BooleanProperty, StringProperty}

case class Addon(name: String, rev: String, file: String, force: Boolean) {
	val enable = new BooleanProperty(this, "enable", false)
	val status = new StringProperty(this, "name", name)

	if (Repository.addonEnabled(name)) {
		if (!Repository.addonPresent(name)) {
			Repository.reset(name)
		} else {
			enable.value = true
		}
	}

	updateStatus()

	private var skipChange = false
	enable.onChange((_, _, n: java.lang.Boolean) => if (!skipChange) {
		Repository.schedule {
			if (n) Repository.install(this)
			else if (!force) Repository.uninstall(this)
			else forceChangeEnable(true)
			updateStatus()
		}
	})

	def forceChangeEnable(value: Boolean): Unit = {
		skipChange = true
		enable.set(value)
		skipChange = false
	}

	def updateStatus(): Unit = {
		val current = Repository.currentRevision(name)
		if (current == null) {
			if (Repository.addonPresent(name)) status.value = "Unmanaged"
			else status.value = "Not installed"
		} else if (current == rev) {
			status.value = "Up to date"
		} else {
			status.value = "Outdated"
		}
	}

	def shouldAutoUpdate: Boolean = Repository.addonEnabled(name) && rev != Repository.currentRevision(name)
}
