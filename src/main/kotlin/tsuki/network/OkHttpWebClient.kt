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
		val body = FormBody.Builder()
		form.forEach { (k, v) ->
			// Let OkHttp handle encoding of keys/values
			body.add(k, v)
		}
		val request = buildRequest(url, RequestMethod.POST, body.build(), extraHeaders)
		return httpClient.newCall(request).await().ensureSuccess()
	}

	override suspend fun httpPost(url: HttpUrl, payload: String, extraHeaders: Headers?): Response {
		// Send raw x-www-form-urlencoded payload instead of reparsing it — preserves encoding
		val requestBody = payload.toRequestBody(FORM_MEDIA_TYPE)
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
		
		json.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let {
			throw GraphQLException(it)
		}
		return json
	}

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

	private enum class RequestMethod {
		GET, HEAD, POST
	}
}
