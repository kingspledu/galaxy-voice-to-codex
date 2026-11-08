(() => {
  'use strict';

  const $ = (selector) => document.querySelector(selector);
  const els = {
    passage: $('#passage'),
    passageFile: $('#passageFile'),
    passageFileState: $('#passageFileState'),
    targetRange: $('#targetRange'),
    examPrompt: $('#examPrompt'),
    buildExamPrompt: $('#buildExamPrompt'),
    copyExamPrompt: $('#copyExamPrompt'),
    examCopyState: $('#examCopyState'),
    prompt: $('#prompt'),
    copyPrompt: $('#copyPrompt'),
    copyState: $('#copyState'),
    htmlInput: $('#htmlInput'),
    preview: $('#preview'),
    sampleHtml: $('#sampleHtml'),
    printPdf: $('#printPdf'),
    zoomIn: $('#zoomIn'),
    zoomOut: $('#zoomOut'),
    zoomValue: $('#zoomValue'),
    resetBtn: $('#resetBtn'),
    fontSize: $('#fontSize'),
    fontSizeOut: $('#fontSizeOut'),
    letterSpacing: $('#letterSpacing'),
    letterOut: $('#letterOut'),
    lineHeight: $('#lineHeight'),
    lineOut: $('#lineOut')
  };

  const WORKBOOK_RULES = `첨부 MD 파일의 모든 대상 문항을 각각 처리해, 아래 구조 샘플과 동일한 간결한 영어 지문 분석 학습지 HTML을 만든다. 첫 문항만 선택하지 말고 MD의 모든 문항을 순서대로 반복한다. 시험명·월·학년·교재명·문항 번호·원본 문제 유형은 첨부 MD에서 자동 추출한다.

[원문 정리] PDF 복사로 붙은 단어의 띄어쓰기, 문장부호 뒤 공백, 줄바꿈과 문단 구분을 복원한다. 줄 끝 하이픈은 단어 분할인지 확인해 결합한다. 원문 단어·문장·문단 순서와 의미는 절대 바꾸지 않는다.

[문항별 출력 구조] 각 문항마다 다음 순서를 지킨다.
1. 제목: 시험명과 문항 번호, 괄호 안에 원본 문제 유형
2. [한 줄 해석]: 원문 문장을 1번부터 번호 매기고, 각 영어 문장 바로 아래에 해석: 형식으로 정확한 한국어 해석을 쓴다.
3. [세부사항]: 주제, 키워드 5~10개, 논리전개, 구조분석 및 문법 3~5개, 내용요약 2~3문장, 단어장.
4. STEP 2.1 workbook_어법·어휘 선택하기: 원문 전체를 다시 제시하고 선택형 문제 5~7개를 만든다.
5. STEP 2.2 workbook_동사형 쓰기: 원문 전체를 다시 제시하고 동사·준동사 문제 5~7개를 만든다.
6. STEP 2.3 workbook_빈칸 채우기: 원문 전체를 다시 제시하고 첫 글자 힌트 빈칸 문제 5~7개를 만든다.
7. STEP 2.4 workbook_어법·어휘 오류 수정하기: 원문 전체를 다시 제시하고 오류 수정 문제 5개를 만든다.
8. 교사용 정답: STEP 2.1~2.4 순서로 정답과 짧은 근거를 제시한다.

[금지] 첨부 MD에 없는 정보나 예시를 추가하지 않는다. 내부 태그, JSON, 검증 로그, 마크다운 코드펜스를 출력하지 않는다. self-contained HTML만 출력하고 @page{size:A4;margin:16mm}, body contenteditable="true", page-break-inside:avoid를 포함한다.`;

  const promptText = () => `${WORKBOOK_RULES}\n\n[파일 첨부 방식]\n이 프롬프트와 함께 사용자가 첨부한 MD 파일 전체를 읽어라. MD 파일의 모든 문항과 지문을 빠짐없이 처리하라. 프롬프트 안에 원문을 다시 붙여 넣지 않았으므로 첨부 파일을 유일한 원문 자료로 사용하라. 파일이 없거나 읽히지 않으면 작성하지 말고 파일 첨부를 요청하라.`;

  const examPromptText = () => `첨부한 시험지 PDF에서 ${els.targetRange?.value.trim() || '지정한'}번 문항만 정확히 추출해 주세요.\n\n- 문항 번호와 원본 문제 유형을 보존합니다.\n- 지문, 선택지, 문장 번호, 밑줄/괄호 표시를 빠짐없이 옮깁니다.\n- PDF 복사 과정의 띄어쓰기와 줄바꿈만 복원하고 내용은 바꾸지 않습니다.\n- 결과는 문항별 Markdown으로 작성하고, 문항 사이에 ---를 넣습니다.\n- 해설이나 정답은 추가하지 않습니다.`;

  const sample = `<!doctype html>\n<html lang="ko">\n<head><meta charset="utf-8"><style>@page{size:A4;margin:16mm}body{font:16px/1.6 Arial,sans-serif}h1{font-size:24px;border-bottom:2px solid #111;padding-bottom:8px}.section{page-break-inside:avoid;margin:20px 0}</style></head>\n<body contenteditable="true">\n<h1>Sample Workbook · Question 18</h1>\n<div class="section"><h2>Reading &amp; Translation</h2><p><strong>1.</strong> A short sample passage appears here.</p><p>해석: 짧은 예시 지문이 여기에 나옵니다.</p></div>\n<div class="section"><h2>STEP 2.1 · Vocabulary</h2><p>Choose the best answer: The author ____ the idea.</p></div>\n</body></html>`;

  let zoom = 1;
  let renderTimer;
  const setCopyState = (node, text) => { if (node) { node.textContent = text; window.setTimeout(() => { if (node.textContent === text) node.textContent = ''; }, 1800); } };
  const copy = async (value, stateNode) => {
    if (!value) return;
    try { await navigator.clipboard.writeText(value); }
    catch { const ta = document.createElement('textarea'); ta.value = value; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); ta.remove(); }
    setCopyState(stateNode, '복사됨 ✓');
  };

  const renderPreview = () => {
    if (!els.preview) return;
    els.preview.srcdoc = els.htmlInput?.value.trim() || '<!doctype html><html><body style="font-family:Arial;padding:32px;color:#777">HTML을 입력하면 미리보기가 표시됩니다.</body></html>';
    els.preview.addEventListener('load', applyPreviewStyles, { once: true });
  };
  const applyPreviewStyles = () => {
    const doc = els.preview?.contentDocument;
    if (!doc?.body) return;
    doc.body.style.fontSize = `${els.fontSize?.value || 16}px`;
    doc.body.style.letterSpacing = `${els.letterSpacing?.value || 0}px`;
    doc.body.style.lineHeight = els.lineHeight?.value || 1.6;
    doc.body.contentEditable = 'true';
  };
  const updateOutputs = () => {
    if (els.fontSizeOut) els.fontSizeOut.value = `${els.fontSize.value}px`;
    if (els.letterOut) els.letterOut.value = `${els.letterSpacing.value}px`;
    if (els.lineOut) els.lineOut.value = els.lineHeight.value;
  };
  const updateZoom = () => { if (els.preview) els.preview.style.transform = `scale(${zoom})`; if (els.zoomValue) els.zoomValue.textContent = `${Math.round(zoom * 100)}%`; };
  const buildPassagePrompt = () => {
    if (!els.passage?.value.trim()) { if (els.prompt) els.prompt.value = ''; if (els.copyPrompt) els.copyPrompt.disabled = true; return; }
    els.prompt.value = promptText();
    if (els.copyPrompt) els.copyPrompt.disabled = false;
  };

  els.passageFile?.addEventListener('change', async (event) => {
    const file = event.target.files?.[0]; if (!file) return;
    els.passage.value = await file.text();
    if (els.passageFileState) { els.passageFileState.textContent = `불러옴 · ${file.name} · ${els.passage.value.length.toLocaleString()}자`; els.passageFileState.classList.add('ready'); }
    buildPassagePrompt();
  });
  els.passage?.addEventListener('input', buildPassagePrompt);
  els.buildExamPrompt?.addEventListener('click', () => { els.examPrompt.value = examPromptText(); els.copyExamPrompt.disabled = false; });
  els.copyPrompt?.addEventListener('click', () => copy(els.prompt.value, els.copyState));
  els.copyExamPrompt?.addEventListener('click', () => copy(els.examPrompt.value, els.examCopyState));
  els.htmlInput?.addEventListener('input', () => { window.clearTimeout(renderTimer); renderTimer = window.setTimeout(renderPreview, 180); });
  els.sampleHtml?.addEventListener('click', () => { els.htmlInput.value = sample; renderPreview(); });
  els.printPdf?.addEventListener('click', () => { const doc = els.preview?.contentDocument; if (!doc?.documentElement) return; const win = window.open('', '_blank'); if (!win) return; win.document.write(doc.documentElement.outerHTML); win.document.close(); win.focus(); window.setTimeout(() => win.print(), 250); });
  els.zoomIn?.addEventListener('click', () => { zoom = Math.min(1.5, +(zoom + 0.1).toFixed(2)); updateZoom(); });
  els.zoomOut?.addEventListener('click', () => { zoom = Math.max(0.6, +(zoom - 0.1).toFixed(2)); updateZoom(); });
  [els.fontSize, els.letterSpacing, els.lineHeight].forEach((input) => input?.addEventListener('input', () => { updateOutputs(); applyPreviewStyles(); }));
  els.resetBtn?.addEventListener('click', () => { if (!window.confirm('현재 입력을 모두 지울까요?')) return; [els.passage, els.prompt, els.examPrompt, els.htmlInput].forEach((node) => { if (node) node.value = ''; }); if (els.copyPrompt) els.copyPrompt.disabled = true; if (els.copyExamPrompt) els.copyExamPrompt.disabled = true; if (els.passageFile) els.passageFile.value = ''; if (els.passageFileState) els.passageFileState.textContent = 'MD 파일을 선택하면 원문을 읽습니다.'; renderPreview(); });

  updateOutputs(); updateZoom(); renderPreview();
})();
