package com.jv.attentionpanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.security.SecureRandom
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) Toast.makeText(this, "Permissions Granted! Syncing...", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        checkStoragePermissions()

        setContent {
            var minMinutes by remember { mutableStateOf("1") }
            var maxMinutes by remember { mutableStateOf("2") }
            var keepScreenOn by remember { mutableStateOf(false) } // New Checkbox State
            
            var statusMessage by remember { mutableStateOf("Initializing...") }
            var isDownloading by remember { mutableStateOf(false) }
            var mediaCount by remember { mutableStateOf(0) }
            var verseCount by remember { mutableStateOf(0) }

            // Check if battery optimization is already ignored
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(packageName)

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val verseHelper = VerseDatabaseHelper.getInstance(applicationContext)
                    val count = verseHelper.getVerseCount()
                    
                    if (count == 0) {
                        isDownloading = true
                        withContext(Dispatchers.Main) { statusMessage = "Downloading Bible Data..." }
                        VerseLoader.downloadAndSaveKJV(verseHelper)
                        withContext(Dispatchers.Main) { statusMessage = "Fetching Wisdom of Sirach..." }
                        VerseLoader.downloadAndSaveSirach(verseHelper)
                        verseCount = verseHelper.getVerseCount()
                        isDownloading = false
                    } else {
                        verseCount = count
                    }
                }

                if (hasStoragePermissions()) {
                    withContext(Dispatchers.Main) { statusMessage = "Syncing Media..." }
                    mediaCount = withContext(Dispatchers.IO) {
                        val helper = MediaDatabaseHelper.getInstance(applicationContext)
                        helper.syncFromMediaStore(applicationContext)
                        helper.getMediaCount()
                    }
                    withContext(Dispatchers.Main) { statusMessage = "Ready" }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("Attention Panner", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isDownloading) {
                        Text(statusMessage, color = Color.Yellow, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    } else {
                         Text("Library Status:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                         Text("Images/Videos: $mediaCount", color = Color.Green, style = MaterialTheme.typography.titleMedium)
                         Text("Verses: $verseCount", color = Color.Cyan, style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    val colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Magenta, unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.Magenta, unfocusedLabelColor = Color.Gray,
                        cursorColor = Color.White
                    )

                    OutlinedTextField(
                        value = minMinutes, onValueChange = { minMinutes = it },
                        label = { Text("Min Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = colors
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = maxMinutes, onValueChange = { maxMinutes = it },
                        label = { Text("Max Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = colors
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- KEEP SCREEN ON CHECKBOX ---
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = keepScreenOn,
                            onCheckedChange = { keepScreenOn = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color.Magenta, uncheckedColor = Color.Gray, checkmarkColor = Color.White)
                        )
                        Text("Wake & Keep Screen On", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }

                    // --- BATTERY OPTIMIZATION BUTTON ---
                    if (!isIgnoringBatteryOptimizations) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                @SuppressLint("BatteryLife")
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.data = Uri.parse("package:$packageName")
                                startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow Background Run (Important!)", color = Color.Yellow)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (Settings.canDrawOverlays(this@MainActivity)) {
                                val intent = Intent(this@MainActivity, OverlayService::class.java)
                                intent.putExtra("MIN_MINUTES", minMinutes.toLongOrNull() ?: 1L)
                                intent.putExtra("MAX_MINUTES", maxMinutes.toLongOrNull() ?: 2L)
                                intent.putExtra("KEEP_SCREEN_ON", keepScreenOn) // Pass boolean
                                startService(intent)
                                Toast.makeText(this@MainActivity, "Service Started!", Toast.LENGTH_SHORT).show()
                                moveTaskToBack(true)
                            } else {
                                Toast.makeText(this@MainActivity, "Overlay Permission required!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Start Service") }
                }
            }
        }
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS))
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun hasStoragePermissions(): Boolean = true 
}

data class Verse(val text: String, val reference: String)

object VerseLoader {
    private const val KJV_URL = "https://openbible.com/textfiles/kjv.txt"
    private const val SIRACH_URL = "https://www.earlyjewishwritings.com/text/sirach.html"
    private val SIRACH_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{1,3})[.]?\\s+(.*?)(?=\\d{1,2}:\\d{1,3}|$)")

    fun downloadAndSaveKJV(dbHelper: VerseDatabaseHelper) {
        try {
            val url = URL(KJV_URL)
            val reader = BufferedReader(InputStreamReader(url.openConnection().getInputStream()))
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.startsWith("Proverbs") || line.startsWith("Ecclesiastes") || line.startsWith("Song of Solomon")) {
                        val firstSpace = line.indexOf(' ')
                        if (firstSpace != -1) {
                            val tabIndex = line.indexOf('\t')
                            val splitIndex = if (tabIndex != -1) tabIndex else {
                                var idx = firstSpace + 1
                                while (idx < line.length && (line[idx].isDigit() || line[idx] == ':')) { idx++ }
                                idx
                            }
                            if (splitIndex < line.length) {
                                val values = ContentValues().apply {
                                    put("text", line.substring(splitIndex).trim())
                                    put("reference", line.substring(0, splitIndex).trim())
                                    put("book", line.substring(0, firstSpace))
                                }
                                db.insert("verses", null, values)
                            }
                        }
                    }
                    line = reader.readLine()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                reader.close()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun downloadAndSaveSirach(dbHelper: VerseDatabaseHelper) {
        try {
            val html = URL(SIRACH_URL).openConnection().getInputStream().bufferedReader().use { it.readText() }
            val rawText = html.replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ")
            val matcher = SIRACH_PATTERN.matcher(rawText)
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                while (matcher.find()) {
                    val text = matcher.group(3)?.trim() ?: ""
                    if (text.length > 5) {
                        val values = ContentValues().apply {
                            put("text", text)
                            put("reference", "Sirach ${matcher.group(1)}:${matcher.group(2)}")
                            put("book", "Sirach")
                        }
                        db.insert("verses", null, values)
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

class TTSManager(context: Context) {
    private var tts: TextToSpeech? = null
    var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isInitialized = true
                    val bestVoice = tts?.voices?.sortedWith(
                        compareByDescending<Voice> { it.name.contains("network", ignoreCase = true) }
                            .thenByDescending { it.quality }
                    )?.firstOrNull { it.locale == Locale.US || it.locale.language == "en" }
                    bestVoice?.let { tts?.voice = it }
                }
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ID")
        }
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

class VerseDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context, "VerseDb", null, 2) {
    companion object {
        @Volatile private var instance: VerseDatabaseHelper? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) { instance ?: VerseDatabaseHelper(context.applicationContext).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE verses (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, reference TEXT, book TEXT)")
        db.execSQL("CREATE INDEX index_id ON verses(id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS verses"); onCreate(db)
    }

    fun getVerseCount(): Int {
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM verses", null).use { 
            if (it.moveToFirst()) it.getInt(0) else 0 
        }
    }

    fun getRandomVerseSet(secureRandom: SecureRandom): Verse? {
        val count = getVerseCount().toLong()
        if (count == 0L) return null
        val randomOffset = (secureRandom.nextDouble() * count).toLong()
        val cursor = readableDatabase.rawQuery("SELECT text, reference, book FROM verses LIMIT 3 OFFSET ?", arrayOf(randomOffset.toString()))
        
        val verses = mutableListOf<Verse>()
        var firstBook: String? = null
        cursor.use {
            while (it.moveToNext()) {
                val book = it.getString(2)
                if (firstBook == null) firstBook = book
                if (book == firstBook) verses.add(Verse(it.getString(0), it.getString(1))) else break
            }
        }
        if (verses.isEmpty()) return null

        val combinedText = verses.joinToString(" ") { it.text }
        val firstRef = verses.first().reference
        val displayRef = if (verses.size > 1) {
            try {
                "${firstRef.substringBeforeLast(":")}:${firstRef.substringAfterLast(":")}-${verses.last().reference.substringAfterLast(":")}"
            } catch (e: Exception) { firstRef }
        } else firstRef
        return Verse(combinedText, displayRef)
    }
}

class MediaDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context, "MediaDb", null, 1) {
    companion object {
        @Volatile private var instance: MediaDatabaseHelper? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) { instance ?: MediaDatabaseHelper(context.applicationContext).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE media_table (id INTEGER PRIMARY KEY AUTOINCREMENT, uri_string TEXT)")
        db.execSQL("CREATE INDEX index_media_id ON media_table(id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS media_table"); onCreate(db)
    }
    
    fun getMediaCount(): Int {
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM media_table", null).use { 
            if (it.moveToFirst()) it.getInt(0) else 0 
        }
    }

    fun syncFromMediaStore(context: Context) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM media_table")
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='media_table'")
            
            val selection = "${MediaStore.MediaColumns.SIZE} > 16384"
            val projection = arrayOf(MediaStore.MediaColumns._ID)

            val uris = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            
            for (contentUri in uris) {
                context.contentResolver.query(contentUri, projection, selection, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val uri = Uri.withAppendedPath(contentUri, cursor.getLong(idCol).toString()).toString()
                        val values = ContentValues().apply { put("uri_string", uri) }
                        db.insert("media_table", null, values)
                    }
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) { e.printStackTrace() } finally {
            db.endTransaction()
            db.execSQL("VACUUM")
        }
    }

    fun getRandomMediaUri(secureRandom: SecureRandom): Uri? {
        val count = getMediaCount().toLong()
        if (count == 0L) return null
        val randomOffset = (secureRandom.nextDouble() * count).toLong()
        return readableDatabase.rawQuery("SELECT uri_string FROM media_table LIMIT 1 OFFSET ?", arrayOf(randomOffset.toString())).use { 
            if (it.moveToFirst()) Uri.parse(it.getString(0)) else null 
        }
    }
}

class OverlayService : android.app.Service() {

    private val secureRandom = SecureRandom()
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayContainer: LifecycleComposeLayout? = null
    
    private var ttsManager: TTSManager? = null
    private var exoPlayer: ExoPlayer? = null 

    private var minDelayMs: Long = 60000L
    private var maxDelayMs: Long = 120000L
    private var keepScreenOn = false // Config Variable

    private val showRunnable = Runnable { showRandomContent() }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val minMin = it.getLongExtra("MIN_MINUTES", 1)
            val maxMin = it.getLongExtra("MAX_MINUTES", 2)
            keepScreenOn = it.getBooleanExtra("KEEP_SCREEN_ON", false) // Get Checkbox Value

            minDelayMs = minMin * 60 * 1000L
            maxDelayMs = maxMin * 60 * 1000L
            if (maxDelayMs < minDelayMs) maxDelayMs = minDelayMs
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ttsManager = TTSManager(this)
        
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer?.repeatMode = ExoPlayer.REPEAT_MODE_ONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("ok_overlay_channel", "OK Overlay", android.app.NotificationManager.IMPORTANCE_LOW)
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
            val notification = android.app.Notification.Builder(this, "ok_overlay_channel")
                .setContentTitle("Attention Panner")
                .setSmallIcon(android.R.drawable.ic_media_play).build()
            startForeground(1, notification)
        }

        handler.removeCallbacks(showRunnable)
        handler.postDelayed(showRunnable, 5000L)
    }

    private fun scheduleNextShow() {
        val range = maxDelayMs - minDelayMs
        val randomExtra = if (range > 0) (secureRandom.nextDouble() * range).toLong() else 0L
        handler.postDelayed(showRunnable, minDelayMs + randomExtra)
    }

    private fun showRandomContent() {
        CoroutineScope(Dispatchers.IO).launch {
            val verseDb = VerseDatabaseHelper.getInstance(applicationContext)
            val mediaDb = MediaDatabaseHelper.getInstance(applicationContext)
            
            val totalCount = verseDb.getVerseCount() + mediaDb.getMediaCount()
            if (totalCount == 0) {
                handler.post { Toast.makeText(applicationContext, "Library Empty", Toast.LENGTH_SHORT).show() }
                scheduleNextShow()
                return@launch
            }

            val randomTicket = (secureRandom.nextDouble() * totalCount).toLong()

            if (randomTicket < verseDb.getVerseCount()) {
                val verse = verseDb.getRandomVerseSet(secureRandom)
                if (verse != null) handler.post { 
                    createOverlay(null, verse)
                    ttsManager?.speak(verse.text)
                }
            } else {
                val uri = mediaDb.getRandomMediaUri(secureRandom)
                if (uri != null) handler.post { createOverlay(uri, null) }
            }
            scheduleNextShow()
        }
    }

    private fun createOverlay(uri: Uri?, verse: Verse?) {
        if (overlayContainer != null) hideOverlay()

        if (uri != null && contentResolver.getType(uri)?.startsWith("video/") == true) {
            exoPlayer?.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
        }

        overlayContainer = LifecycleComposeLayout(this).apply {
            setContent {
                MediaContent(
                    uri = uri, 
                    verse = verse, 
                    player = exoPlayer, 
                    onClose = { hideOverlay() }
                )
            }
        }

        // --- CONFIGURE WINDOW FLAGS BASED ON CHECKBOX ---
        var layoutFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        
        if (keepScreenOn) {
            // Keep screen on while overlay is visible, and wake it up if sleeping
            layoutFlags = layoutFlags or 
                          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                          WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            layoutFlags, // Use dynamic flags
            PixelFormat.TRANSLUCENT
        )

        overlayContainer?.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        overlayContainer?.handleLifecycleEvent(Lifecycle.Event.ON_START)
        overlayContainer?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager?.addView(overlayContainer, params)
    }

    private fun hideOverlay() {
        overlayContainer?.let {
            it.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager?.removeView(it)
            overlayContainer = null
        }
        exoPlayer?.stop() 
        exoPlayer?.clearMediaItems()
        ttsManager?.stop() 
    }

    override fun onDestroy() {
        handler.removeCallbacks(showRunnable)
        hideOverlay()
        ttsManager?.shutdown()
        exoPlayer?.release()
        super.onDestroy()
    }
}

@Composable
fun MediaContent(uri: Uri?, verse: Verse?, player: ExoPlayer?, onClose: () -> Unit) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), 
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight().heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)) 
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (uri != null) {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    if (mime.startsWith("image/")) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit, 
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        )
                    } else if (mime.startsWith("video/") && player != null) {
                        DisposableEffect(Unit) { onDispose { } } 
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
                            modifier = Modifier.fillMaxWidth().height(400.dp)
                        )
                    }
                } else if (verse != null) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = verse.text, style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic), color = Color.White, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "— ${verse.reference}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.Cyan, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), shape = CircleShape).size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

class LifecycleComposeLayout(context: Context) : FrameLayout(context), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var composeView: ComposeView? = null

    init {
        this.setViewTreeLifecycleOwner(this)
        this.setViewTreeSavedStateRegistryOwner(this)
    }

    fun setContent(content: @Composable () -> Unit) {
        if (composeView == null) {
            composeView = ComposeView(context).apply { setContent(content) }
            addView(composeView)
        } else { composeView?.setContent(content) }
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_CREATE) savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}