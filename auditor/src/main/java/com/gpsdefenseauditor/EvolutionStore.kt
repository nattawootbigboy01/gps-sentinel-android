package com.gpsdefenseauditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object EvolutionStore {
    private const val PREFS = "mahathep_evolution"
    private const val SOURCE = "source_cases"
    private const val HISTORY = "cycle_history"
    private const val MAX_HISTORY = 50

    fun saveSourceCases(context: Context, cases: List<AuditCase>) {
        val array = JSONArray()
        cases.takeLast(200).forEach { item ->
            array.put(JSONObject().apply {
                put("name", item.name); put("expected", item.expected); put("actual", item.actual)
                put("score", item.score); put("passed", item.passed); put("findings", item.findings)
            })
        }
        prefs(context).edit().putString(SOURCE, array.toString()).apply()
    }

    fun sourceCases(context: Context): List<AuditCase> = runCatching {
        val array = JSONArray(prefs(context).getString(SOURCE, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { item ->
                AuditCase(item.optString("name"), item.optString("expected"), item.optString("actual"),
                    item.optInt("score"), item.optBoolean("passed"), item.optString("findings"))
            }
        }
    }.getOrDefault(emptyList())

    fun runAndSave(context: Context): EvolutionCycleReport {
        val report = MahathepEvolutionEngine.runCycle(sourceCases(context))
        val history = JSONArray()
        (listOf(report) + history(context)).take(MAX_HISTORY).forEach { history.put(encode(it)) }
        prefs(context).edit().putString(HISTORY, history.toString()).apply()
        return report
    }

    fun history(context: Context): List<EvolutionCycleReport> = runCatching {
        val array = JSONArray(prefs(context).getString(HISTORY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::decode) }
            .sortedByDescending { it.timestampMs }
    }.getOrDefault(emptyList())

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(report: EvolutionCycleReport) = JSONObject().apply {
        put("timestampMs", report.timestampMs)
        put("sourceCases", report.sourceCases)
        put("status", report.status)
        put("notes", report.notes)
        put("generatedCases", JSONArray().apply {
            report.generatedCases.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id); put("expected", item.expected)
                    put("findings", item.findings); put("reason", item.reason)
                })
            }
        })
        put("candidateRules", JSONArray().apply {
            report.candidateRules.forEach { item ->
                put(JSONObject().apply {
                    put("findingCode", item.findingCode); put("minimumSeverity", item.minimumSeverity)
                    put("evidenceCases", item.evidenceCases); put("status", item.status)
                })
            }
        })
        put("validations", JSONArray().apply {
            report.validations.forEach { item ->
                put(JSONObject().apply {
                    put("findingCode", item.findingCode); put("missedBefore", item.missedBefore)
                    put("missedAfter", item.missedAfter); put("falseAlarmsBefore", item.falseAlarmsBefore)
                    put("falseAlarmsAfter", item.falseAlarmsAfter); put("acceptedForReview", item.acceptedForReview)
                })
            }
        })
    }

    private fun decode(value: JSONObject): EvolutionCycleReport {
        val generated = value.optJSONArray("generatedCases") ?: JSONArray()
        val candidates = value.optJSONArray("candidateRules") ?: JSONArray()
        val validations = value.optJSONArray("validations") ?: JSONArray()
        return EvolutionCycleReport(
            timestampMs = value.optLong("timestampMs"),
            sourceCases = value.optInt("sourceCases"),
            generatedCases = (0 until generated.length()).mapNotNull { i ->
                generated.optJSONObject(i)?.let { GeneratedTestCase(
                    it.optString("id"), it.optString("expected"), it.optString("findings"), it.optString("reason")
                ) }
            },
            candidateRules = (0 until candidates.length()).mapNotNull { i ->
                candidates.optJSONObject(i)?.let { CandidateRule(
                    it.optString("findingCode"), it.optString("minimumSeverity"),
                    it.optInt("evidenceCases"), it.optString("status")
                ) }
            },
            validations = (0 until validations.length()).mapNotNull { i ->
                validations.optJSONObject(i)?.let { RuleValidation(
                    it.optString("findingCode"), it.optInt("missedBefore"), it.optInt("missedAfter"),
                    it.optInt("falseAlarmsBefore"), it.optInt("falseAlarmsAfter"),
                    it.optBoolean("acceptedForReview")
                ) }
            },
            status = value.optString("status"),
            notes = value.optString("notes")
        )
    }
}
