package responsible

import com.fasterxml.jackson.databind.JsonNode
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.RequestOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.openapi.Operation
import io.vertx.kotlin.coroutines.coAwait
import org.openapi4j.core.model.v3.OAI3Context
import org.openapi4j.operation.validator.model.Request
import org.openapi4j.operation.validator.model.impl.Body
import org.openapi4j.operation.validator.model.impl.DefaultRequest
import org.openapi4j.operation.validator.model.impl.DefaultResponse
import org.openapi4j.operation.validator.validation.RequestValidator
import org.openapi4j.parser.model.v3.OpenApi3
import java.net.URL
import java.net.URLEncoder

private fun List<Map.Entry<String, String>>.toMap(): Map<String, MutableList<String>> {
    val map = mutableMapOf<String, MutableList<String>>()
    for ((k, v) in this) {
        map.getOrPut(k) { mutableListOf() }.add(v)
    }
    return map
}

private fun HttpRequest<*>.toDefault(json: Any?): DefaultRequest =
    DefaultRequest.Builder(uri(), Request.Method.getMethod(method().name()))
        .headers(headers().entries().toMap())
        .body(json?.let(Body::from))
        .build()

private fun HttpResponse<*>.toDefault(): DefaultResponse =
    DefaultResponse.Builder(statusCode())
        .headers(headers().entries().toMap())
        .body(bodyAsString()?.let(Body::from))
        .build()

private fun openAPI3(doc: JsonObject): OpenApi3 =
    Json.CODEC.run {
        fromValue(doc, OpenApi3::class.java).apply {
            context = OAI3Context(URL("http://localhost"), fromValue(doc, JsonNode::class.java))
        }
    }

private fun queryString(params: Map<String, String>): String {
    if (params.isEmpty()) return ""

    val joined = params.entries.joinToString(separator = "&") { (k, v) ->
        "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
    }

    return "?$joined"
}

fun Operation.toRequest(
    pathParams: Map<String, String> = emptyMap(),
    query: Map<String, String> = emptyMap(),
    headers: Map<CharSequence, String> = emptyMap(),
): RequestOptions {
    val ro = RequestOptions().setMethod(httpMethod)
    for ((k, v) in headers) {
        ro.putHeader(k, v)
    }

    var path = "$openAPIPath${queryString(query)}"
    if ("{" !in openAPIPath && "}" !in openAPIPath) {
        return ro.setURI(path)
    }

    for ((k, v) in pathParams) {
        path = path.replace("{$k}", v)
    }

    check("{" !in path && "}" !in path) { path }

    return ro.setURI(path)
}

class Responsible(
    openAPI: JsonObject,
    private val client: WebClient,
) {
    private val rv = RequestValidator(openAPI3(openAPI))

    suspend fun check(
        req: RequestOptions,
        json: Any? = null,
        text: String? = null,
        status: Int,
    ): HttpResponse<Buffer> {
        require(status in 100..599) { status }

        val builtReq = client.request(req.method, req)
        
        val res = when {
            json != null ->
                // TODO fail if method is not POST, PUT, PATCH
                builtReq.sendJson(json)

            text != null ->
                // TODO fail if method is not POST, PUT, PATCH
                builtReq.sendBuffer(Buffer.buffer(text))

            else ->
                builtReq.send()
        }.coAwait()

        check(res.statusCode() == status) {
            "expected status $status, got ${res.statusCode()}. ${res.bodyAsString()}"
        }

        rv.validate(res.toDefault(), builtReq.toDefault(json))

        return res
    }
}
