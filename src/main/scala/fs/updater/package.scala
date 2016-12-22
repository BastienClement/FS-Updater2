package fs

import java.io.File
import java.util.concurrent.Semaphore
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalafx.application.Platform

package object updater {
	/**
	  * Reference to the status bar of the main UI window
	  */
	var UpdaterStatusInstance: UpdaterStatus = _

	/**
	  * Try-with-resource implementation for Scala.
	  *
	  * @param closeable an AutoCloseable object
	  * @param body      the body using the closeable object
	  * @tparam T the type of the closable object
	  * @tparam U the type of value produced by the body chunk
	  * @return the return value of the body chunk
	  */
	def using[T <: AutoCloseable, U](closeable: T)(body: T => U): U = {
		try {
			body(closeable)
		} finally {
			closeable.close()
		}
	}

	/**
	  * Implicit path operator
	  *
	  * @param parent the parent File
	  */
	implicit class FilePath(private val parent: File) extends AnyVal {
		/**
		  * Creates a new File object referencing a file from the parent folder.
		  *
		  * @param child the child file name
		  */
		@inline final def / (child: String): File = new File(parent, child)
	}

	/**
	  * The root local configuration directory
	  */
	val ConfRoot: File = new File(System.getProperty("user.home"), ".fs-updater")
	if (!ConfRoot.exists()) ConfRoot.mkdirs()

	/**
	  * The local settings file
	  */
	val LocalSettings = new PropertiesFile(ConfRoot / "settings.properties")

	/**
	  * A mutable value container
	  *
	  * @tparam T the type of the contained value
	  */
	private class Ref[T] {
		@volatile private var value: T = _
		def set(v: T): Unit = value = v
		def get(): T = value
	}

	/**
	  * Executes a chunk of code on the JavaFX UI thread and returns the result.
	  *
	  * @param body the chunk of code to execute
	  * @tparam T the return type of the block
	  * @return the return value of the block
	  */
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

	/**
	  * Executes a chunk of code asynchronously from the JavaFX UI thread and then
	  * executes the callback function on the UI thread.
	  *
	  * This function can only be called from the JavaFX UI thread.
	  *
	  * @param body  the block to execute asynchronously
	  * @param block the callback function
	  * @tparam T the return type of the given chunk
	  */
	def async[T](body: => T)(block: T => Unit): Unit = {
		require(Platform.isFxApplicationThread)
		for (res <- Future(body)) Platform.runLater(block(res))
	}
}
