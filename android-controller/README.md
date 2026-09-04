# Touch Racer Enhanced Controller (Android APK)

Aplikasi controller Android khusus untuk balapan game PC, dirancang sebagai pengganti dan penyempurna aplikasi mobile Touch Racer resmi agar dapat terhubung langsung ke PC server **Touch Racer** (`C:\Program Files (x86)\Touch Racer\Touch Racer.exe`) dengan fitur-fitur mutakhir yang tidak dimiliki aplikasi aslinya.

---

## 🏎️ Fitur Unggulan (Enhanced Features)

1. **Simultaneous Gas & Brake (Bisa Gas & Rem Bersamaan / Terpisah)**
   - **Sisi Kanan Layar:** Pedal Gas / Akselerator analog.
   - **Sisi Kiri Layar:** Pedal Rem analog.
   - **Multi-Touch Pointer ID Tracking:** Jari jempol kiri dan kanan dideteksi secara terpisah tanpa saling mengganggu. Anda bisa melakukan *trail braking*, *launch control*, atau *heel-and-toe* (gas 100% sambil rem 50%).
   - **Mixing Mode:** Tersedia mode *Analog Blend* (kedua pedal berpadu dinamis) dan *Brake Priority* di menu Settings.

2. **Hardware Volume Buttons sebagai Shifter (Gigi Transmisi)**
   - **Volume UP (+):** Gear Up / Shift Up (mengirim sinyal Tombol 1 Touch Racer).
   - **Volume DOWN (-):** Gear Down / Shift Down (mengirim sinyal Tombol 2 Touch Racer).
   - Menghilangkan popup volume bawaan Android (`KeyEvent` dikonsumsi langsung).
   - Dilengkapi **Haptic Feedback** (getaran presisi saat oper gigi) dan indikator gear visual di layar (`R`, `N`, `1`, `2`, `3`, `4`, `5`, `6`).

3. **Sensor Giroskop & Akselerometer Steering (Steering Wheel)**
   - Menggunakan sensor fusion `Sensor.TYPE_ROTATION_VECTOR` (Kalman filter tingkat OS) dengan fallback ke Complementary Filter Gyro + Accel.
   - Respon setir instan tanpa delay dan anti-jitter.
   - **One-Tap Calibrate Button:** Tombol kalibrasi 1-sentuhan untuk menyetel sudut netral (0°) sesuai posisi genggam HP ternyaman Anda.
   - Animasi visual setir racing GT3 berputar *real-time* 60 FPS dengan tampilan derajat sudut (`-35.4°`) dan persentase (`-42%`).

4. **Steering Sensitivity (Sensitivitas 45° hingga 180°)**
   - Dapat diatur bebas melalui slider di menu Settings dari **45°** (sangat responsif/twitchy) hingga **180°** (presisi tinggi simulator).
   - Dilengkapi pengaturan **Deadzone** (0% - 15%) agar mobil tetap stabil lurus di lintasan lurus.

5. **Multi-Konektivitas Lengkap (Wi-Fi, Bluetooth, USB)**
   - **Wi-Fi UDP (Default, Port 41503):** Transmisi datagram ultra low-latency (~70–80 Hz) untuk respon tanpa lag.
   - **Wi-Fi TCP (Port 41503):** Aliran data andal berbasis socket TCP.
   - **Bluetooth (SPP / RFCOMM):** Terhubung ke PC via Bluetooth tanpa perlu router Wi-Fi.
   - **USB (Tethering / ADB Reverse):**
     - Mode USB Tethering: terhubung ke IP gateway USB (biasanya `192.168.42.129`).
     - Mode ADB Reverse: ketik `adb reverse tcp:41503 tcp:41503`, lalu hubungkan ke `127.0.0.1`.

6. **Tombol Balap Tambahan di Layar (Racing Action Buttons)**
   - **P:** Handbrake / Rem Tangan (Tombol 3)
   - **NOS:** Nitro / Boost (Tombol 4)
   - **RST:** Reset Posisi Mobil (Tombol 5)
   - **CAM:** Ganti Kamera (Tombol 6)
   - **ESC:** Pause / Menu (Tombol 7)
   - **HORN:** Klakson / Lampu (Tombol 8)
   - Tombol sentuh shifter `[-]` dan `[+]` di layar jika tidak ingin memakai tombol fisik volume.

---

## 📦 Lokasi File Output

- **File APK Siap Pakai:**
  `G:\MasterJangkir project\TouchRacerEnhancedController\TouchRacerEnhanced.apk`
- **Source Code Proyek Android:**
  `G:\MasterJangkir project\TouchRacerEnhancedController\`
- **Toolchain Android SDK & JDK 17:**
  `G:\MasterJangkir project\tools\`

---

## 🛠️ Cara Install & Menggunakan

### 1. Di HP Android:
1. Salin file `TouchRacerEnhanced.apk` ke HP Anda (bisa lewat kabel data, WhatsApp, Google Drive, atau `adb install TouchRacerEnhanced.apk`).
2. Pasang/Install APK di HP Anda.
3. Buka aplikasi **Touch Racer Enhanced**.

### 2. Di PC:
1. Pastikan driver **vJoy** sudah terpasang.
2. Buka aplikasi **Touch Racer.exe** di:
   `C:\Program Files (x86)\Touch Racer\Touch Racer.exe`
3. Di jendela Touch Racer PC:
   - Masuk ke tab/menu **Controls**.
   - Pastikan **Gear up** dicentang ke Button 1, dan **Gear down** ke Button 2.
   - Touch Racer secara default otomatis membuka listening port **41503** (UDP dan TCP) serta Bluetooth.
4. Cek IP Address PC Anda melalui `cmd` (`ipconfig`), misalnya `192.168.1.15`.

### 3. Menghubungkan Controller ke PC:
1. Di HP Anda, tekan tombol **SETTINGS** di pojok kanan atas.
2. Masukkan IP PC Anda (misal `192.168.1.15`).
3. Port biarkan `41503`.
4. Pilih tipe koneksi (misal **Wi-Fi UDP**).
5. Atur **Steering Sensitivity** sesuai selera (misal `90°`).
6. Tekan **Save**, lalu tekan tombol hijau **CONNECT**.
7. Status akan berubah menjadi hijau `UDP to 192.168.1.15:41503` dan meteran Hz akan menunjukkan pengiriman data aktif (~70 Hz).
8. Pegang HP dalam posisi mengemudi yang nyaman, lalu tekan tombol **CALIBRATE** satu kali untuk menyetel posisi tengah setir.
9. Jalankan game balap Anda (Assetto Corsa, Forza Horizon, F1, NFS, ETS2, dsb.) dan nikmati kendali presisi!

---

## 💻 Cara Rebuild APK (Bila Mengubah Kode)

Jika Anda ingin mengubah kode sumber di masa mendatang, cukup jalankan script berikut dari command prompt di folder proyek:

```cmd
cd /d "G:\MasterJangkir project\TouchRacerEnhancedController"
build.bat
```

Atau menggunakan script Node / Bun:
```cmd
node build.js
```

Build script akan secara otomatis mengompilasi resource, meng-compile Java 8 bytecode, menjalankan dexing `d8`, melakukan alignment 4-byte `zipalign`, dan menandatangani APK secara otomatis dengan cryptographic signature v1, v2, dan v3.
