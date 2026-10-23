package com.spendtracker

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
    val ctx = LocalContext.current
    var remindOn by remember { mutableStateOf(Reminders.enabled(ctx)) }
    var hour by remember { mutableStateOf(Reminders.hour(ctx)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindOn = granted
        Reminders.setEnabled(ctx, granted)
        if (!granted) vm.message = "Notifications are blocked for this app in Android settings"
    }

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

        Text("Daily reminder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            "A notification once a day listing unpaid expenses and card bills. A card is included only " +
                "from its bill date onwards (set on Cards > Due dates).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Remind me every day", Modifier.weight(1f))
            Switch(checked = remindOn, onCheckedChange = { on ->
                if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    remindOn = on
                    Reminders.setEnabled(ctx, on)
                }
            })
        }
        if (remindOn) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("At ${String.format("%02d:00", hour)}", Modifier.weight(1f))
                OutlinedButton(onClick = { hour = (hour + 23) % 24; Reminders.setHour(ctx, hour) }) { Text("−1h") }
                OutlinedButton(onClick = { hour = (hour + 1) % 24; Reminders.setHour(ctx, hour) }, modifier = Modifier.padding(start = 8.dp)) { Text("+1h") }
            }
            OutlinedButton(onClick = {
                if (Reminders.canNotify(ctx)) Reminders.sendTestNow(ctx) else askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Send a test reminder now") }
        }
    }
}
