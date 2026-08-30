import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewCompat
import android.webkit.WebView

fun test(webView: WebView) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        val store = ProfileStore.getInstance()
        val profile = store.getOrCreateProfile("test")
        WebViewCompat.setProfile(webView, profile.name)
    }
}
