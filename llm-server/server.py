#!/usr/bin/env python3
"""
AIOS LLM Server — laeuft auf dem Host-Mac und bedient die Android-App via HTTP.
Nutzt Apple MLX fuer native Apple Silicon Inference.

Starten: python3 server.py
Die Android-App verbindet sich ueber http://10.0.2.2:8085 (Emulator Host-IP).
"""

import json
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from mlx_lm import load, generate

MODEL_ID = "mlx-community/Qwen2.5-1.5B-Instruct-4bit"

SYSTEM_PROMPT = """Du bist AIOS, ein intelligenter Systemassistent fuer ein Android-Geraet.
Du analysierst Nutzerbefehle und entscheidest, welche System-Tools aufgerufen werden muessen.

WICHTIG: Antworte IMMER mit gueltigem JSON in diesem Format:
{
  "steps": [
    {"tool": "tool.id", "params": {...}, "description": "Was dieser Schritt tut"}
  ],
  "message": "Kurze Antwort an den Nutzer",
  "confidence": 0.9
}

Wenn der Nutzer eine normale Frage stellt (keine Systemaktion), antworte so:
{
  "steps": [],
  "message": "Deine Antwort hier",
  "confidence": 1.0
}

Verfuegbare System-Tools:

EINSTELLUNGEN:
- system.settings.set_focus_mode (params: enabled:bool, until:string?) - Nicht-Stoeren-Modus
- system.settings.control_brightness (params: level:int 0-100) - Bildschirmhelligkeit
- system.settings.control_volume (params: level:int 0-100, stream:string media|ring|alarm|notification) - Lautstaerke
- system.settings.set_wifi (params: enabled:bool) - WiFi ein/aus
- system.settings.set_bluetooth (params: enabled:bool) - Bluetooth ein/aus
- system.settings.set_airplane_mode (params: enabled:bool) - Flugmodus
- system.settings.set_rotation (params: enabled:bool) - Auto-Rotation
- system.settings.set_location (params: enabled:bool) - Standortdienste
- system.settings.get_battery (params: keine) - Akkustand abfragen

APPS:
- system.apps.open (params: app_name:string) - App oeffnen
- system.apps.list_installed (params: keine) - Installierte Apps auflisten
- system.apps.get_info (params: package_name:string) - App-Info abrufen

KOMMUNIKATION:
- system.phone.call (params: number:string) - Anruf starten
- system.phone.send_sms (params: recipient:string, body:string) - SMS senden
- system.contacts.search (params: query:string) - Kontakt suchen
- system.contacts.add (params: name:string, phone:string?, email:string?) - Kontakt hinzufuegen

MEDIEN:
- system.media.play_pause (params: keine) - Wiedergabe starten/pausieren
- system.media.next_track (params: keine) - Naechster Titel
- system.media.prev_track (params: keine) - Vorheriger Titel
- system.media.take_photo (params: keine) - Foto aufnehmen
- system.media.take_screenshot (params: keine) - Screenshot

KALENDER & WECKER:
- system.calendar.create_event (params: title:string, start_time:string?, end_time:string?) - Termin erstellen
- system.calendar.list_events (params: keine) - Termine anzeigen
- system.alarm.set (params: hour:int, minute:int, label:string?) - Wecker stellen
- system.alarm.set_timer (params: seconds:int) - Timer stellen

GERAET:
- system.device.get_info (params: keine) - Geraeteinfo
- system.device.get_network_info (params: keine) - Netzwerkinfo
- system.device.get_storage (params: keine) - Speicherinfo

ZWISCHENABLAGE:
- system.clipboard.copy (params: text:string) - Text kopieren
- system.clipboard.paste (params: keine) - Einfuegen

BENACHRICHTIGUNGEN:
- system.notifications.list (params: keine) - Benachrichtigungen anzeigen
- system.notifications.dismiss_all (params: keine) - Alle entfernen
- system.notifications.send (params: title:string, body:string) - Benachrichtigung senden

DATEIEN:
- system.files.read (params: path:string) - Datei lesen
- system.files.write (params: path:string, content:string) - Datei schreiben
- system.files.list (params: path:string?) - Verzeichnis auflisten
- system.files.search (params: query:string) - Datei suchen
- system.files.delete (params: path:string) - Datei loeschen

SHELL:
- system.shell.execute (params: command:string) - Shell-Befehl ausfuehren
- system.shell.get_prop (params: key:string) - System-Property lesen

Du kannst mehrere Tools in einem Plan kombinieren. Sei praezise mit den Tool-IDs und Parametern.
Antworte immer auf Deutsch wenn der Nutzer Deutsch spricht."""

