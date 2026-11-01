# OmoAuto protocols

Reconstructed from WayXAuto 0.2.7 (`com.sdmv.wayxauto`, versionCode 22) and re-implemented in
`core/protocol-aa` and `core/protocol-omoway`. Both modules are pure Kotlin/JVM and covered by
unit tests that play the other side (fake phone / fake display).

---

## 1. Motorcycle display (OMOWAY) — `core/protocol-omoway`

### Transport
- Wi-Fi Direct: the display is **group owner**. The phone joins with `WifiP2pConfig`
  (`wps.setup = PBC`, `groupOwnerIntent = 1`), built with `WifiP2pConfig.Builder`
  `.setDeviceAddress(..).enablePersistentMode(true)` on Android 10+, exactly as WayXAuto. A display
  that only knows the phone from a temporary group accepted the projection connection but stayed
  black; it showed the picture after WayXAuto had joined persistently. Display IP =
  `WifiP2pInfo.groupOwnerAddress`.
- Two TCP connections from the phone to the display:
  - **9527 – control**, **9528 – video**. `TCP_NODELAY`, connect timeout 4 s, read timeout 6 s.
  - Video socket send buffer 16 KiB when the live queue is enabled (keeps latency low).
- Sockets are bound to the Wi-Fi network that routes to the display, if Android exposes one.

### Control packets (little endian)
```
int32 type | int32 length | u16 id | body (length - 2 bytes)
```

| Dir | type | id | Body | Meaning |
|---|---|---|---|---|
| → | 1 | 1 | JSON `{"platform":"Android","screenWidth":"960","screenHeight":"360"}` (org.json, 4-space indent) | Hello |
| → | 7 | 41 | `{"foreground":"1"}` | App in foreground |
| ← | 1 | 49 | X.509 EC P-256 public key | Key exchange. Reply `1/49` with our X.509 public key (ECDH; secret currently unused) → projection ready |
| ← | 1 | 9 | any | Echo / heartbeat. Reply `1/9` with the same body → projection ready |
| → | 7 | 1 | `{"code":0}` | Projection ready – start sending video (key frame first) |
| ← | 1 | 40 | – | Key frame request (flush queue, request IDR) |
| ← | 1 | 7 | 1 byte status | **Status 3 = busy**: another phone app (usually the official OMOWAY app) holds the display; the display then closes the connection. Force-stopping that app frees it. WayXAuto logged this as "last reply 7 / 3" (id / status byte), which was first misread as type 7 id 3. |
| ← | 7 | 3 | – | Treated as busy too (never observed). |
| ← | 7 | 27 | – | Sent with the theme query and *show* when the display switches to the phone (meaning unknown; WayXAuto ignores it too). |
| ← | 7 | 10 | – | Show (visible again; needs key frame) |
| ← | 7 | 11 | – | Hide (display shows another screen; stop sending) |
| ← | 7 | 37 | – | Theme query → reply `7/39` `{"mode":"night"}` (or `"day"`) |

The display must send something at least every 6 s (heartbeat), otherwise the read times out
and the session is restarted.

### Video packets (little endian)
```
int32 type = 2
int32 length            = 10 + config + frame
u8    1
u8    1 = key frame, 2 = delta frame
int64 pts (µs)
[key frames only]  u16 spsLen | SPS | u16 ppsLen | PPS     (EACH WITH 00 00 00 01 start code)
H.264 access unit, Annex-B (00 00 00 01 start codes)
```
- **SPS and PPS carry their 4-byte start code** (`00 00 00 01`, included in the length), exactly as
  WayXAuto's helper returned them. Raw NAL units (no start code) leave a freshly started display
  black: it cannot configure its decoder. It only worked after WayXAuto had configured the decoder
  first, which made OmoAuto look like it needed "priming".
- H.264 **baseline**, 960×360 (or 1280×480), 60 fps, bitrate 2–10 Mbps (default 6).
- **Key frame every 6 frames** (forced with `request-sync`), as WayXAuto did: the display only recovers
  from dropped frames at key frames.
