package com.PRMovies

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import org.jsoup.nodes.Element
import kotlinx.coroutines.delay

class PrmoviesProvider : MainAPI() {
    override var mainUrl = "https://prmovies.giving"
    override var name = "Prmovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val fallbackUrls = listOf(
        "https://prmovies.live",
        "https://prmovies.one",
        "https://prmovies.online"
    )

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "en-US,en;q=0.9,en-IN;q=0.8",
        "Cache-Control" to "max-age=0",
        "Referer" to "$mainUrl/",
        "Sec-Ch-Ua" to "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"125\", \"Chromium\";v=\"125\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "DNT" to "1"
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

    private suspend fun getWorkingUrl(): String {
        return try {
            app.get(mainUrl, headers = commonHeaders, timeout = 15L)
            mainUrl
        } catch (e: Exception) {
            fallbackUrls.firstOrNull { url ->
                try {
                    app.get(url, headers = commonHeaders, timeout = 15L)
                    true
                } catch (e: Exception) {
                    false
                }
            } ?: mainUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val workingUrl = getWorkingUrl()
            val url = if (page == 1) request.data.removeSuffix("page/").removeSuffix("/") else "${request.data}$page/"
            val fixedUrl = url.replace(mainUrl, workingUrl)
            
            val document = app.get(
                fixedUrl, 
                headers = commonHeaders,
                timeout = 30L
            ).document
            
            val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }
            newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        return try {
            val title = this.selectFirst(".mli-info h2, h2, .title")?.text()?.trim() ?: return null
            val href = this.selectFirst("a")?.attr("href") ?: return null
            val fixedHref = fixUrl(href)
            
            val img = this.selectFirst("img")
            val poster = img?.attr("data-original") ?: img?.attr("data-src") ?: img?.attr("src")
            val quality = this.selectFirst(".mli-quality")?.text()?.trim()

            newMovieSearchResponse(title, fixedHref, TvType.Movie) { 
                this.posterUrl = fixUrlNull(poster)
                if (quality != null) addQuality(quality)
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val workingUrl = getWorkingUrl()
            val document = app.get(
                "$workingUrl/?s=$query", 
                headers = commonHeaders,
                timeout = 30L
            ).document
            document.select("div.ml-item").mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val document = app.get(
                url, 
                headers = commonHeaders,
                timeout = 30L
            ).document
            
            val title = document.selectFirst("h1.entry-title, .mvic-desc h3, .data h1")?.text()?.trim() ?: return null
            val poster = document.selectFirst(".poster img, .thumb img, .mvic-thumb img")?.attr("src")

            val episodes = document.select(".les-content a, .episodios a, #video-player-content a").mapNotNull {
                val href = it.attr("href") ?: return@mapNotNull null
                newEpisode(href) {
                    this.name = it.text().trim()
                }
            }

            val tvType = if (episodes.size > 1) TvType.TvSeries else TvType.Movie

            if (tvType == TvType.TvSeries) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = fixUrlNull(poster)
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(
                data, 
                headers = commonHeaders,
                timeout = 30L
            ).document
            
            val iframes = document.select("iframe, .movieplay iframe, #video-player-content iframe, .player iframe, [src*='iframe']")
            
            iframes.forEach { iframe ->
                try {
                    val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                    if (src.isNotEmpty()) {
                        val source = fixUrl(src)
                        if (!source.contains("youtube") && 
                            !source.contains("google") && 
                            !source.contains("youtu.be") &&
                            source.isNotBlank()) {
                            
                            safeApiCall { 
                                loadExtractor(source, data, subtitleCallback, callback) 
                            }
                            delay(500)
                        }
                    }
                } catch (e: Exception) {
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
