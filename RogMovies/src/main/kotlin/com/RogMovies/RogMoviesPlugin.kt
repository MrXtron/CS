package com.RogMovies

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class RogMoviesPlugin: Plugin() { // All providers should be added in this manner. Please don't edit the providers list directly.
    override fun load(context: Context) {
        registerMainAPI(RogmoviesProvider())
        registerExtractorAPI(VCloud())
    }
}

