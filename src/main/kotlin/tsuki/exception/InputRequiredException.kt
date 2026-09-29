package tsuki.exception

import okio.IOException
import tsuki.InternalParsersApi
import tsuki.model.MangaSource

/**
 * Input is required for access to the requested content
 *
 * When the app catches this exception it must show a mandatory input dialog
 * displaying [message] and, after the user confirms, store the entered text
 * so that the parser can retrieve it on the next attempt.
 *
 * @param source  The manga source that raised this exception.
 * @param message A human-readable prompt shown to the user inside the dialog.
 * @param key     An arbitrary identifier that the parser uses to look up the
 *                returned value (e.g. "password", "token").
 * @param cause   Optional underlying cause.
 */
public class InputRequiredException @InternalParsersApi @JvmOverloads constructor(
	public val source: MangaSource,
	override val message: String,
	public val key: String = "",
	cause: Throwable? = null,
) : IOException(message, cause)
