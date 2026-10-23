package com.spendtracker

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("spend_tracker", Context.MODE_PRIVATE)

    var scriptUrl by mutableStateOf(prefs.getString("url", "") ?: "")
        private set
    var token by mutableStateOf(prefs.getString("token", "") ?: "")
        private set
    var month by mutableStateOf(YearMonth.now())
        private set
    var state by mutableStateOf<SheetState?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    val configured: Boolean get() = scriptUrl.startsWith("https://") && token.isNotBlank()

    init {
        if (configured) refresh()
    }

    fun saveSettings(url: String, newToken: String) {
        scriptUrl = url.trim()
        token = newToken.trim()
        prefs.edit().putString("url", scriptUrl).putString("token", token).apply()
        refresh()
    }

    fun changeMonth(delta: Long) {
        month = month.plusMonths(delta)
        refresh()
    }

    fun refresh() {
        if (!configured) return
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        loading = true
        try {
            state = Api.fetchState(scriptUrl, token, month.toString())
        } catch (e: Exception) {
            message = e.message ?: "Could not load the sheet"
        } finally {
            loading = false
        }
    }

    fun defaultPaidBy(): String =
        prefs.getString("lastPaidBy", null)?.takeIf { it.isNotBlank() }
            ?: state?.paidByOptions?.firstOrNull().orEmpty()

    /** Today if viewing the current month, otherwise the 1st of the viewed month. */
    fun defaultDate(): LocalDate = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1)

    fun markExpense(
        utility: String, paidBy: String, paidOn: String, amount: Double, mode: String,
        createIfMissing: Boolean, onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            busy = true
            var ok = false
            try {
                Api.markExpense(scriptUrl, token, month.toString(), utility, paidBy, paidOn, amount, mode, createIfMissing)
                prefs.edit().putString("lastPaidBy", paidBy).apply()
                message = "Saved to your sheet"
                ok = true
            } catch (e: Exception) {
                message = e.message ?: "Could not save"
            } finally {
                busy = false
            }
            onResult(ok)
            if (ok) load()
        }
    }

    fun markBill(card: String, amount: Double, method: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            busy = true
            var ok = false
            try {
                Api.markBill(scriptUrl, token, month.toString(), card, amount, method)
                message = "Saved to your sheet"
                ok = true
            } catch (e: Exception) {
                message = e.message ?: "Could not save"
            } finally {
                busy = false
            }
            onResult(ok)
            if (ok) load()
        }
    }
}
