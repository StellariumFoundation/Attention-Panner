package ok.ok.ok

import android.Manifest
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

// --- IMPORTS FOR LIFECYCLE ---
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
// -----------------------------

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
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
        if (granted) {
            Toast.makeText(this, "Permissions Granted! Syncing...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions Denied.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        checkStoragePermissions()

        setContent {
            var minMinutes by remember { mutableStateOf("1") }
            var maxMinutes by remember { mutableStateOf("2") }
            
            var statusMessage by remember { mutableStateOf("Initializing...") }
            var isDownloading by remember { mutableStateOf(false) }
            var mediaCount by remember { mutableStateOf(0) }
            var verseCount by remember { mutableStateOf(0) }

            LaunchedEffect(Unit) {
                // 1. Check Verses & Download if empty
                withContext(Dispatchers.IO) {
                    val verseHelper = VerseDatabaseHelper.getInstance(applicationContext)
                    val count = verseHelper.getVerseCount()
                    
                    if (count == 0) {
                        isDownloading = true
                        withContext(Dispatchers.Main) { statusMessage = "Downloading Bible Data..." }
                        
                        // Download KJV
                        VerseLoader.downloadAndSaveKJV(verseHelper)
                        
                        // Download Sirach
                        withContext(Dispatchers.Main) { statusMessage = "Fetching Wisdom of Sirach..." }
                        VerseLoader.downloadAndSaveSirach(verseHelper)
                        
                        verseCount = verseHelper.getVerseCount()
                        isDownloading = false
                    } else {
                        verseCount = count
                    }
                }

                // 2. Check Media
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("OK Overlay Settings", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    
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

                    OutlinedTextField(
                        value = minMinutes,
                        onValueChange = { minMinutes = it },
                        label = { Text("Min Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Magenta,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.Magenta,
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = maxMinutes,
                        onValueChange = { maxMinutes = it },
                        label = { Text("Max Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Magenta,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.Magenta,
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (Settings.canDrawOverlays(this@MainActivity)) {
                                val intent = Intent(this@MainActivity, OverlayService::class.java)
                                intent.putExtra("MIN_MINUTES", minMinutes.toLongOrNull() ?: 1L)
                                intent.putExtra("MAX_MINUTES", maxMinutes.toLongOrNull() ?: 2L)
                                
                                startService(intent)
                                Toast.makeText(this@MainActivity, "Service Started!", Toast.LENGTH_SHORT).show()
                                moveTaskToBack(true)
                            } else {
                                Toast.makeText(this@MainActivity, "Permission required!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Start Service")
                    }
                }
            }
        }
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return true 
    }
}

// --- DATA CLASS ---
data class Verse(val text: String, val reference: String)

// --- VERSE DOWNLOADER ---
object VerseLoader {
    private const val KJV_URL = "https://openbible.com/textfiles/kjv.txt"
    private const val SIRACH_URL = "https://www.earlyjewishwritings.com/text/sirach.html"

    fun downloadAndSaveKJV(dbHelper: VerseDatabaseHelper) {
        try {
            val url = URL(KJV_URL)
            val connection = url.openConnection()
            val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
            
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.startsWith("Proverbs") || 
                        line.startsWith("Ecclesiastes") || 
                        line.startsWith("Song of Solomon")) {
                        
                        val firstSpace = line.indexOf(' ')
                        if (firstSpace != -1) {
                            val tabIndex = line.indexOf('\t')
                            val splitIndex = if (tabIndex != -1) tabIndex else {
                                var idx = firstSpace + 1
                                while (idx < line.length && (line[idx].isDigit() || line[idx] == ':')) { idx++ }
                                idx
                            }

                            if (splitIndex < line.length) {
                                val reference = line.substring(0, splitIndex).trim()
                                val text = line.substring(splitIndex).trim()
                                val book = reference.substringBeforeLast(" ") // Extract "Proverbs" from "Proverbs 1:1"
                                
                                val values = ContentValues().apply {
                                    put("text", text)
                                    put("reference", reference)
                                    put("book", book) // Store book for grouping logic
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
            val url = URL(SIRACH_URL)
            val connection = url.openConnection()
            val html = connection.getInputStream().bufferedReader().use { it.readText() }
            
            // Clean HTML
            val rawText = html.replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")

            // Regex for "1:1 Text" or "1:1. Text"
            val matcher = Pattern.compile("(\\d{1,2}):(\\d{1,3})[.]?\\s+(.*?)(?=\\d{1,2}:\\d{1,3}|$)").matcher(rawText)

            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                while (matcher.find()) {
                    val chapter = matcher.group(1)
                    val verseNum = matcher.group(2)
                    val text = matcher.group(3)?.trim() ?: ""
                    
                    if (text.isNotEmpty() && text.length > 5) {
                        val values = ContentValues().apply {
                            put("text", text)
                            put("reference", "Sirach $chapter:$verseNum")
                            put("book", "Sirach")
                        }
                        db.insert("verses", null, values)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// --- TTS MANAGER ---
class TTSManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

// --- VERSE DATABASE HELPER ---
class VerseDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context, "VerseDb", null, 2) {

    companion object {
        @Volatile
        private var instance: VerseDatabaseHelper? = null
        fun getInstance(context: Context): VerseDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: VerseDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Added 'book' column for filtering context
        db.execSQL("CREATE TABLE verses (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, reference TEXT, book TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS verses")
        onCreate(db)
    }

    fun getVerseCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM verses", null)
        var count = 0
        if (cursor.moveToFirst()) { count = cursor.getInt(0) }
        cursor.close()
        return count
    }

    // --- FETCH 3 CONSECUTIVE VERSES ---
    fun getRandomVerseSet(secureRandom: SecureRandom): Verse? {
        val db = readableDatabase
        val count = getVerseCount().toLong()
        if (count == 0L) return null

        // Pick a random spot
        val randomOffset = (secureRandom.nextDouble() * count).toLong()
        
        // Fetch 3 verses starting from that offset
        val query = "SELECT text, reference, book FROM verses LIMIT 3 OFFSET ?"
        val cursor = db.rawQuery(query, arrayOf(randomOffset.toString()))
        
        val verses = mutableListOf<Verse>()
        var firstBook: String? = null

        try {
            while (cursor.moveToNext()) {
                val text = cursor.getString(0)
                val ref = cursor.getString(1)
                val book = cursor.getString(2)

                // Only group verses if they are from the SAME book
                if (firstBook == null) firstBook = book
                if (book == firstBook) {
                    verses.add(Verse(text, ref))
                } else {
                    break // Stop if we hit a different book
                }
            }
        } finally {
            cursor.close()
        }

        if (verses.isEmpty()) return null

        // Combine Texts
        val combinedText = verses.joinToString(" ") { it.text }
        
        // Combine References (e.g., Proverbs 1:1, 1:2 -> Proverbs 1:1-2)
        val firstRef = verses.first().reference // "Proverbs 1:1"
        val lastRef = verses.last().reference // "Proverbs 1:3"
        
        val displayRef = if (verses.size > 1) {
            try {
                val bookChap = firstRef.substringBeforeLast(":") // "Proverbs 1"
                val startNum = firstRef.substringAfterLast(":") // "1"
                val endNum = lastRef.substringAfterLast(":") // "3"
                "$bookChap:$startNum-$endNum"
            } catch (e: Exception) {
                firstRef // Fallback if format is weird
            }
        } else {
            firstRef
        }

        return Verse(combinedText, displayRef)
    }
}

// --- MEDIA DATABASE HELPER ---
class MediaDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context, "MediaDb", null, 1) {

    companion object {
        @Volatile
        private var instance: MediaDatabaseHelper? = null
        fun getInstance(context: Context): MediaDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: MediaDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE media_table (id INTEGER PRIMARY KEY AUTOINCREMENT, uri_string TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS media_table")
        onCreate(db)
    }
    
    fun getMediaCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM media_table", null)
        var count = 0
        if (cursor.moveToFirst()) { count = cursor.getInt(0) }
        cursor.close()
        return count
    }

    fun syncFromMediaStore(context: Context) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM media_table")
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='media_table'")
            
            // --- FILTER: Only items > 16KB (16 * 1024 bytes) ---
            val selection = "${MediaStore.MediaColumns.SIZE} > 16384"
            val projection = arrayOf(MediaStore.MediaColumns._ID)

            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(imageUri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(imageUri, id.toString()).toString()
                    val values = ContentValues().apply { put("uri_string", uri) }
                    db.insert("media_table", null, values)
                }
            }

            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(videoUri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(videoUri, id.toString()).toString()
                    val values = ContentValues().apply { put("uri_string", uri) }
                    db.insert("media_table", null, values)
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) { e.printStackTrace() } finally {
            db.endTransaction()
            db.execSQL("VACUUM")
        }
    }

    fun getRandomMediaUri(secureRandom: SecureRandom): Uri? {
        val db = readableDatabase
        val count = getMediaCount().toLong()
        if (count == 0L) return null
        val randomOffset = (secureRandom.nextDouble() * count).toLong()
        val query = "SELECT uri_string FROM media_table LIMIT 1 OFFSET ?"
        val dataCursor = db.rawQuery(query, arrayOf(randomOffset.toString()))
        var uriString: String? = null
        if (dataCursor.moveToFirst()) { uriString = dataCursor.getString(0) }
        dataCursor.close()
        return uriString?.let { Uri.parse(it) }
    }
}

class OverlayService : android.app.Service() {

    private val secureRandom = SecureRandom()
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayContainer: LifecycleComposeLayout? = null
    private var ttsManager: TTSManager? = null

    private var minDelayMs: Long = 1 * 60 * 1000L
    private var maxDelayMs: Long = 2 * 60 * 1000L

    private val showRunnable = Runnable { showRandomContent() }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val minMin = it.getLongExtra("MIN_MINUTES", 1)
            val maxMin = it.getLongExtra("MAX_MINUTES", 2)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("ok_overlay_channel", "OK Overlay", android.app.NotificationManager.IMPORTANCE_LOW)
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
            val notification = android.app.Notification.Builder(this, "ok_overlay_channel")
                .setContentTitle("OK Overlay Active")
                .setContentText("Displaying random content")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
            startForeground(1, notification)
        } else {
            startForeground(1, android.app.Notification.Builder(this)
                .setContentTitle("OK Overlay Active")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build())
        }

        handler.removeCallbacks(showRunnable)
        handler.postDelayed(showRunnable, 5000L)
    }

    private fun scheduleNextShow() {
        val range = maxDelayMs - minDelayMs
        val randomExtra = if (range > 0) (secureRandom.nextDouble() * range).toLong() else 0L
        val delay = minDelayMs + randomExtra
        val seconds = delay / 1000
        handler.post {
            Toast.makeText(applicationContext, "Next content in $seconds seconds", Toast.LENGTH_SHORT).show()
        }
        handler.postDelayed(showRunnable, delay)
    }

    private fun showRandomContent() {
        // --- 30% Chance for Verse ---
        val chance = secureRandom.nextInt(100)
        if (chance < 30) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = VerseDatabaseHelper.getInstance(applicationContext)
                // New Function: Gets 3 consecutive verses
                val verse = db.getRandomVerseSet(secureRandom)
                
                if (verse != null) {
                    handler.post {
                        createOverlay(null, verse)
                        ttsManager?.speak(verse.text)
                    }
                } else {
                    showRandomMedia()
                }
            }
            scheduleNextShow()
        } else {
            showRandomMedia()
            scheduleNextShow()
        }
    }

    private fun showRandomMedia() {
        CoroutineScope(Dispatchers.IO).launch {
            val dbHelper = MediaDatabaseHelper.getInstance(applicationContext)
            val uri = dbHelper.getRandomMediaUri(secureRandom)
            if (uri != null) {
                handler.post { createOverlay(uri, null) }
            } else {
                handler.post { Toast.makeText(applicationContext, "No media found.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun createOverlay(uri: Uri?, verse: Verse?) {
        if (overlayContainer != null) { hideOverlay() }

        overlayContainer = LifecycleComposeLayout(this).apply {
            setContent {
                MediaContent(uri = uri, verse = verse, onClose = { hideOverlay() })
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
    }

    override fun onDestroy() {
        handler.removeCallbacks(showRunnable)
        hideOverlay()
        ttsManager?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MediaContent(uri: Uri?, verse: Verse?, onClose: () -> Unit) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)), 
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)) 
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                
                if (uri != null) {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    if (mime.startsWith("image/")) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit, 
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        )
                    } else if (mime.startsWith("video/")) {
                        val exoPlayer = remember {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(uri))
                                prepare()
                                playWhenReady = true
                                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                            }
                        }
                        DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false } },
                            modifier = Modifier.fillMaxWidth().height(400.dp)
                        )
                    }
                } 
                else if (verse != null) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = verse.text,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "— ${verse.reference}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Cyan,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        .size(32.dp)
                ) {
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
        } else {
            composeView?.setContent(content)
        }
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_CREATE) { savedStateRegistryController.performRestore(null) }
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}