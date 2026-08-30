#!/bin/bash
# For DashboardScreen
sed -i '1i import androidx.activity.compose.rememberLauncherForActivityResult' app/src/main/java/com/example/DashboardScreen.kt
sed -i '1i import com.example.utils.SessionManager' app/src/main/java/com/example/DashboardScreen.kt

# For BrowserScreen
sed -i '1i import androidx.compose.material3.NavigationBarItem' app/src/main/java/com/example/BrowserScreen.kt
sed -i '1i import androidx.compose.material3.NavigationBar' app/src/main/java/com/example/BrowserScreen.kt

# Change androidx.compose.material3.NavigationBarItem back to NavigationBarItem
sed -i 's/androidx.compose.material3.NavigationBarItem/NavigationBarItem/g' app/src/main/java/com/example/BrowserScreen.kt
sed -i 's/androidx.compose.material3.NavigationBar(/NavigationBar(/g' app/src/main/java/com/example/BrowserScreen.kt

