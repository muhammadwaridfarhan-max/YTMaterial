package com.example.ytclient

import android.app.Application
import com.example.ytclient.data.AppDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class YTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(AppDownloader(), Localization("id", "ID"), ContentCountry("ID"))
    }
}
