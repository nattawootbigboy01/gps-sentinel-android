package com.gpsdefenseauditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SecurityCoreStore {
    private const val PREFS = "mahathep_security_core"
    private const val KEY_HISTORY = "history"
    private const val MAX_RUNS = 50

    fun history(context: Context): List<CoreSelfTestReport> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let(::decode)
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun run(context: Context): CoreSelfTestReport {
        val report = MahathepSecurityCore.runOfflineSelfTest()
        val json = JSONArray()
        (listOf(report) + history(context)).take(MAX_RUNS).forEach { json.put(encode(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, json.toString()).apply()
        return report
    }

    private fun encode(report: CoreSelfTestReport) = JSONObject().apply {
        put("createdAtMs", report.createdAtMs)
        put("passed", report.passed)
        put("total", report.total)
        put("checks", JSONArray().apply {
            report.checks.forEach { check ->
                put(JSONObject().apply {
                    put("id", check.id)
                    put("expected", check.expected)
                    put("actual", check.actual)
                    put("passed", check.passed)
                    put("detail", check.detail)
                })
            }
        })
    }

    private fun decode(value: JSONObject): CoreSelfTestReport {
        val checks = value.optJSONArray("checks") ?: JSONArray()
        val results = (0 until checks.length()).mapNotNull { i ->
            checks.optJSONObject(i)?.let { item ->
                CoreCheckResult(
                    id = item.optString("id"),
                    expected = item.optString("expected"),
                    actual = item.optString("actual"),
                    passed = item.optBoolean("passed"),
                    detail = item.optString("detail")
                )
            }
        }
        return CoreSelfTestReport(
            createdAtMs = value.optLong("createdAtMs"),
            checks = results,
            passed = value.optInt("passed"),
            total = value.optInt("total")
        )
    }
}
