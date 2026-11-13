package com.uzuu.signbridge

import android.app.Application
import com.uzuu.signbridge.core.di.AppContainer

class MyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
