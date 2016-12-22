package fs.updater

import com.mashape.unirest.http.Unirest
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import javafx.concurrent.Worker.State
import org.json.JSONObject
import scala.concurrent.duration._
import scalafx.scene.Scene
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.web.WebView
import scalafx.stage.{Modality, Stage}

/**
  * OAuth management stuff
  */
object OAuth {
	/**
	  * Fetches the current access token, if available.
	  */
	private def accessToken: Option[String] = Option(LocalSettings.get("oauth.token"))

	/**
	  * Fetches the current refresh token, if availble.
	  */
	private def refreshCode: Option[String] = Option(LocalSettings.get("oauth.refresh"))

	/**
	  * Time of the next token status recheck
	  */
	private var recheck: Deadline = Deadline.now

	/**
	  * Cached username of the authenticated user
	  */
	private var cachedUsername: String = "Guest"

	/**
	  * The connected user's name
	  */
	def username: String = cachedUsername

	/**
	  * Checks the authentication status of the client
	  */
	def isAuthenticated: Boolean = token.isDefined

	/**
	  * Returns a valid access token if possible
	  */
	def token: Option[String] = {
		if (recheck.hasTimeLeft) accessToken
		else accessToken.filter(tokenIsValid).orElse(newToken)
	}

	/**
	  * Loads token from an authorization code.
	  *
	  * @param code the authorization code
	  */
	def authorize(code: String): Unit = {
		val res = Unirest.post("https://auth.fromscratch.gg/oauth/token")
				.basicAuth("30f3f11b-0d6b-4881-aa51-55ddad1b97ac", "")
				.field("grant_type", "authorization_code")
				.field("code", code)
				.asJson()
		if (res.getStatus == 200) loadToken(res.getBody.getObject)
	}

	/**
	  * Attempts to fetch a new access token
	  */
	private def newToken: Option[String] = {
		refreshCode.flatMap { refresh =>
			val res = Unirest.post("https://auth.fromscratch.gg/oauth/token")
					.basicAuth("30f3f11b-0d6b-4881-aa51-55ddad1b97ac", "")
					.field("grant_type", "refresh_token")
					.field("refresh_token", refresh)
					.asJson()
			if (res.getStatus == 200) Some(res.getBody.getObject)
			else {
				LocalSettings.set("oauth.refresh", null)
				None
			}
		}.map(loadToken).filter(tokenIsValid)
	}

	/**
	  * Checks whether a token is valid.
	  *
	  * @param token the access token to check
	  */
	private def tokenIsValid(token: String): Boolean = {
		val res = Unirest.get("https://addons.fromscratch.gg/verify.php").queryString("token", token).asJson()
		val valid = res.getStatus == 200 && res.getBody.getObject.getBoolean("active")
		if (valid) {
			recheck = 5.minutes.fromNow
			cachedUsername = res.getBody.getObject.getString("user")
		}
		valid
	}

	/**
	  * Loads and save token information from a valid token response.
	  *
	  * @param obj the token response object
	  * @return the access token itself
	  */
	private def loadToken(obj: JSONObject): String = {
		LocalSettings.set("oauth.token", obj.getString("access_token"))
		LocalSettings.set("oauth.refresh", obj.getString("refresh_token"))
		LocalSettings.get("oauth.token")
	}

	/**
	  * Performs OAuth authentication using a WebView to FS-Auth.
	  */
	def authenticate(init: () => Unit): Unit = exec {
		// Prepare WebView
		lazy val stage: Stage = new Stage {
			title = "Login"
			scene = new Scene(550, 500) {
				val webView = new WebView
				webView.engine.userAgent = "X-FS-Updater 2.0"
				webView.engine.load("https://auth.fromscratch.gg/oauth/authorize?client_id=30f3f11b-0d6b-4881-aa51-55ddad1b97ac&response_type=code&embed&scope=addons")
				webView.engine.getLoadWorker.stateProperty().addListener(new ChangeListener[State] {
					def changed(observable: ObservableValue[_ <: State], oldValue: State, newValue: State): Unit = {
						if (newValue == Worker.State.SUCCEEDED) {
							val location = webView.engine.location.value
							if (location.startsWith("about:blank")) {
								location.split('?').last.split('&').collectFirst {
									case param if param.startsWith("code=") => param.substring(5)
								} match {
									case Some(code) =>
										authorize(code)
										stage.close()
									case None =>
										stage.close()
								}
							}
						}
					}
				})
				root = webView
				resizable = false
			}
		}

		// Open as modal
		stage.initOwner(null)
		stage.initModality(Modality.ApplicationModal)
		stage.showAndWait()

		// Check final authentication state
		async(isAuthenticated) {
			case true => init()
			case false => failure()
		}
	}

	/**
	  * Authentication failure handler.
	  * Displays an error and exit the application.
	  */
	def failure(): Unit = exec {
		new Alert(AlertType.Error) {
			initOwner(Launcher.stage)
			title = "Authentication failure"
			headerText = "Unable to validate access to FS-Updater"
			contentText = "Either an error occurred during the authentication process or you don't have the required authorizations to access FS-Updater.\n\nThe application will now exit."
		}.showAndWait()
		System.exit(0)
	}
}
