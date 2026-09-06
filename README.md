# HeelAndThumb 🏎️

> **An Android Steering Wheel & PC Receiver Ecosystem for Racing Games**

[![Release](https://img.shields.io/badge/Release-Pre--Release_0.2.0-00E5FF.svg)](https://github.com/MasterJangkir/HeelAndThumb/releases)
[![Android](https://img.shields.io/badge/Platform-Android_5.0+-00E676.svg)](https://android.com)
[![Windows](https://img.shields.io/badge/Platform-Windows_x64-1976D2.svg)](https://microsoft.com)
[![vJoy](https://img.shields.io/badge/Driver-vJoy_2.x-FFD700.svg)](https://github.com/shauleiz/vJoy)
[![License](https://img.shields.io/badge/License-MIT-white.svg)](LICENSE)

**HeelAndThumb** adalah ekosistem pengendali balap balap virtual yang mengubah smartphone Android Anda menjadi setir balap berpresisi tinggi dengan pedal gas dan rem simultan mandiri, paddle shifter berbasis tombol volume hardware, serta receiver Windows 64-bit berlatensi nol yang terintegrasi langsung dengan driver **vJoy**.

---

## 📦 Komponen Proyek

| Komponen | Nama | Platform | Deskripsi |
|---|---|---|---|
| 📱 **Controller** | **HeelAndThumb** | Android (`.apk`) | Aplikasi kemudi setir gyro, pedal simultan, dan paddle shifter. |
| 🖥️ **Receiver** | **HeelAndThumb Rx** | Windows 64-bit (`.exe`) | PC Receiver GUI berlatensi nol yang langsung mengontrol vJoy. |

---

## ⚡ Fitur Utama (Features)

### 1. 🦶 Simultaneous Gas & Brake (Split Pedals)
- **Sisi Kanan:** Pedal Gas / Akselerator analog penuh.
- **Sisi Kiri:** Pedal Rem analog penuh.
- **Multi-Touch Pointer ID Tracking:** Jempol kiri dan kanan terisolasi 100%. Anda bisa menginjak rem 100% sambil menekan gas 100% untuk teknik *trail braking*, *launch control*, atau *burnout* tanpa saling membatalkan.

### 2. 🎮 Shoulder Paddle Shifter (Tombol Volume Tanpa Capping)
- **Volume Up (+):** Right Shoulder / Paddle Up (**vJoy Button 1 / RB**).
- **Volume Down (-):** Left Shoulder / Paddle Down (**vJoy Button 2 / LB**).
- **Single-Click Only (Hold Repeat Mati):** Menahan tombol volume tidak akan memicu pergantian gigi liar. 1 kali tekan = tepat 1 kali klik gigi.
- **15-Frame Buffer:** Setiap klik gigi dikunci dan ditransmisikan selama 15 paket (~150 ms) berturut-turut, ditambah *120ms latch buffer* di receiver, menjamin 100% klik terbaca oleh game engine tanpa miss-shift.
- **Tanpa Batasan (No Capping):** Tidak ada batasan 6 gigi. Gearbox apa pun (4-speed, 5-speed, 7-speed, sequential) langsung diatur oleh game balap Anda.

### 3. 🧭 Stabilized Roll Steering & Horizon Gyroscope Pesawat
- **3D Gravity Anchored:** Orientasi 3D menyerap kemiringan arah muka (*pitch*) untuk mengunci titik tengah tetap stabil (*zero center drift*).
- **Pure Roll Steering:** Sumbu kemudi murni membaca rotasi putar layar smartphone.
- **100% Linear Response:** Kurva kemudi linear murni (respons 0°–45° dan 45°–90° memiliki presisi dan kecepatan yang persis sama).
- **Artificial Horizon HUD:** Setir bulat digantikan dengan garis horison giroskop pesawat tempur yang ramping di tengah layar.
- **Fixed Landscape Lock:** Layar dikunci permanen ke mode horizontal. Tidak akan pernah terbalik 180° saat Anda menyetir tajam melebihi 90°.

### 4. 🛑 Dedicated Handbrake (Khusus Drifting)
- Tombol **HANDBRAKE** murni memicu **vJoy Button 3** tanpa memaksa sumbu rem kaki (*Axis Z*).
- Pemain drift dapat menginjak rem kaki (misal 30%) sambil menarik rem tangan secara terpisah untuk inisiasi sudut drift.

### 5. ⚡ Ultra-Lean 8-Byte High-Speed Transmission (100 Hz)
- Paket data dipadatkan menjadi **8-byte statis**:
  - `Byte 0`: Header (`0x01`)
  - `Byte 1..2`: Steering Axis X (`0..32767`, center = `16384`)
  - `Byte 3..4`: Throttle / Gas Axis Y (`0..32767`)
  - `Byte 5..6`: Brake Pedal Axis Z (`0..32767`)
  - `Byte 7`: Buttons Bitmask (Shift Up, Shift Down, Handbrake, NOS, Camera, Reset, Pause, Horn)
- Frekuensi kirim **100 Hz (setiap 10 ms)** dengan latensi sub-milidetik.

### 6. 🚀 Zero-Lag Receiver & Background Power Saving
- **Single-IOCTL Update (`UpdateVJD`):** Receiver memperbarui semua sumbu dan 8 tombol dalam **1 panggilan driver tunggal atomik**, memangkas 11x panggilan driver yang sebelumnya menjadi penyebab lag.
- **Process & Thread Priority High:** Thread receiver disetel ke `ThreadPriority.Highest` dan `ProcessPriorityClass.High`, memastikan respons kontrol tetap 0 ms meskipun game balap berjalan 144 FPS *fullscreen*.
- **Background CPU Saver:** Saat diminimize ke background, receiver mematikan seluruh render UI (`uiTimer.Stop()`), memangkas penggunaan CPU PC hingga mendekati 0.0%.
- **Instant GDI+ FastBars:** Menghilangkan jeda animasi ~300 ms bawaan Windows ProgressBar. Bar hijau/merah di PC meloncat seketika dalam **0.0 milidetik**.
- **Tampilan Persentase (%):** Tampilan kemudi di receiver menggunakan persentase akurat (`L 100%` s/d `R 100%`), tersinkronisasi otomatis dengan sensitivitas apa pun yang Anda atur di HP (45°, 90°, 120°, 180°).

---

## 📥 Prasyarat: Driver vJoy (Virtual Joystick)

Receiver Windows (`HeelAndThumbRx.exe`) membutuhkan driver **vJoy** agar Windows dan game balap dapat mengenali smartphone Anda sebagai perangkat *joystick / racing wheel* virtual:

- **Download Installer Resmi vJoy:** 
  🔗 [https://github.com/shauleiz/vJoy/releases](https://github.com/shauleiz/vJoy/releases)
- **Download Versi Terbaru (Latest Release):**
  🔗 [vJoySetup.exe di GitHub Releases](https://github.com/shauleiz/vJoy/releases/latest)
- **Source Code & Repositori vJoy:**
  🔗 [https://github.com/shauleiz/vJoy](https://github.com/shauleiz/vJoy)
- **Mirror Alternatif (SourceForge):**
  🔗 [https://sourceforge.net/projects/vjoysoftware/](https://sourceforge.net/projects/vjoysoftware/)

> *Catatan: Setelah menginstal vJoy, pastikan aplikasi `vJoyConf.exe` (Configure vJoy) telah mengaktifkan **Device 1** dengan minimal sumbu X, Y, Z dan 8 tombol.*

---

## 🎮 Panduan Penggunaan Cepat

### 1. Persiapan di PC:
1. Unduh dan pasang driver [vJoy](https://github.com/shauleiz/vJoy/releases) (jika belum terpasang).
2. Unduh dan ekstrak **`HeelAndThumbRx-v0.2.0-prerelease.zip`** dari folder `release/` atau dari [Halaman Rilis GitHub](https://github.com/MasterJangkir/HeelAndThumb/releases).
3. Jalankan **`HeelAndThumbRx.exe`**.
4. Pastikan status berwarna hijau: `vJoy: Device 1 ACQUIRED (Ready!)`.
   *(Jika tertulis BUSY karena Touch Racer lama terbuka, klik tombol **"Take vJoy Control"**)*.
5. Catat IP yang tertera pada bagian `PC IP:` (contoh: `192.168.1.15`).
### 2. Persiapan di Android:
1. Unduh dan pasang **`HeelAndThumb-v0.2.0-prerelease.apk`** di smartphone Anda.
2. Buka aplikasi **HeelAndThumb**.
3. Buka **SETTINGS**, masukkan IP PC Anda, pilih tipe koneksi (Wi-Fi TCP / UDP), lalu tap **Save**.
4. Tap tombol **CONNECT**.
5. Pegang HP dalam posisi mengemudi yang nyaman, lalu tap tombol **CALIBRATE** satu kali.
6. Jalankan game balap favorit Anda (Assetto Corsa, Forza Horizon, F1, NFS, BeamNG, ETS2, dsb.) dan petakan sumbu kemudi serta pedal!

---

## 🛠️ Kompilasi dari Source Code

### Android Controller (`android-controller/`):
```cmd
cd android-controller
build.bat
```
*(Menghasilkan file `HeelAndThumb.apk` yang sudah di-align dan di-sign).*

### Windows Receiver (`windows-receiver/`):
```cmd
cd windows-receiver
build.bat
```
*(Mengompilasi source code C# 64-bit menggunakan compiler bawaan Windows `csc.exe` menjadi `HeelAndThumbRx.exe`).*

---

## 📜 Lisensi
Proyek ini dilisensikan di bawah [MIT License](LICENSE).
