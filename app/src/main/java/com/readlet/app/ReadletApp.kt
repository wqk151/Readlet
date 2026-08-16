package com.readlet.app

import android.app.Application
import com.readlet.app.data.Settings
import com.readlet.app.data.WordLevels
import com.readlet.app.data.db.AppDatabase
import com.readlet.app.data.repo.CardRepository

class ReadletApp : Application() {

    lateinit var db: AppDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var wordLevels: WordLevels
        private set
    lateinit var repository: CardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = Settings(this)
        wordLevels = WordLevels.load(this)
        repository = CardRepository(this, db, settings, wordLevels)
    }
}
