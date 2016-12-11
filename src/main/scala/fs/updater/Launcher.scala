package fs.updater

import java.io.File
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLView, NoDependencyResolver}

object Launcher extends JFXApp {
	val ConfDir: File = new File(System.getProperty("user.home"), ".fs-updater")

	val root = FXMLView(getClass.getResource("form/updater.fxml"), NoDependencyResolver)

	stage = new PrimaryStage {
		title = "FS-Updater"
		scene = new Scene(root)
	}
}
