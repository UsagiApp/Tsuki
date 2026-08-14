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
		val request = buildRequest(url, RequestMethod.GET, null, extraHeaders)
		return httpClient.newCall(request).await().ensureSuccess()
	}

	override suspend fun httpHead(url: HttpUrl): Response {
		val request = buildRequest(url, RequestMethod.HEAD, null, null)
		return httpClient.newCall(request).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, form: Map<String, String>, extraHeaders: Headers?): Response {
		val requestBody = buildFormBody(form)
		val request = buildRequest(url, RequestMethod.POST, requestBody, extraHeaders)
		return httpClient.newCall(request).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, payload: String, extraHeaders: Headers?): Response {
		val requestBody = parseAndBuildFormBody(payload)
		val request = buildRequest(url, RequestMethod.POST, requestBody, extraHeaders)
		return httpClient.newCall(request).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, body: JSONObject, extraHeaders: Headers?): Response {
		val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE)
		val request = buildRequest(url, RequestMethod.POST, requestBody, extraHeaders)
		return httpClient.newCall(request).await().ensureSuccess()
	}

	override suspend fun graphQLQuery(endpoint: String, query: String): JSONObject {
		val body = JSONObject().apply {
			put("operationName", null as Any?)
			put("variables", JSONObject())
			put("query", "{$query}")
		}

		val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE)
		val request = buildRequest(endpoint.toHttpUrl(), RequestMethod.POST, requestBody, null)
		val json = httpClient.newCall(request).await().parseJson()
		
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
		form.forEach { (key, value) ->
			bodyBuilder.addEncoded(key, value)
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
				val key = pair.substring(0, separatorIndex)
				val value = pair.substring(separatorIndex + 1)
				bodyBuilder.addEncoded(key, value)
			} else if (separatorIndex == 0) {
				// Handle edge case: value without key (e.g., "=value")
				// Treat as empty key with value
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

	/**
	 * Builds a complete HTTP request with method, URL, body, and headers.
	 * Centralizes request construction logic to ensure consistency and flexibility.
	 * 
	 * Supports GET, HEAD, and POST methods. Easy to extend for other methods
	 * by adding to RequestMethod enum.
	 * 
	 * @param url Target URL
	 * @param method HTTP method
	 * @param body Request body (null for GET/HEAD)
	 * @param extraHeaders Optional additional headers
	 * @return Constructed Request
	 */
	private fun buildRequest(
		url: HttpUrl,
		method: RequestMethod,
		body: RequestBody?,
		extraHeaders: Headers?,
	): Request {
		val builder = Request.Builder()
			.url(url)
			.addTags()

		when (method) {
			RequestMethod.GET -> builder.get()
			RequestMethod.HEAD -> builder.head()
			RequestMethod.POST -> if (body != null) builder.post(body)
		}

		builder.addExtraHeaders(extraHeaders)
		return builder.build()
	}

	/**
	 * Adds MangaSource tag to request for tracking and filtering.
	 * 
	 * @return This builder for chaining
	 */
	private fun Request.Builder.addTags(): Request.Builder {
		tag(MangaSource::class.java, mangaSource)
		return this
	}

	/**
	 * Adds extra headers to request if provided.
	 * Uses null-safe let scope to avoid null checks.
	 * 
	 * @param headers Headers to add (nullable)
	 * @return This builder for chaining
	 */
	private fun Request.Builder.addExtraHeaders(headers: Headers?): Request.Builder {
		headers?.let { headers(it) }
		return this
	}

	/**
	 * Validates HTTP response status code and throws appropriate exceptions.
	 * Handles 404 (Not Found), 401 (Unauthorized), and other 4xx/5xx errors.
	 * Properly closes response and chains exceptions on failure.
	 * 
	 * API 21+ compatible error handling without Java 8+ features.
	 * 
	 * @return Response if successful
	 * @throws NotFoundException for 404 responses
	 * @throws AuthRequiredException for 401 responses
	 * @throws HttpStatusException for other 4xx/5xx responses
	 */
	private fun Response.ensureSuccess(): Response {
		val exception: Exception? = when (code) {
			HttpURLConnection.HTTP_NOT_FOUND -> 
				NotFoundException(message, request.url.toString())
			
			HttpURLConnection.HTTP_UNAUTHORIZED -> 
				request.tag(MangaSource::class.java)?.let {
					AuthRequiredException(it)
				} ?: HttpStatusException(message, code, request.url.toString())

			in 400..599 -> 
				HttpStatusException(message, code, request.url.toString())
			
			else -> null
		}
		
		if (exception != null) {
			runCatching {
				close()
			}.onFailure { closeException ->
				exception.addSuppressed(closeException)
			}
			throw exception
		}
		
		return this
	}

	/**
	 * HTTP request methods supported by this client.
	 * Easily extendable for PUT, PATCH, DELETE if needed.
	 */
	private enum class RequestMethod {
		GET, HEAD, POST
	}
}
