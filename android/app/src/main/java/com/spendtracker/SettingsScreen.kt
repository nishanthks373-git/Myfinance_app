package com.spendtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var url by remember { mutableStateOf(vm.scriptUrl) }
    var token by remember { mutableStateOf(vm.token) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sheet connection", style = MaterialTheme.typography.titleLarge)
        Text(
            "Paste the web app URL from your Apps Script deployment (it ends in /exec) and the " +
                "same secret you set in the script's SECRET constant.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it }, label = { Text("Web app URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it }, label = { Text("Secret") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton("Save and test connection", enabled = url.startsWith("https://") && token.isNotBlank()) {
            vm.saveSettings(url, token)
        }
        if (vm.configured && vm.state != null) {
            Text("Connected. Reading ${vm.state?.rewards?.size ?: 0} payment methods from your sheet.")
        }
        if (vm.loading) Text("Connecting…")
    }
}
