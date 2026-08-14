package tsuki.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.HttpStatusException
import tsuki.exception.AuthRequiredException
import tsuki.exception.GraphQLException
import tsuki.exception.NotFoundException
import tsuki.model.MangaSource
import tsuki.util.await
import tsuki.util.parseJson
import java.net.HttpURLConnection

public class OkHttpWebClient(
	private val httpClient: OkHttpClient,
	private val mangaSource: MangaSource,
) : WebClient {

	companion object {
		private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
		private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
	}

	override suspend fun httpGet(url: HttpUrl, extraHeaders: Headers?): Response {
		val request = Request.Builder()
			.get()
			.url(url)
			.addTags()
			.addExtraHeaders(extraHeaders)
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpHead(url: HttpUrl): Response {
		val request = Request.Builder()
			.head()
			.url(url)
			.addTags()
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, form: Map<String, String>, extraHeaders: Headers?): Response {
		val body = buildFormBody(form)
		val request = Request.Builder()
			.post(body)
			.url(url)
			.addTags()
			.addExtraHeaders(extraHeaders)
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, payload: String, extraHeaders: Headers?): Response {
		val body = parseAndBuildFormBody(payload)
		val request = Request.Builder()
			.post(body)
			.url(url)
			.addTags()
			.addExtraHeaders(extraHeaders)
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, body: JSONObject, extraHeaders: Headers?): Response {
		val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE)
		val request = Request.Builder()
			.post(requestBody)
			.url(url)
			.addTags()
			.addExtraHeaders(extraHeaders)
		return httpClient.newCall(request.build()).await().ensureSuccess()
	}

	override suspend fun graphQLQuery(endpoint: String, query: String): JSONObject {
		val body = JSONObject().apply {
			put("operationName", null as Any?)
			put("variables", JSONObject())
			put("query", "{$query}")
		}

		val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE)
		val request = Request.Builder()
			.post(requestBody)
			.url(endpoint.toHttpUrl())
			.addTags()
		val json = httpClient.newCall(request.build()).await().parseJson()
		validateGraphQLResponse(json)
		return json
	}

	/**
	 * Builds a form body from a map of key-value pairs.
	 * Uses addEncoded() to preserve pre-encoded values from the source.
	 * 
	 * @param form Map of form fields
	 * @return Constructed FormBody
	 */
	private fun buildFormBody(form: Map<String, String>): RequestBody {
		val bodyBuilder = FormBody.Builder()
		form.forEach { (k, v) ->
			bodyBuilder.addEncoded(k, v)
		}
		return bodyBuilder.build()
	}

	/**
	 * Parses a URL-encoded form string and builds a FormBody.
	 * Handles edge cases like missing values or malformed entries gracefully.
	 * 
	 * For Android API 21+ compatibility, uses manual string splitting
	 * instead of URLDecoder to avoid potential compatibility issues.
	 * 
	 * @param payload URL-encoded form string (e.g., "key1=value1&key2=value2")
	 * @return Constructed FormBody
	 */
	private fun parseAndBuildFormBody(payload: String): RequestBody {
		val bodyBuilder = FormBody.Builder()
		
		payload.split('&').forEach { pair ->
			val separatorIndex = pair.indexOf('=')
			if (separatorIndex > 0) { // Key must exist and not be empty
				val k = pair.substring(0, separatorIndex)
				val v = pair.substring(separatorIndex + 1)
				bodyBuilder.addEncoded(k, v)
			} else if (separatorIndex == 0) {
				// Handle edge case: value without key (e.g., "=value")
				bodyBuilder.addEncoded("", pair.substring(1))
			}
			// Skip entries without '=' separator
		}
		
		return bodyBuilder.build()
	}

	/**
	 * Validates GraphQL response for errors.
	 * Throws GraphQLException if errors array is present and non-empty.
	 * 
	 * @param json Response JSON object
	 * @throws GraphQLException if errors are found
	 */
	private fun validateGraphQLResponse(json: JSONObject) {
		json.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let {
			throw GraphQLException(it)
		}
	}

	private fun Request.Builder.addTags(): Request.Builder {
		tag(MangaSource::class.java, mangaSource)
		return this
	}

	private fun Request.Builder.addExtraHeaders(headers: Headers?): Request.Builder {
		headers?.let { headers(it) }
		return this
	}

	private fun Response.ensureSuccess(): Response {
		val exception: Exception? = when (code) { // Catch some error codes, not all
			HttpURLConnection.HTTP_NOT_FOUND -> NotFoundException(message, request.url.toString())
			HttpURLConnection.HTTP_UNAUTHORIZED -> request.tag(MangaSource::class.java)?.let {
				AuthRequiredException(it)
			} ?: HttpStatusException(message, code, request.url.toString())

			in 400..599 -> HttpStatusException(message, code, request.url.toString())
			else -> null
		}
		if (exception != null) {
			runCatching {
				close()
			}.onFailure {
				exception.addSuppressed(it)
			}
			throw exception
		}
		return this
	}
}
