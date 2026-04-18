package com.hdmovie2

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.utils.AppUtils.parsedSafe

@CloudstreamPlugin
class HDMovie2Plugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HDMovie2Provider())
        
        registerExtractorAPI(FMHD())
        registerExtractorAPI(Akamaicdn())
        registerExtractorAPI(Luluvdo())
        registerExtractorAPI(FMX())
        registerExtractorAPI(Lulust())
        registerExtractorAPI(Playonion())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(Movierulzups())
        registerExtractorAPI(Movierulz())
        registerExtractorAPI(HDm2())
        registerExtractorAPI(cherryMovierulzups())
    }

    companion object {
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/MrXtron/CSF/refs/heads/main/domains.json"
        
        @Volatile
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains != null && !forceRefresh) return cachedDomains

            return try {
                app.get(DOMAINS_URL).parsedSafe<Domains>().also {
                    cachedDomains = it
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        data class Domains(
            @JsonProperty("hdmovie2")
            val hdmovie2: String,
        )
    }
}
