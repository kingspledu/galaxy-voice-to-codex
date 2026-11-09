package com.kuiro.youtubemarker

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.work.WorkManager
import com.kuiro.youtubemarker.data.NoteDatabase
import com.kuiro.youtubemarker.transcription.*

class TranscriptionSettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val config = OfflineSettings(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24); setBackgroundColor(-1) }
        fun label(text: String) = TextView(this).apply { this.text = text; setTextColor(0xff111111.toInt()); root.addView(this) }
        label("무료 기기 내 Whisper").textSize = 24f
        label("음성을 외부로 전송하지 않습니다. 최초 모델 다운로드 후 인터넷 없이 변환합니다. 한국어 메모, 현재 최대 2분을 대상으로 합니다. Galaxy 성능은 실측 전입니다.")
        val models = ModelCatalog.models
        val model = Spinner(this).apply {
            adapter = ArrayAdapter(this@TranscriptionSettingsActivity, android.R.layout.simple_spinner_dropdown_item, models.map { it.label })
            setSelection(models.indexOfFirst { it.id == config.model.id }.coerceAtLeast(0)); root.addView(this)
        }
        val automatic = CheckBox(this).apply { text = "새 녹음 저장 후 자동 로컬 변환"; setTextColor(0xff111111.toInt()); isChecked = config.automatic; root.addView(this) }
        label("base부터 시험하세요. small은 정확도·속도 비교용, large-v3-turbo는 메모리·발열 검증이 필요한 실험용입니다. 여유 메모리 검사는 측정 결과가 아닌 보수적 사전 조건입니다.")
        val status = label("선택 모델을 저장한 뒤 다운로드하세요. 다운로드 진행은 상태 확인으로 볼 수 있습니다.")
        fun action(text: String, run: () -> Unit) { root.addView(Button(this).apply { this.text = text; setOnClickListener { run() } }) }
        action("모델·자동 변환 설정 저장") {
            val selected = models[model.selectedItemPosition]
            val auto = automatic.isChecked
            fun save() {
                NoteDatabase.executor.execute {
                    val ok = runCatching { config.save(selected.id, auto) }.isSuccess
                    runOnUiThread { status.text = if (ok) "저장했습니다. 모델 다운로드 상태를 확인하세요." else "설정 저장 실패" }
                }
            }
            if (selected.id.startsWith("large")) AlertDialog.Builder(this).setTitle("실험 모델 선택")
                .setMessage("Galaxy S23 Ultra 성능·안정성을 아직 측정하지 않았습니다. 메모리 부족으로 앱이 종료될 수 있으니 원본을 백업하고 비교 시험에 사용하세요.")
                .setNegativeButton("취소", null).setPositiveButton("실험 모델로 저장") { _, _ -> save() }.show()
            else save()
        }
        action("선택 모델 다운로드") {
            val selected = models[model.selectedItemPosition]
            AlertDialog.Builder(this).setTitle("모델 다운로드")
                .setMessage("${selected.label}\n무료 공개 모델 파일만 내려받습니다. Wi-Fi 사용을 권장합니다. 녹음 파일은 전송하지 않습니다.")
                .setNegativeButton("취소", null).setPositiveButton("다운로드") { _, _ ->
                    ModelDownloadWorker.enqueue(this, selected.id); status.text = "다운로드 대기 중 · 모델 설정 저장도 확인하세요."
                }.show()
        }
        action("다운로드·설치 상태 확인") {
            val selected = models[model.selectedItemPosition]
            NoteDatabase.executor.execute {
                val result = runCatching {
                    val work = WorkManager.getInstance(this).getWorkInfosForUniqueWork(ModelDownloadWorker.name(selected.id)).get()
                    val installed = ModelCatalog.installed(this, selected)
                    "${selected.label}\n파일 ${if (installed) "설치됨" else "없음/불완전"}\n" + work.joinToString("\n") { "${it.state} ${it.progress.getInt("percent", 0)}% ${it.outputData.getString("message").orEmpty()}" }
                }.getOrDefault("상태 확인 실패")
                runOnUiThread { status.text = result }
            }
        }
        action("마이크·알림 권한 설정") { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
        action("돌아가기") { finish() }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }
}
