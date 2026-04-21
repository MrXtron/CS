package com.hdmovie2

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
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

    override val mainPage = mainPageOf(
        "release/2024" to "Latest",
        "genre/hindi-dubbed" to "Hindi Dubbed",
        "genre/bollywood" to "BollyWood",
        "genre/hindi-webseries" to "Hindi Web Series"
    )

    private suspend fun fixUrl() {
        try {
            HDMovie2Plugin.getDomains()?.hdmovie2?.let {
                mainUrl = it
            }
        } catch (e: Exception) { }
    }

    // FIX 1: MainPageRequest use kiya aur nullability handle ki
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        fixUrl()
        val url = if (page <= 1) "$mainUrl/${request.data}/" else "$mainUrl/${request.data}/page/$page/"
        val document = app.get(url).document
        val items = document.select("div.items > article, div.result-item").mapNotNull {
            val title = it.selectFirst("h3 > a, div.title > a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("h3 > a, div.title > a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        fixUrl()
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("div.title > a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("div.title > a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        fixUrl()
        val document = app.get(url).document
        val title = document.selectFirst("div.data > h1")?.text() ?: return null
        val poster = document.selectFirst("div.poster > img")?.attr("src")
        val plot = document.selectFirst("div.wp-content > p")?.text()
        val isTv = url.contains("/tvshows/") || document.selectFirst("ul.episodios") != null

        return if (isTv) {
            val episodes = document.select("ul.episodios > li").map {
                val href = it.selectFirst("a")?.attr("href") ?: ""
                val name = it.selectFirst("div.episodiotitle > a")?.text() ?: ""
                val num = it.selectFirst("div.numerando")?.text()?.split("-")
                val s = num?.firstOrNull()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                val e = num?.lastOrNull()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                // FIX 2: newEpisode use kiya constructor ki jagah
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
        val id = document.selectFirst("ul#playeroptionsul > li")?.attr("data-post") ?: return false
        val type = if (data.contains("/movies/")) "movie" else "tv"

        document.select("ul#playeroptionsul > li").forEach { li ->
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
