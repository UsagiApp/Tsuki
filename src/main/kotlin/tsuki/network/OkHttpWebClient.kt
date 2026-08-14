package tsuki.network

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
		// Send raw x-www-form-urlencoded payload instead of reparsing it — preserves encoding
		val requestBody = payload.toRequestBody(FORM_MEDIA_TYPE)
		val request = Request.Builder()
			.post(requestBody)
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
	 * Let OkHttp handle encoding of keys/values automatically.
	 * 
	 * @param form Map of form fields
	 * @return Constructed FormBody
	 */
	private fun buildFormBody(form: Map<String, String>): RequestBody {
		val bodyBuilder = okhttp3.FormBody.Builder()
		form.forEach { (k, v) ->
			// Let OkHttp handle encoding of keys/values
			bodyBuilder.add(k, v)
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
		val exception: Exception? = when (code) {
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
