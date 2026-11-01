# Kebijakan Keamanan

## Versi yang didukung

| Versi | Didukung |
|---|---|
| 0.3.x | ✅ |
| < 0.3 | ❌ |

## Melaporkan celah keamanan

**Jangan** laporkan celah keamanan lewat issue publik.

Gunakan salah satu jalur privat berikut:

1. **GitHub › tab Security › Report a vulnerability** (private vulnerability reporting), atau
2. email ke **logicsekai@gmail.com** dengan subjek `[SECURITY] OmoAuto`.

Sertakan versi OmoAuto, model HP dan versi Android, langkah reproduksi, serta dampaknya.

Kami akan membalas dalam **7 hari**, memberi kabar perkembangan, dan mencantumkan nama Anda di
catatan rilis (bila Anda mau) setelah perbaikan dirilis.

## Lingkup

Termasuk lingkup laporan, misalnya:
- penanganan sertifikat/kunci head unit dan sesi TLS ke Android Auto (`core/protocol-aa`, `core/data/keys`);
- link broadcast OmoAuto ↔ OmoWeb (`OmoWebLink`) yang bisa dipalsukan aplikasi lain;
- protokol layar OMOWAY dan Wi-Fi Direct (`core/protocol-omoway`, `core/streaming`);
- kebocoran data pribadi lewat log atau fitur *Bagikan diagnostik*.

Di luar lingkup: celah pada Android Auto, firmware OMOWAY, atau aplikasi pihak ketiga. Laporkan ke
pemiliknya masing-masing.
