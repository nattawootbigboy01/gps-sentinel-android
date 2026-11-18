package com.gpssentinel

import android.content.Context
import android.location.Location
import android.provider.Settings

/**
 * Read-only audit of Android mock-location related signals.
 *
 * Android does not provide ordinary third-party apps with a reliable public API
 * to identify the currently selected mock-location provider package on modern
 * Android versions. This class deliberately reports only signals the app can
 * read without broad installed-app inventory access.
 */
object MockEnvironmentAudit {
    data class Report(
        val developerOptionsEnabled: Boolean,
        val legacyMockSettingEnabled: Boolean?,
        val latestFixIsMock: Boolean?,
        val summary: String
    )

    fun inspect(context: Context, latestLocation: Location?): Report {
        val resolver = context.contentResolver
        val developerOptions = readSetting {
            Settings.Global.getInt(
                resolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } ?: false

        // Legacy Android setting only. It is deprecated and is not a reliable
        // indicator of the selected mock provider on current Android releases.
        val legacyMockSetting = readSetting {
            Settings.Secure.getInt(
                resolver,
                Settings.Secure.ALLOW_MOCK_LOCATION,
                0
            ) == 1
        }

        val fixIsMock = latestLocation?.isFromMockProvider

        val summary = buildString {
            appendLine("ผลตรวจสภาพแวดล้อม Android")
            appendLine(
                "Developer Options: " +
                    if (developerOptions) "เปิดอยู่" else "ปิดอยู่/อ่านค่าไม่ได้"
            )
            appendLine(
                "ค่า Mock Location แบบเดิม: " +
                    when (legacyMockSetting) {
                        true -> "เปิด"
                        false -> "ปิด"
                        null -> "ไม่สามารถอ่านได้"
                    }
            )
            appendLine(
                "ตำแหน่งล่าสุด: " +
                    when (fixIsMock) {
                        true -> "Android ระบุว่าเป็น Mock Location"
                        false -> "Android ไม่ได้ระบุว่าเป็น Mock Location"
                        null -> "ยังไม่มีข้อมูลตำแหน่ง"
                    }
            )
            append(
                "ข้อจำกัด: Android รุ่นใหม่ไม่เปิด API สาธารณะที่เชื่อถือได้ " +
                    "ให้แอปทั่วไปตรวจชื่อแพ็กเกจที่ถูกเลือกเป็น Mock Location ได้โดยตรง " +
                    "ผลนี้จึงไม่ใช่การยืนยันว่ามีหรือไม่มีแอปจำลอง GPS ในเครื่อง"
            )
        }

        return Report(developerOptions, legacyMockSetting, fixIsMock, summary)
    }

    private inline fun readSetting(block: () -> Boolean): Boolean? =
        try {
            block()
        } catch (_: Exception) {
            null
        }
}
