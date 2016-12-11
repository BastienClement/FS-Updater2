package fs.updater

import com.mashape.unirest.http.Unirest

object PushThread {
	def start(update: () => Unit): Unit = {
		val connect = Unirest.get("https://addons.fromscratch.gg/client/connect").asString().getBody
		println(connect)
		val hub = new Thread(() => {
			var last = System.currentTimeMillis()
			while (true) {
				try {
					Thread.sleep(5000)
					if ((System.currentTimeMillis() - last) < 300000) {
						Unirest.get("http://" + connect + "/wait").asString().getBody
						Thread.sleep(1000)
					}
					exec(update())
					last = System.currentTimeMillis()
				} catch {
					case e: Throwable =>	// ignored
				}
			}
		})
		hub.setDaemon(true)
		hub.start()
	}
}
