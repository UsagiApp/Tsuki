@file:JvmName("EnumUtils")

package tsuki.util

import kotlin.enums.EnumEntries

public fun <E : Enum<E>> EnumEntries<E>.names(): Array<String> = Array(size) { i ->
	get(i).name
}

public fun <E : Enum<E>> EnumEntries<E>.find(name: String): E? {
	return find { x -> x.name == name }
}
