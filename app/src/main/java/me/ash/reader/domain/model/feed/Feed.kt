package me.ash.reader.domain.model.feed

import androidx.room.*
import me.ash.reader.domain.model.group.Group

/**
 * TODO: Add class description
 */
@Entity(
    tableName = "feed",
    foreignKeys = [ForeignKey(
        entity = Group::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
)
data class Feed(
    @PrimaryKey
    var id: String,
    @ColumnInfo
    var name: String,
    @ColumnInfo
    var icon: String? = null,
    @ColumnInfo
    var url: String,
    @ColumnInfo(index = true)
    var groupId: String,
    @ColumnInfo(index = true)
    var accountId: Int,
    @ColumnInfo
    var isNotification: Boolean = false,
    @ColumnInfo
    var isFullContent: Boolean = false,
    @ColumnInfo
    var isBrowser: Boolean = false,
) {

    @Ignore
    var important: Int? = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Feed

        if (id != other.id) return false
        if (name != other.name) return false
        if (icon != other.icon) return false
        if (url != other.url) return false
        if (groupId != other.groupId) return false
        if (accountId != other.accountId) return false
        if (isNotification != other.isNotification) return false
        if (isFullContent != other.isFullContent) return false
        if (isBrowser != other.isBrowser) return false
        if (important != other.important) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (icon?.hashCode() ?: 0)
        result = 31 * result + url.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + accountId
        result = 31 * result + isNotification.hashCode()
        result = 31 * result + isFullContent.hashCode()
        result = 31 * result + isBrowser.hashCode()
        result = 31 * result + (important ?: 0)
        return result
    }
}
