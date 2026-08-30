#!/bin/bash
cat << 'INNER_EOF' > /tmp/SessionManagerExt.kt
    suspend fun exportCookiesToJson(context: Context, uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val allCookies = db.sessionCookieDao().getAllCookiesSync()
            val jsonArray = org.json.JSONArray()
            for (cookie in allCookies) {
                val jsonObj = org.json.JSONObject()
                jsonObj.put("profileId", cookie.profileId)
                jsonObj.put("domain", cookie.domain)
                jsonObj.put("cookieString", cookie.cookieString)
                jsonObj.put("lastUpdated", cookie.lastUpdated)
                jsonObj.put("isAutoLoginActive", cookie.isAutoLoginActive)
                jsonArray.put(jsonObj)
            }
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonArray.toString(2).toByteArray())
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun importCookiesFromJson(context: Context, uri: android.net.Uri): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
            if (!jsonString.isNullOrBlank()) {
                val jsonArray = org.json.JSONArray(jsonString)
                val db = AppDatabase.getDatabase(context)
                val dao = db.sessionCookieDao()
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val cookie = SessionCookie(
                        profileId = jsonObj.optInt("profileId"),
                        domain = jsonObj.optString("domain"),
                        cookieString = jsonObj.optString("cookieString"),
                        lastUpdated = jsonObj.optLong("lastUpdated", System.currentTimeMillis()),
                        isAutoLoginActive = jsonObj.optBoolean("isAutoLoginActive", true)
                    )
                    dao.insertOrUpdate(cookie)
                    importedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext importedCount
    }
INNER_EOF
sed -i -e '/fun extractRootDomain/r /tmp/SessionManagerExt.kt' app/src/main/java/com/example/utils/SessionManager.kt
