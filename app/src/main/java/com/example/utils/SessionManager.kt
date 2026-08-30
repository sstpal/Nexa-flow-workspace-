package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import com.example.data.AppDatabase
import com.example.data.SessionCookie
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionManager {
    private const val TAG = "SessionManager"

    @Volatile
    var currentActiveProfileId: Int? = null

    // Known multi-domain ecosystem hubs for single-sign-on (SSO) & auth sharing
    private val GOOGLE_AUTH_DOMAINS = listOf(
        "google.com",
        "accounts.google.com",
        "myaccount.google.com",
        "labs.google",
        "aitestkitchen.withgoogle.com",
        "experiments.withgoogle.com",
        "apis.google.com",
        "googleusercontent.com",
        "ogs.google.com",
        "youtube.com",
        "studio.youtube.com"
    )

    private val OPENAI_AUTH_DOMAINS = listOf(
        "openai.com",
        "chatgpt.com",
        "auth0.openai.com",
        "auth.openai.com",
        "platform.openai.com"
    )

    /**
     * Isolates WebView cookies for the target profile.
     * Extracts and saves cookies from the previous profile,
     * clears CookieManager, then injects all cookies belonging to the target profile.
     */
    suspend fun isolateAndSwitchProfile(
        context: Context,
        targetProfileId: Int,
        activeUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                val previousId = currentActiveProfileId
                if (previousId != null && previousId != targetProfileId) {
                    extractAndSaveAllSessionCookies(context, previousId, activeUrl)
                }
                currentActiveProfileId = targetProfileId
                return@withContext true
            }
            val previousId = currentActiveProfileId
            if (previousId != null && previousId != targetProfileId) {
                extractAndSaveAllSessionCookies(context, previousId, activeUrl)
            }

            // Wipe CookieManager memory & WebStorage (IndexedDB / LocalStorage) so sessions do not bleed
            val deferred = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    
                    // Clear WebStorage (IndexedDB, LocalStorage) across origins
                    android.webkit.WebStorage.getInstance().deleteAllData()

                    cookieManager.removeSessionCookies { }
                    cookieManager.removeAllCookies {
                        cookieManager.flush()
                        deferred.complete(true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting web storage/cookies on main thread", e)
                    deferred.complete(true)
                }
            }
            deferred.await()

            currentActiveProfileId = targetProfileId

            // Inject all stored cookies for the target profile
            val injected = injectAllCookiesForProfile(context, targetProfileId)
            Log.d(TAG, "Profile $targetProfileId isolated successfully ($injected domain cookies loaded)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to isolate profile $targetProfileId", e)
            false
        }
    }

    /**
     * Extracts and saves all session cookies for the active domain as well as
     * any related SSO / OAuth auth hubs (e.g., Google Labs + accounts.google.com + .google.com).
     */
    suspend fun extractAndSaveAllSessionCookies(
        context: Context,
        profileId: Int,
        url: String? = null
    ): List<SessionCookie> = withContext(Dispatchers.IO) {
        val saved = mutableListOf<SessionCookie>()
        try {
            val cookieManager = if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                val profileStore = androidx.webkit.ProfileStore.getInstance()
                val webkitProfile = profileStore.getProfile("profile_${profileId}")
                webkitProfile?.cookieManager ?: android.webkit.CookieManager.getInstance()
            } else {
                android.webkit.CookieManager.getInstance()
            }
            cookieManager.flush()

            val domainsToInspect = mutableSetOf<String>()
            if (!url.isNullOrBlank() && url.startsWith("http")) {
                val host = extractDomain(url)
                if (host.isNotBlank()) {
                    domainsToInspect.add(host)
                    val root = extractRootDomain(host)
                    if (root.isNotBlank()) domainsToInspect.add(root)
                }
            }

            // If any domain is Google-related, inspect all Google SSO domains
            val isGoogle = domainsToInspect.any { it.contains("google") || it == "labs.google" || it.contains("youtube") }
            if (isGoogle || url?.contains("google") == true) {
                domainsToInspect.addAll(GOOGLE_AUTH_DOMAINS)
            }

            // If OpenAI related
            val isOpenAI = domainsToInspect.any { it.contains("openai") || it.contains("chatgpt") }
            if (isOpenAI || url?.contains("openai") == true) {
                domainsToInspect.addAll(OPENAI_AUTH_DOMAINS)
            }

            val db = AppDatabase.getDatabase(context)
            val cookieDao = db.sessionCookieDao()

            for (domain in domainsToInspect) {
                val cookieList = mutableSetOf<String>()
                val probes = listOf(
                    "https://$domain",
                    "https://www.$domain",
                    "https://m.$domain",
                    "https://.$domain",
                    "http://$domain"
                )

                for (probe in probes) {
                    val raw = cookieManager.getCookie(probe)
                    if (!raw.isNullOrBlank()) {
                        raw.split(";").forEach { item ->
                            val trimmed = item.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieList.add(trimmed)
                            }
                        }
                    }
                }

                if (cookieList.isNotEmpty()) {
                    val consolidatedCookies = cookieList.joinToString("; ")
                    val existing = cookieDao.getCookieForDomain(profileId, domain)
                    val sessionCookie = SessionCookie(
                        id = existing?.id ?: 0,
                        profileId = profileId,
                        domain = domain,
                        cookieString = consolidatedCookies,
                        lastUpdated = System.currentTimeMillis(),
                        isAutoLoginActive = true
                    )
                    cookieDao.insertOrUpdate(sessionCookie)
                    saved.add(sessionCookie)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting all session cookies for profile $profileId", e)
        }
        saved
    }

    /**
     * Extracts session cookies from WebView CookieManager for the active URL
     * and persists them in the Room database associated with the profile.
     */
    suspend fun extractAndSaveCookies(
        context: Context,
        profileId: Int,
        url: String
    ): SessionCookie? = withContext(Dispatchers.IO) {
        val list = extractAndSaveAllSessionCookies(context, profileId, url)
        val domain = extractDomain(url)
        list.find { it.domain.equals(domain, ignoreCase = true) } ?: list.firstOrNull()
    }

    /**
     * Injects saved session cookies from Room database into WebView's CookieManager
     * for a given profile and URL.
     */
    suspend fun injectCookiesForUrl(
        context: Context,
        profileId: Int,
        url: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank() || !url.startsWith("http")) return@withContext false

        val domain = extractDomain(url)
        if (domain.isBlank()) return@withContext false

        try {
            val db = AppDatabase.getDatabase(context)
            val cookieDao = db.sessionCookieDao()
            val cookie = cookieDao.getCookieForDomain(profileId, domain)

            val isGoogle = domain.contains("google") || domain == "labs.google" || domain.contains("youtube")
            val cookiesToInject = mutableListOf<SessionCookie>()
            if (cookie != null && cookie.cookieString.isNotBlank() && cookie.isAutoLoginActive) {
                cookiesToInject.add(cookie)
            }

            if (isGoogle) {
                // Also load google root & accounts cookies
                for (gDomain in GOOGLE_AUTH_DOMAINS) {
                    if (gDomain != domain) {
                        val gCookie = cookieDao.getCookieForDomain(profileId, gDomain)
                        if (gCookie != null && gCookie.cookieString.isNotBlank() && gCookie.isAutoLoginActive) {
                            cookiesToInject.add(gCookie)
                        }
                    }
                }
            }

            if (cookiesToInject.isNotEmpty()) {
                val cookieManager = if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                    val profileStore = androidx.webkit.ProfileStore.getInstance()
                    val webkitProfile = profileStore.getProfile("profile_${profileId}")
                    webkitProfile?.cookieManager ?: android.webkit.CookieManager.getInstance()
                } else {
                    android.webkit.CookieManager.getInstance()
                }
                cookieManager.setAcceptCookie(true)

                for (sc in cookiesToInject) {
                    injectSingleCookieEntry(cookieManager, sc.domain, sc.cookieString)
                }
                cookieManager.flush()
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting cookies for $url", e)
        }
        return@withContext false
    }

    /**
     * Injects all stored cookies for a profile across all domains into WebView CookieManager.
     */
    suspend fun injectAllCookiesForProfile(
        context: Context,
        profileId: Int
    ): Int = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val cookies = db.sessionCookieDao().getCookiesForProfileSync(profileId)

            val cookieManager = if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                val profileStore = androidx.webkit.ProfileStore.getInstance()
                val webkitProfile = profileStore.getProfile("profile_${profileId}")
                webkitProfile?.cookieManager ?: android.webkit.CookieManager.getInstance()
            } else {
                android.webkit.CookieManager.getInstance()
            }
            cookieManager.setAcceptCookie(true)

            var injectedCount = 0
            for (cookie in cookies) {
                if (cookie.cookieString.isNotBlank() && cookie.isAutoLoginActive) {
                    injectSingleCookieEntry(cookieManager, cookie.domain, cookie.cookieString)
                    injectedCount++
                }
            }
            cookieManager.flush()
            Log.d(TAG, "Injected cookies for $injectedCount domains for Profile $profileId")
            return@withContext injectedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting all cookies for profile $profileId", e)
            return@withContext 0
        }
    }

    private fun injectSingleCookieEntry(cookieManager: CookieManager, domain: String, cookieString: String) {
        val cleanDomain = if (domain.startsWith(".")) domain.substring(1) else domain
        val target = "https://$cleanDomain"

        val individualCookies = cookieString.split(";")
        for (indCookie in individualCookies) {
            val trimmed = indCookie.trim()
            if (trimmed.isNotEmpty()) {
                // Set the cookie only once with proper domain and path to prevent duplicate headers
                cookieManager.setCookie(target, "$trimmed; Domain=.$cleanDomain; Path=/; Secure")
            }
        }
    }

    /**
     * Extracts standard hostname from URL string.
     */
    fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extracts root domain (e.g. accounts.google.com -> google.com, labs.google -> google.com / labs.google)
     */
    fun extractRootDomain(host: String): String {
        val parts = host.split(".")
        return if (parts.size >= 2) {
            parts.takeLast(2).joinToString(".")
        } else {
            host
        }
    }
}
