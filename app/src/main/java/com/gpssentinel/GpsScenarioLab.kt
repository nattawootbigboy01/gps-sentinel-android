package com.gpssentinel

data class ScenarioResult(
    val name: String,
    val expected: RiskLevel,
    val report: GpsRiskReport,
    val passed: Boolean
)

/**
 * Offline adversarial fixtures for validating our own risk engine.
 * These samples are never injected into Android LocationManager or other apps.
 */
object GpsScenarioLab {
    fun runAll(): List<ScenarioResult> {
        val engine = GpsRiskEngine()
        val t = 120_000_000_000L
        val fixtures = listOf(
            Triple("การเดินทางปกติ", RiskLevel.LOW,
                { engine.evaluate(LocationSample(16.46005, 102.82000, t + 5_000_000_000L, 8f, 2f),
                    LocationSample(16.46000, 102.82000, t, 8f, 2f)) }),
            Triple("ข้อมูลระบุ Mock Provider", RiskLevel.HIGH,
                { engine.evaluate(LocationSample(16.46, 102.82, t, 5f, 0f, true)) }),
            Triple("ตำแหน่งกระโดดไกลในเวลาสั้น", RiskLevel.REVIEW,
                { engine.evaluate(LocationSample(16.47000, 102.82000, t + 1_000_000_000L),
                    LocationSample(16.46000, 102.82000, t)) }),
            Triple("ข้อมูลตำแหน่งเก่า", RiskLevel.REVIEW,
                { engine.evaluate(LocationSample(16.46, 102.82, t),
                    evaluationElapsedRealtimeNanos = t + 60_000_000_000L) }),
            Triple("เวลาอ้างอิงอยู่ในอนาคต", RiskLevel.REVIEW,
                { engine.evaluate(LocationSample(16.46, 102.82, t + 10_000_000_000L),
                    evaluationElapsedRealtimeNanos = t) }),
            Triple("ค่าความแม่นยำต่ำ", RiskLevel.LOW,
                { engine.evaluate(LocationSample(16.46, 102.82, t, 150f)) }),
            Triple("เวลา GPS ซ้ำหรือย้อนกลับ", RiskLevel.REVIEW,
                { engine.evaluate(LocationSample(16.46001, 102.82, t),
                    LocationSample(16.46, 102.82, t)) }),
            Triple("ความเร็วรายงานไม่ตรงกับระยะทาง", RiskLevel.REVIEW,
                { engine.evaluate(LocationSample(16.46050, 102.82, t + 5_000_000_000L, 5f, 40f),
                    LocationSample(16.46000, 102.82, t, 5f, 1f)) })
        )
        return fixtures.map { (name, expected, evaluate) ->
            val report = evaluate()
            ScenarioResult(name, expected, report, report.level == expected)
        }
    }
}
