package com.kuiro.youtubemarker.ai

import org.junit.Assert.*
import org.junit.Test

class GeminiPromptTest {
    @Test fun missingCaptionsDoNotBecomeVideoClaims() {
        val prompt = GeminiPrompt.build(PromptSources("", "", null, "내 의견", ""))
        assertTrue(prompt.contains("영상 원문 확인 불가"))
        assertTrue(prompt.contains("영상 재생 시점(초): 미입력"))
        assertTrue(prompt.contains("음성 메모를 영상의 발언으로 인용하지 마세요"))
        assertFalse(prompt.contains("음성 파일을 분석했습니다"))
    }
    @Test fun suppliedDataRemainsQuotedAndSourceSeparated() {
        val prompt = GeminiPrompt.build(PromptSources("제목", "https://youtu.be/abcdefghijk", 754, "의견\n\"인용\"", "영상 원문"))
        assertTrue(prompt.contains("영상 재생 시점(초): 754"))
        assertTrue(prompt.contains("\"의견\\n\\\"인용\\\"\""))
        assertTrue(prompt.contains("사용자 음성 메모 전사"))
        assertTrue(prompt.contains("영상 원문 자막"))
        assertTrue(prompt.contains("\"영상 원문\""))
    }
}
