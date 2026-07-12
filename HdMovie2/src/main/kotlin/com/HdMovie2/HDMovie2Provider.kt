package com.hdmovie2

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.Jsoup
import java.util.Calendar

class HDMovie2Provider : MainAPI() {
    override var mainUrl: String = "https://newhdmovie2.asia"
    get() {
        return runBlocking {
            HDMovie2Plugin.getDomains()?.hdmovie2 ?: field
        }
    }
    
    override var name = "Hdmovie2"
    override var lang = "hi"
    override val hasMainPage = true
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "release/${Calendar.getInstance().get(Calendar.YEAR)}" to "Latest",
        "genre/bollywood" to "BollyWood",
        "genre/hollywood" to "Hollywood",
        "genre/hindi-dubbed" to "Hindi Dubbed",
        "genre/netflix" to "NETFLIX",
        "genre/hindi-webseries" to "Hindi Web Series",
        "genre/adventure" to "Adventure",
        "genre/comedy" to "Comedy",
        "genre/crime" to "Crime",
        "genre/drama" to "Drama",
        "genre/family" to "Family",
        "genre/horror" to "Horror",
        "genre/science-fiction" to "Science Fiction",
        "genre/thriller" to "Thriller"
    )

    private suspend fun fixUrl() {
        try {
            HDMovie2Plugin.getDomains()?.hdmovie2?.let {
                mainUrl = it
            }
        } catch (e: Exception) { }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        fixUrl()
        val url = if (page <= 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url).document
        val items = document.select("div.items > article, div.result-item, article.item, .animation-2").mapNotNull {
            val title = it.selectFirst("h3 > a, div.title > a, h4 > a, .title a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("h3 > a, div.title > a, h4 > a, .title a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("src") ?: it.selectFirst("img")?.attr("data-src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        fixUrl()
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.result-item, article.item, .animation-2").mapNotNull {
            val title = it.selectFirst("div.title > a, h3 > a, h4 > a, .title a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("div.title > a, h3 > a, h4 > a, .title a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("src") ?: it.selectFirst("img")?.attr("data-src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        fixUrl()
        val document = app.get(url).document
        val title = document.selectFirst("div.data > h1, h1.entry-title, .sheader h1")?.text() ?: return null
        val poster = document.selectFirst("div.poster > img, .poster img")?.attr("src") ?: document.selectFirst("div.poster > img, .poster img")?.attr("data-src")
        val plot = document.selectFirst("div.wp-content > p, #info p, .entry-content p")?.text()
        val isTv = url.contains("/tvshows/") || url.contains("/seasons/") || document.selectFirst("ul.episodios, .list_episodes") != null

        return if (isTv) {
            val episodes = document.select("ul.episodios > li, .list_episodes li").map {
                val href = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst("div.episodiotitle > a, .episodiotitle a")?.text() ?: ""
                val num = it.selectFirst("div.numerando, .numerando")?.text()?.split("-")
                val s = num?.firstOrNull()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                val e = num?.lastOrNull()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                newEpisode(href) {
                    this.name = name
                    this.season = s
                    this.episode = e
                }
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
        val document = app.get(data).document
        val id = document.selectFirst("ul#playeroptionsul > li, #playeroptionsul li")?.attr("data-post") ?: return false
        val type = if (data.contains("/movies/") || data.contains("/movie/")) "movie" else "tv"

        document.select("ul#playeroptionsul > li, #playeroptionsul li").forEach { li ->
            val nume = li.attr("data-nume")
            val response = app.post(
                url = "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type),
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text
            
            val embedUrl = AppUtils.tryParseJson<ResponseHash>(response)?.embed_url
            val source = embedUrl?.let { Jsoup.parse(it).select("iframe").attr("src") }
            if (!source.isNullOrEmpty() && !source.contains("youtube")) {
                loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
            }
        }
        return true
    }

    data class ResponseHash(@JsonProperty("embed_url") val embed_url: String? = null)
}
