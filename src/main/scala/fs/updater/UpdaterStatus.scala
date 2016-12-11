package fs.updater

import scalafx.scene.control.{Label, ProgressIndicator}

case class UpdaterStatus(status: Label, progress: ProgressIndicator) {
	def set(label: String, loading: Boolean = false): Unit = exec {
		//println(label)
		status.text = label
		progress.visible = loading
	}

	def start(label: String): Unit = set(label, loading = true)
	def stop(label: String): Unit = set(label)
}
