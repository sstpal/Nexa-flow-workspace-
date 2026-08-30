package com.example.utils

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.AppDatabase
import com.example.data.Bookmark
import com.example.data.Profile
import com.example.data.SessionCookie
import com.example.data.SiteSetting
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"

    fun ensureFirebase(context: Context? = null) {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context ?: return).isEmpty()) {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:500339830613:android:71d5dad637afb3246a09dc")
                    .setApiKey("AIzaSyDeHgBgfox1bdbuuaNzvxDKxSCIPjV1zhg")
                    .setProjectId("eternal-mark-384306")
                    .setStorageBucket("eternal-mark-384306.firebasestorage.app")
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase initialized with explicit options")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ensureFirebase error: ${e.message}")
            try {
                if (context != null) {
                    com.google.firebase.FirebaseApp.initializeApp(context)
                }
            } catch (ignored: Throwable) {}
        }
    }

    val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth not initialized: ${e.message}")
            null
        }

    val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseFirestore not initialized: ${e.message}")
            null
        }

    val currentUser: FirebaseUser?
        get() = try {
            auth?.currentUser
        } catch (e: Throwable) {
            null
        }

    val isUserSignedIn: Boolean
        get() = currentUser != null

    /**
     * Signs in with Google using Jetpack CredentialManager and links to Firebase Auth.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            ensureFirebase(context)
            val firebaseAuth = auth ?: return@withContext Result.failure(Exception("Firebase is not initialized. Please check network/services."))
            val credentialManager = CredentialManager.create(context)
            val webClientId = "500339830613-o58e1j6o8nrs7ro7qdrttn7n7vrbsn8o.apps.googleusercontent.com"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return@withContext Result.failure(Exception("Firebase user is null"))

                Log.d(TAG, "Successfully signed in Firebase user: ${user.email} (${user.uid})")
                Result.success(user)
            } else {
                Result.failure(Exception("Unsupported credential type received: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled Google Sign-In")
            Result.failure(Exception("Sign-in cancelled"))
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Signs out the user.
     */
    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            ensureFirebase(context)
            auth?.signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "Signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error", e)
        }
    }

    /**
     * Automatically uploads all local profiles, cookies, bookmarks, and site settings to user's Firestore vault.
     */
    suspend fun syncToCloud(context: Context): Result<SyncSummary> = withContext(Dispatchers.IO) {
        ensureFirebase(context)
        val user = currentUser ?: return@withContext Result.failure(Exception("Please sign in to backup to Firebase Cloud."))
        val dbFirestore = firestore ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val db = AppDatabase.getDatabase(context)
            val profiles: List<Profile> = db.profileDao().getAllProfilesSync()
            val cookies: List<SessionCookie> = db.sessionCookieDao().getAllCookiesSync()
            val siteSettings: List<SiteSetting> = db.siteSettingDao().getAllSettingsSync()
            val bookmarks: List<Bookmark> = db.bookmarkDao().getAllBookmarksSync()

            val userDocRef = dbFirestore.collection("users").document(user.uid)

            val backupPayload = hashMapOf(
                "version" to 2,
                "userEmail" to (user.email ?: ""),
                "timestamp" to System.currentTimeMillis(),
                "deviceInfo" to android.os.Build.MODEL,
                "profiles" to profiles.map { p ->
                    hashMapOf(
                        "id" to p.id,
                        "name" to p.name,
                        "url" to p.url,
                        "isDesktopMode" to p.isDesktopMode,
                        "isBackground" to p.isBackground,
                        "userAgent" to p.userAgent,
                        "javascriptEnabled" to p.javascriptEnabled,
                        "cookiesEnabled" to p.cookiesEnabled,
                        "domStorageEnabled" to p.domStorageEnabled,
                        "autoLoginEnabled" to p.autoLoginEnabled,
                        "colorHex" to p.colorHex,
                        "iconKey" to p.iconKey,
                        "lastActiveAt" to p.lastActiveAt
                    )
                },
                "cookies" to cookies.map { c ->
                    hashMapOf(
                        "profileId" to c.profileId,
                        "domain" to c.domain,
                        "cookieString" to c.cookieString,
                        "lastUpdated" to c.lastUpdated,
                        "isAutoLoginActive" to c.isAutoLoginActive
                    )
                },
                "siteSettings" to siteSettings.map { s ->
                    hashMapOf(
                        "id" to s.id,
                        "profileId" to s.profileId,
                        "domain" to s.domain,
                        "desktopMode" to s.desktopMode,
                        "allowJavascript" to s.allowJavascript,
                        "blockAds" to s.blockAds,
                        "zoomPercent" to s.zoomPercent,
                        "clearCookiesOnExit" to s.clearCookiesOnExit
                    )
                },
                "bookmarks" to bookmarks.map { b ->
                    hashMapOf(
                        "id" to b.id,
                        "profileId" to b.profileId,
                        "title" to b.title,
                        "url" to b.url,
                        "faviconUrl" to b.faviconUrl,
                        "createdAt" to b.createdAt
                    )
                }
            )

            userDocRef.set(backupPayload, SetOptions.merge()).await()
            Log.d(TAG, "Uploaded cloud backup for user ${user.uid} with ${profiles.size} profiles & ${cookies.size} cookies.")

            Result.success(
                SyncSummary(
                    profilesCount = profiles.size,
                    cookiesCount = cookies.size,
                    bookmarksCount = bookmarks.size,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Restores user's cloud backup from Firestore into Room database and injects cookies into WebView.
     */
    suspend fun restoreFromCloud(context: Context): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        ensureFirebase(context)
        val user = currentUser ?: return@withContext Result.failure(Exception("Please sign in to restore from Firebase Cloud."))
        val dbFirestore = firestore ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val userDocRef = dbFirestore.collection("users").document(user.uid)
            val snapshot = userDocRef.get().await()

            if (!snapshot.exists()) {
                return@withContext Result.failure(Exception("No cloud backup found for account ${user.email}."))
            }

            val db = AppDatabase.getDatabase(context)
            var profilesRestored = 0
            var cookiesRestored = 0
            var bookmarksRestored = 0
            var settingsRestored = 0

            // 1. Restore Profiles
            val rawProfiles = snapshot.get("profiles") as? List<Map<String, Any?>>
            if (rawProfiles != null) {
                val profiles = rawProfiles.map { map ->
                    Profile(
                        id = (map["id"] as? Number)?.toInt() ?: 0,
                        name = map["name"] as? String ?: "Profile",
                        url = map["url"] as? String ?: "https://google.com",
                        isDesktopMode = map["isDesktopMode"] as? Boolean ?: false,
                        isBackground = map["isBackground"] as? Boolean ?: false,
                        userAgent = map["userAgent"] as? String ?: "",
                        javascriptEnabled = map["javascriptEnabled"] as? Boolean ?: true,
                        cookiesEnabled = map["cookiesEnabled"] as? Boolean ?: true,
                        domStorageEnabled = map["domStorageEnabled"] as? Boolean ?: true,
                        autoLoginEnabled = map["autoLoginEnabled"] as? Boolean ?: true,
                        colorHex = map["colorHex"] as? String ?: "#3B82F6",
                        iconKey = map["iconKey"] as? String ?: "work",
                        lastActiveAt = (map["lastActiveAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                }
                for (p in profiles) {
                    db.profileDao().insert(p)
                    profilesRestored++
                }
            }

            // 2. Restore Cookies & Inject into CookieManager
            val rawCookies = snapshot.get("cookies") as? List<Map<String, Any?>>
            if (rawCookies != null) {
                val cookies = rawCookies.map { map ->
                    SessionCookie(
                        id = 0L,
                        profileId = (map["profileId"] as? Number)?.toInt() ?: 0,
                        domain = map["domain"] as? String ?: "",
                        cookieString = map["cookieString"] as? String ?: "",
                        lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        isAutoLoginActive = map["isAutoLoginActive"] as? Boolean ?: true
                    )
                }
                for (c in cookies) {
                    db.sessionCookieDao().insertOrUpdate(c)
                    cookiesRestored++
                }
                // Auto-inject into Android CookieManager
                val allProfiles = db.profileDao().getAllProfilesSync()
                for (p in allProfiles) {
                    SessionManager.injectAllCookiesForProfile(context, p.id)
                }
            }

            // 3. Restore Site Settings
            val rawSettings = snapshot.get("siteSettings") as? List<Map<String, Any?>>
            if (rawSettings != null) {
                val settings = rawSettings.map { map ->
                    SiteSetting(
                        id = (map["id"] as? Number)?.toLong() ?: 0L,
                        profileId = (map["profileId"] as? Number)?.toInt() ?: 0,
                        domain = map["domain"] as? String ?: "",
                        desktopMode = map["desktopMode"] as? Boolean,
                        allowJavascript = map["allowJavascript"] as? Boolean ?: true,
                        blockAds = map["blockAds"] as? Boolean ?: false,
                        zoomPercent = (map["zoomPercent"] as? Number)?.toInt() ?: 100,
                        clearCookiesOnExit = map["clearCookiesOnExit"] as? Boolean ?: false
                    )
                }
                for (s in settings) {
                    db.siteSettingDao().insertOrUpdate(s)
                    settingsRestored++
                }
            }

            // 4. Restore Bookmarks
            val rawBookmarks = snapshot.get("bookmarks") as? List<Map<String, Any?>>
            if (rawBookmarks != null) {
                val bookmarks = rawBookmarks.map { map ->
                    Bookmark(
                        id = (map["id"] as? Number)?.toLong() ?: 0L,
                        profileId = (map["profileId"] as? Number)?.toInt() ?: 0,
                        title = map["title"] as? String ?: "",
                        url = map["url"] as? String ?: "",
                        faviconUrl = map["faviconUrl"] as? String ?: "",
                        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                }
                for (b in bookmarks) {
                    db.bookmarkDao().insert(b)
                    bookmarksRestored++
                }
            }

            Log.d(TAG, "Restored $profilesRestored profiles and $cookiesRestored cookies from Firebase Cloud.")
            Result.success(
                RestoreSummary(
                    profilesRestored = profilesRestored,
                    cookiesRestored = cookiesRestored,
                    bookmarksRestored = bookmarksRestored,
                    siteSettingsRestored = settingsRestored,
                    isAutoLoginReady = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore from cloud failed", e)
            Result.failure(e)
        }
    }
}

data class SyncSummary(
    val profilesCount: Int,
    val cookiesCount: Int,
    val bookmarksCount: Int,
    val timestamp: Long
)
