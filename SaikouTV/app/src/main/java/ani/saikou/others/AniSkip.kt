package ani.saikou.others

import ani.saikou.client
import kotlinx.serialization.Serializable

object AniSkip {

    /**
     * Skip times are optional, so any failure just means no skip button: nothing is shown to the user.
     *
     * [useProxyForTimeStamps] is ignored: the corsproxy.io proxy it used now rejects keyless requests and
     * answers with its own error JSON, which broke every episode. AniSkip works fine directly.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun getResult(malId: Int, episodeNumber: Int, episodeLength: Long, useProxyForTimeStamps: Boolean): List<Stamp>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"
        return try {
            val res = client.get(url).parsedSafe<AniSkipResponse>()
            if (res?.found == true) res.results else null
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    data class AniSkipResponse(
        val found: Boolean = false,
        val results: List<Stamp>? = null,
        val message: String? = null,
        val statusCode: Int = 0
    )

    @Serializable
    data class Stamp(
        val interval: AniSkipInterval,
        val skipType: String,
        val skipId: String,
        val episodeLength: Double
    )


    fun String.getType(): String {
        return when (this) {
            "op"    -> "Opening"
            "ed"    -> "Ending"
            "recap" -> "Recap"
            "mixed-ed" -> "Mixed Ending"
            "mixed-op" -> "Mixed Opening"
            else -> this
        }
    }

    @Serializable
    data class AniSkipInterval(
        val startTime: Double,
        val endTime: Double
    )
}