import time
class InputController:
    def __init__(self, allowed_titles): self.allowed_titles = allowed_titles
    def active_title(self):
        try:
            import pygetwindow as gw
            return gw.getActiveWindowTitle() or ''
        except Exception: return ''
    def is_ready(self):
        title = self.active_title()
        return bool(title) and any(x.lower() in title.lower() for x in self.allowed_titles)
    def append_text(self, text):
        if not self.is_ready(): return {'ok':False,'message':'VS Code 창을 앞에 두고 Codex 입력창을 클릭한 뒤 다시 전송하세요.','window_title':self.active_title()}
        try:
            import pyperclip, pyautogui
            old = pyperclip.paste(); pyperclip.copy(text); pyautogui.hotkey('ctrl','v'); time.sleep(0.08); pyperclip.copy(old)
            return {'ok':True,'message':'입력창에 붙여넣었습니다. 실행하려면 직접 확인 후 Enter를 누르세요.'}
        except Exception as e: return {'ok':False,'message':f'붙여넣기에 실패했습니다: {e}'}
