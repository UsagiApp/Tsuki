@file:Suppress("DEPRECATION")

package tsuki.core

import androidx.annotation.CallSuper
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import tsuki.InternalParsersApi
import tsuki.MangaLoaderContext
import tsuki.MangaParser
import tsuki.config.ConfigKey
import tsuki.config.MangaSourceConfig
import tsuki.model.*
import tsuki.model.search.MangaSearchQuery
import tsuki.model.search.MangaSearchQueryCapabilities
import tsuki.network.CommonHeaders
import tsuki.network.OkHttpWebClient
import tsuki.network.WebClient
import tsuki.util.*
import java.util.*

@Suppress("OVERRIDE_DEPRECATION")
@InternalParsersApi
public abstract class AbstractMangaParser @InternalParsersApi constructor(
	@property:InternalParsersApi public val context: MangaLoaderContext,
	public final override val source: MangaSource,
) : MangaParser {

	public final override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = filterCapabilities.toMangaSearchQueryCapabilities()

	public override val config: MangaSourceConfig by lazy { context.getConfig(source) }

	public open val sourceLocale: Locale
		get() = if (source.locale.isEmpty()) Locale.ROOT else Locale(source.locale)

	protected val sourceContentRating: ContentRating?
		get() = if (source.contentType == ContentType.HENTAI) {
			ContentRating.ADULT
		} else {
			null
		}

	protected val isNsfwSource: Boolean = source.contentType == ContentType.HENTAI

	protected open val userAgentKey: ConfigKey.UserAgent = ConfigKey.UserAgent(context.getDefaultUserAgent())

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add(CommonHeaders.USER_AGENT, config[userAgentKey])
		.build()

	/**
	 * Used as fallback if value of `order` passed to [getList] is null
	 */
	public open val defaultSortOrder: SortOrder
		get() {
			val supported = availableSortOrders
			return SortOrder.entries.first { it in supported }
		}

	final override val domain: String
		get() = config[configKeyDomain]

	protected open val webClient: WebClient = OkHttpWebClient(context.httpClient, source)

	/**
	 * Search list of manga by specified searchQuery
	 *
	 * @param query searchQuery
	 */
	public final override suspend fun getList(query: MangaSearchQuery): List<Manga> = getList(
		offset = query.offset,
		order = query.order ?: defaultSortOrder,
		filter = convertToMangaListFilter(query),
	)

	/**
	 * Fetch direct link to the page image.
	 */
	public override suspend fun getPageUrl(page: MangaPage): String = page.url.toAbsoluteUrl(domain)

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	public override suspend fun getFavicons(): Favicons {
		return FaviconParser(webClient, domain).parseFavicons()
	}

	@CallSuper
	public override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	public override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return RelatedMangaFinder(listOf(this)).invoke(seed)
	}

	/**
	 * Return [Manga] object by web link to it
	 * @see [Manga.publicUrl]
	 */
	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? = null

	override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
