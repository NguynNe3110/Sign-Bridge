package com.uzuu.signbridge.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenDeviceCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SignBridge") },
                actions = {
                    FilledTonalButton(
                        onClick = onOpenDeviceCheck,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Test Thiết Bị (TV2)")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SignBridge AI",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Cầu nối giao tiếp hai chiều cho người khiếm thính\n(Màn hình Compose nền tảng đã sẵn sàng cho TV1 dựng Split-Screen)",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(28.dp))
            OutlinedButton(
                onClick = onOpenDeviceCheck,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("Mở bảng kiểm tra phần cứng (TV2)")
            }
        }
    }
}
