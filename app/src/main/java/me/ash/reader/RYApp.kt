package me.ash.reader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.data.module.ApplicationScope
import me.ash.reader.data.module.IODispatcher
import me.ash.reader.data.repository.*
import me.ash.reader.data.source.OpmlLocalDataSource
import me.ash.reader.data.source.RYDatabase
import me.ash.reader.data.source.RYNetworkDataSource
import me.ash.reader.ui.ext.*
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class RYApp : Application(), Configuration.Provider {

    init {
        // From: https://gitlab.com/spacecowboy/Feeder
        // Install Conscrypt to handle TLSv1.3 pre Android10
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    @Inject
    lateinit var ryDatabase: RYDatabase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var ryNetworkDataSource: RYNetworkDataSource

    @Inject
    lateinit var opmlLocalDataSource: OpmlLocalDataSource

    @Inject
    lateinit var rssHelper: RssHelper

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var ryRepository: RYRepository

    @Inject
    lateinit var stringsRepository: StringsRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var localRssRepository: LocalRssRepository

    @Inject
    lateinit var opmlRepository: OpmlRepository

    @Inject
    lateinit var rssRepository: RssRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @IODispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        CrashHandler(this)
        dataStoreInit()
        applicationScope.launch {
            accountInit()
            workerInit()
            if (notFdroid) checkUpdate()
        }
    }

    private fun dataStoreInit() {
    }

    private suspend fun accountInit() {
        withContext(ioDispatcher) {
            if (accountRepository.isNoAccount()) {
                val account = accountRepository.addDefaultAccount()
                applicationContext.dataStore.put(DataStoreKeys.CurrentAccountId, account.id!!)
                applicationContext.dataStore.put(DataStoreKeys.CurrentAccountType, account.type.id)
            }
        }
    }

    private fun workerInit() {
        rssRepository.get().doSync()
    }

    private suspend fun checkUpdate() {
        withContext(ioDispatcher) {
            applicationContext.getLatestApk().let {
                if (it.exists()) it.del()
            }
        }
        ryRepository.checkUpdate(showToast = false)
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
