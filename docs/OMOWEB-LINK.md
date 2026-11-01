# OmoAuto ↔ OmoWeb link

Android Auto keeps the head unit's buttons and rotary knob for its own UI. An app shown on the
car screen (OmoWeb) never receives them. That is why OmoAuto's Controls showed no reaction in
OmoWeb. OmoAuto and OmoWeb run on the same phone, so they talk directly with explicit broadcasts.

| Direction | Action | Extras | Target packages |
|---|---|---|---|
| OmoWeb → OmoAuto | `com.logicsekai.omoauto.action.WEB_STATE` | `active` (boolean) | `com.logicsekai.omoauto`, `com.logicsekai.omoauto.debug` |
| OmoAuto → OmoWeb | `com.logicsekai.omoweb.action.REMOTE` | `command` (string) | the OmoWeb package that sent `active=true` |

- OmoWeb sends `active=true` when it is started **on the car display** (not the phone's display 0),
  every 4 s as a heartbeat, and `active=false` when stopped. OmoAuto treats 10 s without a
  heartbeat as inactive.
- Commands: `up`, `down`, `left`, `right`, `select`, `back`; screen buttons `fullscreen` and
  `ultrawide` (toggles). (OmoWeb also still accepts `move` / `scroll` / `tap` with `dx`, `dy`
  from the former trackpad mode; OmoAuto no longer sends them.) Playback (play/pause, next, previous, rewind, fast forward) is **not** sent here: OmoAuto
  presses standard media keys and OmoWeb answers them with its media session, like any player.
  - Controls in Media mode: previous → `up`, next → `down`, left/right/select as named.
  - Controls in Remote mode: up → `up`, down → `down` (same as previous/next), arrows, OK → `select`.
  - Back → `back`. **Home is always sent to Android Auto**, so the rider can leave OmoWeb.
- Both sides send with `BroadcastOptions.setShareIdentityEnabled(true)` (Android 14+) and accept
  only the other app's package names (`BroadcastReceiver.getSentFromPackage()`).
- Both apps list each other under `<queries>` (package visibility).

Code: OmoAuto `core/streaming/.../OmoWebLink.kt`, OmoWeb `app/.../OmoAutoLink.kt`.
