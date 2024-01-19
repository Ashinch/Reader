package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKeys
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

sealed class FlowArticleListAlwaysHighlightStarredPreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListAlwaysHighlightStarredPreference(true)
    object OFF : FlowArticleListAlwaysHighlightStarredPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKeys.FlowArticleListAlwaysHighlightStarred,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKeys.FlowArticleListAlwaysHighlightStarred.key]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListAlwaysHighlightStarredPreference.not(): FlowArticleListAlwaysHighlightStarredPreference =
    when (value) {
        true -> FlowArticleListAlwaysHighlightStarredPreference.OFF
        false -> FlowArticleListAlwaysHighlightStarredPreference.ON
    }