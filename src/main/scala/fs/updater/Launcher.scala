package fs.updater

import java.io.File
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLView, NoDependencyResolver}

/**
  * Application launcher
  */
object Launcher extends JFXApp {
	val root = FXMLView(getClass.getClassLoader.getResource("updater.fxml"), NoDependencyResolver)
	stage = new PrimaryStage {
		title = "FS-Updater"
		scene = new Scene(root)
	}
}
