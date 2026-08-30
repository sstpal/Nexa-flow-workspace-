#!/bin/bash
cat << 'INNER_EOF' > /tmp/drawer_patch.kt
    var showProfileMenu by remember { mutableStateOf(false) } // Keep this if used elsewhere
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    // Hardware back press handler
INNER_EOF
