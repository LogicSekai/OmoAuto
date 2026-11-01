# Kredit & pemberitahuan pihak ketiga

OmoAuto ditulis oleh Logic Sekai, tetapi berdiri di atas karya banyak orang. Terima kasih kepada
semua pembuat proyek di bawah ini. Setiap komponen tetap berada di bawah lisensinya sendiri;
lisensi AGPL OmoAuto **tidak** mengubah lisensi mereka.

*Third-party credits and notices. Each component below keeps its own license.*

## Dibundel di dalam aplikasi

| Komponen | Pembuat | Lisensi | Dipakai untuk |
|---|---|---|---|
| [Kotlin standard library](https://github.com/JetBrains/kotlin) | JetBrains s.r.o. dan kontributor Kotlin | Apache-2.0 | Bahasa & runtime |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | JetBrains s.r.o. dan kontributor | Apache-2.0 | Concurrency (`core`, `feature`) |
| [AndroidX](https://developer.android.com/jetpack/androidx): Core KTX, Activity Compose, Lifecycle, Navigation Compose, DataStore Preferences, Core SplashScreen, WorkManager | The Android Open Source Project | Apache-2.0 | Fondasi aplikasi Android |
| [Jetpack Compose](https://developer.android.com/jetpack/compose): UI, Foundation, Material 3 | The Android Open Source Project | Apache-2.0 | Seluruh UI |
| [Material Icons Extended](https://fonts.google.com/icons) | Google LLC | Apache-2.0 | Ikon UI |
| [Poppins](https://github.com/itfoundry/Poppins) (Regular, Medium, SemiBold, Bold) | The Poppins Project Authors (Indian Type Foundry) | SIL OFL 1.1 ([teks lisensi](docs/licenses/Poppins-OFL.txt)) | Font aplikasi (`core/designsystem/src/main/res/font/`) |

## Diunduh saat runtime (tidak dibundel)

| Komponen | Pembuat | Lisensi | Keterangan |
|---|---|---|---|
| [Open Headunit](https://github.com/andreknieriem/open-headunit), sertifikat & kunci contoh (`app/src/main/res/raw/cert`, `privkey`) | [@andreknieriem](https://github.com/andreknieriem) dan kontributor | AGPL-3.0 | Diunduh hanya saat pengguna mengetuk **Ambil kunci**, dari commit `a8d8a92d80256feee56174a21d2d33cc2664c9a7`, lalu dicek SHA-256-nya. Lihat `core/data/.../HeadUnitKeyRepository.kt`. |
| ↳ [Headunit](https://github.com/mikereidis/headunit) (asal Open Headunit) | **Michael Reid** ([@mikereidis](https://github.com/mikereidis), alm.) | AGPL-3.0 | Perintis rekayasa balik protokol head unit Android Auto. Open Headunit adalah kelanjutan dari karyanya. |

## Hanya untuk build & pengujian (tidak masuk APK)

| Komponen | Pembuat | Lisensi |
|---|---|---|
| [Gradle](https://gradle.org) & Gradle Wrapper (`gradle/wrapper/gradle-wrapper.jar`) | Gradle, Inc. | Apache-2.0 |
| [Android Gradle Plugin](https://developer.android.com/build) | Google LLC | Apache-2.0 |
| [Kotlin Gradle Plugin & Compose Compiler Plugin](https://github.com/JetBrains/kotlin) | JetBrains s.r.o. | Apache-2.0 |
| [JUnit 4](https://github.com/junit-team/junit4) | JUnit team | EPL-1.0 |
| [kotlin-test](https://github.com/JetBrains/kotlin), kotlinx-coroutines-test | JetBrains s.r.o. | Apache-2.0 |
| GitHub Actions: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), [upload-artifact](https://github.com/actions/upload-artifact), [gradle/actions](https://github.com/gradle/actions) | GitHub, Inc. / Gradle, Inc. | MIT |

Sertifikat di `core/protocol-aa/src/test/resources/` dibuat sendiri khusus untuk unit test
(self-signed, sekali pakai) dan tidak pernah dipakai oleh aplikasi.

## Merek dagang

OMOWAY, Android, Android Auto, Google Play, dan nama terkait adalah merek dagang milik pemiliknya
masing-masing. OmoAuto adalah proyek independen dan **tidak berafiliasi** dengan, didukung, atau
disponsori oleh OMOWAY maupun Google.

## Teks lisensi

- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- SIL Open Font License 1.1: [`docs/licenses/Poppins-OFL.txt`](docs/licenses/Poppins-OFL.txt)
- GNU AGPL v3.0: [`LICENSE`](LICENSE)
- Eclipse Public License 1.0: <https://www.eclipse.org/legal/epl-v10.html>
- MIT License: <https://opensource.org/license/mit>

Menemukan kredit yang kurang atau salah? Buka issue atau kirim email ke logicsekai@gmail.com.
