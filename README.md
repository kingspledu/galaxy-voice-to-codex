# Galaxy Voice to Codex

갤럭시 브라우저에서 한국어 음성을 텍스트로 바꾸고, 같은 Wi-Fi의 Windows VS Code Codex 입력창에 텍스트만 붙여넣는 로컬 도구입니다.

## 실행
`start.bat`를 실행하고 콘솔의 PIN을 갤럭시 페이지에 입력한 뒤 `http://PC_IP:8765`를 엽니다. Web Speech API가 HTTP에서 제한되면 `certs/cert.pem`, `certs/key.pem`을 준비해 HTTPS를 사용하세요. 자체 서명 인증서는 휴대폰에서 한 번 신뢰해야 합니다.

자동 Enter, 명령 실행, 기존 입력 삭제는 수행하지 않습니다. VS Code 창이 포그라운드이고 제목이 허용 목록과 일치할 때만 붙여넣습니다. Web Speech API가 지원되지 않으면 텍스트 직접 입력 또는 삼성 키보드 음성 입력을 사용하세요.

`python -m unittest discover -s tests`로 기본 검사를 실행할 수 있습니다. 실제 붙여넣기는 Windows VS Code와 Codex 확장 버전에 따라 수동 확인이 필요합니다.
