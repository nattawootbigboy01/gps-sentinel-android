package com.gpsdefenseauditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local journal for evidence-led research cycles. */
object AutonomousResearchStore {
    private const val PREFS = "mahathep_autonomous_research"
    private const val KEY_REPORTS = "reports"
    private const val MAX_REPORTS = 50

    fun history(context: Context): List<ResearchCycleReport> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPORTS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::decode)
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun run(context: Context, cases: List<AuditCase>): ResearchCycleReport {
        val report = AutonomousResearchEngine.analyze(cases)
        val json = JSONArray()
        (listOf(report) + history(context)).take(MAX_REPORTS).forEach { json.put(encode(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPORTS, json.toString()).apply()
        return report
    }

    private fun encode(report: ResearchCycleReport) = JSONObject().apply {
        put("createdAtMs", report.createdAtMs)
        put("casesReviewed", report.casesReviewed)
        put("evidenceCount", report.evidenceCount)
        put("topicsCovered", JSONArray(report.topicsCovered))
        put("hypotheses", JSONArray().apply {
            report.hypotheses.forEach { item ->
                put(JSONObject().apply {
                    put("title", item.title)
                    put("evidence", item.evidence)
                    put("confidence", item.confidence)
                    put("safeExperiment", item.safeExperiment)
                    put("defensiveAction", item.defensiveAction)
                })
            }
        })
        put("limitations", report.limitations)
    }

    private fun decode(item: JSONObject): ResearchCycleReport {
        val topics = item.optJSONArray("topicsCovered") ?: JSONArray()
        val hypotheses = item.optJSONArray("hypotheses") ?: JSONArray()
        return ResearchCycleReport(
            createdAtMs = item.optLong("createdAtMs"),
            casesReviewed = item.optInt("casesReviewed"),
            evidenceCount = item.optInt("evidenceCount"),
            topicsCovered = (0 until topics.length()).map { topics.optString(it) },
            hypotheses = (0 until hypotheses.length()).mapNotNull { index ->
                hypotheses.optJSONObject(index)?.let { value ->
                    ResearchHypothesis(
                        title = value.optString("title"),
                        evidence = value.optString("evidence"),
                        confidence = value.optString("confidence"),
                        safeExperiment = value.optString("safeExperiment"),
                        defensiveAction = value.optString("defensiveAction")
                    )
                }
            },
            limitations = item.optString("limitations")
        )
    }
}
