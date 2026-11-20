package tsuki.webview

/**
 * Request captured during [tsuki.MangaLoaderContext.interceptWebViewRequests].
 */
public data class InterceptedRequest(
	public val url: String,
	public val method: String,
	public val headers: Map<String, String>,
	public val timestamp: Long,
)

/**
 * Configuration for [tsuki.MangaLoaderContext.interceptWebViewRequests].
 */
public data class InterceptionConfig(
	public val timeoutMs: Long = 30_000L,
	public val maxRequests: Int = 100,
	public val urlPattern: Regex? = null,
	public val pageScript: String? = null,
	public val blockImages: Boolean = false,
	public val injectOnStart: Boolean = true,
	public val userAgent: String? = null,
) {
	init {
		require(timeoutMs > 0L) { "timeoutMs must be positive" }
		require(maxRequests > 0) { "maxRequests must be positive" }
	}
}
