@file:Suppress("warnings", "unused")

package tsuki.util

public inline fun <T> T?.ifNotNull(block: (T) -> Unit) {
	if (this != null) block(this)
}

public fun String?.nullIfEmpty(): String? = if (isNullOrEmpty()) null else this
