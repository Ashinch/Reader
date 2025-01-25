package me.ash.reader.infrastructure.rss

import android.util.Log
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import rust.nostr.sdk.Client
import rust.nostr.sdk.Event
import rust.nostr.sdk.Filter
import rust.nostr.sdk.Kind
import rust.nostr.sdk.KindEnum
import rust.nostr.sdk.Nip19Profile
import rust.nostr.sdk.Nip21
import rust.nostr.sdk.Nip21Enum
import rust.nostr.sdk.NostrSdkException
import rust.nostr.sdk.PublicKey
import rust.nostr.sdk.RelayMetadata
import rust.nostr.sdk.TagKind
import rust.nostr.sdk.extractRelayList
import rust.nostr.sdk.getNip05Profile
import java.time.Duration

sealed interface FetchedFeed {
    fun getIconLink(): String?
    //The function below is for compatibility with SyndFeed
    fun getIconUrl(): String?
    fun getFeedLink(): String
    var title: String
    fun getFeedAuthor(): String
    fun getArticles(): List<*>
}

class SyndFeedDelegate(
    private val syndFeed: SyndFeed
): FetchedFeed {

    override fun getIconLink(): String? {
        return syndFeed.icon.link
    }

    override fun getIconUrl(): String? {
        return syndFeed.icon.url
    }

    override fun getFeedLink(): String {
        return syndFeed.link
    }

    override var title: String
        get() = syndFeed.title
        set(value) {
            syndFeed.title = value
        }

    override fun getFeedAuthor(): String {
        return syndFeed.author
    }

    override fun getArticles(): List<SyndEntry> {
        return syndFeed.entries
    }
}

