# Changelog

## 0.4.0 — 2026-11-04

### Baru
- **Pengaturan › Tampilan › Tema Android Auto:** empat mode siang/malam untuk Android Auto (peta
  dan aplikasi yang mengikutinya): **Otomatis** (mengikuti tema gelap HP, bawaan), **Terang**,
  **Gelap**, dan **Mengikuti jam** (jam mulai gelap dan terang bisa diatur, bawaan 18:00–06:00).
  Langsung berlaku tanpa menyambung ulang. Bingkai dan bilah ikon Android Auto memang selalu gelap.
  Menggantikan sakelar "Mode malam Android Auto"; yang sebelumnya menyala menjadi **Gelap**.
- **Pengaturan › Penyesuaian layar › Warna tepi:** ruang kosong di sekitar gambar di layar motor
  bisa **Gelap** (bawaan) atau **Terang**, terpisah dari tema Android Auto. Langsung berlaku.

### Berubah
- **Motor tidak lagi disimpan.** Beranda kini cukup **Cari motor** lalu ketuk motornya; daftar
  "Motor saya" dan daftar motor di Pengaturan dihapus. Sebelumnya motor tersimpan sering gagal
  tersambung lagi dan harus dihapus lalu ditambahkan ulang: Wi-Fi Direct hanya bisa bergabung ke
  perangkat yang baru saja ditemukan Android, dan motor tersimpan disambungkan tanpa mencari dulu.
  Sekarang setiap sambungan (termasuk sambung ulang otomatis saat koneksi putus) selalu mencari motor
  terlebih dahulu dan baru bergabung setelah motor terlihat.

### Perbaikan
- **Kontrol › Sentuh lebih akurat:** koordinat sentuhan kini berupa piksel area UI Android Auto
  (tanpa margin, tanpa penskalaan). Sebelumnya margin ikut dihitung sehingga ketukan meleset satu
  ikon ke bawah (mis. Spotify membuka Telepon). Gerakan yang tidak mengubah titik sentuh tidak lagi
  dikirim (antrean input tidak penuh), dan gestur kembali sistem tidak lagi menelan sentuhan di tepi
  pratinjau.
- **Sentuh tetap presisi dengan tepi kustom:** layar sentuh kini diumumkan ke Android Auto seukuran
  area UI (gambar tanpa margin), sehingga titik sentuh cocok berapa pun tepi yang diatur. Layar
  sentuh juga diumumkan sebagai kapasitif, seperti head unit sungguhan.
- **Scroll di aplikasi HP (OmoWeb, dll.) tanpa tombol Kembali/Exit:** Android Auto menganggap geseran
  yang dimulai dalam 100 dp dari tepi layar sebagai gestur sistem dan memunculkan toolbar Kembali/Exit.
  Di layar 360 px, zona atas + bawah menutupi lebih dari separuh tinggi, jadi hampir setiap scroll
  memicunya. Kini geseran yang dimulai di zona tepi dikirim mulai dari titik terdekat di luar zona
  (jarak geser tetap sama); ketukan dan tekan lama tetap di posisi aslinya.

## 0.3.1 — 2026-11-01

- **Pembaruan dalam aplikasi:** OmoAuto memeriksa GitHub Releases (otomatis sekitar dua kali sehari,
  bisa dimatikan), memberi notifikasi saat ada versi baru, lalu mengunduh dan memasang APK langsung
  dari aplikasi. Unduhan dicek SHA-256, nama paket, dan versinya. Layar baru **Pembaruan aplikasi**
  (dari notifikasi, banner Beranda, atau Tentang). Build Google Play tetap diperbarui lewat Play.
- Halaman **Dukung** (menu samping): donasi lewat GitHub Sponsors, Ko-fi, dan Trakteer, plus cara
  berkontribusi (kode, laporan bug, panduan) dan tombol bagikan.
- Lisensi komersial: ketentuan tambahan agar tidak memberi hak atas merek/perangkat OMOWAY dan Google.

- **Repositori publik** di github.com/LogicSekai/OmoAuto. Lisensi ganda: GNU AGPL-3.0-or-later
  dengan ketentuan atribusi (`NOTICE`) atau lisensi komersial bagi sponsor repo (`COMMERCIAL-LICENSE.md`). Layar
  Tentang kini menampilkan pemberitahuan lisensi dan kredit Open Headunit / Headunit.

- **Perbaikan utama layar motor hitam:** SPS/PPS di paket gambar utuh kini dikirim **dengan start code
  `00 00 00 01`**, sama seperti WayXAuto. Sebelumnya dikirim tanpa start code, sehingga layar yang
  baru menyala tidak bisa menyiapkan pemutar videonya dan tetap hitam; baru tampil setelah WayXAuto
  pernah menyiapkannya ("pancingan").

- Wi-Fi Direct ke motor kini **persisten**, sama seperti WayXAuto (`enablePersistentMode`). Grup
  sementara sisa koneksi sebelumnya dilepas lalu disambung ulang secara persisten. Diduga inilah
  "pancingan" dari WayXAuto: layar yang mengenal HP hanya lewat grup sementara menerima koneksi
  tetapi tetap hitam.
- Peringatan "OMOWAY masih memakai layar" yang muncul terus-menerus dihapus.

- Layar motor hitam: penyebab sebenarnya adalah **aplikasi OMOWAY resmi** yang masih aktif dan memegang
  layar. Sinyal "layar dipakai" dari motor (sistem 1/7, status 3) kini dikenali dengan benar: Beranda
  langsung menyuruh menghentikan paksa OMOWAY dan OmoAuto mencoba menghentikan salinan latar
  belakangnya. Setiap pesan dari layar memicu key frame, seperti WayXAuto.
