<div align="center">

# OmoAuto

**Android Auto di layar motor OMOWAY, lewat Wi-Fi Direct.**

[![Android CI](https://github.com/LogicSekai/OmoAuto/actions/workflows/android.yml/badge.svg)](https://github.com/LogicSekai/OmoAuto/actions/workflows/android.yml)
[![License: AGPL v3](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue.svg)](LICENSE)
[![Sponsor](https://img.shields.io/badge/sponsor-LogicSekai-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/LogicSekai)
[![Commercial license](https://img.shields.io/badge/commercial-via%20sponsor-orange.svg)](COMMERCIAL-LICENSE.md)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

Package `com.logicsekai.omoauto` · dibuat oleh komunitas [**Logic Sekai**](https://logicsekai.com)

</div>

> [!WARNING]
> Perangkat lunak eksperimental. **Jangan mengoperasikan HP saat berkendara.** Atur semuanya saat
> motor berhenti. OmoAuto adalah proyek independen, tidak berafiliasi dengan OMOWAY maupun Google.

---

## Fitur

- **Android Auto di layar motor**: navigasi, musik, dan aplikasi Android Auto tampil di layar
  OMOWAY 960 × 360 @ 60 fps.
- **Head unit virtual**: tidak perlu head unit fisik; OmoAuto terhubung ke *head unit server*
  Android Auto di HP yang sama.
- **Kontrol dari HP**: pratinjau langsung yang bisa disentuh, mode **Media**, **Remote** (D-pad),
  dan **Sentuh** layar penuh.
- **OmoWeb**: tonton YouTube/web di layar motor lewat aplikasi pendamping, dikendalikan dari OmoAuto.
- **Banyak motor tersimpan**, sambung ulang otomatis, mode malam, tema layar motor.
- **Penyesuaian layar**: tepi bawaan atau tepi kustom kiri/kanan/atas/bawah.
- **Setup bertahap** dengan panduan per merek HP (Xiaomi, Samsung, OPPO/realme, vivo, Huawei, Infinix, dll.).
- **Diagnostik** dengan skor koneksi, statistik video, salin/bagikan log.
- **Pembaruan otomatis**: notifikasi saat ada versi baru di GitHub Releases, lalu unduh dan pasang
  langsung dari aplikasi (checksum dan paket diperiksa sebelum dipasang).
- Halaman **Dukung**: donasi lewat GitHub Sponsors, Ko-fi, atau Trakteer, dan cara berkontribusi.
- Dwibahasa **Indonesia** dan **Inggris**, tema terang/gelap.

## Unduh

APK tersedia di halaman [**Releases**](https://github.com/LogicSekai/OmoAuto/releases).
Butuh Android 8.0 (API 26) atau lebih baru dan aplikasi Android Auto.

Setelah terpasang, OmoAuto memeriksa rilis baru sekitar dua kali sehari dan memberi notifikasi.
Ketuk notifikasinya (atau **Tentang › Pembaruan aplikasi**) untuk mengunduh dan memasang versi baru.
Saat pertama kali, Android akan meminta izin **Instal aplikasi tidak dikenal** untuk OmoAuto.

---

## Cara kerja singkat

```
 Android Auto (di HP)                    OmoAuto                                Layar OMOWAY
 ─────────────────────   TLS + AA   ┌───────────────────────────────┐   TCP    ─────────────
 head unit server      ───────────▶ │ AaHeadUnitSession             │          kontrol :9527
 127.0.0.1:5277         H.264 1280p │   → AvcDecoder → GlCompositor │ ───────▶ video   :9528
                                    │   → AvcEncoder (960×360 60fps)│ Wi-Fi
                                    │   → OmoDisplaySession         │ Direct
                                    └───────────────────────────────┘
```

1. HP bergabung ke grup **Wi-Fi Direct** milik motor (motor = group owner).
2. OmoAuto berperan sebagai **head unit Android Auto virtual** dan terhubung ke *head unit server*
   Android Auto di HP yang sama (mode developer).
3. Video Android Auto di-decode, dipotong sesuai margin, lalu di-encode ulang ke 960×360 @ 60 fps
   dan dikirim ke layar motor dengan protokol OMOWAY (port 9527/9528).

Dokumentasi: [Protokol](docs/PROTOCOL.md) · [Arsitektur](docs/ARCHITECTURE.md) ·
[Link OmoWeb](docs/OMOWEB-LINK.md) · [Catatan analisis](docs/REVERSE-ENGINEERING.md) ·
[Changelog](CHANGELOG.md)

---

## Cara pakai aplikasi

1. **Setup** (sekali), dipandu di aplikasi:
   1. Pasang Android Auto.
   2. **Aktifkan mode developer Android Auto**: buka pengaturan Android Auto, gulir ke paling bawah
      sampai **Versi**, ketuk ±10 kali berturut-turut, lalu ketuk **OK** pada dialog
      *Allow development settings?*. Tanpa ini, menu ⋮ **tidak** menampilkan *Start head unit server*.
   3. Di pengaturan Android Auto: ⋮ (kanan atas) → **Start head unit server**. Ulangi setelah HP
      restart atau Android Auto dihentikan paksa.
   4. **Ambil kunci**: sertifikat contoh dari proyek Open Headunit diunduh dan diverifikasi SHA-256
      (tidak dibundel di APK).
   5. HP Xiaomi, Samsung, OPPO/realme/OnePlus, vivo, Huawei/Honor, Infinix/Tecno: izinkan Android Auto
      berjalan di latar belakang (Autostart + baterai tanpa batasan). Setup menunjukkan caranya per merek.
   6. Android 12 ke bawah: nyalakan **Lokasi** (dibutuhkan Wi-Fi Direct untuk menemukan motor).
2. Tutup/hentikan paksa aplikasi lain yang terhubung ke layar motor (mis. aplikasi **OMOWAY**):
   layar hanya melayani satu aplikasi HP dalam satu waktu.
3. Nyalakan motor → **Cari** → pilih motor.
4. **Mulai Android Auto**. Pratinjau tampil di HP dan gambar dikirim ke layar motor.
5. **Kontrol** (saat berhenti): pratinjau langsung (bisa diketuk) dan tiga mode: **Media**,
   **Remote** (D-pad + OK), dan **Sentuh** (layar penuh landscape; ✕ untuk menutup).
6. **Pengaturan › Penyesuaian layar**: pilih tepi bawaan, atau aktifkan **Tepi kustom**.

### YouTube di layar motor: OmoWeb

Browser untuk peluncur Android Auto adalah aplikasi terpisah, **OmoWeb** (`com.logicsekai.omoweb`).
OmoWeb bisa dikendalikan dari layar Kontrol OmoAuto dan dari pratinjau sentuh di Beranda.

---

## Build dari kode sumber

| Kebutuhan | Versi |
|---|---|
| JDK | **17 atau lebih baru** (JBR bawaan Android Studio sudah cukup) |
| Android SDK | Platform 35 + Build-Tools + Platform-Tools (adb) |
| Gradle | Otomatis lewat **Gradle Wrapper** 8.11.1 |
| OS | Windows (script `.bat`), atau macOS/Linux lewat `./gradlew` |

```bash
git clone https://github.com/LogicSekai/OmoAuto.git
cd OmoAuto
./gradlew :app:assembleDebug          # APK debug
./gradlew :app:bundleRelease          # AAB release
./gradlew :app:bundleBeta             # AAB beta
./gradlew test                        # semua unit test
```

### Windows

Jalankan **`omo.bat`** di root project untuk menu interaktif, atau script satuan:

| Script | Fungsi |
|---|---|
| `scripts\build-debug.bat` | APK debug → `dist\OmoAuto-<versi>-debug.apk` (package `.debug`, bisa berdampingan dengan rilis) |
| `scripts\build-release.bat` | APK release (minify + shrink) → `dist\` |
| `scripts\install-usb.bat [debug\|release]` | Build + install lewat **USB** + jalankan aplikasi |
| `scripts\install-wifi.bat [ip:port] [debug\|release]` | Build + install lewat **ADB Wi-Fi** |
| `scripts\bundle-release.bat` | **AAB release** untuk Google Play (Produksi) |
| `scripts\bundle-beta.bat` | **AAB beta** untuk Google Play (Pengujian), versi `-beta` |
| `scripts\signing-setup.bat` | Membuat keystore rilis + `keystore.properties` |
| `scripts\test.bat` | Menjalankan semua unit test |
| `scripts\clean.bat` | Membersihkan build dan `dist\` |

Script mencari JDK dan SDK otomatis (`JAVA_HOME`, JBR Android Studio, `ANDROID_HOME`, atau
`%LOCALAPPDATA%\Android\Sdk`). `local.properties` dibuat otomatis.

### Signing rilis

1. Jalankan `scripts\signing-setup.bat` sekali → membuat `keystore\omoauto-release.jks` dan `keystore.properties`.
2. **Cadangkan keduanya di tempat aman.** Keduanya di-*ignore* git dan tidak boleh di-commit.
3. Tanpa keystore, APK release ditandatangani kunci debug (oke untuk sideload, **tidak** untuk Play).

### Versi

Atur di `gradle.properties`:

```properties
omo.versionCode=300   # naikkan setiap upload ke Play (release maupun beta)
omo.versionName=0.3.0
```

---

## Struktur project

```
OmoAuto/
├─ app/                     Application, MainActivity, navigasi + drawer, DI (AppContainer)
├─ core/
│  ├─ common/               (JVM) logging, util byte
│  ├─ protocol-aa/          (JVM) head unit Android Auto: framing, protobuf, TLS, sesi
│  ├─ protocol-omoway/      (JVM) protokol layar OMOWAY: kontrol, video, antrean live
│  ├─ designsystem/         Tema (warna, Poppins), komponen UI bersama
│  ├─ data/                 Pengaturan (DataStore), kunci head unit, cek update, helper Android Auto
│  └─ streaming/            Wi-Fi Direct, decoder/GL/encoder, ProjectionEngine, foreground service
├─ feature/
│  ├─ onboarding/  home/  setup/  controls/  diagnostics/  settings/  about/
├─ build-logic/convention/  Convention plugin Gradle (konfigurasi modul seragam)
├─ gradle/libs.versions.toml  Semua versi dependency di satu tempat
├─ scripts/                 Script batch build/install/bundle
└─ docs/                    Arsitektur, protokol, lisensi font
```

Modul `core/*` berlabel **(JVM)** bisa di-test di mesin mana pun tanpa Android SDK.

---

## Kontribusi

Kontribusi sangat diharapkan, dan **setiap perbaikan atau fitur wajib dikirim balik** ke repo ini
lewat Pull Request. Baca dulu:

- [CONTRIBUTING.md](CONTRIBUTING.md): alur kerja, konvensi kode, cara build
- [CLA.md](CLA.md): perjanjian kontributor (centang di template PR)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md): kode etik
- [SECURITY.md](SECURITY.md): cara melaporkan celah keamanan secara privat

## Dukung Logic Sekai

OmoAuto gratis dan dikembangkan oleh komunitas **Logic Sekai**. Bila aplikasi ini bermanfaat,
apalagi bila dipakai untuk bisnis, dukung pengembangannya:

| Cara | Tautan | Cocok untuk |
|---|---|---|
| 💛 **GitHub Sponsors** | [github.com/sponsors/LogicSekai](https://github.com/sponsors/LogicSekai) | Sponsor rutin, juga [lisensi komersial](COMMERCIAL-LICENSE.md) |
| ☕ **Ko-fi** | [ko-fi.com/logicsekai](https://ko-fi.com/logicsekai) | Donasi sekali, PayPal atau kartu |
| 🇮🇩 **Trakteer** | [trakteer.id/kwaii](https://trakteer.id/kwaii) | Donasi dari Indonesia: QRIS, e-wallet, bank |

Di aplikasi, buka menu **Dukung** untuk semua tautan ini. Cara lain membantu:

- 🛠️ Kirim perbaikan dan fitur lewat Pull Request
- 🐞 Uji versi beta dan laporkan bug
- ⭐ Beri bintang dan bagikan ke sesama pengguna OMOWAY

---

## Lisensi

Copyright © 2026 **Logic Sekai** (logicsekai@gmail.com)

OmoAuto memakai **lisensi ganda**:

1. **[GNU AGPL-3.0-or-later](LICENSE)** dengan **[Ketentuan Tambahan](NOTICE)** (pasal 7). Ringkasnya:
   - ✅ Boleh dipakai, dipelajari, dimodifikasi, dan **dipakai untuk komersial**.
   - 📢 Setiap versi yang dibagikan (APK, produk, atau layanan jaringan) **wajib membuka seluruh kode
     sumbernya** dengan lisensi yang sama.
   - 🏷️ **Wajib mencantumkan kredit**: *"Based on OmoAuto by Logic Sekai –
     https://github.com/LogicSekai/OmoAuto"* di layar Tentang/Kredit atau dokumentasi, serta
     menyertakan `LICENSE` dan `NOTICE`.
   - ✏️ Versi modifikasi wajib memakai nama aplikasi dan *application ID* sendiri, serta tidak boleh
     memakai nama/logo OmoAuto atau Logic Sekai.
2. **[Lisensi komersial lewat sponsor](COMMERCIAL-LICENSE.md)**: wajib untuk pemakaian kode
   tertutup, tanpa atribusi, atau memakai merek OmoAuto/Logic Sekai. Caranya: menjadi **sponsor
   aktif** repo ini di [GitHub Sponsors](https://github.com/sponsors/LogicSekai) pada tier
   Komersial, lalu konfirmasi ke **logicsekai@gmail.com**. Lisensi berlaku selama sponsor aktif.

Ringkasan ini bukan pengganti teks lisensi; yang berlaku adalah `LICENSE`, `NOTICE`, dan
`COMMERCIAL-LICENSE.md`.

## Kredit

OmoAuto berdiri di atas karya banyak orang. Daftar lengkap dan lisensinya ada di
[**THIRD-PARTY-NOTICES.md**](THIRD-PARTY-NOTICES.md).

- **[Open Headunit](https://github.com/andreknieriem/open-headunit)** oleh
  [@andreknieriem](https://github.com/andreknieriem) dan kontributor (AGPL-3.0): sertifikat contoh
  head unit yang diunduh saat *Ambil kunci* (tidak dibundel).
- **[Headunit](https://github.com/mikereidis/headunit)** oleh almarhum **Michael Reid
  ([@mikereidis](https://github.com/mikereidis))**: perintis rekayasa balik protokol head unit
  Android Auto yang menjadi asal Open Headunit.
- **[Poppins](https://github.com/itfoundry/Poppins)** oleh The Poppins Project Authors (SIL OFL 1.1).
- **Kotlin, kotlinx.coroutines** (JetBrains), **AndroidX & Jetpack Compose, Material 3, Material Icons**
  (Google / Android Open Source Project), semuanya Apache-2.0.
- **Gradle**, **Android Gradle Plugin** (Apache-2.0) dan **JUnit 4** (EPL-1.0) untuk build & test.

## Penafian

- OmoAuto adalah proyek independen. **Tidak berafiliasi** dengan, didukung, atau disponsori oleh
  OMOWAY maupun Google. OMOWAY, Android, Android Auto, dan Google Play adalah merek dagang pemiliknya
  masing-masing.
- Kunci head unit **tidak** dibundel di APK; diunduh saat pengguna mengetuk *Ambil kunci*.
- Disediakan **apa adanya, tanpa garansi**. Gunakan dengan risiko sendiri dan utamakan keselamatan berkendara.

<div align="center">

Dibuat dengan ❤️ oleh komunitas [Logic Sekai](https://logicsekai.com)

</div>
