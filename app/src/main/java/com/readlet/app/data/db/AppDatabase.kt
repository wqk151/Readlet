package com.readlet.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Card::class, CardWord::class, ReviewLog::class, LlmUsage::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun cardWordDao(): CardWordDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun llmUsageDao(): LlmUsageDao

    companion object {
        /** v1 → v2：cards 表新增 mastered 列（已掌握标记）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN mastered INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：新增 llm_usage 表（大模型调用次数 / token 用量统计）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `llm_usage` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`calledAt` INTEGER NOT NULL, " +
                        "`promptTokens` INTEGER NOT NULL, " +
                        "`completionTokens` INTEGER NOT NULL, " +
                        "`totalTokens` INTEGER NOT NULL)"
                )
            }
        }

        /** v3 → v4：card_words 新增 level 列（重点词级别角标，可空）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE card_words ADD COLUMN level TEXT")
            }
        }

        /**
         * v4 → v5：学习门槛（方案 A，无表结构变化）。
         * 旧数据迁移为已学习：dueAt=null 且未掌握的卡 → 次日到期进入排程
         * （此前它们会被每日队列自动纳入新卡；此后「待学习」= 已分析 && dueAt=null && 未掌握）。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE cards SET dueAt = CAST(strftime('%s','now','+1 day') AS INTEGER) / 86400 " +
                        "WHERE dueAt IS NULL AND mastered = 0"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "readlet.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
