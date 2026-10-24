package com.spendtracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the Apps Script web app bound to the Google Sheet (see /apps-script/Code.gs). */
object Api {

    private suspend fun call(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            // text/plain avoids any preflight; Apps Script reads the raw body either way.
            setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode   // Apps Script answers 302 -> HttpURLConnection follows it with GET
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("Server returned HTTP $code")
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Unexpected reply. Check the URL is the web app's /exec link and access is set to \"Anyone\"."
                )
            }
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("error", "Unknown error"))
            }
            json
        } finally {
            conn.disconnect()
        }
    }

    private fun req(token: String, action: String) = JSONObject().put("token", token).put("action", action)

    suspend fun fetchState(url: String, token: String, month: String): SheetState =
        parseState(call(url, req(token, "state").put("month", month)))

    /** Last 12 months (ending at [month]) of one expense (kind "expense") or card bill (kind "bill"). */
    suspend fun itemHistory(url: String, token: String, month: String, kind: String, name: String): List<ItemPoint> {
        val j = call(url, req(token, "itemHistory").put("month", month).put("kind", kind).put("name", name))
        return j.getJSONArray("points").objs().map {
            ItemPoint(it.optString("month"), it.dbl("amount"), it.dbl("reward"), it.optString("mode"))
        }
    }

    /** Mark a payment or edit an existing row (row > 0 edits exactly that row). */
    suspend fun markExpense(
        url: String, token: String, month: String, row: Int, origUtility: String, utility: String,
        paidBy: String, paidOn: String, amount: Double, mode: String, adhoc: Double, createIfMissing: Boolean,
    ) {
        call(
            url,
            req(token, "markExpense").put("month", month).put("row", row).put("origUtility", origUtility)
                .put("utility", utility).put("paidBy", paidBy).put("paidOn", paidOn)
                .put("amount", amount).put("mode", mode).put("adhoc", adhoc)
                .put("createIfMissing", createIfMissing),
        )
    }

    suspend fun clearExpense(url: String, token: String, month: String, row: Int, utility: String) {
        call(url, req(token, "clearExpense").put("month", month).put("row", row).put("utility", utility))
    }

    suspend fun markBill(
        url: String, token: String, month: String, row: Int, card: String, amount: Double, method: String,
    ) {
        call(
            url,
            req(token, "markCCBill").put("month", month).put("row", row).put("card", card)
                .put("amount", amount).put("method", method),
        )
    }

    suspend fun clearBill(url: String, token: String, month: String, row: Int, card: String) {
        call(url, req(token, "clearBill").put("month", month).put("row", row).put("card", card))
    }

    /** rate is a fraction (0.05 = 5%). create = true adds a new payment method. */
    suspend fun setRate(url: String, token: String, name: String, rate: Double, create: Boolean) {
        call(url, req(token, "setRate").put("name", name).put("rate", rate).put("create", create))
    }

    suspend fun updateCardField(url: String, token: String, card: String, field: String, value: String) {
        call(url, req(token, "updateCardField").put("card", card).put("field", field).put("value", value))
    }

    suspend fun updateDue(
        url: String, token: String, card: String, billDate: String, dueDate: String, limit: Double?,
    ) {
        call(
            url,
            req(token, "updateDue").put("card", card).put("billDate", billDate).put("dueDate", dueDate)
                .put("limit", limit ?: JSONObject.NULL),
        )
    }

    /* ---------- parsing ---------- */

    private fun JSONObject.dbl(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strs(): List<String> = (0 until length()).map { optString(it, "") }

    private fun parseState(j: JSONObject): SheetState {
        val rewards = j.getJSONArray("rewards").objs().map { Reward(it.getString("name"), it.getDouble("rate")) }
        val expenses = j.getJSONArray("expenses").objs().map {
            Expense(
                row = it.getInt("row"), utility = it.optString("utility"), paidBy = it.optString("paidBy"),
                paidOn = it.optString("paidOn"), amount = it.dbl("amount"), mode = it.optString("mode"),
                rate = it.dbl("rate"), adhoc = it.dbl("adhoc") ?: 0.0, actual = it.dbl("actual"),
            )
        }
        val bills = j.getJSONArray("ccBills").objs().map {
            CcBill(
                row = it.getInt("row"), card = it.optString("card"), amount = it.dbl("amount"),
                method = it.optString("method"), rate = it.dbl("rate"), actual = it.dbl("actual"),
            )
        }
        val dues = j.getJSONArray("dues").objs().map {
            Due(it.optString("card"), it.optString("billDate"), it.optString("dueDate"), it.dbl("limit"))
        }
        val history = j.getJSONArray("history").objs().map {
            MonthHistory(it.optString("month"), it.dbl("spends"), it.dbl("returns"), it.dbl("pct"))
        }
        val cards = j.getJSONArray("cards").let { arr -> (0 until arr.length()).map { arr.getJSONArray(it).strs() } }
        return SheetState(
            month = j.optString("month"),
            rewards = rewards,
            expenses = expenses,
            ccBills = bills,
            templateUtilities = j.getJSONArray("templateUtilities").strs(),
            templateCards = j.getJSONArray("templateCards").strs(),
            paidByOptions = j.getJSONArray("paidByOptions").strs(),
            utilities = j.getJSONArray("utilities").strs(),
            cardHeader = j.getJSONArray("cardHeader").strs(),
            cards = cards,
            dues = dues,
            history = history,
        )
    }
}
