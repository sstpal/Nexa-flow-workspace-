#!/bin/bash
sed -i '/import androidx.activity.compose.rememberLauncherForActivityResult/d' app/src/main/java/com/example/DashboardScreen.kt
sed -i '/import com.example.utils.SessionManager/d' app/src/main/java/com/example/DashboardScreen.kt

sed -i '/import androidx.compose.material3.NavigationBarItem/d' app/src/main/java/com/example/BrowserScreen.kt
sed -i '/import androidx.compose.material3.NavigationBar/d' app/src/main/java/com/example/BrowserScreen.kt

sed -i '/^import android/i import androidx.activity.compose.rememberLauncherForActivityResult\nimport com.example.utils.SessionManager' app/src/main/java/com/example/DashboardScreen.kt

sed -i '/^import android/i import androidx.compose.material3.NavigationBarItem\nimport androidx.compose.material3.NavigationBar' app/src/main/java/com/example/BrowserScreen.kt
