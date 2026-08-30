#!/bin/bash

# Update setLayerType
sed -i 's/\/\/ setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)/setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)/g' app/src/main/java/com/example/BrowserScreen.kt

# Add onRenderProcessGone
sed -i '/webViewClient = object : WebViewClient() {/a\
                                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {\
                                        android.util.Log.e("BrowserScreen", "Renderer crashed. Recreating...");\
                                        coroutineScope.launch {\
                                            BackgroundSessionManager.removeSession(profile.id, context)\
                                            webViewInstance = null\
                                        }\
                                        return true\
                                    }' app/src/main/java/com/example/BrowserScreen.kt

