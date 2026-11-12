package eu.wanderclan.wordquest.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val db: WordQuestDatabase) {
    fun settings(): Flow<AppSettingsEntity> = db.settings().observe().map { it ?: AppSettingsEntity() }
    suspend fun setSound(enabled: Boolean) = db.withTransaction {
        db.settings().insertDefault(AppSettingsEntity())
        db.settings().setSound(enabled)
    }
}
