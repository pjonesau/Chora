package com.craftworks.music.managers

import android.content.Context
import androidx.core.content.edit
import com.craftworks.music.migrations.GenresTabMigration
import com.craftworks.music.migrations.Migration
import com.craftworks.music.migrations.ProvidersRefactorMigration
import com.craftworks.music.migrations.UnisonLyricsOffMigration

object MigrationManager {
    private const val MIGRATION_VERSION = "version"

    // Factories rather than KClass references: the migration is constructed when it runs, but R8
    // can see the constructor and no reflective instantiation (and no keep rule) is needed.
    private val Migrations: List<() -> Migration> = listOf(
        ::ProvidersRefactorMigration,
        ::GenresTabMigration,
        ::UnisonLyricsOffMigration,
    )
    fun init(context: Context) {
        val migrationStatus = context.getSharedPreferences("MigrationStatus", Context.MODE_PRIVATE)
        var version = migrationStatus.getInt(MIGRATION_VERSION, -1)
        if (version == -1) {
            // Check if a version before migration has been used
            if (
                context.getSharedPreferences("LocalProviderPrefs", Context.MODE_PRIVATE).contains("local_folders") ||
                context.getSharedPreferences("NavidromePrefs", Context.MODE_PRIVATE).contains("navidrome_servers")
            ) {
                version = 0
            }
        }

        // Migrate if needed
        if (version >= 0 && version < Migrations.size) {
            for (i in version..<Migrations.size) {
                Migrations[i]().up(context)
            }
        }

        // Update migration version
        migrationStatus.edit {
            putInt(MIGRATION_VERSION, Migrations.size)
        }
    }
}