- Kontrol › Media: **Pemutar media** memakai tombol media standar Android (putar/jeda, sebelumnya,
  berikutnya, mundur, maju cepat), jadi bekerja dengan Spotify, YouTube Music, OmoWeb, dan pemutar
  lain. Kotak **OmoWeb**: Layar penuh dan **Ultrawide** (layar penuh + video di-zoom selebar layar,
  atas-bawah terpotong); aktif hanya saat OmoWeb tampil di layar motor.
- Kontrol › **Sentuh** (mode ketiga): pratinjau memenuhi layar HP dalam posisi landscape dan bisa
  disentuh seperti layar sentuh Android Auto; tombol ✕ (atau Kembali) menutupnya ke mode sebelumnya.
- Keterangan status pratinjau ("Pratinjau di HP ini…") kini di bawah gambar, tidak menutupi gambar.
- Pengaturan › Penyesuaian layar: **Tepi kustom** untuk mengetik sendiri ruang kosong kiri, kanan,
  atas, dan bawah (satuan layar 960 × 360), dengan pratinjau ukuran gambar dan tombol atur ulang.

- Kontrol bekerja di **OmoWeb**: Android Auto tidak meneruskan tombol ke aplikasi di layar mobil,
  jadi selama OmoWeb tampil di layar motor, tombol Kontrol dikirim langsung ke OmoWeb
  (`docs/OMOWEB-LINK.md`). Tombol Beranda tetap ke Android Auto.
- Mode Remote: tombol atas/bawah kini sama dengan "sebelumnya"/"berikutnya" di mode Media
  (Android Auto tidak menanggapi D-pad atas/bawah).

- Halaman **Kontrol**: pratinjau langsung (bisa disentuh) di bagian atas, dan dua mode tombol yang
  pilihannya diingat: **Media** (gaya kenop putar: sebelumnya/kiri/pilih/kanan/berikutnya) dan
  **Remote** (D-pad gaya remote TV: atas/bawah/kiri/kanan mengelilingi OK; tahan panah untuk mengulang).
- Lebih ringan: pratinjau di HP hanya digambar saat ada gambar baru dari Android Auto, maksimal
  30 fps, dan tidak menunggu vsync layar HP, sehingga tidak memperlambat video ke motor.
  Perpindahan Beranda ↔ Kontrol tidak lagi bisa memutus pratinjau.

- Pratinjau di Beranda kini berfungsi sebagai layar sentuh: ketuk/geser di HP dikirim ke Android
  Auto sebagai event sentuh (head unit kini mengumumkan touchscreen 1280×720).

- Setup: langkah mode developer & head unit server diperjelas (ketuk Versi ±10 kali → OK; tanpa itu
  menu ⋮ tidak menampilkan Start head unit server).
- Layar motor dipakai aplikasi lain (balasan 7/3, mis. aplikasi OMOWAY): OmoAuto mengenalinya,
  menyebut aplikasinya + tombol info aplikasi (Paksa berhenti), dan tersambung sendiri setelahnya.
- Key frame setiap 6 frame dan bitrate default 6 Mbps, sama dengan WayXAuto.
- Layar motor hitam: timestamp video kini dihitung dari awal sesi (seperti WayXAuto), bukan dari
  waktu sejak HP menyala. Diagnostik menampilkan paket kontrol dari layar, frame pertama terkirim,
  serta statistik sesi layar dan pipeline video.
- Android Auto yang tidak menjawab (dibekukan HP di latar belakang / server macet) kini dikenali
  dalam 4 detik: OmoAuto berhenti mencoba ulang (retry justru menumpuk koneksi menggantung) dan
  Beranda menampilkan langkah + tombol Buka Android Auto. Retry saat server belum jalan dibatasi 6x.
- Perbaikan crash saat memulai Android Auto (`NoSuchFieldError NEED_UNWRAP_AGAIN`): handshake TLS
  tidak lagi memakai API Java 9+ yang tidak ada di Android. Modul protokol kini di-compile
  terhadap API Java 8 (`-Xjdk-release=1.8`) agar kesalahan sejenis tertangkap saat build.
- Setup: langkah "Biarkan Android Auto berjalan" kini untuk semua brand yang membatasi aplikasi latar
  belakang (Xiaomi/Redmi/POCO, Samsung, OPPO/realme/OnePlus, vivo/iQOO, Huawei/Honor,
  Infinix/Tecno/itel), dengan instruksi dan tombol pintas Autostart khusus per brand.
- Setup + Beranda: pengecekan layanan Lokasi di Android 8–12 (wajib menyala agar Wi-Fi Direct
  bisa menemukan motor), dengan tombol ke pengaturan Lokasi.

## 0.3.0 — OmoAuto (rewrite)

Ditulis ulang total dari WayXAuto 0.2.7 sebagai `com.logicsekai.omoauto`.

- Arsitektur multi-modul (core JVM untuk protokol, core Android, modul fitur) + convention plugin.
- UI baru dengan Jetpack Compose: drawer oranye, kartu motor, widget statistik, Setup bertahap,
  layar Kontrol, Diagnostik dengan skor koneksi, Pengaturan lengkap. Tema terang/gelap.
- Dwibahasa: Indonesia dan Inggris.
- Protokol Android Auto head unit dan layar OMOWAY diimplementasi ulang dengan unit test
  end-to-end (fake phone dengan TLS sungguhan, fake display).
- Fitur baru: banyak motor tersimpan, sambung ulang otomatis, mode malam Android Auto, pilihan
  tema layar motor, salin/bagikan diagnostik, cek update GitHub (build debug).
- Dihapus: Firebase Analytics, pemeriksaan integritas native (`libwayxautocore.so`).
- Script batch: build debug/release, install USB & Wi-Fi ADB, AAB release & beta, signing.
