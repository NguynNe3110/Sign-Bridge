package com.uzuu.signbridge

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.uzuu.signbridge.feature.devicecheck.DeviceCheckFragment
import com.uzuu.signbridge.feature.main.MainScreen
import com.uzuu.signbridge.ui.theme.SignBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SignBridgeTheme {
                MainScreen(
                    onOpenDeviceCheck = {
                        supportFragmentManager.beginTransaction()
                            .replace(android.R.id.content, DeviceCheckFragment())
                            .addToBackStack("device_check")
                            .commit()
                    }
                )
            }
        }
    }
}
