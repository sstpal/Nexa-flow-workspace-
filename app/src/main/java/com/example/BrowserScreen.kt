package com.example
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.Bookmark
import com.example.data.DownloadItem
import com.example.data.Profile
import com.example.data.SessionCookie
import com.example.ui.components.DownloadsDialog
import com.example.ui.theme.AppThemeMode
import com.example.utils.AdBlocker
import com.example.utils.BackgroundSessionManager
import com.example.utils.DownloadHelper
import com.example.utils.SessionManager
import com.example.utils.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_INJECTION_SCRIPT = """
    (function() {
        if (window.location.hostname.includes('google.') || window.location.hostname.includes('youtube.')) return;
        try {
            // 1. Spoof Navigator Platform & Device Properties
            Object.defineProperty(navigator, 'platform', { get: () => 'Win32', configurable: true });
            Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0, configurable: true });
            Object.defineProperty(navigator, 'userAgent', { get: () => '$DESKTOP_USER_AGENT', configurable: true });
            Object.defineProperty(navigator, 'appVersion', { get: () => '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36', configurable: true });

            // 2. High-Entropy Client Hints for Chromium / Google Services
            var uad = {
                brands: [
                    { brand: 'Google Chrome', version: '131' },
                    { brand: 'Chromium', version: '131' },
                    { brand: 'Not_A Brand', version: '24' }
                ],
                mobile: false,
                platform: 'Windows',
                getHighEntropyValues: function(hints) {
                    return Promise.resolve({
                        architecture: 'x86',
                        bitness: '64',
                        brands: [
                            { brand: 'Google Chrome', version: '131' },
                            { brand: 'Chromium', version: '131' },
                            { brand: 'Not_A Brand', version: '24' }
                        ],
                        fullVersionList: [
                            { brand: 'Google Chrome', version: '131.0.6778.86' },
                            { brand: 'Chromium', version: '131.0.6778.86' },
                            { brand: 'Not_A Brand', version: '24.0.0.0' }
                        ],
                        mobile: false,
                        model: '',
                        platform: 'Windows',
                        platformVersion: '15.0.0',
                        wow64: false
                    });
                },
                toJSON: function() {
                    return {
                        brands: this.brands,
                        mobile: false,
                        platform: 'Windows'
                    };
                }
            };
            try {
                Object.defineProperty(navigator, 'userAgentData', {
                    get: () => uad,
                    configurable: true
                });
            } catch(e) {}

            // 3. Spoof Screen Dimensions for Desktop AI Agent Layouts (e.g. Google Flow)
            try {
                Object.defineProperty(screen, 'width', { get: () => 1920, configurable: true });
                Object.defineProperty(screen, 'availWidth', { get: () => 1920, configurable: true });
                Object.defineProperty(screen, 'height', { get: () => 1080, configurable: true });
                Object.defineProperty(screen, 'availHeight', { get: () => 1040, configurable: true });
                Object.defineProperty(screen, 'colorDepth', { get: () => 24, configurable: true });
                Object.defineProperty(screen, 'pixelDepth', { get: () => 24, configurable: true });
            } catch(e) {}

            // 4. Spoof CSS Media Queries for Fine Pointer, Hover & Desktop Breakpoints
            try {
                var origMatchMedia = window.matchMedia;
                window.matchMedia = function(query) {
                    if (!query) return origMatchMedia.call(window, query);
                    var q = query.toLowerCase();
                    if (q.includes('pointer: fine') || q.includes('hover: hover') || q.includes('any-pointer: fine') || q.includes('any-hover: hover')) {
                        return {
                            matches: true,
                            media: query,
                            onchange: null,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; }
                        };
                    }
                    if (q.includes('pointer: coarse') || q.includes('hover: none')) {
                        return {
                            matches: false,
                            media: query,
                            onchange: null,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; }
                        };
                    }
                    if (q.includes('min-width: 1024') || q.includes('min-width: 1200') || q.includes('min-width: 1280') || q.includes('min-width: 1366') || q.includes('min-width: 1440')) {
                        return {
                            matches: true,
                            media: query,
                            onchange: null,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; }
                        };
                    }
                    if (q.includes('max-width: 768') || q.includes('max-width: 800') || q.includes('max-width: 1023')) {
                        return {
                            matches: false,
                            media: query,
                            onchange: null,
                            addListener: function() {},
                            removeListener: function() {},
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; }
                        };
                    }
                    return origMatchMedia.call(window, query);
                };
            } catch(e) {}

            // 5. Enforce Desktop Viewport & Observe Dynamic SPA Changes
            function applyDesktopViewport() {
                try {
                    var metas = document.querySelectorAll('meta[name="viewport"]');
                    metas.forEach(function(m) { m.remove(); });
                    var meta = document.createElement('meta');
                    meta.name = 'viewport';
                    meta.content = 'width=1440, initial-scale=0.75, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes';
                    if (document.head) {
                        document.head.appendChild(meta);
                    }
                } catch(e) {}
            }

            applyDesktopViewport();

            if (window.MutationObserver && document.head) {
                var observer = new MutationObserver(function(mutations) {
                    var needsFix = false;
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.tagName === 'META' && node.name === 'viewport') {
                                if (node.content && node.content.includes('device-width')) {
                                    needsFix = true;
                                }
                            }
                        });
                    });
                    if (needsFix) applyDesktopViewport();
                });
                observer.observe(document.head, { childList: true, subtree: true });
            }
        } catch(e) {}
    })();
"""

