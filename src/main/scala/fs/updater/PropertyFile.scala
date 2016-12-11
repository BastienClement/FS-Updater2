package fs.updater

import java.io.{File, FileReader, FileWriter}
import java.util.Properties
import scala.language.dynamics
import scala.collection.JavaConverters._

class PropertyFile(val file: File) extends Dynamic {
	val props = new Properties()

	// Create the file an load it
	if (!file.exists) file.createNewFile()
	using(new FileReader(file)) { in => props.load(in) }

	def get(key: String): String = synchronized {
		props.getProperty(key)
	}

	def set(key: String, value: String): Unit = synchronized {
		if (value == null) props.remove(key)
		else props.setProperty(key, value)
		flush()
	}

	def keys: Seq[String] = synchronized {
		props.keySet.asInstanceOf[java.util.Set[String]].asScala.toSeq
	}

	def flush(): Unit = synchronized {
		using(new FileWriter(file)) { out =>
			props.store(out, null)
			out.flush()
		}
	}
}
