# Catatan analisis WayXAuto 0.2.7

OmoAuto ditulis ulang dari APK WayXAuto 0.2.7 (`com.sdmv.wayxauto`, versionCode 22) karena
source code aslinya hilang. Dokumen ini mencatat apa yang diambil dari APK dan bagaimana
analisisnya bisa diulang.

APK dan library native-nya disimpan di `_reference/` (di-*ignore* git, jangan di-commit).

## Fakta yang diverifikasi dari APK

| Fakta | Sumber di APK | Dipakai di |
|---|---|---|
| minSdk 26, targetSdk 36 | Manifest | `gradle/libs.versions.toml` |
| Port layar motor 9527 (kontrol) dan 9528 (video) | konstanta di `x/q.run` dan `x/t` | `OmoProtocol` |
| Resolusi layar 960×360 | konstanta di `x/t.d` | `VideoGeometry` |
| Head unit server Android Auto di port 5277 | `x/l.run`, `x/ob.g` | `HeadUnitConfig` |
| JSON `{"foreground":"1"}`, `{"mode":"night"}`, field `screenWidth` | string di `x/q` | `OmoProtocol` |
| Kunci contoh Open Headunit, commit `a8d8a92d…` | URL di `x/qa`, `x/lg` | `HeadUnitKeyRepository` (SHA-256 dicek) |
| Wi-Fi Direct (motor = group owner), izin `NEARBY_WIFI_DEVICES` | Manifest, `WifiOnlyService` | `WifiDirectLink` |
| Foreground service tipe `connectedDevice` | Manifest | `ProjectionService` |
| `libwayxautocore.so` hanya mengekspor `JNI_OnLoad` (pemeriksaan integritas) | REA `inspect_native_exports` | **Tidak** dibawa ke OmoAuto |
| Firebase Analytics | Manifest | **Tidak** dibawa ke OmoAuto |

Detail format paket ada di [`PROTOCOL.md`](PROTOCOL.md).

## Mengulang analisis dengan REA

REA (konektor MCP di Claude Desktop) bisa dipakai langsung pada `_reference/WayXAuto-0.2.7.apk`.

Tool yang langsung jalan tanpa tool tambahan:
- `inspect_apk_signature`: penandatangan dan skema signature.
- `inspect_native_exports`, `inspect_elf_notes`: isi `libwayxautocore.so`.

Untuk dekompilasi kelas dan resource Android, REA memerlukan:
1. **JADX headless MCP 0.7.1** + JDK. Set environment `REA_JADX_MCP_JAR` ke path JAR-nya.
   Ini membuka `inspect_android_package`, `search_android_classes`, `inspect_android_class`,
   `inspect_android_method`, dan `trace_android_references`.
2. **apktool** di `PATH` (atau set `REA_APKTOOL_COMMAND`). Ini membuka `decode_android_resources`.

Tambahkan variabel tersebut di blok `"env"` entri `rea` pada `claude_desktop_config.json`, lalu
restart Claude Desktop.

Contoh permintaan ke Claude:

> Pakai REA, cari di `_reference/WayXAuto-0.2.7.apk` kelas yang menangani paket kontrol tipe 7
> dari layar motor, lalu bandingkan dengan `OmoProtocol.kt`.

## Yang sengaja tidak disalin

- Kode hasil dekompilasi tidak disalin ke OmoAuto. Semua kode ditulis ulang dari perilaku yang
  teramati, dengan unit test yang memerankan sisi lawan (HP dan layar motor palsu).
- Aset (font Figtree, ikon) diganti: Poppins (OFL) dan ilustrasi skuter buatan sendiri di kode.
