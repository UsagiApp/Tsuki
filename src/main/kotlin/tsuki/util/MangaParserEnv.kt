package tsuki.util

import okhttp3.HttpUrl
import org.jsoup.nodes.Element
import tsuki.ErrorMessages
import tsuki.InternalParsersApi
import tsuki.MangaParser
import tsuki.core.AbstractMangaParser
import tsuki.exception.ParseException
import tsuki.model.*


/**
 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
 * @param source the manga source
 * @param url must be relative url, without a domain
 * @see [Manga.id]
 * @see [MangaChapter.id]
 * @see [MangaPage.id]
 */
public fun generateUid(source: MangaSource, url: String): Long {
	var h = LONG_HASH_SEED
	source.name.forEach { c ->
		h = 31 * h + c.code
	}
	url.forEach { c ->
		h = 31 * h + c.code
	}
	return h
}

/**
 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
 * @param source the manga source
 * @param id an internal identifier
 * @see [Manga.id]
 * @see [MangaChapter.id]
 * @see [MangaPage.id]
 */
public fun generateUid(source: MangaSource, id: Long): Long {
	var h = LONG_HASH_SEED
	source.name.forEach { c ->
		h = 31 * h + c.code
	}
	h = 31 * h + id
	return h
}

@InternalParsersApi
public fun Element.parseFailed(message: String? = null): Nothing {
	throw ParseException(message, ownerDocument()?.location() ?: baseUri(), null)
}

@InternalParsersApi
public fun Set<MangaTag>?.oneOrThrowIfMany(): MangaTag? = oneOrThrowIfMany(
	ErrorMessages.FILTER_MULTIPLE_GENRES_NOT_SUPPORTED,
)

@InternalParsersApi
public fun Set<MangaState>?.oneOrThrowIfMany(): MangaState? = oneOrThrowIfMany(
	ErrorMessages.FILTER_MULTIPLE_STATES_NOT_SUPPORTED,
)

@InternalParsersApi
public fun Set<ContentType>?.oneOrThrowIfMany(): ContentType? = oneOrThrowIfMany(
	ErrorMessages.FILTER_MULTIPLE_CONTENT_TYPES_NOT_SUPPORTED,
)

@InternalParsersApi
public fun Set<Demographic>?.oneOrThrowIfMany(): Demographic? = oneOrThrowIfMany(
	ErrorMessages.FILTER_MULTIPLE_DEMOGRAPHICS_NOT_SUPPORTED,
)

@InternalParsersApi
public fun Set<ContentRating>?.oneOrThrowIfMany(): ContentRating? = oneOrThrowIfMany(
	ErrorMessages.FILTER_MULTIPLE_CONTENT_RATING_NOT_SUPPORTED,
)

private fun <T> Set<T>?.oneOrThrowIfMany(msg: String): T? = when {
	isNullOrEmpty() -> null
	size == 1 -> first()
	else -> throw IllegalArgumentException(msg)
}

public fun urlBuilder(domain: String, subdomain: String? = null): HttpUrl.Builder {
	return HttpUrl.Builder()
		.scheme(SCHEME_HTTPS)
		.host(if (subdomain == null) domain else "$subdomain.$domain")
}

public fun MangaParser.generateUid(url: String): Long =
	tsuki.util.generateUid(source, url)

public fun MangaParser.generateUid(id: Long): Long =
	tsuki.util.generateUid(source, id)


public fun MangaParser.urlBuilder(subdomain: String? = null): HttpUrl.Builder =
	tsuki.util.urlBuilder(domain, subdomain)

@InternalParsersApi
public fun getDomain(parser: MangaParser, subdomain: String): String {
	return subdomain + "." + parser.domain.removePrefix("www.")
}
