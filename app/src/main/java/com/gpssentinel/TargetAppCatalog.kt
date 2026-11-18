package com.gpssentinel
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
data class TargetApp(val packageName: String, val label: String, val versionName: String)
object TargetAppCatalog {
 private const val PREFS = "mahathep_target_app"
 fun load(context: Context): List<TargetApp> {
  val pm = context.packageManager
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { item ->
   val pkg = item.activityInfo?.packageName ?: return@mapNotNull null
   if (pkg == context.packageName || pkg == "com.gpsdefenseauditor") return@mapNotNull null
   val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return@mapNotNull null }
   val version = try { pm.getPackageInfo(pkg, 0).versionName ?: "ไม่ระบุ" } catch (_: Exception) { "ไม่ระบุ" }
   TargetApp(pkg, pm.getApplicationLabel(info).toString(), version)
  }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
 }
 fun restore(context: Context): TargetApp? {
  val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  val pkg = p.getString("package", null) ?: return null
  return TargetApp(pkg, p.getString("label", pkg) ?: pkg, p.getString("version", "ไม่ระบุ") ?: "ไม่ระบุ")
 }
 fun save(context: Context, app: TargetApp) {
  context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("package", app.packageName).putString("label", app.label).putString("version", app.versionName).apply()
 }
 fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
}