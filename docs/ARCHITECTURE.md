# Arsitektur OmoAuto

## Prinsip

1. **Protokol terpisah dari Android.** `core/protocol-aa` dan `core/protocol-omoway` adalah modul
   Kotlin/JVM murni. Mudah di-test (ada fake phone dan fake display) dan bisa dipakai ulang.
2. **Satu mesin, banyak layar.** `ProjectionEngine` (di `core/streaming`) memegang seluruh status
   perjalanan. UI hanya membaca `StateFlow<ProjectionState>` dan memanggil perintah.
3. **Fitur tidak saling bergantung.** Setiap `feature/*` hanya bergantung pada `core/*`. Navigasi
   dan wiring ada di `:app`.
4. **Konfigurasi build seragam** lewat convention plugin di `build-logic/` dan versi terpusat di
   `gradle/libs.versions.toml`.

## Grafik dependency

```
app ─┬─ feature/onboarding ─┐
     ├─ feature/home ───────┤
     ├─ feature/setup ──────┤      ┌─ core/designsystem
     ├─ feature/controls ───┼──────┤
     ├─ feature/diagnostics ┤      └─ core/common
     ├─ feature/settings ───┤
     └─ feature/about ──────┘
                │
                ▼
         core/streaming ──► core/data ──► core/protocol-aa ──► core/common
                │
                └──────────► core/protocol-omoway ──► core/common
```

## Alur data saat berkendara

```
WifiDirectLink ── Connected(ip) ──► ProjectionEngine.maybeStartDisplaySession()
                                          │
AaHeadUnitSession ── onVideoConfig/Frame ─► VideoPipeline
                                          │   AvcDecoder ─► GlCompositor (60 fps, crop) ─► AvcEncoder
                                          ▼
                                 OmoDisplaySession.submitFrame() ─► LiveVideoQueue ─► socket 9528
```

- `ProjectionService` (foreground, `connectedDevice`) hanya menjaga proses tetap hidup dan
  menampilkan notifikasi. Ia aktif selama `engine.needsForeground == true`.
- Wake lock + Wi-Fi lock (low-latency) dipegang selama Android Auto berjalan.
- Watchdog engine: jika layar menerima tapi tidak ada frame terkirim selama 8 detik, sesi layar
  dimulai ulang. Link Wi-Fi Direct yang putus disambung ulang otomatis (maks. 5 kali).

## Pembaruan aplikasi

`core/data/update`: `UpdateChecker` membaca rilis terbaru di GitHub, `UpdateManager` memegang status
(cek → unduh → siap pasang), `UpdateCheckWorker` (WorkManager, tiap 12 jam) memberi notifikasi lewat
`UpdateNotifier`. APK diserahkan ke installer sistem lewat `FileProvider` (`<package>.updates`).
Build Play (`-Pomo.distribution=play`) mematikan semuanya; lihat `app/build.gradle.kts`.

## Menambah fitur baru

1. Buat modul `feature/<nama>/` dengan `build.gradle.kts`:
   ```kotlin
   plugins { alias(libs.plugins.omoauto.android.feature) }
   android { namespace = "com.logicsekai.omoauto.feature.<nama>" }
   dependencies { implementation(project(":core:streaming")) } // sesuai kebutuhan
   ```
2. Daftarkan di `settings.gradle.kts` (`include(":feature:<nama>")`).
3. Tulis `XxxViewModel(deps)` + `XxxRoute(viewModel, ...)`; string di `res/values` (EN) dan
   `res/values-in` (ID).
4. Di `:app`, tambahkan dependency, route di `Routes`, entri `composable(...)` di `OmoAutoApp.kt`,
   dan (opsional) item drawer di `OmoDrawerScaffold.kt`.

## Ide fitur lanjutan

- Audio guidance Android Auto (saluran 4) ke speaker motor/headset.
- Profil per motor (bitrate, margin) yang otomatis dipakai saat tersambung.
- Mulai otomatis saat motor terdeteksi (auto-connect + auto-start).
- Integrasi data motor (baterai, kecepatan) jika protokol layar menyediakannya.
- Uji instrumentasi Compose untuk alur Setup.
