package fs.updater

import scalafx.scene.control.{Label, ProgressIndicator}

/**
  * The main UI status bar.
  *
  * @param status   the status bar text
  * @param progress the progress indicator
  */
case class UpdaterStatus(status: Label, progress: ProgressIndicator) {
	/**
	  * Sets the state of the status bar.
	  *
	  * @param label   the status text label
	  * @param loading the progress indicator state
	  */
	def set(label: String, loading: Boolean = false): Unit = exec {
		//println(label)
		status.text = label
		progress.visible = loading
	}

	/**
	  * Starts a new "loading" activity.
	  *
	  * @param label the activity label
	  */
	def start(label: String): Unit = set(label, loading = true)

	/**
	  * Stops a previously "loading" activity.
	  *
	  * @param label the new status text to display
	  */
	def stop(label: String): Unit = set(label)
}