private const val BLOB_DOWNLOAD_SCRIPT = """
    (function() {
        if (window._nexaBlobInjected) return;
        window._nexaBlobInjected = true;
        
        var originalClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function() {
            try {
                var href = this.href || '';
                var filename = this.download || 'generated_file';
                if (href.startsWith('blob:') || href.startsWith('data:')) {
                    fetch(href)
                        .then(function(r) { return r.blob(); })
                        .then(function(blob) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                if (window.AndroidBlobDownloader) {
                                    window.AndroidBlobDownloader.processBase64Data(
                                        reader.result,
                                        blob.type,
                                        filename
                                    );
                                }
                            };
                            reader.readAsDataURL(blob);
                        }).catch(function(err) {});
                }
            } catch(e) {}
            return originalClick.apply(this, arguments);
        };
    })();
"""

private const val MEDIA_INSPECTION_SCRIPT = """
    (function() {
        if (window._nexaMediaInspectorInjected) return;
        window._nexaMediaInspectorInjected = true;

        function findMedia(target, touchX, touchY) {
            if (!target) return null;

            // 1. Check for Video element (direct, child, parent, or closest wrapper)
            var vid = (target.tagName === 'VIDEO') ? target : (target.querySelector('video') || target.closest('video') || (target.parentElement ? target.parentElement.querySelector('video') : null));

            // Check bounding boxes of all video tags on page if near touch point
            if (!vid && touchX !== undefined && touchY !== undefined) {
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                    var r = vids[i].getBoundingClientRect();
                    if (touchX >= r.left - 50 && touchX <= r.right + 50 && touchY >= r.top - 50 && touchY <= r.bottom + 50) {
                        vid = vids[i];
                        break;
                    }
                }
            }

            if (vid) {
                var vSrc = vid.currentSrc || vid.src;
                if (!vSrc) {
                    var srcEl = vid.querySelector('source');
                    if (srcEl) vSrc = srcEl.src;
                }
                var poster = vid.poster || '';
                var title = vid.getAttribute('title') || vid.getAttribute('aria-label') || document.title || 'Video';
                if (vSrc) {
                    return { type: 'video', url: vSrc, poster: poster, title: title };
                }
            }

            // 2. Check for Image element
            var img = (target.tagName === 'IMG') ? target : (target.querySelector('img') || target.closest('img'));
            if (img) {
                var iSrc = img.currentSrc || img.src;
                var title = img.alt || img.getAttribute('title') || document.title || 'Image';
                if (iSrc) {
                    return { type: 'image', url: iSrc, poster: '', title: title };
                }
            }

            return null;
        }

        var pressTimer = null;
        var startX = 0, startY = 0;

        document.addEventListener('touchstart', function(e) {
            if (e.touches.length === 1) {
                startX = e.touches[0].clientX;
                startY = e.touches[0].clientY;
                var t = document.elementFromPoint(startX, startY);
                clearTimeout(pressTimer);
                pressTimer = setTimeout(function() {
                    var m = findMedia(t, startX, startY);
                    if (m && m.url && window.AndroidMediaInspector) {
                        window.AndroidMediaInspector.onMediaDetected(m.type, m.url, m.poster, m.title);
                    }
                }, 500);
            }
        }, { passive: true });

        document.addEventListener('touchmove', function(e) {
            if (e.touches.length === 1) {
                if (Math.abs(e.touches[0].clientX - startX) > 15 || Math.abs(e.touches[0].clientY - startY) > 15) {
                    clearTimeout(pressTimer);
                }
            }
        }, { passive: true });

        document.addEventListener('touchend', function() {
            clearTimeout(pressTimer);
        }, { passive: true });

        document.addEventListener('contextmenu', function(e) {
            var m = findMedia(e.target, e.clientX, e.clientY);
            if (m && m.url && window.AndroidMediaInspector) {
                e.preventDefault();
                window.AndroidMediaInspector.onMediaDetected(m.type, m.url, m.poster, m.title);
            }
        });
    })();
"""

