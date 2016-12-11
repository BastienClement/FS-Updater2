package fs.updater

import com.mashape.unirest.http.Unirest
import java.io.File
import javafx.collections.ObservableList
import scala.annotation.tailrec
import scalafx.beans.property.ObjectProperty
import scalafx.beans.value.ObservableValue
import scalafx.scene.control._
import scalafx.scene.control.cell.CheckBoxTableCell
import scalafx.stage.DirectoryChooser
import scalafxml.core.macros.sfxml

@sfxml
class Updater (val addonsPath: TextField,
               val table: TableView[Addon],
               val enableColumn: TableColumn[Addon, java.lang.Boolean],
               val nameColumn: TableColumn[Addon, String],
               val statusColumn: TableColumn[Addon, String],
               val revColumn: TableColumn[Addon, String],
               val statusLabel: Label,
               val statusProgress: ProgressIndicator) {
	implicit val status = UpdaterStatus(statusLabel, statusProgress)
	UpdaterStatusInstance = status
	val addons: ObservableList[Addon] = table.getItems

	table.editable = true
	table.focusTraversable = false
	table.columnResizePolicy = TableView.ConstrainedResizePolicy

	enableColumn.cellValueFactory = _.getValue.enable.asInstanceOf[ObservableValue[java.lang.Boolean, java.lang.Boolean]]
	enableColumn.cellFactory = CheckBoxTableCell.forTableColumn(enableColumn)

	nameColumn.cellValueFactory = t => ObjectProperty[String](t.value.name)
	statusColumn.cellValueFactory = t => t.value.status
	revColumn.cellValueFactory = t => ObjectProperty[String](t.value.rev.substring(0, 10))

	status.start("Checking authentication status...")
	async(OAuth.isAuthenticated) {
		case false => login()
		case true => init()
	}

	def login(): Unit = {
		status.start("Authenticating user...")
		OAuth.authenticate(init)
	}

	def init(): Unit = {
		ensureValidAddonsFolder()
		update()
		PushThread.start(update)
	}

	@tailrec
	private def ensureValidAddonsFolder(): Unit = {
		if (!Repository.addonsFolderValid) {
			val choice = pickAddonsFolder()
			if (choice == null) System.exit(1)
			ensureValidAddonsFolder()
		} else {
			addonsPath.text = Repository.addonsFolder.toString
		}
	}

	def pickAddonsFolder(): File = {
		val chooser = new DirectoryChooser()
		chooser.title = "Select WoW addons folder"
		val choice = chooser.showDialog(Launcher.stage)
		if (Repository.addonsFolderValid(choice)) {
			Repository.setAddonsFolder(choice)
			addonsPath.text = choice.toString
		}
		choice
	}

	def pickAddonsFolderAndUpdate(): Unit = {
		pickAddonsFolder()
		update()
	}

	def update(): Unit = {
		status.start(s"Refreshing addons list...")
		async(Unirest.get("https://addons.fromscratch.gg/manifest.php")
				.header("Authorization", s"Bearer ${ OAuth.token.get }")
		      .asJson()) { response =>
			response.getStatus match {
				case 403 => OAuth.failure()
				case 200 =>
					val list = response.getBody.getArray
					addons.clear()
					(0 until list.length).map(list.getJSONObject).map { data =>
						Addon(data.getString("name"), data.getString("rev"), data.getString("file"), data.getBoolean("force"))
					}.foreach(addons.add)
					status.stop("%d addons available".format(addons.size))
					addons.forEach { addon =>
						if (addon.shouldAutoUpdate) {
							Repository.schedule {
								Repository.update(addon)
								addon.updateStatus()
							}
						} else if (!addon.enable.value && addon.force) {
							addon.enable.set(true)
						}
					}
				case _ => // ignore
			}
		}
	}
}
