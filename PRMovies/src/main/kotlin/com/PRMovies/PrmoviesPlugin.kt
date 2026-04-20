package com.PRMovies

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty

@CloudstreamPlugin
class PrmoviesPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PrmoviesProvider())
    }

    companion object {
        suspend fun getDomains(): PrmoviesDomains? {
            return try {
                app.get("https://raw.githubusercontent.com/MrXtron/CSF/refs/heads/main/domains.json").parsed<PrmoviesDomains>()
            } catch (e: Exception) {
                null
            }
        }
    }

    data class PrmoviesDomains(
        @JsonProperty("PRMovies") val PRMovies: String? = null
    )
}
