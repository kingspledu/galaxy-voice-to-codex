from __future__ import annotations
import os, secrets, socket, webbrowser
from pathlib import Path
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from dotenv import load_dotenv
import uvicorn
import threading
from input_control import InputController
ROOT=Path(__file__).parent; load_dotenv(ROOT/'.env')
PIN=os.getenv('PIN') or f'{secrets.randbelow(1000000):06d}'; MAX_LEN=int(os.getenv('MAX_TEXT_LENGTH','5000'))
ALLOWED=[x.strip() for x in os.getenv('ALLOWED_WINDOW_TITLES','Visual Studio Code,Code - Insiders').split(',') if x.strip()]
app=FastAPI(title='Galaxy Voice to Codex'); app.mount('/static',StaticFiles(directory=ROOT/'static'),name='static'); controller=InputController(ALLOWED)
@app.get('/',response_class=HTMLResponse)
def index(): return (ROOT/'static'/'index.html').read_text(encoding='utf-8')
@app.websocket('/ws')
async def websocket(ws:WebSocket):
    await ws.accept(); authenticated=False
    try:
        while True:
            m=await ws.receive_json(); kind=m.get('type')
            if kind=='auth':
                authenticated=secrets.compare_digest(str(m.get('pin','')),PIN); await ws.send_json({'type':'auth','ok':authenticated,'message':'인증 성공' if authenticated else 'PIN이 올바르지 않습니다.'})
            elif kind=='send':
                if not authenticated: await ws.send_json({'type':'result','ok':False,'message':'먼저 PIN 인증을 하세요.'}); continue
                text=str(m.get('text','')).strip()
                if not text: await ws.send_json({'type':'result','ok':False,'message':'보낼 텍스트가 없습니다.'}); continue
                if len(text)>MAX_LEN: await ws.send_json({'type':'result','ok':False,'message':f'텍스트는 {MAX_LEN}자 이하입니다.'}); continue
                await ws.send_json({'type':'result',**controller.append_text(text)})
    except WebSocketDisconnect: pass
def local_ip():
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    try: s.connect(('8.8.8.8',80)); return s.getsockname()[0]
    except OSError: return socket.gethostbyname(socket.gethostname())
    finally: s.close()
def make_qr(url):
    try:
        import qrcode
        path=ROOT/'galaxy-voice-to-codex-qr.png'
        qrcode.make(url).save(path)
        print(f'QR image: {path}')
        try: os.startfile(path)
        except OSError: pass
    except Exception as e:
        print(f'QR generation skipped: {e}')
if __name__=='__main__':
    url=f'http://{local_ip()}:{os.getenv("HTTP_PORT","8765")}'
    print(f'PIN: {PIN}\nHTTP: {url}')
    make_qr(url)
    host=os.getenv('HOST','0.0.0.0'); http_port=int(os.getenv('HTTP_PORT','8765'))
    if (ROOT/'certs'/'cert.pem').exists() and (ROOT/'certs'/'key.pem').exists():
        threading.Thread(target=lambda: uvicorn.run(app,host=host,port=int(os.getenv('HTTPS_PORT','8766')),ssl_certfile=str(ROOT/'certs'/'cert.pem'),ssl_keyfile=str(ROOT/'certs'/'key.pem')),daemon=True).start()
    uvicorn.run(app,host=host,port=http_port)
