#!/usr/bin/env python3
"""
mock_harness_backend.py — Servidor mock local em Python para testar voz e telemetria do TicWatch E2.
Porta: 8765
"""

import http.server
import json
import os
import socketserver
import sys

PORT = 8765
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_DIR = os.path.join(ROOT, "logs")
AUDIO_OUTPUT = os.path.join(LOG_DIR, "last_voice_cmd.m4a")

os.makedirs(LOG_DIR, exist_ok=True)

class HarnessHandler(http.server.BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        sys.stdout.write(f"[{self.log_date_time_string()}] {self.address_string()} - {format % args}\n")
        sys.stdout.flush()

    def do_GET(self):
        if self.path == "/status" or self.path == "/status/":
            data = {
                "agent": "HERMES",
                "state": "IDLE",
                "last_msg": "Ready for command",
                "tokens": 128
            }
            body = json.dumps(data).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/voice" or self.path == "/voice/":
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length > 0:
                audio_data = self.rfile.read(content_length)
                with open(AUDIO_OUTPUT, "wb") as f:
                    f.write(audio_data)
                print(f"\n[MOCK BACKEND] >>> Áudio recebido com sucesso: {len(audio_data)} bytes salvos em {AUDIO_OUTPUT}\n", flush=True)
                resp_data = {
                    "agent": "HERMES",
                    "state": "RUNNING",
                    "last_msg": f"Audio received ({len(audio_data)}B)",
                    "tokens": 256
                }
            else:
                resp_data = {
                    "agent": "HERMES",
                    "state": "ERROR",
                    "last_msg": "Empty audio payload",
                    "tokens": 0
                }

            body = json.dumps(resp_data).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

class ReusableTCPServer(socketserver.TCPServer):
    allow_reuse_address = True

if __name__ == "__main__":
    print(f"=== Mock Harness Backend escutando em 0.0.0.0:{PORT} ===")
    print(f"Arquivo de áudio salvo em: {AUDIO_OUTPUT}")
    with ReusableTCPServer(("0.0.0.0", PORT), HarnessHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nEncerrando mock server...")
