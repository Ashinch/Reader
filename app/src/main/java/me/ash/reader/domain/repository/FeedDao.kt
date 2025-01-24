package me.ash.reader.domain.repository

import android.util.Log
import androidx.room.*
import me.ash.reader.domain.model.feed.Feed

@Dao
interface FeedDao {

    @Query(
        """
        UPDATE feed SET groupId = :targetGroupId
        WHERE groupId = :groupId
        AND accountId = :accountId
        """
    )
    suspend fun updateTargetGroupIdByGroupId(
        accountId: Int,
        groupId: String,
        targetGroupId: String,
    )

    @Query(
        """
        UPDATE feed SET isFullContent = :isFullContent
        WHERE accountId = :accountId
        AND groupId = :groupId
        """
    )
    suspend fun updateIsFullContentByGroupId(
        accountId: Int,
        groupId: String,
        isFullContent: Boolean,
    )
    
    @Query(
        """
        UPDATE feed SET isBrowser = :isBrowser
        WHERE accountId = :accountId
        AND groupId = :groupId
        """
    )
    suspend fun updateIsBrowserByGroupId(
        accountId: Int,
        groupId: String,
        isBrowser: Boolean,
    )

    @Query(
        """
        UPDATE feed SET isNotification = :isNotification
        WHERE accountId = :accountId
        AND groupId = :groupId
        """
    )
    suspend fun updateIsNotificationByGroupId(
        accountId: Int,
        groupId: String,
        isNotification: Boolean,
    )

    @Query(
        """
        SELECT * FROM feed
        WHERE groupId = :groupId
        AND accountId = :accountId
        """
    )
    suspend fun queryByGroupId(accountId: Int, groupId: String): List<Feed>

    @Query(
        """
        DELETE FROM feed
        WHERE groupId = :groupId
        AND accountId = :accountId
        """
    )
    suspend fun deleteByGroupId(accountId: Int, groupId: String)

    @Query(
        """
        DELETE FROM feed
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteByAccountId(accountId: Int)

    @Query(
        """
        SELECT * FROM feed
        WHERE id = :id
        """
    )
    suspend fun queryById(id: String): Feed?

    @Query(
        """
        SELECT * FROM feed
        WHERE accountId = :accountId
        """
    )
    suspend fun queryAll(accountId: Int): List<Feed>

    @Query(
        """
        SELECT * FROM feed
        WHERE accountId = :accountId
        AND (icon IS NUll OR icon = '')
        """
    )
    suspend fun queryNoIcon(accountId: Int): List<Feed>

    @Query(
        """
        SELECT * FROM feed
        WHERE accountId = :accountId
        AND url = :url
        """
    )
    suspend fun queryByLink(accountId: Int, url: String): List<Feed>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg feed: Feed)

    @Insert
    suspend fun insertList(feeds: List<Feed>): List<Long>

    @Update
    suspend fun update(vararg feed: Feed)

    @Delete
    suspend fun delete(vararg feed: Feed)

    suspend fun insertOrUpdate(feeds: List<Feed>) {
        feeds.forEach {
            val feed = queryById(it.id)
            if (feed == null) {
                insert(it)
            } else {
                Log.i("RLog", "insertOrUpdate it: $it")
                Log.i("RLog", "insertOrUpdate feed: $feed")
                if (it.icon.isNullOrEmpty()) it.icon = feed.icon
                // TODO: Consider migrating the fields to be nullable.
                it.isNotification = feed.isNotification
                it.isFullContent = feed.isFullContent
                it.isBrowser = feed.isBrowser
                update(it)
            }
        }
    }
}
