// use an integer for version numbers
version = 5


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

     description = "Hdmovie2"
     authors = listOf("-")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://raw.githubusercontent.com/MrXtron/CSF/refs/heads/main/Icons/HDMovie2.png"

    isCrossPlatform = true
}
