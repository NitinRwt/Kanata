package app.kanata.anilist.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Query{
    @Serializable
    data class Viewer(
        @SerialName("data")
        val data : Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Viewer")
            val user: app.kanata.anilist.api.User?
        )
    }
    @Serializable
    data class Media(
        @SerialName("data")
        val data :  Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Media")
            val media: app.kanata.anilist.api.Media?
        )
    }

    @Serializable
    data class Page(
        @SerialName("data")
        val data : Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Page")
            val page : app.kanata.anilist.api.Page?
        )
    }
//    data class AiringSchedule(
//        val data : Data?
//    ){
//        data class Data(
//            val AiringSchedule: app.kanata.anilist.api.AiringSchedule?
//        )
//    }

    @Serializable
    data class Character(
        @SerialName("data")
        val data :  Data?
    ){

        @Serializable
        data class Data(
            @SerialName("Character")
            val character: app.kanata.anilist.api.Character?
        )
    }

    @Serializable
    data class Studio(
        @SerialName("data")
        val data: Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Studio")
            val studio: app.kanata.anilist.api.Studio?
        )
    }

//    data class MediaList(
//        val data: Data?
//    ){
//        data class Data(
//            val MediaList: app.kanata.anilist.api.MediaList?
//        )
//    }

    @Serializable
    data class MediaListCollection(
        @SerialName("data")
        val data : Data?
    ){
        @Serializable
        data class Data(
            @SerialName("MediaListCollection")
            val mediaListCollection: app.kanata.anilist.api.MediaListCollection?
        )
    }

    @Serializable
    data class GenreCollection(
        @SerialName("data")
        val data: Data
    ){
        @Serializable
        data class Data(
            @SerialName("GenreCollection")
            val genreCollection: List<String>?
        )
    }

    @Serializable
    data class MediaTagCollection(
        @SerialName("data")
        val data: Data
    ){
        @Serializable
        data class Data(
            @SerialName("MediaTagCollection")
            val mediaTagCollection: List<MediaTag>?
        )
    }

    @Serializable
    data class User(
        @SerialName("data")
        val data: Data
    ){
        @Serializable
        data class Data(
            @SerialName("User")
            val user: app.kanata.anilist.api.User?
        )
    }
}

//data class WhaData(
//    val Studio: Studio?,
//
//    // Follow query
//    val Following: User?,
//
//    // Follow query
//    val Follower: User?,
//
//    // Thread query
//    val Thread: Thread?,
//
//    // Recommendation query
//    val Recommendation: Recommendation?,
//
//    // Like query
//    val Like: User?,

//    // Review query
//    val Review: Review?,
//
//    // Activity query
//    val Activity: ActivityUnion?,
//
//    // Activity reply query
//    val ActivityReply: ActivityReply?,

//    // Comment query
//    val ThreadComment: List<ThreadComment>?,

//    // Notification query
//    val Notification: NotificationUnion?,

//    // Media Trend query
//    val MediaTrend: MediaTrend?,

//    // Provide AniList markdown to be converted to html (Requires auth)
//    val Markdown: ParsedMarkdown?,

//    // SiteStatistics: SiteStatistics
//    val AniChartUser: AniChartUser?,
//)
