#!/bin/bash
cat << 'INNER_EOF' > /tmp/bg.kt
    fun registerSession(profileId: Int, webView: WebView, isBackgroundEnabled: Boolean, context: Context? = null) {
        // Safely detach from any previous parent view
        (webView.parent as? ViewGroup)?.removeView(webView)
        
        if (isBackgroundEnabled && globalBackgroundTasksEnabled.value) {
            profileWebViews[profileId] = webView
            activeTaskProfiles[profileId] = true
            // Resume timers so background generations continue
            webView.resumeTimers()
            context?.let { ctx ->
                BackgroundProfileService.start(ctx, activeTaskProfiles.size)
            }
        } else {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (e: Exception) {}
            profileWebViews.remove(profileId)
        }
    }
INNER_EOF

# Replace registerSession
awk '
/fun registerSession/ {
    in_func=1
    system("cat /tmp/bg.kt")
    next
}
in_func && /fun resumeAllTimers/ {
    in_func=0
    print $0
    next
}
!in_func { print $0 }
' app/src/main/java/com/example/utils/BackgroundSessionManager.kt > /tmp/out.kt

mv /tmp/out.kt app/src/main/java/com/example/utils/BackgroundSessionManager.kt

