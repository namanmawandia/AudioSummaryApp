package com.example.audiosummeryapp.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@TypeConverters(Converters::class)
@Database(
    entities  = [RecordingSessionEntity::class],
    version   = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao
}

class Converters {
    @TypeConverter fun fromStatus(s: SessionStatus): String = s.name
    @TypeConverter fun toStatus(s: String): SessionStatus    = SessionStatus.valueOf(s)
}