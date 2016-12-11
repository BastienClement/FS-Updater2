package fs

import java.io.File
import java.util.concurrent.Semaphore
import scala.concurrent.Future
import scalafx.application.Platform
import scala.concurrent.ExecutionContext.Implicits.global

package object updater {
	var UpdaterStatusInstance: UpdaterStatus = _

	def using[T <: AutoCloseable, U](closeable: T)(body: T => U): U = {
		try {
			body(closeable)
		} finally {
			closeable.close()
		}
	}

	implicit class FilePath(private val file: File) extends AnyVal {
		@inline final def /(child: String): File = new File(file, child)
	}

	val ConfRoot: File = new File(System.getProperty("user.home"), ".fs-updater")
	val LocalSettings = new PropertyFile(ConfRoot / "settings.properties")

	private class Ref[T] {
		@volatile private var value: T = _
		def set(v: T): Unit = value = v
		def get(): T = value
	}

	def exec[T](body: => T): T = {
		if (Platform.isFxApplicationThread) body
		else {
			val lock = new Semaphore(0)
			val res = new Ref[T]
			Platform.runLater {
				res.set(body)
				lock.release()
			}
			lock.acquire()
			res.get()
		}
	}

	def async[T](body: => T)(block: T => Unit): Unit = {
		require(Platform.isFxApplicationThread)
		for (res <- Future(body)) Platform.runLater(block(res))
	}
}