- **pts = encoder timestamp, starting at 0 when the video pipeline starts** (as WayXAuto). Absolute
  `System.nanoTime()` values (hours of uptime) leave the display black.
- Every control message from the display (heartbeat included; not hide) triggers a fresh key frame,
  as WayXAuto did.
- **Live queue**: drop everything when the oldest frame is > 100 ms old, > 6 frames or > 256 KiB
  are queued; then wait for the next key frame. A write that stalls > 750 ms restarts the session.

---

## 2. Android Auto head unit — `core/protocol-aa`

OmoAuto is a *virtual head unit* that connects to Android Auto's developer **head unit server**
on `127.0.0.1:5277` (Android Auto → ⋮ → *Start head unit server*). Detection: if binding
`127.0.0.1:5277` fails with `EADDRINUSE`, the server is running.

### Framing
```
u8 channel | u8 flags | u16 length (BE) | [u32 total length if FIRST && !LAST] | payload
flags: FIRST=1  LAST=2  CONTROL=4  ENCRYPTED=8
message payload: u16 message id (BE) | protobuf body
```

### Handshake (channel 0)
1. → `VERSION_REQUEST (1)` body `00 01 00 01` (v1.1).
2. ← `VERSION_RESPONSE (2)` `major | minor | status` (status 0 = OK).
3. TLS: OmoAuto is the **TLS client**; records travel in `SSL_HANDSHAKE (3)` messages.
   Client certificate = head unit identity (Open Headunit sample, *Google-Android-Reference*,
   pinned SHA-256 `851d80c8…` cert / `015d172a…` key). The phone's certificate is accepted.
4. → `AUTH_COMPLETE (4)` `{1: 0}` (plain), then everything is encrypted.
5. ← `SERVICE_DISCOVERY_REQUEST (5)` → `SERVICE_DISCOVERY_RESPONSE (6)`:

| Ch | Service | Details |
|---|---|---|
| 1 | Sensor source | driving status (13), night mode (10) |
| 2 | Video sink | H.264 BP, 1280×720 (or 1080p), 60 fps, margins W×H, density 160/240 |
| 3 | Input source | keycodes 3, 4, 19–23, 65536 (rotary), 65538 |
| 4 | Audio sink | PCM 16 kHz/16-bit/mono (accepted and discarded) |
| 5 | Microphone | declared, never opened |

Make/model strings: `LogicSekai` / `OmoAuto`.

### Other control messages
`PING (11→12)` echo timestamp · `NAV_FOCUS (13→14)` type 2 · `AUDIO_FOCUS (18→19)` GAIN, or LOSS
for release · `SHUTDOWN (15→16)` phone exited · channel open `7 → 8` with CONTROL flag.

### Media (channel 2 video, 4 audio, 5 mic)
`SETUP (0x8000) → CONFIG (0x8003)` {status 2, max unacked 12/16/1} · video also sends
`VIDEO_FOCUS (0x8008)` projected · `START (0x8001)` carries the session id ·
`DATA (0)` = 8-byte timestamp + H.264 · `CODEC_CONFIG (1)` = SPS/PPS · every media message is
acknowledged with `ACK (0x8004)` {session, 1}.

### Input (channel 3)
`BINDING_REQUEST (0x8002) → BINDING_RESPONSE (0x8003)`, then `EVENT (0x8001)`:
key = `{1: ts, 4: {1: {1: code, 2: down, 3: 0, 4: false}}}` (down + up),
rotary = `{1: ts, 6: {1: {1: 65536, 2: delta}}}`.

---

## 3. Picture geometry — `VideoGeometry`

Output `W × (W·3/8)`. With right margin `r` and vertical margin `v` (in 960-px units):

```
UI area on display : x 0 … W − r·W/960,  y v·W/960 … H − v·W/960
AA margins         : marginW = srcW − cropW,  marginH = srcH − cropH   (crop has the same aspect)
```
Default (960 out, 1280 source, r = 120, v = 45): margins 160×360, crop (80,180,1120,360) →
display rect (0,45,840,270). The compositor renders at a fixed 60 fps and repeats the last frame
when Android Auto pauses.
