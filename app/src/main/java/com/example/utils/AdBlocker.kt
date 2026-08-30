package com.example.utils

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Free, lightweight, and high-performance Ad Blocker for WebView.
 * Intercepts requests to known ad networks, trackers, popups, and intrusive scripts.
 * Also provides CSS / JS element hiding rules to keep page layout clean and ad-free.
 */
object AdBlocker {

    var isEnabled: Boolean = true
    val blockedCount = AtomicInteger(0)

    // Common ad, tracker, popup and banner domains list
    private val AD_DOMAINS: Set<String> = Collections.unmodifiableSet(
        setOf(
            // Google Ads / DoubleClick / Syndication
            "doubleclick.net",
            "adservice.google.com",
            "googleadservices.com",
            "googlesyndication.com",
            "pagead2.googlesyndication.com",
            "pagead.l.doubleclick.net",
            "ads.google.com",
            "adwords.google.com",
            "stats.g.doubleclick.net",
            
            // Popular Ad Networks & Exchanges
            "adnxs.com",
            "ib.adnxs.com",
            "rubiconproject.com",
            "openx.net",
            "pubmatic.com",
            "casalemedia.com",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "adcash.com",
            "adroll.com",
            "infolinks.com",
            "mgid.com",
            "revcontent.com",
            "zergnet.com",
            "exponential.com",
            "tribalfusion.com",
            "media.net",
            "smartadserver.com",
            "bidswitch.net",
            "sharethrough.com",
            "yieldmo.com",
            "sovrn.com",
            "lijit.com",
            "adtech.de",
            "adtechus.com",
            "applovin.com",
            "unityads.unity3d.com",
            "ironsrc.com",
            "vungle.com",
            "chartboost.com",
            "mopub.com",
            "fyber.com",
            "inmobi.com",
            "adcolony.com",
            "smaato.net",
            "indexexchange.com",
            "triplelift.com",
            "teads.tv",
            "moatads.com",
            "iasds01.com",
            "scorecardresearch.com",
            "quantserve.com",
            "ad-delivery.net",
            "trafficjunky.com",
            "exoclick.com",
            "juicyads.com",
            "clickadu.com",
            "admaven.com",
            "hilltopads.com",
            "richaudience.com",
            "gumgum.com",
            "seedtag.com",
            "connatix.com",
            "spotxchange.com",
            "kargo.com",
            "undertone.com",
            "adblade.com",
            "bidvertiser.com",
            "adsupply.com",
            "yllix.com",
            "adf.ly",
            "shorte.st",
            "adtrue.com",
            "adsterra.com",
            "monetag.com"
        )
    )

    // Suspicious path or keyword signatures in ad URLs
    private val AD_URL_KEYWORDS = arrayOf(
        "/ads.js",
        "/pagead/",
        "/adserver/",
        "/adservice/",
        "/ad_frame",
        "ad_type=",
        "/popunder",
        "/adbanner",
        "/ads/banner",
        "ad_unit=",
        "ad_slot=",
        "doubleclick",
        "googleads",
        "adservice",
        "/advertisement/",
        "partner.googleadservices"
    )

    /**
     * Checks if a URL belongs to an ad or tracker network.
     */
    fun isAd(url: String?): Boolean {
        if (!isEnabled || url.isNullOrBlank()) return false

        try {
            val lower = url.lowercase()
            val uri = URI(lower)
            val host = uri.host ?: ""

            // Domain matching
            for (adDomain in AD_DOMAINS) {
                if (host == adDomain || host.endsWith(".$adDomain")) {
                    blockedCount.incrementAndGet()
                    return true
                }
            }

            // Keyword pattern matching for scripts/frames
            for (keyword in AD_URL_KEYWORDS) {
                if (lower.contains(keyword)) {
                    blockedCount.incrementAndGet()
                    return true
                }
            }
        } catch (e: Exception) {
            // ignore URI parsing errors for blob/data schemes
        }

        return false
    }

    /**
     * Creates an empty WebResourceResponse to block the ad network request immediately.
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Content-Type" to "text/plain; charset=UTF-8"
            ),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * JavaScript and CSS injection to hide ad containers, popups, and sponsored cards cleanly.
     */
    const val AD_BLOCK_CSS_JS = """
        (function() {
            try {
                var adSelectors = [
                    'ins.adsbygoogle',
                    '[id^="google_ads_"]',
                    '[id^="div-gpt-ad"]',
                    '.adsbygoogle',
                    '.ad-banner',
                    '.ad-container',
                    '.ad-slot',
                    '.advertisement',
                    '.ad-wrapper',
                    '.sponsor-post',
                    '.taboola-ad',
                    '.outbrain-ad',
                    '#taboola-below-article-thumbnails',
                    '#outbrain_widget_0',
                    'iframe[src*="doubleclick.net"]',
                    'iframe[src*="googlesyndication.com"]',
                    'iframe[src*="adnxs.com"]',
                    'iframe[src*="adservice.google"]',
                    '.popunder',
                    '.popup-overlay-ad',
                    '.interstitial-ad'
                ];

                var styleId = 'nexaflow-adblock-styles';
                if (!document.getElementById(styleId)) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.innerHTML = adSelectors.join(', ') + ' { display: none !important; width: 0 !important; height: 0 !important; visibility: hidden !important; pointer-events: none !important; opacity: 0 !important; }';
                    (document.head || document.documentElement).appendChild(style);
                }

                // Prevent common popup ads
                if (window.open && !window._origOpen) {
                    window._origOpen = window.open;
                    window.open = function(url, target, features) {
                        if (url && (url.indexOf('ad') !== -1 || url.indexOf('pop') !== -1 || url.indexOf('click') !== -1)) {
                            console.log('Blocked popup window:', url);
                            return null;
                        }
                        return window._origOpen.apply(this, arguments);
                    };
                }
            } catch(e) {}
        })();
    """
}
