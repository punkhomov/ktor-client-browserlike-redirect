package punkhomov.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.logging.*

private val LOGGER = KtorSimpleLogger("punkhomov.ktor.client.plugins.BrowserlikeHttpRedirect")

class BrowserlikeHttpRedirect(
    private val forceChangeMethod: Boolean,
    private val allowHttpsDowngrade: Boolean,
) {
    private fun isMustChangeMethodFor(call: HttpClientCall): Boolean {
        if (forceChangeMethod) {
            return call.response.status.isRedirectMayChangeMethod()
        }

        return call.response.status.isRedirectMustChangeMethod()
    }

    class Config {
        var forceChangeMethod = true
        var allowHttpsDowngrade = false
    }

    companion object Plugin : HttpClientPlugin<Config, BrowserlikeHttpRedirect> {
        override val key = AttributeKey<BrowserlikeHttpRedirect>("BrowserlikeHttpRedirect")

        override fun prepare(block: Config.() -> Unit): BrowserlikeHttpRedirect {
            val config = Config().apply(block)
            return BrowserlikeHttpRedirect(
                forceChangeMethod = config.forceChangeMethod,
                allowHttpsDowngrade = config.allowHttpsDowngrade,
            )
        }

        override fun install(plugin: BrowserlikeHttpRedirect, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { context ->
                val origin = execute(context)
                handleCall(context, origin, plugin, scope)
            }
        }

        @OptIn(InternalAPI::class)
        private suspend fun Sender.handleCall(
            context: HttpRequestBuilder,
            origin: HttpClientCall,
            plugin: BrowserlikeHttpRedirect,
            client: HttpClient
        ): HttpClientCall {
            if (!origin.response.status.isRedirect()) return origin

            var call = origin
            var requestBuilder = context
            val originProtocol = origin.request.url.protocol
            val originAuthority = origin.request.url.authority

            while (true) {
                client.monitor.raise(HttpRedirect.HttpResponseRedirect, call.response)

                val location = call.response.headers[HttpHeaders.Location]
                LOGGER.trace("Received redirect response to $location for request ${context.url}")

                requestBuilder = HttpRequestBuilder().apply {
                    takeFromWithExecutionContext(requestBuilder)
                    url.parameters.clear()

                    location?.let { url.takeFrom(it) }

                    if (plugin.isMustChangeMethodFor(call)) {
                        changeMethodToGet()
                    }

                    /**
                     * Disallow redirect with a security downgrade.
                     */
                    if (!plugin.allowHttpsDowngrade && originProtocol.isSecure() && !url.protocol.isSecure()) {
                        LOGGER.trace("Can not redirect ${context.url} because of security downgrade")
                        return call
                    }

                    if (originAuthority != url.authority) {
                        headers.remove(HttpHeaders.Authorization)
                        LOGGER.trace("Removing Authorization header from redirect for ${context.url}")
                    }
                }

                call = execute(requestBuilder)
                if (!call.response.status.isRedirect()) return call
            }
        }

        @OptIn(InternalAPI::class)
        private fun HttpRequestBuilder.changeMethodToGet() {
            method = HttpMethod.Get
            body = EmptyContent
            bodyType = null
        }
    }
}

private fun HttpStatusCode.isRedirectMayChangeMethod(): Boolean {
    return value == HttpStatusCode.MovedPermanently.value || value == HttpStatusCode.Found.value
}

private fun HttpStatusCode.isRedirectMustChangeMethod(): Boolean {
    return value == HttpStatusCode.SeeOther.value
}

private fun HttpStatusCode.isRedirect(): Boolean = when (value) {
    HttpStatusCode.MovedPermanently.value, // non-GET -> GET (body lost) *
    HttpStatusCode.PermanentRedirect.value, // method and body unchanged

    HttpStatusCode.Found.value, // non-GET -> GET (body lost) *
    HttpStatusCode.SeeOther.value, // non-GET -> GET (body lost)
    HttpStatusCode.TemporaryRedirect.value, // method and body unchanged
    -> true

    else -> false
}