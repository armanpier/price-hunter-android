package com.teamwork.pricehunter.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.teamwork.pricehunter.core.security.AuthStorage

@Composable
fun ManualAuthHeaderDialog(
    authStorage: AuthStorage,
    onDismiss: () -> Unit,
    providers: List<String> = listOf("snappfood", "tapsifood", "digikala", "torob", "snappshop", "okala")
) {
    var selectedProvider by remember { mutableStateOf(providers.first()) }
    var tokenText by remember(selectedProvider) {
        mutableStateOf(authStorage.getToken(selectedProvider) ?: "")
    }
    var cookiesText by remember(selectedProvider) {
        mutableStateOf(authStorage.getCookies(selectedProvider) ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("manual_auth_dialog"),
        title = { Text("ورود دستی اطلاعات احراز هویت (Debug)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Provider Selector Tabs
                ScrollableTabRow(
                    selectedTabIndex = providers.indexOf(selectedProvider),
                    edgePadding = 0.dp,
                    modifier = Modifier.testTag("auth_provider_tabs")
                ) {
                    providers.forEachIndexed { _, provider ->
                        Tab(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                            text = { Text(provider) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it },
                    label = { Text("توکن (Authorization / Bearer)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_token_field"),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = cookiesText,
                    onValueChange = { cookiesText = it },
                    label = { Text("کوکی‌ها (Cookie Header)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_cookies_field"),
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tokenText.isNotBlank()) {
                        authStorage.saveToken(selectedProvider, tokenText.trim())
                    } else {
                        // If blank, clear token
                    }
                    if (cookiesText.isNotBlank()) {
                        authStorage.saveCookies(selectedProvider, cookiesText.trim())
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("auth_save_button")
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    authStorage.clearProvider(selectedProvider)
                    tokenText = ""
                    cookiesText = ""
                },
                modifier = Modifier.testTag("auth_clear_button")
            ) {
                Text("پاکسازی")
            }
        }
    )
}