data class DetectedMediaItem(
    val type: String, // "video" or "image"
    val mediaUrl: String,
    val posterUrl: String = "",
    val title: String = ""
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    profile: Profile,
    allProfiles: List<Profile> = emptyList(),
    onSwitchProfile: (Profile) -> Unit = {},
    onToggleDesktopMode: (Profile, Boolean) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val clipboardManager = LocalClipboardManager.current

    var isDesktop by remember(profile.id) { mutableStateOf(profile.isDesktopMode) }
    var isBackgroundActive by remember(profile.id) { mutableStateOf(profile.isBackground) }
    var currentUrl by remember(profile.id) {
        mutableStateOf(if (profile.url.isNotBlank()) profile.url else "https://www.google.com")
    }
    var inputUrl by remember(profile.id) { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var pageProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var lastExtractedCookies by remember { mutableStateOf<String?>(null) }
    var cookieNotificationVisible by remember { mutableStateOf(false) }

    var showProfileMenu by remember { mutableStateOf(false) }
    var showSessionInspector by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }

    // Media inspector & video download dialog state
    var detectedMedia by remember { mutableStateOf<DetectedMediaItem?>(null) }
    var showMediaDialog by remember { mutableStateOf(false) }

    // Fullscreen video & landscape state
    var isFullScreenVideo by remember { mutableStateOf(false) }
    var customFullScreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var isLandscapeLocked by remember { mutableStateOf(false) }

    var isProfileReady by remember(profile.id) { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isAdBlockActive by remember { mutableStateOf(AdBlocker.isEnabled) }
    var blockedAdsCounter by remember { mutableIntStateOf(AdBlocker.blockedCount.get()) }

    val currentThemeMode by ThemePreferences.currentThemeMode

    // Live cookies, bookmarks and downloads from Room
    val savedCookies by db.sessionCookieDao().getCookiesForProfile(profile.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val savedBookmarks by db.bookmarkDao().getBookmarksForProfile(profile.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val downloads by db.downloadDao().getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val currentDomain = remember(currentUrl) { SessionManager.extractDomain(currentUrl) }
    val activeDomainCookie = remember(savedCookies, currentDomain) {
        savedCookies.find { it.domain.equals(currentDomain, ignoreCase = true) || currentDomain.endsWith(it.domain) }
    }
    val isCurrentUrlBookmarked = remember(savedBookmarks, currentUrl) {
        savedBookmarks.any { it.url == currentUrl }
    }

    // Hardware back press handler
    BackHandler(enabled = true) {
        if (isFullScreenVideo) {
            customViewCallback?.onCustomViewHidden()
            customFullScreenView = null
            customViewCallback = null
            isFullScreenVideo = false
            val activity = context as? Activity
            activity?.requestedOrientation = if (isLandscapeLocked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            isTopBarVisible = true
            return@BackHandler
        }

        coroutineScope.launch {
            SessionManager.extractAndSaveCookies(context, profile.id, currentUrl)
        }
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onNavigateBack()
        }
    }

    // Handle background task preservation or destruction on dispose
    DisposableEffect(profile.id) {
        onDispose {
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                    val profileStore = androidx.webkit.ProfileStore.getInstance()
                    val webkitProfile = profileStore.getProfile("profile_${profile.id}")
                    webkitProfile?.cookieManager?.flush()
                } else {
                    CookieManager.getInstance().flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            webViewInstance?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                BackgroundSessionManager.registerSession(profile.id, wv, isBackgroundActive, context)
            }
        }
    }

    // Strictly isolate session cookies, IndexedDB, local storage & load profile
    LaunchedEffect(profile.id) {
        val existing = BackgroundSessionManager.getSession(profile.id)
        if (existing != null) {
            isProfileReady = false
            SessionManager.isolateAndSwitchProfile(context, profile.id, currentUrl)
            webViewInstance = existing
            currentUrl = existing.url ?: currentUrl
            inputUrl = currentUrl
            isProfileReady = true
        } else {
            // Initialization for a new WebView (including isolateAndSwitchProfile) 
            // is handled inside the AndroidView factory to prevent race conditions.
            isProfileReady = false
        }
    }

    Scaffold(
        bottomBar = {
            if (isTopBarVisible && !isFullScreenVideo) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    val displayProfiles = allProfiles.take(5)
                    for (p in displayProfiles) {
                        NavigationBarItem(
                            selected = p.id == profile.id,
                            onClick = {
                                if (p.id != profile.id) {
                                    coroutineScope.launch {
                                        SessionManager.extractAndSaveCookies(context, profile.id, currentUrl)
                                        SessionManager.isolateAndSwitchProfile(context, p.id, p.url)
                                        onSwitchProfile(p)
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (p.isDesktopMode) Icons.Outlined.DesktopWindows else Icons.Outlined.Smartphone,
                                    contentDescription = p.name,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = p.name,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        },
        topBar = {
            AnimatedVisibility(
                visible = isTopBarVisible && !isFullScreenVideo,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        // Header Bar with Adaptive Layout & Ad Blocker Shield
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Home Button
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        SessionManager.extractAndSaveCookies(context, profile.id, currentUrl)
                                    }
                                    val activity = context as? Activity
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    onNavigateBack()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    Icons.Outlined.Home,
                                    contentDescription = "Return to Dashboard",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Profile Switcher Pill
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { showProfileMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                try {
                                                    Color(android.graphics.Color.parseColor(profile.colorHex))
                                                } catch (e: Exception) {
                                                    Color(0xFF3B82F6)
                                                }
                                            )
                                    )
                                    Text(
                                        profile.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                }

                                DropdownMenu(
                                    expanded = showProfileMenu,
                                    onDismissRequest = { showProfileMenu = false }
                                ) {
                                    Text(
                                        "Switch Workspace Profile",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider()
                                    allProfiles.forEach { p ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        if (p.isDesktopMode) Icons.Outlined.DesktopWindows else Icons.Outlined.Smartphone,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Column {
                                                        Text(p.name, fontWeight = if (p.id == profile.id) FontWeight.Bold else FontWeight.Normal)
                                                        Text(p.workspaceName, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showProfileMenu = false
                                                if (p.id != profile.id) {
                                                    coroutineScope.launch {
                                                        SessionManager.extractAndSaveCookies(context, profile.id, currentUrl)
                                                        SessionManager.isolateAndSwitchProfile(context, p.id, p.url)
                                                        onSwitchProfile(p)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Quick Action Tools (Scrollable / Compact Row)
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Ad Blocker Shield Button
                                item {
                                    IconButton(
                                        onClick = {
                                            val next = !isAdBlockActive
                                            isAdBlockActive = next
                                            AdBlocker.isEnabled = next
                                            if (next) {
                                                webViewInstance?.evaluateJavascript(AdBlocker.AD_BLOCK_CSS_JS, null)
                                            }
                                            webViewInstance?.reload()
                                            Toast.makeText(
                                                context,
                                                if (next) "Ad Blocker Enabled (Blocking ads & popups)" else "Ad Blocker Disabled",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isAdBlockActive)
                                                    Color(0xFF10B981).copy(alpha = 0.18f)
                                                else
                                                    MaterialTheme.colorScheme.surface
                                            )
                                    ) {
                                        BadgeBox(isAdBlockActive && blockedAdsCounter > 0) {
                                            Icon(
                                                if (isAdBlockActive) Icons.Outlined.Shield else Icons.Outlined.ShieldMoon,
                                                contentDescription = "Ad Blocker ($blockedAdsCounter blocked)",
                                                tint = if (isAdBlockActive) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Background Task Toggle Pill / Indicator
                                item {
                                    IconButton(
                                        onClick = {
                                            val next = !isBackgroundActive
                                            isBackgroundActive = next
                                            coroutineScope.launch {
                                                db.profileDao().update(profile.copy(isBackground = next))
                                            }
                                            if (next) {
                                                BackgroundSessionManager.activeTaskProfiles[profile.id] = true
                                            } else {
                                                BackgroundSessionManager.activeTaskProfiles.remove(profile.id)
                                            }
                                            Toast.makeText(
                                                context,
                                                if (next) "Background tasks enabled: Continues even when minimized" else "Background tasks paused for this profile",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isBackgroundActive)
                                                    Color(0xFF10B981).copy(alpha = 0.18f)
                                                else
                                                    MaterialTheme.colorScheme.surface
                                            )
                                    ) {
                                        Icon(
                                            Icons.Outlined.Sync,
                                            contentDescription = "Background Task State",
                                            tint = if (isBackgroundActive) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Landscape / Rotation Mode Toggle Button
                                item {
                                    IconButton(
                                        onClick = {
                                            val activity = context as? Activity
                                            val nextLandscape = !isLandscapeLocked
                                            isLandscapeLocked = nextLandscape
                                            activity?.requestedOrientation = if (nextLandscape) {
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                            Toast.makeText(
                                                context,
                                                if (nextLandscape) "Landscape Mode On" else "Portrait Mode On",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isLandscapeLocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    ) {
                                        Icon(
                                            Icons.Outlined.ScreenRotation,
                                            contentDescription = "Toggle Orientation",
                                            tint = if (isLandscapeLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Downloads & Media Vault Button
                                item {
                                    IconButton(
                                        onClick = { showDownloadsDialog = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        BadgeBox(downloads.isNotEmpty()) {
                                            Icon(
                                                Icons.Outlined.FileDownload,
                                                contentDescription = "Downloads & Media Vault",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Auto-Login & Session Inspector Button
                                item {
                                    IconButton(
                                        onClick = { showSessionInspector = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (activeDomainCookie != null)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else
                                                    MaterialTheme.colorScheme.surface
                                            )
                                    ) {
                                        BadgeBox(activeDomainCookie != null) {
                                            Icon(
                                                Icons.Outlined.Key,
                                                contentDescription = "Session Inspector",
                                                tint = if (activeDomainCookie != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Theme Mode Toggle Button
                                item {
                                    IconButton(
                                        onClick = { ThemePreferences.toggleTheme(context) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        val (themeIcon, themeDesc) = when (currentThemeMode) {
                                            AppThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto to "Theme: Auto"
                                            AppThemeMode.DARK -> Icons.Outlined.DarkMode to "Theme: Dark"
                                            AppThemeMode.LIGHT -> Icons.Outlined.LightMode to "Theme: Light"
                                        }
                                        Icon(
                                            themeIcon,
                                            contentDescription = themeDesc,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Desktop / Mobile Mode Toggle Button
                                item {
                                    IconButton(
                                        onClick = {
                                            val nextMode = !isDesktop
                                            isDesktop = nextMode
                                            onToggleDesktopMode(profile, nextMode)
                                            coroutineScope.launch {
                                                db.profileDao().update(profile.copy(isDesktopMode = nextMode))
                                            }
                                            webViewInstance?.let { wv ->
                                                wv.settings.apply {
                                                    userAgentString = if (nextMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                                    useWideViewPort = nextMode
                                                    loadWithOverviewMode = nextMode
                                                }
                                                if (nextMode) {
                                                    wv.evaluateJavascript(DESKTOP_INJECTION_SCRIPT, null)
                                                }
                                                wv.reload()
                                            }
                                            Toast.makeText(
                                                context,
                                                if (nextMode) "Desktop Mode Enabled" else "Mobile Mode Enabled",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDesktop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    ) {
                                        Icon(
                                            if (isDesktop) Icons.Outlined.DesktopWindows else Icons.Outlined.PhoneAndroid,
                                            contentDescription = "Toggle Desktop Mode",
                                            tint = if (isDesktop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Reload Button
                                item {
                                    IconButton(
                                        onClick = { webViewInstance?.reload() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = "Reload",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // URL Bar & Controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                            }

                            // Address Field
                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                leadingIcon = {
                                    Icon(
                                        if (currentUrl.startsWith("https")) Icons.Outlined.Lock else Icons.Outlined.Public,
                                        contentDescription = "SSL Status",
                                        tint = if (currentUrl.startsWith("https")) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    if (isCurrentUrlBookmarked) {
                                                        val bm = savedBookmarks.find { it.url == currentUrl }
                                                        if (bm != null) db.bookmarkDao().delete(bm)
                                                        Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        db.bookmarkDao().insert(
                                                            Bookmark(
                                                                profileId = profile.id,
                                                                title = pageTitle.ifBlank { currentUrl },
                                                                url = currentUrl
                                                            )
                                                        )
                                                        Toast.makeText(context, "Saved to Bookmarks", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                if (isCurrentUrlBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "Bookmark",
                                                tint = if (isCurrentUrlBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (inputUrl.isNotBlank() && inputUrl != currentUrl) {
                                            IconButton(
                                                onClick = {
                                                    val target = formatNavUrl(inputUrl)
                                                    currentUrl = target
                                                    coroutineScope.launch {
                                                        SessionManager.injectCookiesForUrl(context, profile.id, target)
                                                        webViewInstance?.loadUrl(target)
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Outlined.ArrowForward, contentDescription = "Go", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        val target = formatNavUrl(inputUrl)
                                        currentUrl = target
                                        coroutineScope.launch {
                                            SessionManager.injectCookiesForUrl(context, profile.id, target)
                                            webViewInstance?.loadUrl(target)
                                        }
                                    }
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                            )
                        }

                        // Progress Bar
                        if (isLoading) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreenVideo) PaddingValues(0.dp) else paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (isFullScreenVideo) {
                                customViewCallback?.onCustomViewHidden()
                                customFullScreenView = null
                                customViewCallback = null
                                isFullScreenVideo = false
                                val activity = context as? Activity
                                activity?.requestedOrientation = if (isLandscapeLocked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                isTopBarVisible = true
                            } else {
                                isTopBarVisible = !isTopBarVisible
                            }
                        }
                    )
                }
        ) {
            key(profile.id) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val existing = BackgroundSessionManager.getSession(profile.id)
                        if (existing != null) {
                            (existing.parent as? ViewGroup)?.removeView(existing)
                            existing.onResume()
                            webViewInstance = existing
                            isProfileReady = true
                            existing
                        } else {
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                try {
                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                                        val profileStore = androidx.webkit.ProfileStore.getInstance()
                                        val webkitProfile = profileStore.getOrCreateProfile("profile_${profile.id}")
                                        androidx.webkit.WebViewCompat.setProfile(this, webkitProfile.name)
                                        android.util.Log.d("BrowserScreen", "Configured MULTI_PROFILE for ${profile.name}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("BrowserScreen", "Failed to set MULTI_PROFILE", e)
                                }

                                // Let system handle hardware acceleration automatically
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                                val cookieManager = try {
                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                                        val profileStore = androidx.webkit.ProfileStore.getInstance()
                                        val webkitProfile = profileStore.getOrCreateProfile("profile_${profile.id}")
                                        webkitProfile.cookieManager
                                    } else {
                                        CookieManager.getInstance()
                                    }
                                } catch (e: Exception) {
                                    CookieManager.getInstance()
                                }
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = profile.javascriptEnabled
                                    domStorageEnabled = profile.domStorageEnabled
                                    databaseEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    useWideViewPort = isDesktop
                                    loadWithOverviewMode = isDesktop
                                    userAgentString = if (isDesktop) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = true
                                    mediaPlaybackRequiresUserGesture = false
                                }

                                // Add JavascriptInterface to handle Base64 / Blob downloads from AI websites!
                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun processBase64Data(base64Data: String, mimeType: String, suggestedFilename: String) {
                                            DownloadHelper.saveBase64Data(context, base64Data, mimeType, suggestedFilename, profile.name)
                                        }
                                    },
                                    "AndroidBlobDownloader"
                                )

                                // Add JavascriptInterface to accurately detect Video & Image elements on Long Press
                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onMediaDetected(type: String, url: String, poster: String, title: String) {
                                            if (url.isNotBlank()) {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    detectedMedia = DetectedMediaItem(
                                                        type = type,
                                                        mediaUrl = url,
                                                        posterUrl = poster,
                                                        title = title.ifBlank { if (type == "video") "Generated Video" else "Image" }
                                                    )
                                                    showMediaDialog = true
                                                }
                                            }
                                        }
                                    },
                                    "AndroidMediaInspector"
                                )

                                // Set download listener for files, videos, images, packages
                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                    if (url.startsWith("blob:") || url.startsWith("data:")) {
                                        val js = """
                                            (function() {
                                                fetch('$url')
                                                    .then(function(r) { return r.blob(); })
                                                    .then(function(blob) {
                                                        var reader = new FileReader();
                                                        reader.onloadend = function() {
                                                            if (window.AndroidBlobDownloader) {
                                                                 window.AndroidBlobDownloader.processBase64Data(
                                                                    reader.result,
                                                                    blob.type,
                                                                    'downloaded_media'
                                                                );
                                                            }
                                                        };
                                                        reader.readAsDataURL(blob);
                                                    }).catch(function(e) {});
                                            })();
                                        """.trimIndent()
                                        evaluateJavascript(js, null)
                                    } else {
                                        DownloadHelper.downloadUrl(context, url, userAgent, contentDisposition, mimetype, profile.name)
                                    }
                                }

                                // Long-press context fallback listener for native image/anchor detection
                                setOnLongClickListener {
                                    val result = hitTestResult
                                    when (result.type) {
                                        WebView.HitTestResult.IMAGE_TYPE,
                                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                            val imgUrl = result.extra
                                            if (!imgUrl.isNullOrEmpty()) {
                                                detectedMedia = DetectedMediaItem(
                                                    type = "image",
                                                    mediaUrl = imgUrl,
                                                    title = "Image Media"
                                                )
                                                showMediaDialog = true
                                                true
                                            } else false
                                        }
                                        WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                            val anchorUrl = result.extra
                                            if (!anchorUrl.isNullOrEmpty()) {
                                                val isVideoUrl = anchorUrl.endsWith(".mp4", ignoreCase = true) ||
                                                        anchorUrl.endsWith(".webm", ignoreCase = true) ||
                                                        anchorUrl.endsWith(".mov", ignoreCase = true) ||
                                                        anchorUrl.contains("video", ignoreCase = true)

                                                detectedMedia = DetectedMediaItem(
                                                    type = if (isVideoUrl) "video" else "image",
                                                    mediaUrl = anchorUrl,
                                                    title = if (isVideoUrl) "Video File" else "Link / Media"
                                                )
                                                showMediaDialog = true
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress
                                        isLoading = newProgress < 100
                                        if (isDesktop && newProgress in 20..40) {
                                            view?.evaluateJavascript(DESKTOP_INJECTION_SCRIPT, null)
                                        }
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        pageTitle = title ?: ""
                                    }

                                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                        customFullScreenView = view
                                        customViewCallback = callback
                                        isFullScreenVideo = true
                                        val activity = context as? Activity
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }

                                    override fun onHideCustomView() {
                                        customFullScreenView = null
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                        isFullScreenVideo = false
                                        val activity = context as? Activity
                                        activity?.requestedOrientation = if (isLandscapeLocked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                        android.util.Log.e("BrowserScreen", "Renderer crashed. Recreating...");
                                        coroutineScope.launch {
                                            BackgroundSessionManager.removeSession(profile.id, context)
                                            webViewInstance = null
                                        }
                                        return true
                                    }
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString()
                                        if (isAdBlockActive && AdBlocker.isAd(reqUrl)) {
                                            blockedAdsCounter = AdBlocker.blockedCount.get()
                                            return AdBlocker.createEmptyResponse()
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (isDesktop) {
                                            view?.evaluateJavascript(DESKTOP_INJECTION_SCRIPT, null)
                                        }
                                        if (isAdBlockActive) {
                                            view?.evaluateJavascript(AdBlocker.AD_BLOCK_CSS_JS, null)
                                        }
                                        view?.evaluateJavascript(BLOB_DOWNLOAD_SCRIPT, null)
                                        view?.evaluateJavascript(MEDIA_INSPECTION_SCRIPT, null)
                                        url?.let {
                                            currentUrl = it
                                            inputUrl = it
                                            isLoading = true
                                            canGoBack = view?.canGoBack() == true
                                            canGoForward = view?.canGoForward() == true
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (isDesktop) {
                                            view?.evaluateJavascript(DESKTOP_INJECTION_SCRIPT, null)
                                        }
                                        if (isAdBlockActive) {
                                            view?.evaluateJavascript(AdBlocker.AD_BLOCK_CSS_JS, null)
                                            blockedAdsCounter = AdBlocker.blockedCount.get()
                                        }
                                        view?.evaluateJavascript(BLOB_DOWNLOAD_SCRIPT, null)
                                        view?.evaluateJavascript(MEDIA_INSPECTION_SCRIPT, null)
                                        isLoading = false
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true

                                        url?.let { targetUrl ->
                                            currentUrl = targetUrl
                                            inputUrl = targetUrl

                                            coroutineScope.launch {
                                                val savedList = SessionManager.extractAndSaveAllSessionCookies(context, profile.id, targetUrl)
                                                if (savedList.isNotEmpty()) {
                                                    lastExtractedCookies = savedList.firstOrNull()?.cookieString
                                                    cookieNotificationVisible = true
                                                }
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val uri = request?.url ?: return false
                                        val scheme = uri.scheme?.lowercase() ?: ""
                                        if (scheme == "http" || scheme == "https") {
                                            return false
                                        }
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                            context.startActivity(intent)
                                            return true
                                        } catch (e: Exception) {
                                            return true
                                        }
                                    }
                                }

                                webViewInstance = this

                                coroutineScope.launch {
                                    SessionManager.isolateAndSwitchProfile(ctx, profile.id, currentUrl)
                                    if (isDesktop) {
                                        evaluateJavascript(DESKTOP_INJECTION_SCRIPT, null)
                                    }
                                    evaluateJavascript(BLOB_DOWNLOAD_SCRIPT, null)
                                    evaluateJavascript(MEDIA_INSPECTION_SCRIPT, null)
                                    loadUrl(currentUrl)
                                    isProfileReady = true
                                }
                            }
                        }
                    },
                    update = { view ->
                        webViewInstance = view
                    }
                )
            }

            // Fullscreen Custom Video Player Overlay (YouTube, Google Flow, HTML5 video player)
            if (isFullScreenVideo && customFullScreenView != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { customFullScreenView!! }
                    )

                    // Exit Fullscreen Floating Badge (Top End)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .clickable {
                                customViewCallback?.onCustomViewHidden()
                                customFullScreenView = null
                                customViewCallback = null
                                isFullScreenVideo = false
                                val activity = context as? Activity
                                activity?.requestedOrientation = if (isLandscapeLocked) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                isTopBarVisible = true
                            },
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Exit Fullscreen (Double-Tap)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Quick floating Top-Bar restore button when hidden in fullscreen/landscape mode
            if (!isTopBarVisible && !isFullScreenVideo) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clickable { isTopBarVisible = true },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Show Bar", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        Text("Show Top Bar (Double-tap)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Profile Isolation & Loading State Overlay
            if (!isProfileReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp),
                            color = try {
                                Color(android.graphics.Color.parseColor(profile.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            },
                            strokeWidth = 3.dp
                        )
                        Text(
                            "Switching to Profile: ${profile.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Isolating cookies & loading account session...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Auto-Login Cookie Captured Floating Toast Notification
            AnimatedVisibility(
                visible = cookieNotificationVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Text(
                            "Auto-login session secured in Room Vault",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { cookieNotificationVisible = false },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // Media Detected / Video Downloader Dialog
    if (showMediaDialog && detectedMedia != null) {
        val media = detectedMedia!!
        MediaDownloadDialog(
            media = media,
            profileName = profile.name,
            onDismiss = {
                showMediaDialog = false
                detectedMedia = null
            },
            onDownload = { targetUrl, mimeType, filename ->
                DownloadHelper.downloadUrl(
                    context = context,
                    url = targetUrl,
                    mimeType = mimeType,
                    profileName = profile.name
                )
                showMediaDialog = false
                detectedMedia = null
            },
            onCopyLink = { urlToCopy ->
                clipboardManager.setText(AnnotatedString(urlToCopy))
                Toast.makeText(context, "Media link copied to clipboard", Toast.LENGTH_SHORT).show()
                showMediaDialog = false
                detectedMedia = null
            },
            onOpenInTab = { urlToOpen ->
                currentUrl = urlToOpen
                inputUrl = urlToOpen
                webViewInstance?.loadUrl(urlToOpen)
                showMediaDialog = false
                detectedMedia = null
            }
        )
    }

    // Downloads Dialog
    if (showDownloadsDialog) {
        DownloadsDialog(
            downloads = downloads,
            onDismiss = { showDownloadsDialog = false },
            onDelete = { downloadItem ->
                coroutineScope.launch {
                    db.downloadDao().delete(downloadItem)
                }
            },
            onClearAll = {
                coroutineScope.launch {
                    db.downloadDao().deleteAll()
                }
            }
        )
    }

    // Session Cookies Inspector Dialog
    if (showSessionInspector) {
        SessionInspectorDialog(
            profile = profile,
            cookies = savedCookies,
            currentDomain = currentDomain,
            onDismiss = { showSessionInspector = false },
            onDeleteCookie = { cookieToDelete ->
                coroutineScope.launch {
                    db.sessionCookieDao().delete(cookieToDelete)
                    Toast.makeText(context, "Session deleted for ${cookieToDelete.domain}", Toast.LENGTH_SHORT).show()
                }
            },
            onReinjectCookies = {
                coroutineScope.launch {
                    val count = SessionManager.injectAllCookiesForProfile(context, profile.id)
                    webViewInstance?.reload()
                    Toast.makeText(context, "Injected cookies for $count domains and reloaded!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Bookmarks Sheet Dialog
    if (showBookmarksDialog) {
        BookmarksDialog(
            bookmarks = savedBookmarks,
            onDismiss = { showBookmarksDialog = false },
            onOpenBookmark = { bookmark ->
                currentUrl = bookmark.url
                inputUrl = bookmark.url
                coroutineScope.launch {
                    SessionManager.injectCookiesForUrl(context, profile.id, bookmark.url)
                    webViewInstance?.loadUrl(bookmark.url)
                }
                showBookmarksDialog = false
            },
            onDeleteBookmark = { bookmark ->
                coroutineScope.launch {
                    db.bookmarkDao().delete(bookmark)
                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun MediaDownloadDialog(
    media: DetectedMediaItem,
    profileName: String,
    onDismiss: () -> Unit,
    onDownload: (url: String, mimeType: String, filename: String) -> Unit,
    onCopyLink: (url: String) -> Unit,
    onOpenInTab: (url: String) -> Unit
) {
    val isVideo = media.type == "video"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isVideo) Color(0xFF2563EB) else Color(0xFF10B981)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isVideo) Icons.Outlined.VideoLibrary else Icons.Outlined.Image,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                if (isVideo) "Video Detected (MP4)" else "Image Detected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                media.title.ifBlank { profileName },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        media.mediaUrl,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Primary Action: Download
                Button(
                    onClick = {
                        val mime = if (isVideo) "video/mp4" else "image/png"
                        val ext = if (isVideo) "mp4" else "png"
                        val name = "${media.title.take(20).replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                        onDownload(media.mediaUrl, mime, name)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVideo) Color(0xFF2563EB) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isVideo) "Download Video (.MP4)" else "Download Image",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Optional Poster image download if available for video
                if (isVideo && media.posterUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val name = "poster_${System.currentTimeMillis()}.jpg"
                            onDownload(media.posterUrl, "image/jpeg", name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download Video Thumbnail (JPG)")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopyLink(media.mediaUrl) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Link", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onOpenInTab(media.mediaUrl) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open URL", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeBox(showBadge: Boolean, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        content()
        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
        }
    }
}

@Composable
fun SessionInspectorDialog(
    profile: Profile,
    cookies: List<SessionCookie>,
    currentDomain: String,
    onDismiss: () -> Unit,
    onDeleteCookie: (SessionCookie) -> Unit,
    onReinjectCookies: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text("Session Vault & Auto-Login", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Profile: ${profile.name}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                Text(
                    "Session cookies extracted from WebView are stored in the Room Database and automatically injected whenever you visit these sites to keep you logged in.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (cookies.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No session cookies recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cookies, key = { it.id }) { cookie ->
                            val isCurrent = cookie.domain.equals(currentDomain, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cookie.domain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            "${cookie.cookieString.length} chars stored",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteCookie(cookie) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Cookie", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReinjectCookies,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Re-Inject Cookies", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksDialog(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Bookmarks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bookmarks saved for this profile", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenBookmark(bm) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(bm.url, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(
                                        onClick = { onDeleteBookmark(bm) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNavUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    }
}
