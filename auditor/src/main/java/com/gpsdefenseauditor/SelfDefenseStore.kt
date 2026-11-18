package com.gpsdefenseauditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SelfDefenseRun(
    val id: String,
    val timestampMs: Long,
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val summary: String
)

/** Local-first self-test journal. Synthetic checks only; no external attacks or automatic rule changes. */
object SelfDefenseStore {
    private const val PREFS = "mahathep_self_defense"
    private const val KEY_RUNS = "runs"
    private const val MAX_RUNS = 100

    fun history(context: Context): List<SelfDefenseRun> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RUNS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SelfDefenseRun(
                    id = item.optString("id"),
                    timestampMs = item.optLong("timestampMs"),
                    passed = item.optBoolean("passed"),
                    checksPassed = item.optInt("checksPassed"),
                    checksTotal = item.optInt("checksTotal"),
                    summary = item.optString("summary")
                )
            }.sortedByDescending { it.timestampMs }
        }.getOrDefault(emptyList())
    }

    fun runSelfTest(context: Context): SelfDefenseRun {
        val synthetic = listOf(
            AuditCase("SELF_TEST_MISSED_HIGH", "HIGH", "LOW", 0, false, "SYNTHETIC_MISSED"),
            AuditCase("SELF_TEST_FALSE_ALARM", "LOW", "HIGH", 0, false, "SYNTHETIC_FALSE_ALARM"),
            AuditCase("SELF_TEST_NORMAL", "LOW", "LOW", 100, true, "")
        )
        val assessment = AdaptiveDefenseEngine.analyze(synthetic)
        val checks = listOf(
            "วิเคราะห์จำนวนกรณีทดสอบ" to (assessment.analyzedCases == 3),
            "ตรวจพบการพลาดระดับ HIGH" to (assessment.missedDetections == 1),
            "ตรวจพบการแจ้งเตือนเกิน" to (assessment.falseAlarms == 1),
            "สร้างคำแนะนำป้องกัน" to assessment.recommendations.isNotEmpty()
        )
        val passed = checks.count { it.second }
        val now = System.currentTimeMillis()
        val run = SelfDefenseRun(
            id = "SELF-$now",
            timestampMs = now,
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            summary = checks.joinToString(" • ") { (name, ok) -> "$name: ${if (ok) "ผ่าน" else "ไม่ผ่าน"}" }
        )
        val json = JSONArray()
        (listOf(run) + history(context)).take(MAX_RUNS).forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("timestampMs", item.timestampMs)
                put("passed", item.passed)
                put("checksPassed", item.checksPassed)
                put("checksTotal", item.checksTotal)
                put("summary", item.summary)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RUNS, json.toString()).apply()
        return run
    }
}
