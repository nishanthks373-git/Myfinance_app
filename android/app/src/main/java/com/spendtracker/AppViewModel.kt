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

    var skipped by mutableStateOf(Skips.all(app))
        private set

    /** True when this expense was marked "not required" for the month being viewed. */
    fun isSkipped(e: Expense): Boolean = Skips.key(month.toString(), e.utility) in skipped

    fun setSkipped(e: Expense, on: Boolean) {
        val k = Skips.key(month.toString(), e.utility)
        skipped = if (on) skipped + k else skipped - k
        Skips.save(getApplication(), skipped)
    }

    /** Pending = not paid and not marked "not required". */
    fun isPending(e: Expense): Boolean = !e.isPaid && !isSkipped(e)

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

    fun goToMonth(m: YearMonth) {
        month = m
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

    /** Runs a sheet write, shows the result, reloads the sheet on success. */
    private fun act(done: String, onResult: (Boolean) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            var ok = false
            try {
                block()
                message = done
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

    fun markExpense(
        row: Int, origUtility: String, utility: String, paidBy: String, paidOn: String,
        amount: Double, mode: String, adhoc: Double, createIfMissing: Boolean, onResult: (Boolean) -> Unit,
    ) = act("Saved to your sheet", onResult) {
        Api.markExpense(
            scriptUrl, token, month.toString(), row, origUtility, utility, paidBy, paidOn,
            amount, mode, adhoc, createIfMissing,
        )
        prefs.edit().putString("lastPaidBy", paidBy).apply()
    }

    fun clearExpense(row: Int, utility: String, onResult: (Boolean) -> Unit) =
        act("Payment cleared in your sheet", onResult) {
            Api.clearExpense(scriptUrl, token, month.toString(), row, utility)
        }

    fun markBill(row: Int, card: String, amount: Double, method: String, onResult: (Boolean) -> Unit) =
        act("Saved to your sheet", onResult) {
            Api.markBill(scriptUrl, token, month.toString(), row, card, amount, method)
        }

    fun clearBill(row: Int, card: String, onResult: (Boolean) -> Unit) =
        act("Bill cleared in your sheet", onResult) {
            Api.clearBill(scriptUrl, token, month.toString(), row, card)
        }

    fun setRate(name: String, rate: Double, create: Boolean, onResult: (Boolean) -> Unit) =
        act(if (create) "Method added to your sheet" else "Rate updated in your sheet", onResult) {
            Api.setRate(scriptUrl, token, name, rate, create)
        }

    fun editCardField(card: String, field: String, value: String, onResult: (Boolean) -> Unit) =
        act("Updated in your sheet", onResult) {
            Api.updateCardField(scriptUrl, token, card, field, value)
        }

    fun editDue(card: String, billDate: String, dueDate: String, limit: Double?, onResult: (Boolean) -> Unit) =
        act("Updated in your sheet", onResult) {
            Api.updateDue(scriptUrl, token, card, billDate, dueDate, limit)
        }
}
