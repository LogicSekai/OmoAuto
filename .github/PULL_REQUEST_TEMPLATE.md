## Ringkasan

<!-- Apa yang diubah dan kenapa? Tautkan issue terkait, mis. "Closes #12". -->

## Jenis perubahan

- [ ] Perbaikan bug
- [ ] Fitur baru
- [ ] Refactor / performa
- [ ] Dokumentasi / terjemahan
- [ ] Build / CI

## Cara menguji

<!-- Langkah uji, HP + versi Android/Android Auto, model motor bila relevan. Lampirkan screenshot untuk perubahan UI. -->

## Checklist

- [ ] `./gradlew test` (atau `scripts\test.bat`) lulus dan build debug berhasil
- [ ] Teks UI lewat `strings.xml` (EN di `values/`, ID di `values-in/`), tidak di-hard-code
- [ ] File Kotlin baru memakai header `SPDX-License-Identifier: AGPL-3.0-or-later`
- [ ] Dependency/kode pihak ketiga baru (bila ada) berlisensi kompatibel AGPL dan sudah dicatat di `THIRD-PARTY-NOTICES.md` + layar *Tentang*
- [ ] `CHANGELOG.md` diperbarui bila perubahan terasa oleh pengguna
- [ ] Semua commit sudah *sign-off* (`git commit -s`)

## Perjanjian kontributor

- [ ] I have read the OmoAuto CLA ([CLA.md](https://github.com/LogicSekai/OmoAuto/blob/main/CLA.md)) and I agree to its terms for this and future contributions.
