package com.Bollyzone

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class BollyzonePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(BollyzoneProvider())
        registerExtractorAPI(Tvlogyflow("Bollyzone"))
    }
}
