package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lens_wear")
data class LensWear(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startDate: Long,
    val durationDays: Int, // e.g. 14, 30 days
    val isActive: Boolean = true,
    val skippedDates: String = "" // Comma-separated date strings "yyyy-MM-dd"
)

@Entity(tableName = "lens_stock")
data class LensStock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brand: String,
    val diopter: String,
    val pairsPerBox: Int, // Number of pairs per new box
    val boxCount: Int, // Unopened full boxes
    val pairsInOpenBox: Int // Pairs left in the currently open box
)

@Entity(tableName = "ophthalmologist_visit")
data class OphthalmologistVisit(
    @PrimaryKey val id: Int = 1,
    val lastVisitDate: Long?,
    val nextAppointmentDate: Long?
)

@Dao
interface LensDao {
    // Lens Wear
    @Query("SELECT * FROM lens_wear ORDER BY id DESC")
    fun getAllLensWear(): Flow<List<LensWear>>

    @Query("SELECT * FROM lens_wear WHERE isActive = 1 LIMIT 1")
    fun getActiveLensWear(): Flow<LensWear?>

    @Query("SELECT * FROM lens_wear WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveLensWearSync(): LensWear?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLensWear(lensWear: LensWear)

    @Update
    suspend fun updateLensWear(lensWear: LensWear)

    @Query("UPDATE lens_wear SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllLensWear()

    // Lens Stock
    @Query("SELECT * FROM lens_stock ORDER BY id DESC")
    fun getAllLensStock(): Flow<List<LensStock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLensStock(lensStock: LensStock)

    @Update
    suspend fun updateLensStock(lensStock: LensStock)

    @Delete
    suspend fun deleteLensStock(lensStock: LensStock)

    // Ophthalmologist Visit
    @Query("SELECT * FROM ophthalmologist_visit WHERE id = 1 LIMIT 1")
    fun getOphthalmologistVisit(): Flow<OphthalmologistVisit?>

    @Query("SELECT * FROM ophthalmologist_visit WHERE id = 1 LIMIT 1")
    suspend fun getOphthalmologistVisitSync(): OphthalmologistVisit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOphthalmologistVisit(visit: OphthalmologistVisit)
}

@Database(
    entities = [LensWear::class, LensStock::class, OphthalmologistVisit::class],
    version = 1,
    exportSchema = false
)
abstract class LensDatabase : RoomDatabase() {
    abstract fun lensDao(): LensDao

    companion object {
        @Volatile
        private var INSTANCE: LensDatabase? = null

        fun getDatabase(context: android.content.Context): LensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    LensDatabase::class.java,
                    "lens_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
