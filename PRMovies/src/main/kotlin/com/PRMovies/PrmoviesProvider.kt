package com.PRMovies

import kotlinx.coroutines.runBlocking
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.jsoup.nodes.Element

class PrmoviesProvider : MainAPI() {
    override var mainUrl = "https://prmovies.giving"
    override var name = "Prmovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/most-favorites/page/" to "Most Viewed",
        "$mainUrl/genre/bollywood/page/" to "Bollywood",
        "$mainUrl/genre/dual-audio/page/" to "Dual Audio",
        "$mainUrl/genre/south-special/page/" to "South Movies",
        "$mainUrl/genre/unofficial-dubbed/page/" to "Unofficial Dubbed",
        "$mainUrl/director/netflix/page/" to "Netflix",
        "$mainUrl/director/amazon-prime/page/" to "Amazon Prime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data.removeSuffix("page/") else "${request.data}$page"
        val document = app.get(url).document
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-original"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document.select("div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.mvic-desc h3")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.thumb.mvic-thumb img")?.attr("src"))
        val tvType = if ((document.selectFirst("div.les-content")?.select("a")?.size ?: 0) > 1) TvType.TvSeries else TvType.Movie
        
        val episodes = document.select("div.les-content a").map {
            newEpisode(it.attr("href")) {
                this.name = it.text().replace("Server Ep", "Episode").trim()
            }
        }

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith(mainUrl)) {
            app.get(data).document.select("div.movieplay iframe").map { fixUrl(it.attr("src")) }.forEach { source ->
                safeApiCall { loadExtractor(source, "$mainUrl/", subtitleCallback, callback) }
            }
        } else {
            loadExtractor(data, "$mainUrl/", subtitleCallback, callback)
        }
        return true
    }
}
