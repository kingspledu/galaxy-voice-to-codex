package com.kuiro.youtubemarker

import android.app.*
import android.content.*
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.*
import com.kuiro.youtubemarker.ai.*
import com.kuiro.youtubemarker.data.*
import com.kuiro.youtubemarker.export.NoteBackup
import com.kuiro.youtubemarker.recording.CaptureGate
import com.kuiro.youtubemarker.youtube.YouTubeLinkParser
import java.io.File

class NoteDetailActivity : Activity() {
    private val dao by lazy { NoteDatabase.get(this).notes() }
    private var note: NoteEntity? = null
    private lateinit var noteId: String
    private lateinit var info: TextView
    private val fields = linkedMapOf<String, EditText>()
    private var player: MediaPlayer? = null
    private var saving = false
    private var savedValues = emptyMap<String, String>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        noteId = intent.getStringExtra("id") ?: run { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24); setBackgroundColor(-1) }
        fun label(value: String) = TextView(this).apply { text = value; setTextColor(0xff111111.toInt()); root.addView(this) }
        label("메모 상세").textSize = 24f
        info = label("불러오는 중…")
        fun field(key: String, title: String, multiline: Boolean = false) {
            label(title)
            fields[key] = EditText(this).apply {
                setTextColor(0xff111111.toInt()); setSingleLine(!multiline)
                if (multiline) { minLines = 3; gravity = android.view.Gravity.TOP }
                root.addView(this)
            }
        }
        field("title", "영상 제목")
        field("url", "YouTube URL")
        field("time", "영상 재생 시점 (분:초 또는 시:분:초)")
        field("transcript", "사용자 음성 메모 전사 (로컬 변환·직접 수정)", true)
        field("captions", "선택 자막 원문 (직접 붙여넣기·자동 수집 아님)", true)
        field("summary", "AI 요약 결과 (직접 붙여넣기)", true)
        field("tags", "태그 (쉼표로 구분)")
        field("category", "카테고리")
        fun action(title: String, block: () -> Unit) { root.addView(Button(this).apply { text = title; setOnClickListener { block() } }) }
        action("변경 내용 저장") { save() }
        action("녹음 재생 / 정지") { play() }
        action("기기 내 무료 음성 변환 / 재시도") { requestTranscription() }
        action("음성 변환 작업 상태 확인") { transcriptionStatus() }
        action("영상 시점 열기 (저장된 정보)") { openVideo() }
        action("Gemini용 프롬프트 복사 (현재 입력)") { copyPrompt() }
        action("저장된 메모 + 녹음 ZIP 백업") { export() }
        action("메모와 녹음 삭제") { confirmDelete() }
        action("목록으로") { closeEditor() }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
        NoteDatabase.executor.execute {
            try {
                val entry = dao.find(noteId)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (entry == null) { toast("메모가 없습니다."); finish(); return@runOnUiThread }
                    note = entry.copy(updatedAtEpochMs = state?.getLong("revision", entry.updatedAtEpochMs) ?: entry.updatedAtEpochMs)
                    val values = mapOf("title" to entry.videoTitle, "url" to entry.videoUrl,
                        "time" to entry.timestampSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }.orEmpty(),
                        "transcript" to entry.transcript, "captions" to entry.captions, "summary" to entry.summary,
                        "tags" to entry.tags, "category" to entry.category)
                    savedValues = values
                    fields.forEach { (key, field) -> field.setText(state?.getString(key) ?: values[key].orEmpty()) }
                    info.text = "생성 ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.createdAtEpochMs))}\n영상 시점은 저장 시각과 별개입니다. 변환 결과를 불러오려면 상세 화면을 다시 열어 주세요."
                }
            } catch (_: Exception) { toast("메모를 불러오지 못했습니다.") }
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        fields.forEach { (key, field) -> out.putString(key, field.text.toString()) }
        note?.let { out.putLong("revision", it.updatedAtEpochMs) }; super.onSaveInstanceState(out)
    }
    override fun onStop() { stopPlayback(); super.onStop() }
    @Deprecated("Legacy back handling")
    override fun onBackPressed() { closeEditor() }
    private fun closeEditor() {
        if (saving) { toast("저장이 끝날 때까지 기다려 주세요."); return }
        if (note == null || fields.all { (key, field) -> field.text.toString() == savedValues[key] }) { finish(); return }
        AlertDialog.Builder(this).setTitle("저장하지 않은 변경이 있습니다.")
            .setMessage("편집을 계속하거나 저장 후 목록으로 돌아갈 수 있습니다.")
            .setNegativeButton("계속 편집", null).setPositiveButton("변경 버리고 닫기") { _, _ -> finish() }.show()
    }
    private fun value(key: String) = fields.getValue(key).text.toString()
    private fun seconds(): Long? {
        val raw = value("time").trim()
        if (raw.isEmpty()) return null
        require(Regex("\\d{1,5}:[0-5]\\d(:[0-5]\\d)?").matches(raw)) { "영상 시점 예: 12:34 또는 1:02:03" }
        return raw.split(':').fold(0L) { n, part -> n * 60 + part.toLong() }
    }
    private fun idle(): Boolean {
        val entry = note ?: return false
        if (saving || entry.status in setOf("RECORDING", "TRANSCRIBING") || CaptureGate.owner == noteId) {
            toast("녹음·저장이 끝난 뒤 상세 화면을 다시 열어 주세요."); return false
        }
        return true
    }
    private fun save() {
        if (!idle()) return
        val entry = note ?: return
        val link = value("url").trim(); val id = YouTubeLinkParser.videoId(link)
        if (link.isNotEmpty() && id == null) { fields.getValue("url").error = "YouTube URL을 확인하세요."; return }
        val time = try { seconds() } catch (e: IllegalArgumentException) { toast(e.message.orEmpty()); return }
        val edited = entry.copy(videoTitle = value("title"), videoUrl = link, videoId = id, timestampSeconds = time,
            transcript = value("transcript"), captions = value("captions"), summary = value("summary"),
            tags = value("tags"), category = value("category"), updatedAtEpochMs = maxOf(System.currentTimeMillis(), entry.updatedAtEpochMs + 1))
        val draft = fields.mapValues { it.value.text.toString() }
        saving = true
        NoteDatabase.executor.execute {
            try {
                val count = dao.edit(entry.id, entry.updatedAtEpochMs, edited.videoTitle, link, id, time,
                    edited.transcript, edited.captions, edited.summary, edited.tags, edited.category, edited.updatedAtEpochMs)
                runOnUiThread {
                    saving = false
                    if (count == 1) { note = edited; savedValues = draft; toast("저장했습니다.") }
                    else toast("다른 곳에서 메모가 변경됐습니다. 입력을 복사해 두고 화면을 다시 열어 주세요.")
                }
            } catch (_: Exception) { runOnUiThread { saving = false; toast("저장 실패 · 입력 내용과 녹음은 유지됩니다.") } }
        }
    }
    private fun copyPrompt() {
        val time = try { seconds() } catch (e: IllegalArgumentException) { toast(e.message.orEmpty()); return }
        val prompt = GeminiPrompt.build(PromptSources(value("title"), value("url"), time, value("transcript"), value("captions")))
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gemini 정리 프롬프트", prompt))
        toast("복사했습니다. Gemini에 붙여넣어 사용하세요. 자동 전송하지 않습니다.")
    }
    private fun requestTranscription() {
        if (!idle()) return
        if (note?.transcript?.isNotBlank() == true || value("transcript").isNotBlank()) { toast("기존 전사는 덮어쓰지 않습니다. 필요하면 백업 후 전사 칸을 비우고 저장하세요."); return }
        if (fields.any { (key, field) -> field.text.toString() != savedValues[key] }) { toast("입력 내용을 먼저 저장하세요."); return }
        val model = com.kuiro.youtubemarker.transcription.OfflineSettings(this).model
        if (!com.kuiro.youtubemarker.transcription.ModelCatalog.installed(this, model)) { toast("음성인식 설정에서 선택 모델을 먼저 다운로드하세요."); return }
        AlertDialog.Builder(this).setTitle("기기 내 무료 음성 변환")
            .setMessage("${model.label}\n음성은 기기 안에서 처리하며 외부로 전송하지 않습니다. 현재 최대 2분 녹음을 지원합니다. 원본 파일은 보존합니다.")
            .setNegativeButton("취소", null).setPositiveButton("로컬 변환") { _, _ ->
                com.kuiro.youtubemarker.transcription.TranscriptionQueue.enqueue(this, noteId)
                toast("로컬 변환을 요청했습니다. 결과를 보려면 작업 상태를 확인하고 화면을 다시 여세요.")
            }.show()
    }
    private fun transcriptionStatus() {
        NoteDatabase.executor.execute {
            try {
                val jobs = androidx.work.WorkManager.getInstance(this).getWorkInfosForUniqueWork(com.kuiro.youtubemarker.transcription.TranscriptionQueue.name(noteId)).get()
                val text = jobs.joinToString("\n") {
                    "${it.state} · ${it.outputData.getString("message").orEmpty()}\n모델 ${it.outputData.getString("model").orEmpty()} · ${it.outputData.getLong("elapsedMs", 0)}ms · PSS 전/후 ${it.outputData.getLong("pssBeforeKb", 0)}/${it.outputData.getLong("pssAfterKb", 0)}KB (최고값 아님)"
                }.ifEmpty { "요청된 작업이 없습니다." }
                runOnUiThread { if (!isDestroyed) AlertDialog.Builder(this).setTitle("음성 변환 작업").setMessage(text + "\n결과를 보려면 목록으로 나갔다 다시 여세요.").setPositiveButton("확인", null).show() }
            } catch (_: Exception) { toast("작업 상태를 확인하지 못했습니다.") }
        }
    }
    private fun play() {
        if (player != null) { stopPlayback(); return }
        if (!idle() || CaptureGate.owner != null) { toast("녹음 종료 후 재생하세요."); return }
        try { player = MediaPlayer().also { p ->
            p.setDataSource(note!!.audioPath); p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { stopPlayback() }
            p.setOnErrorListener { _, _, _ -> stopPlayback(); toast("재생할 수 없습니다. 파일은 보존됩니다."); true }
            p.prepareAsync()
        } } catch (_: Exception) { stopPlayback(); toast("녹음 파일을 재생할 수 없습니다.") }
    }
    private fun stopPlayback() { player?.release(); player = null }
    private fun openVideo() {
        val entry = note ?: return
        val id = entry.videoId ?: run { toast("연결된 영상이 없습니다."); return }
        val link = "https://www.youtube.com/watch?v=$id" + (entry.timestampSeconds?.let { "&t=${it}s" } ?: "")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))) }.onFailure { toast("영상을 열 앱이 없습니다.") }
    }
    private fun export() {
        if (!idle()) return
        stopPlayback()
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "voice-note-$noteId.zip")
            }, 40)
        }.onFailure { toast("파일 저장 앱을 열지 못했습니다.") }
    }
    @Deprecated("Legacy document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 40 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        NoteDatabase.executor.execute {
            try {
                val entry = dao.find(noteId) ?: error("Missing note")
                check(entry.status != "RECORDING")
                val output = contentResolver.openOutputStream(uri, "w") ?: error("Cannot open output")
                output.use { NoteBackup.write(entry, File(noBackupFilesDir, "recordings"), it) }
                toast(if (File(entry.audioPath).length() > 0) "ZIP 백업 완료" else "메모 백업 완료 · 녹음 파일이 없거나 비어 있습니다.")
            } catch (_: Exception) { toast("백업 실패 · 불완전한 ZIP은 저장 위치에서 삭제 후 다시 시도하세요. 원본은 보존됩니다.") }
        }
    }
    private fun confirmDelete() {
        if (!idle()) return
        val entry = note ?: return
        AlertDialog.Builder(this).setTitle("메모와 녹음을 삭제할까요?")
            .setMessage("복구할 수 없습니다. 필요한 녹음은 먼저 ZIP으로 백업하세요. 저장하지 않은 입력도 삭제됩니다.")
            .setNegativeButton("취소", null).setPositiveButton("영구 삭제") { _, _ ->
                stopPlayback(); saving = true
                NoteDatabase.executor.execute {
                    try {
                        val deleted = dao.deleteInactive(entry.id, entry.updatedAtEpochMs)
                        if (deleted != 1) error("Changed note")
                        val removed = runCatching {
                            val file = File(entry.audioPath).canonicalFile
                            val allowed = file.parentFile == File(noBackupFilesDir, "recordings").canonicalFile
                            allowed && (!file.exists() || file.delete())
                        }.getOrDefault(false)
                        runOnUiThread {
                            saving = false; toast(if (removed) "삭제했습니다." else "메모는 삭제됐지만 녹음 파일 정리에 실패했습니다."); finish()
                        }
                    } catch (_: Exception) { runOnUiThread { saving = false; toast("삭제하지 못했습니다. 녹음 상태 또는 변경 내용을 확인하세요.") } }
                }
            }.show()
    }
    private fun toast(value: String) { runOnUiThread { if (!isDestroyed) Toast.makeText(this, value, Toast.LENGTH_LONG).show() } }
}
