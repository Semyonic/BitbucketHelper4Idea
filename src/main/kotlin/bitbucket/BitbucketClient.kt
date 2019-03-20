package bitbucket

import bitbucket.data.*
import bitbucket.data.merge.MergeStatus
import bitbucket.data.merge.Veto
import bitbucket.httpparams.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.intellij.openapi.diagnostic.Logger
import com.palominolabs.http.url.UrlBuilder
import http.HttpAuthRequestFactory
import http.HttpResponseHandler
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.ByteArrayEntity
import ui.Settings
import java.net.URL

class BitbucketClient(
        private val httpClient: HttpClient,
        private val httpRequestFactory: HttpAuthRequestFactory,
        val settings: Settings,
        objReader: ObjectReader,
        private val objWriter: ObjectWriter,
        private val listener: ClientListener
) {
    private val log = Logger.getInstance("BitbucketClient")
    private val mergeStatusResponseHandler = HttpResponseHandler(
            objReader, object : TypeReference<PagedResponse<MergeStatus>>() {}, listener)
    private val pagedResponseHandler = HttpResponseHandler(
            objReader, object : TypeReference<PagedResponse<PR>>() {}, listener)
    private val appVersionResponseHandler = HttpResponseHandler(
            objReader, object : TypeReference<AppVersion>() {}, listener)
    private val pullRequestResponseHandler = HttpResponseHandler(
            objReader, object : TypeReference<PR>() {}, listener)

    fun reviewedPRs(): List<PR> {
        return inbox(Role.REVIEWER)
    }

    fun checkAppVersion(): AppVersion {
        val request = httpRequestFactory.createGet("${settings.url}rest/api/1.0/application-properties")
        return sendRequest(request, appVersionResponseHandler)
    }

    fun ownPRs(): List<PR> {
        return inbox(Role.AUTHOR)
    }

    // /rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/pull-requests/{pullRequestId}/participants/{userSlug}
    fun approve(pr: PR) {
        try {
            val urlBuilder = urlBuilder().pathSegments(
                    "projects", settings.project, "repos", settings.slug, "pull-requests", pr.id.toString(), "participants", settings.login)
            println(urlBuilder.toUrlString())
            val request = httpRequestFactory.createPut(urlBuilder.toUrlString())
            val body = objWriter.writeValueAsBytes(Approve(SimpleUser(settings.login)))
            val entity = ByteArrayEntity(body)
            request.entity = entity
            HttpResponseHandler.handle(httpClient.execute(request))
        } catch (e: Exception) {
            listener.requestFailed(e)
            throw e
        }
    }

    // /rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/pull-requests/{pullRequestId}/decline
    fun decline(pr: PR) {
        try {
            val urlBuilder = urlBuilder().pathSegments(
                    "projects", settings.project, "repos", settings.slug, "pull-requests", pr.id.toString(), "decline", settings.login)
            println(urlBuilder.toUrlString())
            val request = httpRequestFactory.createPost(urlBuilder.toUrlString())
            val body = objWriter.writeValueAsBytes(Approve(SimpleUser(settings.login)))
            val entity = ByteArrayEntity(body)
            request.entity = entity
            HttpResponseHandler.handle(httpClient.execute(request))
        } catch (e: Exception) {
            listener.requestFailed(e)
            throw e
        }
    }


    fun merge(pr: PR): PR {
        return try {
            val urlBuilder = mergeUrl(pr)
            val request = httpRequestFactory.createPost(urlBuilder.toUrlString())
            sendRequest(request, pullRequestResponseHandler)
        } catch (e: Exception) {
            listener.requestFailed(e)
            pr
        }
    }

    /**
     * Calls /rest/api/1.0/inbox/pull-requests
     * @see <a href="https://docs.atlassian.com/bitbucket-server/rest/5.13.0/bitbucket-rest.html#idm46209336621072">inbox</a>
     */
    private fun inbox(role: Role, limit: Limit = Limit.Default, start: Start = Start.Zero): List<PR> {
        return try {
            val urlBuilder = UrlBuilder.fromUrl(URL(settings.url))
                    .pathSegments("rest", "inbox", "latest", "pull-requests")

            val request = httpRequestFactory.createGet(urlBuilder.toUrlString())
            filterByProject(replayPageRequest(request) { inbox(role, limit, Start(it)) })
        } catch (e: Exception) {
            listener.requestFailed(e)
            emptyList()
        }
    }

    // /rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/pull-requests
    fun retrieveMergeStatus(pr: PR): List<MergeStatus> {
        try {
            val urlBuilder = urlBuilder().pathSegments(
                    "rest", "api", "1.0", "projects", pr.projectKey, "repos",
                    pr.repoSlug, "pull-requests")
            val request = httpRequestFactory.createGet(urlBuilder.toUrlString())
            val pagedResponse = sendRequest(request, mergeStatusResponseHandler)
            val mergeStat = ArrayList(pagedResponse.values)
            return mergeStat
        } catch (e: Exception) {
            log.info(e)
        }
        return emptyList()
    }

    // /rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/pull-requests/{pullRequestId}/merge
    private fun mergeUrl(pr: PR): UrlBuilder {
        return urlBuilder().pathSegments("rest", "api", "1.0", "projects", pr.projectKey, "repos", pr.repoSlug,
                "pull-requests", pr.id.toString(), "merge")
    }

    private fun filterByProject(prs: List<PR>): List<PR> {
        return prs.filter {
            it.projectKey.equals(settings.project, true)
                    && settings.slug.contains(it.repoSlug, true)
        }
    }

    private fun findPRs(state: PRState, order: PROrder, start: Start = Start.Zero): List<PR> {
        val urlBuilder = urlBuilder()
                .pathSegments("projects", settings.project, "repos", settings.slug, "pull-requests")
        applyParameters(urlBuilder, start, order, state)

        val request = httpRequestFactory.createGet(urlBuilder.toUrlString())
        return replayPageRequest(request) { findPRs(state, order, Start(it)) }
    }

    private fun urlBuilder() = UrlBuilder.fromUrl(URL(settings.url)).pathSegments("rest", "latest")

    private fun applyParameters(urlBuilder: UrlBuilder, vararg params: HttpRequestParameter) {
        for (param in params)
            param.apply(urlBuilder)
    }

    private fun <T> sendRequest(request: HttpUriRequest, responseHandler: HttpResponseHandler<T>) =
            responseHandler.handle(httpClient.execute(request))

    private fun replayPageRequest(request: HttpUriRequest, replay: (Int) -> List<PR>): List<PR> {
        try {
            val pagedResponse = sendRequest(request, pagedResponseHandler)
            val prs = ArrayList(pagedResponse.values)
            if (!pagedResponse.isLastPage)
                prs.addAll(replay.invoke(pagedResponse.nextPageStart))
            return prs
        } catch (e: HttpResponseHandler.UnauthorizedException) {
            log.info(e)
        }
        return emptyList()
    }
}