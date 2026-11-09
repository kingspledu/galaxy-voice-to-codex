package com.kuiro.youtubemarker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.*
import android.text.*
import com.kuiro.youtubemarker.data.*
import com.kuiro.youtubemarker.recording.LocalRecorder
import com.kuiro.youtubemarker.recording.CaptureGate
import com.kuiro.youtubemarker.overlay.FloatingButtonService
import android.provider.Settings
import com.kuiro.youtubemarker.youtube.YouTubeLinkParser
import java.io.File
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var heading: EditText
    private lateinit var url: EditText
    private lateinit var position: EditText
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var button: Button
    private lateinit var rows: LinearLayout
    private val recorder by lazy { LocalRecorder(this) }
    private val dao by lazy { NoteDatabase.get(this).notes() }
    private var current: NoteEntity? = null
    private var notes = emptyList<NoteEntity>()
    private var player: MediaPlayer? = null
    private var visible = false
    private var busy = false
    private var pendingOnly = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24)
            setBackgroundColor(-1); fitsSystemWindows = true
        }
        fun label(value: String) = TextView(this).apply { text = value; setTextColor(0xff111111.toInt()); root.addView(this) }
        fun field(value: String) = EditText(this).apply {
            hint = value; setTextColor(0xff111111.toInt()); setHintTextColor(0xff666666.toInt()); root.addView(this)
        }
        label("YouTube Voice Marker").textSize = 24f
        label("영상 시점은 직접 입력합니다. 저장 시각과 구분됩니다.")
        heading = field("영상 제목 (선택)")
        url = field("YouTube URL (선택)")
        position = field("영상 시점 분:초 또는 시:분:초 (선택)")
        status = label("녹음은 기기에만 저장됩니다.")
        button = Button(this).apply { root.addView(this); setOnClickListener { if (current != null) finishRecording() else requestRecording() } }
        root.addView(Button(this).apply {
            text = "플로팅 버튼 켜기 / 끄기"
            setOnClickListener {
                if (FloatingButtonService.running) stopService(Intent(this@MainActivity, FloatingButtonService::class.java))
                else if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                    message("다른 앱 위 표시를 허용한 뒤 버튼을 다시 눌러 주세요.")
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
                    getSharedPreferences("video", MODE_PRIVATE).edit().putString("title", heading.text.toString()).putString("url", url.text.toString()).apply()
                    runCatching { startForegroundService(Intent(this@MainActivity, FloatingButtonService::class.java)) }
                        .onFailure { message("플로팅 버튼을 시작하지 못했습니다. 권한을 확인하세요.") }
                }
            }
        })
        search = field("메모 검색")
        root.addView(Button(this).apply { text = "음성인식 설정"; setOnClickListener { startActivity(Intent(this@MainActivity, TranscriptionSettingsActivity::class.java)) } })
        root.addView(CheckBox(this).apply {
            text = "처리 대기만 보기 (변환 대기·실패·중단)"
            setTextColor(0xff111111.toInt())
            setOnCheckedChangeListener { _, checked -> pendingOnly = checked; render() }
        })
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(rows) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root); controls()
        heading.setText(savedInstanceState?.getString("heading").orEmpty())
        url.setText(savedInstanceState?.getString("url").orEmpty())
        position.setText(savedInstanceState?.getString("position").orEmpty())
        if (savedInstanceState == null) receiveShare(intent)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) {}
        })
        NoteDatabase.executor.execute { try { NoteDatabase.recoverOnce(dao); refresh() } catch (_: Exception) { message("저장소를 열지 못했습니다.") } }
    }
    override fun onStart() {
        super.onStart(); visible = true
        NoteDatabase.executor.execute { runCatching { refresh() } }
    }
    override fun onStop() { visible = false; if (current != null) finishRecording(); stopPlayback(); super.onStop() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("heading", heading.text.toString()); out.putString("url", url.text.toString())
        out.putString("position", position.text.toString()); super.onSaveInstanceState(out)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (current != null || busy) message("녹음 저장 후 다시 공유해 주세요.") else receiveShare(intent)
    }
    private fun receiveShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val found = Regex("https?://[^\\s]+").findAll(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            .map { it.value }.firstOrNull { YouTubeLinkParser.videoId(it) != null }
        if (found != null) {
            url.setText(found)
            heading.setText(""); position.setText("")
            getSharedPreferences("video", MODE_PRIVATE).edit().putString("url", found).putString("title", "").apply()
        } else message("유효한 YouTube 링크가 없습니다.")
    }
    private fun requestRecording() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        else startRecording()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 11) return
        if (code == 10 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED && visible) startRecording()
        else message("녹음하려면 설정에서 마이크 권한을 허용해 주세요.")
    }
    private fun startRecording() {
        if (busy || current != null) return
        if (CaptureGate.owner != null) { message("다른 창에서 진행 중인 녹음을 먼저 저장하세요."); return }
        val raw = position.text.toString().trim()
        if (raw.isNotEmpty() && !Regex("\\d{1,5}:[0-5]\\d(:[0-5]\\d)?").matches(raw)) { position.error = "예: 12:34 또는 1:02:03"; return }
        val seconds = if (raw.isEmpty()) null else raw.split(':').fold(0L) { n, p -> n * 60 + p.toLong() }
        val link = url.text.toString().trim()
        val videoId = YouTubeLinkParser.videoId(link)
        if (link.isNotEmpty() && videoId == null) { url.error = "YouTube URL을 확인하세요."; return }
        stopPlayback()
        val dir = File(noBackupFilesDir, "recordings")
        if (!dir.isDirectory && !dir.mkdirs()) { message("저장 폴더를 만들지 못했습니다."); return }
        val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        if (!CaptureGate.claim(id)) return
        val file = File(dir, "$id.m4a")
        val note = NoteEntity(id, heading.text.toString(), link, videoId, seconds, file.absolutePath, createdAtEpochMs = now, updatedAtEpochMs = now)
        busy = true; controls()
        NoteDatabase.executor.execute {
            try {
                dao.insert(note)
                runOnUiThread {
                    if (!visible) { saveStatus(id, "INTERRUPTED"); CaptureGate.release(id); busy = false; controls(); return@runOnUiThread }
                    try { recorder.start(file); current = note; status.text = "녹음 중 · 화면을 벗어나면 자동 저장합니다." }
                    catch (_: Exception) { CaptureGate.release(id); saveStatus(id, "FAILED"); status.text = "녹음 시작 실패 · 마이크와 저장공간을 확인하세요." }
                    busy = false; controls()
                }
            } catch (_: Exception) { runOnUiThread { CaptureGate.release(id); busy = false; controls(); status.text = "저장소 오류로 녹음을 시작하지 않았습니다." } }
        }
    }
    private fun finishRecording() {
        val note = current ?: return; current = null
        val ok = recorder.stop()
        CaptureGate.release(note.id)
        status.text = if (ok) "녹음 저장 완료 · 음성 변환 대기" else "녹음 종료 오류 · 남은 파일은 보존했습니다."
        controls(); saveStatus(note.id, if (ok) "RECORDED" else "FAILED")
    }
    private fun saveStatus(id: String, state: String) {
        NoteDatabase.executor.execute {
            try {
                dao.status(id, state, System.currentTimeMillis())
                if (state == "RECORDED") runCatching { com.kuiro.youtubemarker.transcription.TranscriptionQueue.afterRecording(this, id) }
                refresh()
            }
            catch (_: Exception) { message("DB 갱신 실패 · 파일은 보존됩니다. 다음 실행에서 중단된 메모를 확인하세요.") }
        }
    }
    private fun refresh() { val result = dao.all(); runOnUiThread { if (!isDestroyed) { notes = result; render() } } }
    private fun controls() {
        button.isEnabled = !busy
        button.text = if (current != null) "■ 종료하고 저장" else if (busy) "저장소 준비 중…" else "● 녹음 시작"
        listOf(heading, url, position).forEach { it.isEnabled = current == null && !busy }
    }
    private fun render() {
        rows.removeAllViews(); val query = search.text.toString().trim()
        val filtered = notes.filter {
            (!pendingOnly || it.status in setOf("RECORDED", "FAILED", "INTERRUPTED", "TRANSCRIBING")) &&
                listOf(it.videoTitle, it.videoUrl, it.transcript, it.captions, it.summary, it.tags, it.category).any { v -> v.contains(query, true) }
        }
        if (filtered.isEmpty()) rows.addView(TextView(this).apply { text = "저장된 메모가 없습니다." })
        filtered.forEach { note -> rows.addView(Button(this).apply {
            val time = note.timestampSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "미입력"
            text = "${note.videoTitle.ifBlank { "음성 메모" }}\n영상 시점 $time · ${stateLabel(note.status)}\n${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.createdAtEpochMs))}"
            setOnClickListener { detail(note) }
        }) }
    }
    private fun detail(note: NoteEntity) {
        startActivity(Intent(this, NoteDetailActivity::class.java).putExtra("id", note.id))
    }
    private fun play(note: NoteEntity) {
        if (current != null || busy || CaptureGate.owner != null) { message("녹음 저장 후 재생하세요."); return }
        stopPlayback()
        try { player = MediaPlayer().also { p ->
            p.setDataSource(note.audioPath); p.setOnCompletionListener { stopPlayback() }
            p.setOnErrorListener { _, _, _ -> stopPlayback(); message("재생할 수 없습니다. 파일은 보존됩니다."); true }
            p.setOnPreparedListener { it.start() }; p.prepareAsync()
        } } catch (_: Exception) { stopPlayback(); message("재생할 수 없습니다.") }
    }
    private fun stopPlayback() { player?.release(); player = null }
    private fun stateLabel(value: String) = when (value) {
        "RECORDED" -> "변환 대기"
        "RECORDING" -> "녹음 중"
        "FAILED" -> "녹음 오류"
        "INTERRUPTED" -> "중단됨 · 파일 확인 필요"
        "TRANSCRIBING" -> "음성 변환 중"
        "TRANSCRIBED" -> "변환 완료"
        else -> value
    }
    private fun message(value: String) { runOnUiThread { if (!isDestroyed) Toast.makeText(this, value, Toast.LENGTH_LONG).show() } }
}
