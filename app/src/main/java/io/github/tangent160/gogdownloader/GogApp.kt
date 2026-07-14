package io.github.tangent160.gogdownloader

import android.app.Application
import io.github.tangent160.gogdownloader.core.CoverRepository
import io.github.tangent160.gogdownloader.core.GameDatabase
import io.github.tangent160.gogdownloader.core.GogCli
import io.github.tangent160.gogdownloader.core.Settings

class GogApp : Application() {

    val gogCli: GogCli by lazy { GogCli(this) }
    val gameDatabase: GameDatabase by lazy { GameDatabase(gogCli.databaseFile) }
    val coverRepository: CoverRepository by lazy { CoverRepository(this) }
    val settings: Settings by lazy { Settings(this) }
}
