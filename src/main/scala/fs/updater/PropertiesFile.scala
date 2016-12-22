package fs.updater

import java.io.{File, FileReader, FileWriter}
import java.util.Properties
import scala.collection.JavaConverters._

/**
  * A high-level view of a Properties file.
  *
  * @param file the file path
  */
class PropertiesFile(val file: File) {
	/**
	  * The underlying properties list
	  */
	val props = new Properties()

	// Create the file an load it
	if (!file.exists) file.createNewFile()
	using(new FileReader(file)) { in => props.load(in) }

	/**
	  * Reads a key from the properties file.
	  *
	  * @param key the key to read
	  * @return the value for the key, or null
	  */
	def get(key: String): String = synchronized {
		props.getProperty(key)
	}

	/**
	  * Sets the value associated with a key.
	  *
	  * @param key   the key
	  * @param value the value to set; if null, the key is removed from the file
	  */
	def set(key: String, value: String): Unit = synchronized {
		if (value == null) props.remove(key)
		else props.setProperty(key, value)
		flush()
	}

	/**
	  * The sequence of keys in this file.
	  */
	def keys: Seq[String] = synchronized {
		props.keySet.asInstanceOf[java.util.Set[String]].asScala.toSeq
	}

	/**
	  * Flushes theses properties to the actual disk file.
	  * This method is automatically valled when mutating the properties list.
	  */
	def flush(): Unit = synchronized {
		using(new FileWriter(file)) { out =>
			props.store(out, null)
			out.flush()
		}
	}
}
