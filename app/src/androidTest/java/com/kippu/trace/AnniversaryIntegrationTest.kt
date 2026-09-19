package com.kippu.trace

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kippu.trace.data.AppDatabase
import com.kippu.trace.model.AnniversaryType
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.BackupManager
import com.kippu.trace.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AnniversaryIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun millis(date: String) = AnniversaryUtils.millis(LocalDate.parse(date))

    @Test fun migrationPreservesExistingEvents() = runBlocking {
        val name = "anniversary-migration-test.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("""CREATE TABLE date_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                    targetDate INTEGER NOT NULL, isFuture INTEGER NOT NULL, isLunar INTEGER NOT NULL,
                    mode TEXT NOT NULL, backgroundUri TEXT, isPinned INTEGER NOT NULL,
                    maskOpacity REAL NOT NULL, position INTEGER NOT NULL)""")
                old.execSQL("INSERT INTO date_events VALUES (1, 'Existing', 0, 0, 0, 'ACCUMULATE', NULL, 1, 0.3, 5)")
                old.version = 2
            }
            val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
            try {
                val saved = database.eventDao().getEventById(1)!!
                assertEquals("Existing", saved.title)
                assertEquals(5, saved.position)
                assertTrue(saved.isPinned)
                assertEquals(RepeatMode.NONE, saved.repeatMode)
                assertEquals(1, saved.repeatInterval)
                assertEquals(AnniversaryType.NONE, saved.anniversaryType)
                assertNull(saved.repeatAnchorDate)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun anniversaryMessageOnlyAppearsOnMatchingDay() {
        val event = DateEvent(title = "Anniversary", targetDate = millis("2026-11-16"), isFuture = false,
            mode = DisplayMode.ACCUMULATE, anniversaryType = AnniversaryType.CALENDAR, anniversaryMessage = "Together")
        assertEquals("Together", TimeUtils.formatAnniversary(context, event, LocalDate.parse("2027-11-16")))
        assertNull(TimeUtils.formatAnniversary(context, event, LocalDate.parse("2027-11-17")))
        assertNull(TimeUtils.formatAnniversary(context, event.copy(showYear = false, showMonth = false, showWeek = false), LocalDate.parse("2027-11-16")))
    }

    @Test fun repeatAdvancePersistsAndBackupRetainsSettings() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val backup = File.createTempFile("anniversary-test", ".zip", context.cacheDir)
        try {
            val event = DateEvent(id = 1, title = "Monthly", targetDate = millis("2026-01-31"), isFuture = true,
                mode = DisplayMode.COUNT_DOWN, repeatMode = RepeatMode.MONTHLY, repeatInterval = 2,
                anniversaryMessage = "Saved")
            database.eventDao().insertEvent(event)
            database.eventDao().advanceRepeatingEvents(LocalDate.parse("2026-02-01"))
            val saved = database.eventDao().getEventById(1)!!
            assertEquals(millis("2026-03-31"), saved.targetDate)
            assertEquals(event.targetDate, saved.repeatAnchorDate)
            BackupManager.exportToZip(context, listOf(saved), Uri.fromFile(backup)).getOrThrow()
            assertEquals(saved, BackupManager.importFromZip(context, Uri.fromFile(backup)).getOrThrow().single())
        } finally {
            database.close()
            backup.delete()
        }
    }
}
