package com.uzuu.signbridge

import android.app.Application
import com.uzuu.signbridge.core.di.AppContainer

class MyApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
