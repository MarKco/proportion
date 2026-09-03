package com.ilsecondodasinistra.proportion.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncLogDataSourceTest {

    private lateinit var dataSource: SyncLogDataSource

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = context.filesDir.resolve("sync-log-${System.nanoTime()}.preferences_pb")
        dataSource = SyncLogDataSource(PreferenceDataStoreFactory.create { file })
    }

    @Test
    fun `starts empty`() = runTest {
        assertThat(dataSource.entries.first()).isEmpty()
    }

    @Test
    fun `an appended entry survives a read back`() = runTest {
        dataSource.append(SyncLogEntry(timestamp = 1_000L, message = "Sincronizzate 3 ricette", isError = false))

        val entry = dataSource.entries.first().single()
        assertThat(entry.timestamp).isEqualTo(1_000L)
        assertThat(entry.message).isEqualTo("Sincronizzate 3 ricette")
        assertThat(entry.isError).isFalse()
    }

    @Test
    fun `entries keep their order, oldest first`() = runTest {
        dataSource.append(SyncLogEntry(1_000L, "primo", false))
        dataSource.append(SyncLogEntry(2_000L, "secondo", true))

        assertThat(dataSource.entries.first().map { it.message }).containsExactly("primo", "secondo").inOrder()
    }

    @Test
    fun `a newline inside a message does not break the encoding`() = runTest {
        dataSource.append(SyncLogEntry(1_000L, "riga uno\nriga due", true))

        val entry = dataSource.entries.first().single()
        assertThat(entry.message).isEqualTo("riga uno riga due")
        assertThat(entry.isError).isTrue()
    }

    @Test
    fun `only the most recent entries survive once the cap is exceeded`() = runTest {
        repeat(60) { i -> dataSource.append(SyncLogEntry(i.toLong(), "entry $i", false)) }

        val kept = dataSource.entries.first()
        assertThat(kept).hasSize(50)
        assertThat(kept.first().message).isEqualTo("entry 10")
        assertThat(kept.last().message).isEqualTo("entry 59")
    }
}