class NostrFeed(
    private val nostrClient: Client
): FetchedFeed {
    private val LOG_TAG = "ReadYou"
    private lateinit var feedFetchResult: NostrFeedResult

    // The default relays to get info from, separated by purpose.
    private val defaultFetchRelays = listOf("wss://relay.nostr.band", "wss://relay.damus.io")
    private val defaultMetadataRelays = listOf("wss://purplepag.es", "wss://user.kindpag.es")
    private val defaultArticleFetchRelays = setOf("wss://nos.lol") + defaultFetchRelays

    override fun getIconLink(): String {
        return feedFetchResult.authorPictureLink
    }

    override fun getIconUrl(): String {
        return feedFetchResult.authorPictureLink
    }

    override fun getFeedLink(): String {
        return feedFetchResult.nostrUri
    }

    override var title: String
        get() = feedFetchResult.feedTitle
        set(value) {
            feedFetchResult.feedTitle = value
        }

    override fun getFeedAuthor(): String {
        return feedFetchResult.authorName
    }

    override fun getArticles(): List<Event> {
        return feedFetchResult.articles
    }

    private suspend fun nreq(nostrUri: String): NostrFeedResult {

        val profile = getProfileMetadata(nostrUri)
        val publishRelays = getUserPublishRelays(profile.publicKey)

        val articles = fetchArticlesForAuthor(
            profile.publicKey,
            publishRelays
        )
        return NostrFeedResult(
            nostrUri = nostrUri,
            authorName = profile.name,
            feedTitle = profile.name,
            authorPictureLink = profile.imageUrl,
            articles = articles
        )
    }

    private suspend fun parseNostrUri(nostrUri: String): Nip19Profile {
        if (nostrUri.contains("@")) { // It means it is a Nip05 address
            val rawString = nostrUri.removePrefix("nostr:")
            val parsedNip5 = getNip05Profile(rawString)
            val (pubkey, relays) = parsedNip5.publicKey() to parsedNip5.relays()
            return Nip19Profile(pubkey, relays)
        } else {
            val parsedProfile = Nip21.parse(nostrUri).asEnum()
            when(parsedProfile) {
                is Nip21Enum.Pubkey -> return Nip19Profile(parsedProfile.publicKey)
                is Nip21Enum.Profile -> return Nip19Profile(parsedProfile.profile.publicKey(), parsedProfile.profile.relays())
                else -> throw Throwable(message = "Could not find the user's info: $nostrUri")
            }
        }
    }

    private suspend fun getProfileMetadata(nostrUri: String): AuthorNostrData {
        val possibleNostrProfile = parseNostrUri(nostrUri)
        val publicKey = possibleNostrProfile.publicKey()
        val relayList =
            possibleNostrProfile.relays()
                .takeIf {
                    it.size < 4
                }.orEmpty()
                .ifEmpty { getUserPublishRelays(publicKey) }
        Log.d(LOG_TAG, "getProfileMetadata: Relays from Nip19 -> ${relayList.joinToString(separator = ", ")}")
        relayList
            .ifEmpty { defaultFetchRelays }
            .forEach { relayUrl ->
                nostrClient.addReadRelay(relayUrl)
            }
        nostrClient.connect()
        val profileInfo =
            try {
                nostrClient.fetchMetadata(
                    publicKey = publicKey,
                    timeout = Duration.ofSeconds(5L),
                )
            } catch (e: NostrSdkException) {
                // We will use a default relay regardless of whether it is added above, to keep things simple.
                nostrClient.addReadRelay(defaultFetchRelays.random())
                nostrClient.connect()
                nostrClient.fetchMetadata(
                    publicKey = publicKey,
                    timeout = Duration.ofSeconds(5L),
                )
            }
        Log.d(LOG_TAG, "getProfileMetadata: ${profileInfo.asPrettyJson()}")

        // Check if all relays in relaylist can be connected to
        return AuthorNostrData(
            uri = possibleNostrProfile.toNostrUri(),
            name = profileInfo.getName().toString(),
            publicKey = publicKey,
            imageUrl = profileInfo.getPicture().toString(),
            relayList = nostrClient.relays().map { relayEntry -> relayEntry.key },
        )
    }


    private suspend fun getUserPublishRelays(userPubkey: PublicKey): List<String> {
        val userRelaysFilter =
            Filter()
                .author(userPubkey)
                .kind(
                    Kind.fromEnum(KindEnum.RelayList),
                )

        nostrClient.removeAllRelays()
        defaultMetadataRelays.forEach { relayUrl ->
            nostrClient.addReadRelay(relayUrl)
        }
        nostrClient.connect()
        val potentialUserRelays =
            nostrClient.fetchEventsFrom(
                urls = defaultMetadataRelays,
                filters = listOf(userRelaysFilter),
                timeout = Duration.ofSeconds(5),
            )
        val relayList = extractRelayList(potentialUserRelays.toVec().first())
        val relaysToUse =
            if (relayList.any { (_, relayType) -> relayType == RelayMetadata.WRITE }) {
                relayList.filter { it.value == RelayMetadata.WRITE }.map { entry -> entry.key }
            } else if (relayList.size < 7) {
                relayList.map { entry -> entry.key } // This represents the relay URL, just as the operation above.
            } else {
                defaultArticleFetchRelays.map { it }
            }

        return relaysToUse
    }

    private suspend fun fetchArticlesForAuthor(
        author: PublicKey,
        relays: List<String>,
    ): List<Event> {
        val articlesByAuthorFilter =
            Filter()
                .author(author)
                .kind(Kind.fromEnum(KindEnum.LongFormTextNote))
        Log.d(LOG_TAG, "Relay List size: ${relays.size}")

        nostrClient.removeAllRelays()
        val relaysToUse =
            relays.take(3).plus(defaultArticleFetchRelays.random())
                .ifEmpty { defaultFetchRelays }
        relaysToUse.forEach { relay -> nostrClient.addReadRelay(relay) }
        nostrClient.connect()
        Log.d(LOG_TAG, "FETCHING ARTICLES")
        val articleEventSet =
            nostrClient.fetchEventsFrom(
                urls = relaysToUse,
                filters =
                listOf(
                    articlesByAuthorFilter,
                ),
                timeout = Duration.ofSeconds(10L),
            ).toVec()
        val articleEvents = articleEventSet.distinctBy { it.tags().find(TagKind.Title) }
        Log.d(LOG_TAG, "fetchArticlesForAuthor: Article Set Size: ${articleEvents.size}")
        nostrClient.removeAllRelays() // This is necessary to avoid piling relays to fetch from(on each fetch).
        return articleEvents
    }

    companion object {
        suspend fun fetchFeedFrom(uri: String, nostrClient: Client): NostrFeed {
            nostrClient.use {
                val feedInstance = NostrFeed(nostrClient)
                val feedResult = feedInstance.nreq(uri)
                feedInstance.feedFetchResult = feedResult
                return if (feedInstance.getArticles().isNotEmpty()){
                    feedInstance
                } else throw EmptyNostrDataException("No feed found for $uri")
            }

        }

        suspend fun fetchFeedMetadata(uri: String, nostrClient: Client): AuthorNostrData {
            val feedFetcher = NostrFeed(nostrClient)
            return feedFetcher.getProfileMetadata(uri)
        }
    }

}

class AuthorNostrData(
    val uri: String,
    val name: String,
    val publicKey: PublicKey,
    val imageUrl: String,
    val relayList: List<String>
)

class EmptyNostrDataException(override val message: String?): Exception(message)

class NostrFeedResult(
    val nostrUri: String,
    val authorName: String,
    var feedTitle: String,
    val authorPictureLink: String,
    val articles: List<Event>
)
