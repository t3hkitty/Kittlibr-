package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.example.data.repository.LibraryRepository
import com.example.data.repository.WebDavRepository
import com.example.ui.components.AppScaffold
import com.example.ui.screens.discovery.DiscoveryViewModel
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.reader.ReaderViewModel
import com.example.ui.screens.settings.ThemeConfigurationScreen
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.KittlibrTheme

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
                prefs.edit().putString(DEBUG_SECRET_KEY, debugToken).commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(RecaptchaAppCheckProviderFactory.getInstance(siteKey))
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mandatory App Check configuration before UI or Firebase operations
        configureAppCheck(this, intent)
        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            var currentAppTheme by remember { mutableStateOf(AppThemeStyle.DEFAULT) }

            KittlibrTheme(appThemeStyle = currentAppTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KittlibrAppNavGraph(
                        currentAppTheme = currentAppTheme,
                        onThemeChange = { currentAppTheme = it }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            val repo = LibraryRepository(this)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val fileName = uri.lastPathSegment ?: "External File"
                    repo.insertBook(
                        com.example.data.local.BookEntity(
                            title = fileName.substringBeforeLast("."),
                            author = "External Reader",
                            description = "Opened via external file intent: $uri",
                            coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                            format = if (uri.toString().endsWith(".md", true)) "MD" else "EPUB",
                            filePath = uri.toString(),
                            tags = "ExternalIntent, ReaderOpen",
                            hasCopy = true,
                            externalUri = uri.toString()
                        )
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to import external file intent", e)
                }
            }
        }
    }
}

@Composable
fun KittlibrAppNavGraph(
    currentAppTheme: AppThemeStyle,
    onThemeChange: (AppThemeStyle) -> Unit
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val libraryViewModel: LibraryViewModel = viewModel()
    val discoveryViewModel: DiscoveryViewModel = viewModel()
    val libraryRepository = remember { LibraryRepository(context) }
    val webDavRepository = remember { WebDavRepository(context) }

    NavHost(navController = navController, startDestination = "main_scaffold") {
        composable("main_scaffold") {
            AppScaffold(
                libraryViewModel = libraryViewModel,
                discoveryViewModel = discoveryViewModel,
                libraryRepository = libraryRepository,
                webDavRepository = webDavRepository,
                currentAppTheme = currentAppTheme,
                onThemeChange = onThemeChange,
                onOpenThemeConfig = { navController.navigate("theme_config") },
                onBookClick = { bookId ->
                    navController.navigate("reader/$bookId")
                }
            )
        }
        composable("theme_config") {
            ThemeConfigurationScreen(
                currentAppTheme = currentAppTheme,
                onThemeChange = onThemeChange,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 1L
            val readerViewModel: ReaderViewModel = viewModel(
                factory = ReaderViewModelFactory(context, bookId)
            )
            ReaderScreen(
                viewModel = readerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

class ReaderViewModelFactory(
    private val context: android.content.Context,
    private val bookId: Long
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(context.applicationContext as android.app.Application, bookId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
