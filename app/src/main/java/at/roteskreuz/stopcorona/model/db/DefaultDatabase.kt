package at.roteskreuz.stopcorona.model.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.roteskreuz.stopcorona.model.db.dao.AutomaticDiscoveryDao
import at.roteskreuz.stopcorona.model.db.dao.ConfigurationDao
import at.roteskreuz.stopcorona.model.db.dao.InfectionMessageDao
import at.roteskreuz.stopcorona.model.db.dao.NearbyRecordDao
import at.roteskreuz.stopcorona.model.entities.configuration.*
import at.roteskreuz.stopcorona.model.entities.discovery.DbAutomaticDiscoveryEvent
import at.roteskreuz.stopcorona.model.entities.infection.info.WarningTypeConverter
import at.roteskreuz.stopcorona.model.entities.infection.message.DbContactWithInfectionMessage
import at.roteskreuz.stopcorona.model.entities.infection.message.DbInfectionMessage
import at.roteskreuz.stopcorona.model.entities.infection.message.MessageTypeConverter
import at.roteskreuz.stopcorona.model.entities.infection.message.UUIDConverter
import at.roteskreuz.stopcorona.model.entities.nearby.DbNearbyRecord
import at.roteskreuz.stopcorona.skeleton.core.model.db.converters.DateTimeConverter

/**
 * Room database description with DAOs specification.
 */
@Database(
    entities = [
        DbConfiguration::class,
        DbQuestionnaire::class,
        DbQuestionnaireAnswer::class,
        DbPageContent::class,
        DbNearbyRecord::class,
        DbInfectionMessage::class,
        DbContactWithInfectionMessage::class,
        DbAutomaticDiscoveryEvent::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(
    DateTimeConverter::class,
    ConfigurationLanguageConverter::class,
    MessageTypeConverter::class,
    WarningTypeConverter::class,
    UUIDConverter::class
)
abstract class DefaultDatabase : RoomDatabase() {

    companion object {
        val migrations = arrayOf(
            // app v1.0.0 with version DB 6
            /**
             * Added fields to the table [DbConfiguration].
             */
            migration(6, 7) {
                execSQL("DELETE FROM `configuration`") // clear DB
                execSQL("ALTER TABLE `configuration` ADD COLUMN `redWarningQuarantine` INTEGER")
                execSQL("ALTER TABLE `configuration` ADD COLUMN `yellowWarningQuarantine` INTEGER")
                execSQL("ALTER TABLE `configuration` ADD COLUMN `selfDiagnosedQuarantine` INTEGER")
            },
            /**
             * Added new debug table.
             * Added field to the [DbNearbyRecord].
             */
            migration(7, 8) {
                execSQL(
                    "CREATE TABLE IF NOT EXISTS `debug_playground_entity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timeStamp` INTEGER NOT NULL, `publicKey` TEXT NOT NULL, `proximity` INTEGER NOT NULL)"
                )
                execSQL("CREATE INDEX IF NOT EXISTS `index_debug_playground_entity_publicKey` ON `debug_playground_entity` (`publicKey`)")
                execSQL("ALTER TABLE `nearby_record` ADD COLUMN `detectedAutomatically` INTEGER DEFAULT 0 NOT NULL")
            },
            /**
             * Added new automatic discovery table.
             */
            migration(8, 9) {
                execSQL(
                    "CREATE TABLE IF NOT EXISTS `automatic_discovery` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timeStamp` INTEGER NOT NULL, `publicKey` BLOB NOT NULL, `proximity` INTEGER NOT NULL)"
                )
                execSQL("CREATE INDEX IF NOT EXISTS `index_automatic_discovery_publicKey` ON `automatic_discovery` (`publicKey`)")
            },
            /**
             * Updated definitions of [DbAutomaticDiscoveryEvent] and [DbNearbyRecord].
             */
            migration(9, 10) {
                // delete old table
                execSQL("DROP TABLE `automatic_discovery`")
                // create new table
                execSQL(
                    "CREATE TABLE IF NOT EXISTS `automatic_discovery` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `publicKey` BLOB NOT NULL, `proximity` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER)"
                )
                execSQL("CREATE INDEX IF NOT EXISTS `index_automatic_discovery_publicKey` ON `automatic_discovery` (`publicKey`)")

                // create new temp table
                execSQL(
                    "CREATE TABLE IF NOT EXISTS `nearby_record_temp` (`publicKey` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, `detectedAutomatically` INTEGER NOT NULL, PRIMARY KEY(`publicKey`))")
                // copy data from old table to temp
                execSQL(
                    "INSERT INTO `nearby_record_temp` (`publicKey`, `timestamp`, `detectedAutomatically`) SELECT `publicKey`, max(`timestamp`), `detectedAutomatically` FROM `nearby_record` GROUP BY `publicKey`"
                )
                // delete old table
                execSQL("DROP TABLE `nearby_record`")
                // rename temp to original
                execSQL("ALTER TABLE `nearby_record_temp` RENAME TO `nearby_record`")
            },
            /**
             * Delete debug table.
             */
            migration(10, 11) {
                // delete old table
                execSQL("DROP TABLE `debug_playground_entity`")
            },
            /**
             * Fix cascade rule on [DbContactWithInfectionMessage].
             */
            migration(11, 12) {
                // create temp table
                execSQL(
                    "CREATE TABLE IF NOT EXISTS `contact_with_infection_message_temp` (`messageUuid` TEXT NOT NULL, `publicKey` BLOB NOT NULL, PRIMARY KEY(`messageUuid`), FOREIGN KEY(`messageUuid`) REFERENCES `infection_message`(`uuid`) ON UPDATE CASCADE ON DELETE CASCADE )"
                )
                // copy data from old table to temp
                execSQL(
                    "INSERT INTO `contact_with_infection_message_temp` (`messageUuid`, `publicKey`) SELECT `messageUuid`, `publicKey` FROM `contact_with_infection_message`"
                )
                // delete old table
                execSQL("DROP TABLE `contact_with_infection_message`")
                // rename temp to original
                execSQL("ALTER TABLE `contact_with_infection_message_temp` RENAME TO `contact_with_infection_message`")
            }
        )
    }

    abstract fun configurationDao(): ConfigurationDao

    abstract fun nearbyRecordDao(): NearbyRecordDao

    abstract fun infectionMessageDao(): InfectionMessageDao

    abstract fun automaticDiscoveryDao(): AutomaticDiscoveryDao
}

/**
 * Helper fun to create migration instance.
 */
private fun migration(startVersion: Int, endVersion: Int, migrateProcedure: SupportSQLiteDatabase.() -> Unit): Migration {
    return object : Migration(startVersion, endVersion) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.migrateProcedure()
        }
    }
}