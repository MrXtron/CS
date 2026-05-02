package com.PRMovies

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.jsoup.nodes.Element

class PrmoviesProvider : MainAPI() {
    override var mainUrl = "https://prmovies.giving"
    override var name = "Prmovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "en-US,en;q=0.5"
    )

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
        val url = if (page == 1) request.data.removeSuffix("page/").removeSuffix("/") else "${request.data}$page/"
        val document = app.get(url, headers = commonHeaders).document
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".mli-info h2, h2, .title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val fixedHref = fixUrl(href)
        
        val img = this.selectFirst("img")
        val poster = img?.attr("data-original") ?: img?.attr("data-src") ?: img?.attr("src")
        val quality = this.selectFirst(".mli-quality")?.text()?.trim()

        return newMovieSearchResponse(title, fixedHref, TvType.Movie) { 
            this.posterUrl = fixUrlNull(poster)
            addQuality(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", headers = commonHeaders).document
        return document.select("div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = commonHeaders).document
        val title = document.selectFirst("h1.entry-title, .mvic-desc h3, .data h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".poster img, .thumb img, .mvic-thumb img")?.attr("src")

        val episodes = document.select(".les-content a, .episodios a, #video-player-content a").map {
            newEpisode(it.attr("href")) {
                this.name = it.text().trim()
            }
        }

        val tvType = if (episodes.size > 1) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrlNull(poster)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrlNull(poster)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document
        document.select("iframe, .movieplay iframe, #video-player-content iframe").forEach { 
            val src = it.attr("src").ifEmpty { it.attr("data-src") }
            val source = fixUrl(src)
            if (source.isNotEmpty() && !source.contains("youtube") && !source.contains("google")) {
                safeApiCall { loadExtractor(source, data, subtitleCallback, callback) }
            }
        }
        return true
    }
}