print("Lade Modell:", MODEL_ID)
model, tokenizer = load(MODEL_ID)
print("Modell geladen. Server bereit.")


class AIOSHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == "/v1/chat":
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")

            try:
                data = json.loads(body)
            except json.JSONDecodeError:
                self.send_error(400, "Ungueltige JSON-Anfrage")
                return

            user_input = data.get("message", "")
            history = data.get("history", [])

            if not user_input:
                self.send_error(400, "Kein 'message' Feld")
                return

            # Build prompt with conversation history
            prompt = SYSTEM_PROMPT + "\n\n"
            for msg in history[-6:]:  # Last 6 messages for context
                role = "Nutzer" if msg.get("isUser") else "AIOS"
                prompt += f"{role}: {msg.get('text', '')}\n"
            prompt += f"Nutzer: {user_input}\nAIOS (JSON):"

            start = time.time()
            response = generate(
                model,
                tokenizer,
                prompt=prompt,
                max_tokens=500,
            )
            duration_ms = int((time.time() - start) * 1000)

            # Try to extract JSON from response
            result = self._parse_response(response, user_input)
            result["duration_ms"] = duration_ms
            result["raw"] = response[:500]

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(result, ensure_ascii=False).encode("utf-8"))
            return

        if self.path == "/v1/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok","model":"' + MODEL_ID.encode() + b'"}')
            return

        self.send_error(404, "Nicht gefunden")

    def do_GET(self):
        if self.path == "/v1/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps({"status": "ok", "model": MODEL_ID}).encode("utf-8")
            )
            return
        self.send_error(404)

    def log_message(self, format, *args):
        print(f"[{time.strftime('%H:%M:%S')}] {args[0]}")

    def _parse_response(self, response, original_input):
        """Extract JSON from LLM response, with fallback."""
        text = response.strip()

        # Try to find JSON in the response
        # Look for { ... } pattern
        start_idx = text.find("{")
        if start_idx >= 0:
            # Find matching closing brace
            depth = 0
            end_idx = start_idx
            for i in range(start_idx, len(text)):
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                    if depth == 0:
                        end_idx = i + 1
                        break

            json_str = text[start_idx:end_idx]
            try:
                parsed = json.loads(json_str)
                # Validate structure
                if "steps" in parsed and "message" in parsed:
                    return parsed
                # If it has tool/action but not steps format, convert
                if "tool" in parsed or "action" in parsed:
                    tool = parsed.get("tool", parsed.get("action", ""))
                    params = parsed.get("params", {})
                    return {
                        "steps": [
                            {
                                "tool": tool,
                                "params": params,
                                "description": parsed.get(
                                    "description", f"Ausfuehren: {tool}"
                                ),
                            }
                        ],
                        "message": parsed.get(
                            "message", f"Fuehre {tool} aus"
                        ),
                        "confidence": float(parsed.get("confidence", 0.8)),
                    }
            except json.JSONDecodeError:
                pass

        # Fallback: return as conversational response
        clean = text.split("<|")[0].strip()  # Remove special tokens
        if not clean:
            clean = "Ich habe deine Anfrage erhalten, konnte sie aber nicht verarbeiten."

        return {
            "steps": [],
            "message": clean,
            "confidence": 0.5,
        }


if __name__ == "__main__":
    port = 8085
    server = HTTPServer(("0.0.0.0", port), AIOSHandler)
    print(f"AIOS LLM Server laeuft auf http://0.0.0.0:{port}")
    print(f"Android Emulator: http://10.0.2.2:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer gestoppt.")
        server.server_close()
