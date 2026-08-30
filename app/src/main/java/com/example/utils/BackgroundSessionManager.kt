package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf

@SuppressLint("StaticFieldLeak")
object BackgroundSessionManager {

    // Map of profileId -> alive background/foreground WebView
    private val profileWebViews = mutableMapOf<Int, WebView>()
    
    // Observable state map for Compose UI indicating active background tasks
    val activeTaskProfiles = mutableStateMapOf<Int, Boolean>()
    
    var globalBackgroundTasksEnabled = mutableStateOf(true)

    fun setGlobalBackgroundEnabled(enabled: Boolean) {
        globalBackgroundTasksEnabled.value = enabled
        if (!enabled) {
            stopAllSessions()
        }
    }

    fun registerSession(profileId: Int, webView: WebView, isBackgroundEnabled: Boolean, context: Context? = null) {
        // Safely detach from any previous parent view
        (webView.parent as? ViewGroup)?.removeView(webView)
        
        profileWebViews[profileId] = webView
        if (isBackgroundEnabled && globalBackgroundTasksEnabled.value) {
            activeTaskProfiles[profileId] = true
            // Resume timers so background generations continue
            webView.resumeTimers()
            context?.let { ctx ->
                BackgroundProfileService.start(ctx, activeTaskProfiles.size)
            }
        }
    }

    fun resumeAllTimers() {
        profileWebViews.values.forEach { wv ->
            try {
                wv.resumeTimers()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onAppBackgrounded(context: Context) {
        if (globalBackgroundTasksEnabled.value && profileWebViews.isNotEmpty()) {
            resumeAllTimers()
            BackgroundProfileService.start(context, profileWebViews.size)
        }
    }

    fun onAppForegrounded(context: Context) {
        resumeAllTimers()
    }

    fun getSession(profileId: Int): WebView? {
        val wv = profileWebViews[profileId]
        if (wv != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.resumeTimers()
        }
        return wv
    }

    fun hasActiveSession(profileId: Int): Boolean {
        return profileWebViews.containsKey(profileId)
    }

    fun removeSession(profileId: Int, context: Context? = null) {
        profileWebViews[profileId]?.let { wv ->
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                    val profileStore = androidx.webkit.ProfileStore.getInstance()
                    val webkitProfile = profileStore.getProfile("profile_${profileId}")
                    webkitProfile?.cookieManager?.flush()
                } else {
                    android.webkit.CookieManager.getInstance().flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        profileWebViews.remove(profileId)
        activeTaskProfiles.remove(profileId)
        if (profileWebViews.isEmpty()) {
            context?.let { BackgroundProfileService.stop(it) }
        }
    }

    fun stopSession(profileId: Int, context: Context? = null) {
        removeSession(profileId, context)
    }

    fun stopAllSessions(context: Context? = null) {
        profileWebViews.forEach { (profileId, wv) ->
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                    val profileStore = androidx.webkit.ProfileStore.getInstance()
                    val webkitProfile = profileStore.getProfile("profile_${profileId}")
                    webkitProfile?.cookieManager?.flush()
                } else {
                    android.webkit.CookieManager.getInstance().flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        profileWebViews.clear()
        activeTaskProfiles.clear()
        context?.let { BackgroundProfileService.stop(it) }
    }

    fun clearAll() {
        stopAllSessions()
    }

    fun flushAllCookies() {
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                val profileStore = androidx.webkit.ProfileStore.getInstance()
                profileWebViews.keys.forEach { profileId ->
                    val webkitProfile = profileStore.getProfile("profile_$profileId")
                    webkitProfile?.cookieManager?.flush()
                }
            }
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRunningTaskCount(): Int = profileWebViews.size
}

