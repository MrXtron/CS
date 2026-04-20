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
