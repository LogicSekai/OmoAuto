# Kontribusi ke OmoAuto

Terima kasih sudah mau ikut mengembangkan OmoAuto! Proyek ini hidup dari kontribusi komunitas
**Logic Sekai**. Prinsipnya sederhana: **kalau kamu memperbaiki atau menambah sesuatu, kirim balik
ke sini** supaya semua pengendara OMOWAY ikut merasakannya.

*English speakers are welcome. Issues and pull requests may be written in Indonesian or English.*

## Sebelum mulai

1. Baca [Kode Etik](CODE_OF_CONDUCT.md).
2. Pahami lisensinya: OmoAuto memakai **AGPL-3.0-or-later + Ketentuan Tambahan** ([`NOTICE`](NOTICE))
   dan lisensi ganda dengan [lisensi komersial](COMMERCIAL-LICENSE.md).
3. Kontribusi pertama wajib menyetujui [CLA](CLA.md) (cukup centang di template Pull Request) dan
   setiap commit wajib *sign-off* (`git commit -s`).
4. Untuk perubahan besar (fitur baru, ubah protokol, ubah arsitektur), **buka issue dulu** agar
   arahnya disepakati sebelum kamu menghabiskan waktu.

## Cara berkontribusi

- **Lapor bug**: pakai template *Laporan bug*. Lampirkan hasil **Diagnostik › Salin** dari
  aplikasi, model HP, versi Android & Android Auto, serta model motor.
- **Usul fitur**: pakai template *Usulan fitur*.
- **Terjemahan**: teks UI ada di `res/values/strings.xml` (Inggris, default) dan
  `res/values-in/strings.xml` (Indonesia) di tiap modul.
- **Kode & dokumentasi**: lewat Pull Request (lihat di bawah).
- **Celah keamanan**: **jangan** lewat issue publik. Ikuti [SECURITY.md](SECURITY.md).

## Menyiapkan lingkungan

| Kebutuhan | Versi |
|---|---|
| JDK | 17 atau lebih baru (JBR bawaan Android Studio cukup) |
| Android SDK | Platform 35 + Build-Tools + Platform-Tools |
| IDE | Android Studio (versi stabil terbaru) |

```bash
git clone https://github.com/LogicSekai/OmoAuto.git
cd OmoAuto
./gradlew :app:assembleDebug        # Windows: scripts\build-debug.bat atau omo.bat
./gradlew test                      # Windows: scripts\test.bat
```

## Alur kerja

1. **Fork** repo ini, lalu buat branch dari `main`: `feature/<singkat>`, `fix/<singkat>`,
   atau `docs/<singkat>`.
2. Commit kecil dan jelas, format `<area>: <ringkasan>` dalam bahasa Indonesia atau Inggris.
   Contoh: `setup: deteksi server lebih cepat`, `protocol-omoway: handle status 7/3`.
   Selalu pakai `git commit -s`.
3. Jalankan `./gradlew test` (atau `scripts\test.bat`) dan pastikan build debug berhasil.
4. Tambahkan catatan di bagian *belum dirilis* pada [`CHANGELOG.md`](CHANGELOG.md) bila
   perubahannya terasa oleh pengguna.
5. Buka Pull Request ke `main` dan isi template-nya. CI (GitHub Actions) menjalankan unit test dan
   build APK debug. PR digabung setelah CI hijau dan disetujui maintainer.

## Konvensi kode

- Kotlin official style (`.editorconfig` sudah disiapkan; Android Studio akan mengikutinya).
- Komentar kode dan nama simbol dalam **bahasa Inggris**. **Jangan** hard-code teks UI; selalu
  lewat `strings.xml` (EN + ID).
- Logika protokol masuk ke modul JVM (`core/protocol-*`) beserta unit test-nya.
  Kode yang butuh API Android masuk ke `core/streaming` atau `core/data`.
- Gunakan komponen dari `core/designsystem` (`OmoPalette`, `OmoTheme`, kartu/tombol) agar
  tampilan konsisten.
- Dependency baru: tulis versinya di `gradle/libs.versions.toml`, jangan di `build.gradle.kts`
  modul. Lisensinya harus kompatibel dengan AGPL-3.0, dan tambahkan kreditnya ke
  [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) serta daftar `NOTICES` di layar *Tentang*
  (`feature/about/.../AboutScreen.kt`).
- File Kotlin baru diawali header lisensi:

  ```kotlin
  // SPDX-License-Identifier: AGPL-3.0-or-later
  // Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.
  ```

## Memakai kode orang lain

- Jangan menyalin kode dari proyek lain tanpa menyebut sumber dan lisensinya di PR.
- Kode hasil dekompilasi aplikasi pihak ketiga **tidak** diterima. Tulis ulang dari perilaku yang
  teramati, disertai unit test.
- Jangan commit kunci, sertifikat produksi, APK, atau file dari `_reference/`.

## Rilis (maintainer)

1. Naikkan `omo.versionCode` dan `omo.versionName` di `gradle.properties`.
2. **GitHub (pembaruan dalam aplikasi):** `scripts\build-release.bat` → `dist\OmoAuto-<versi>-release.apk`.
   Buat GitHub Release dengan tag **`v<versionName>`** (mis. `v0.3.1`), lampirkan APK itu, dan
   tempel ringkasan CHANGELOG sebagai deskripsi. Jangan tandai sebagai *pre-release*.
   - APK **wajib** ditandatangani kunci rilis yang sama (`keystore.properties`). Kunci berbeda
     membuat pembaruan ditolak Android.
   - Aplikasi membaca rilis terbaru lewat GitHub API, memilih aset `.apk` yang bukan debug,
     memverifikasi SHA-256 dari GitHub, nama paket, dan versionCode sebelum memasang.
3. **Google Play:** `scripts\bundle-beta.bat` → jalur pengujian, lalu `scripts\bundle-release.bat`
   → produksi. Script ini memakai `-Pomo.distribution=play`: pembaruan mandiri, izin
   `REQUEST_INSTALL_PACKAGES`, dan tautan donasi dimatikan sesuai kebijakan Play.

## Rahasia

`keystore/`, `keystore.properties`, `local.properties`, `dist/`, dan `_reference/` di-*ignore*.
Jangan pernah meng-commit kunci signing, password, atau token.

## Dukung tanpa menulis kode

Tidak bisa ngoding? Kamu tetap bisa membantu: uji versi beta, laporkan bug, bantu jawab pertanyaan
di komunitas, atau jadi sponsor repo ini lewat [GitHub Sponsors](https://github.com/sponsors/LogicSekai).
