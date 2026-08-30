#!/bin/bash
cat << 'INNER_EOF' > /tmp/CookieButtons.kt
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    
                    Text("Cookie Backup & Restore", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Export or import all session cookies across profiles as JSON. Fully complies with Google flow policies, backing up local authenticated states safely.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    val exportLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
                        if (uri != null) {
                            coroutineScope.launch {
                                val success = SessionManager.exportCookiesToJson(context, uri)
                                Toast.makeText(context, if (success) "Cookies exported successfully!" else "Failed to export cookies", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    val importLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            coroutineScope.launch {
                                val count = SessionManager.importCookiesFromJson(context, uri)
                                Toast.makeText(context, if (count > 0) "Imported $count cookies successfully!" else "Failed to import or no cookies found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("nexaflow_cookies.json") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import", fontSize = 12.sp)
                        }
                    }
INNER_EOF
# We will inject this into the "Storage Management" section in DashboardScreen.
