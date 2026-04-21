package com.hdmovie2

import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.util.Calendar

class HDMovie2Provider : MainAPI() {
    override var mainUrl: String = "https://hdmovie2.equipment"
    override var name = "Hdmovie2"
    override var lang = "hi"
    override val hasMainPage = true
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    init {
        runBlocking {
            HDMovie2Plugin.getDomains()?.hdmovie2?.let {
                mainUrl = it
            }
        }
    }

    override val mainPage = mainPageOf(
        "release/${Calendar.getInstance().get(Calendar.YEAR)}" to "Latest",
        "genre/hindi-dubbed" to "Hindi Dubbed",
        "genre/bollywood" to "BollyWood",
        "genre/hindi-webseries" to "Hindi Web Series",
        "genre/Action" to "Action",
        "genre/adventure" to "Adventure",
        "genre/comedy" to "Comedy",
        "genre/crime" to "Crime",
        "genre/drama" to "Drama"
    )

    override suspend fun getMainPage(page: Int, request: HomePageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val items = document.select("div.items > article, div.result-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.result-item").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun org.jsoup.nodes.Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 > a, div.title > a")?.text() ?: return null
        val href = this.selectFirst("h3 > a, div.title > a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.data > h1")?.text() ?: return null
        val poster = document.selectFirst("div.poster > img")?.attr("src")
        val plot = document.selectFirst("div.wp-content > p")?.text()
        val type = if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.TvSeries) {
            val episodes = document.select("ul.episodios > li").map {
                val href = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst("div.episodiotitle > a")?.text() ?: ""
                val season = it.selectFirst("div.numerando")?.text()?.split("-")?.firstOrNull()?.trim()?.toIntOrNull()
                val episode = it.selectFirst("div.numerando")?.text()?.split("-")?.lastOrNull()?.trim()?.toIntOrNull()
                Episode(href, name, season, episode)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        val commonHeaders = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest"
        )

        suspend fun fetchSource(post: String, nume: String, type: String): String {
            return try {
                val response = app.post(
                    url = ajaxUrl,
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to post,
                        "nume" to nume,
                        "type" to type
                    ),
                    referer = data,
                    headers = commonHeaders
                ).parsed<ResponseHash>()
                response.embed_url.getIframe()
            } catch (e: Exception) { "" }
        }

        if (data.startsWith("{")) {
            val loadData = tryParseJson<LinkData>(data) ?: return false
            val source = fetchSource(
                loadData.post.orEmpty(),
                loadData.nume.orEmpty(),
                loadData.type.orEmpty()
            )
            if (source.isNotEmpty() && !source.contains("youtube")) {
                loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
            }
        } else {
            val document = app.get(data).document
            val id = document.selectFirst("ul#playeroptionsul > li")?.attr("data-post") ?: return false
            val type = if (data.contains("/movies/")) "movie" else "tv"

            document.select("ul#playeroptionsul > li").amap { li ->
                val nume = li.attr("data-nume")
                val source = fetchSource(id, nume, type)
                if (source.isNotEmpty() && !source.contains("youtube")) {
                    loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
                }
            }
        }
        return true
    }

    private fun String.getIframe(): String {
        return Jsoup.parse(this).select("iframe").attr("src")
    }

    data class LinkData(
        val type: String? = null,
        val post: String? = null,
        val nume: String? = null,
    )

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}
