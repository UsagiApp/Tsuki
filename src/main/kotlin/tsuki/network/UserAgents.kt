package tsuki.network

public object UserAgents {

	public const val CHROME_MOBILE: String =
		"Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.8103.79 Mobile Safari/537.36"

	public const val FIREFOX_MOBILE: String =
		"Mozilla/5.0 (Android 17; Mobile; LG-M255; rv:160.0) Gecko/160.0 Firefox/160.0"

	/* Fallback: Set Linux as default for "DESKTOP" User-Agent */

	public const val CHROME_DESKTOP: String =
		"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.8103.79 Safari/537.36"

	public const val FIREFOX_DESKTOP: String = "Mozilla/5.0 (X11; Linux x86_64; rv:160.0) Gecko/20100101 Firefox/160.0"

	public const val CHROME_WINDOWS: String =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.8103.79 Safari/537.36"

	public const val FIREFOX_WINDOWS: String =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:160.0) Gecko/20100101 Firefox/160.0"

	public const val KOTATSU: String = "Kotatsu/9.0 (Android 16;;; en)"

	public const val LEGACY_KOTATSU: String = "Kotatsu/6.8 (Android 13;;; en)"
}
