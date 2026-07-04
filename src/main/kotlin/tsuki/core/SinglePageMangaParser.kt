package tsuki.core

import tsuki.InternalParsersApi
import tsuki.MangaLoaderContext
import tsuki.model.Manga
import tsuki.model.MangaListFilter
import tsuki.model.MangaSource
import tsuki.model.SortOrder

@InternalParsersApi
public abstract class SinglePageMangaParser(
	context: MangaLoaderContext,
	source: MangaSource,
) : AbstractMangaParser(context, source) {

	final override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		return getList(order, filter)
	}

	public abstract suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga>
}
