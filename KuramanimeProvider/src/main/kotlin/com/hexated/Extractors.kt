package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class Nyomo : StreamSB() {
    override var name = "Nyomo"
    override var mainUrl = "https://nyomo.my.id"
    override val rateLimit = 2 // Add rate limiting
}

class Streamhide : Filesim() {
    override var name = "Streamhide"
    override var mainUrl = "https://streamhide.to"
    override val rateLimit = 2
}

open class Lbx : ExtractorApi() {
    override val name = "Linkbox"
    override val mainUrl = "https://lbx.to"
    private val realUrl = "https://www.linkbox.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val token = Regex("""(?:/f/|/file/|\?id=)(\w+)""").find(url)?.groupValues?.getOrNull(1)
                ?: throw ErrorLoadingException("Invalid URL format")

            val id = app.get(
                "$realUrl/api/file/share_out_list/?sortField=utime&sortAsc=0&pageNo=1&pageSize=50&shareToken=$token",
                referer = url
            ).parsedSafe<Responses>()?.data?.itemId
                ?: throw ErrorLoadingException("Failed to get item ID")

            val response = app.get(
                "$realUrl/api/file/detail?itemId=$id",
                referer = url
            ).parsedSafe<Responses>()
                ?: throw ErrorLoadingException("Invalid API response")

            response.data?.itemInfo?.resolutionList?.forEach { link ->
                link.url?.let { videoUrl ->
                    callback.invoke(
                        ExtractorLink(
                            name,
                            name,
                            videoUrl,
                            "$realUrl/",
                            link.resolution?.let { getQualityFromName(it) } ?: Qualities.Unknown.value,
                            isM3u8 = videoUrl.contains(".m3u8")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to extract links: ${e.message}")
        }
    }

    data class Resolutions(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("resolution") val resolution: String? = null,
    )

    data class ItemInfo(
        @JsonProperty("resolutionList") val resolutionList: ArrayList<Resolutions> = arrayListOf(),
    )

    data class Data(
        @JsonProperty("itemInfo") val itemInfo: ItemInfo? = null,
        @JsonProperty("itemId") val itemId: String? = null,
    )

    data class Responses(
        @JsonProperty("data") val data: Data? = null,
    )
}

open class Kuramadrive : ExtractorApi() {
    override val name = "DriveKurama"
    override val mainUrl = "https://kuramadrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val req = app.get(url, referer = referer)
            val doc = req.document

            val token = doc.selectFirst("meta[name=csrf-token]")?.attr("content")
                ?: throw ErrorLoadingException("CSRF token not found")
            
            val routeCheckAvl = doc.selectFirst("input#routeCheckAvl")?.attr("value")
                ?: throw ErrorLoadingException("Route not found")

            val json = app.get(
                routeCheckAvl,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "X-CSRF-TOKEN" to token
                ),
                referer = url,
                cookies = req.cookies
            ).parsedSafe<Source>()
                ?: throw ErrorLoadingException("Invalid API response")

            val videoUrl = json.url
                ?: throw ErrorLoadingException("No video URL found")

            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    videoUrl,
                    "$mainUrl/",
                    getQualityFromUrl(videoUrl),
                    isM3u8 = videoUrl.contains(".m3u8")
                )
            )
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to extract links: ${e.message}")
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("360p") -> Qualities.P360.value
            url.contains("480p") -> Qualities.P480.value
            url.contains("720p") -> Qualities.P720.value
            url.contains("1080p") -> Qualities.P1080.value
            else -> Qualities.Unknown.value
        }
    }

    private data class Source(
        @JsonProperty("url") val url: String? = null,
    )
}