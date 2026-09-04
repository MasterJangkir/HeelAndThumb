# Touch Racer Receiver + (Windows PC Receiver)

Aplikasi Windows Receiver mandiri (*standalone*) berbasis C# 64-bit dengan GUI modern untuk menghubungkan controller Android **Touch Racer Enhanced** langsung ke driver **vJoy** di PC tanpa batasan dan tanpa bug yang ada pada Touch Racer lama.

---

## 🚀 Keunggulan Dibandingkan Touch Racer Bawaan

1. **True Split Pedals (Gas & Rem Independen Sejati)**:
   - Di Touch Racer lama, gas dan rem dipaksa ke 1 sumbu tunggal (*Axis Y*), sehingga tidak bisa gas dan rem berbarengan.
   - Di **Touch Racer Receiver +**:
     - **Gas / Akselerator:** Sumbu `Axis Y` (`0` hingga `32767`).
     - **Rem / Brake:** Sumbu `Axis Z` (`0` hingga `32767`).
     - Anda bisa menekan gas 100% sambil menginjak rem 100% secara bersamaan untuk *trail-braking*, *launch control*, atau *burnout*!
   - Tersedia juga opsi *Combined Pedals (Axis Y)* untuk game arcade jadul.

2. **Dukungan Tombol & Shifter Tanpa Batas**:
   - **Tombol 1 (Shift Up / RB):** Dipicu oleh Volume Up (+) di HP.
   - **Tombol 2 (Shift Down / LB):** Dipicu oleh Volume Down (-) di HP.
   - **Tombol 3 (Handbrake):** Rem tangan.
   - **Tombol 4 (NOS / Boost):** Nitro.
   - **Tombol 5–8:** Reset, Camera, Pause, Horn.
   - Tidak ada batasan atau capping gigi (gigi mobil berapa pun langsung diatur oleh game).

3. **Dual Network Listener (UDP + TCP Sekaligus)**:
   - Otomatis membuka listening port `41503` untuk UDP dan TCP secara bersamaan.
   - Baik HP Anda terhubung via UDP maupun TCP, paket data langsung diterima dan diproses instan dengan latensi sub-milidetik.

4. **Tampilan Cockpit Lengkap & Indikator LED Input Real-Time**:
   - Menampilkan alamat **IP PC** secara otomatis di bagian atas layar (sehingga Anda langsung tahu IP apa yang harus dimasukkan di HP tanpa perlu buka `ipconfig`).
   - Meteran live **Hz** (laju transmisi paket).
   - Bar visual untuk **Steering (Axis X)**, **Throttle (Axis Y)**, dan **Brake (Axis Z)**.
   - 8 kotak tombol interaktif yang menyala (*highlight*) secara *real-time* saat tombol ditekan di HP.
   - Tombol **1-Klik "Take vJoy Control"** yang otomatis menutup Touch Racer lama jika sedang mengunci Device 1 vJoy.

---

## 📦 Lokasi File Aplikasi

- **File Eksekusi Receiver:**
  `G:\MasterJangkir project\TouchRacerReceiver\TouchRacerReceiver.exe`
- **Source Code C#:**
  `G:\MasterJangkir project\TouchRacerReceiver\ReceiverForm.cs`
- **Script Build Ulang:**
  `G:\MasterJangkir project\TouchRacerReceiver\build.bat`

---

## 🎮 Cara Menggunakan

1. **Jalankan Receiver di PC:**
   Buka file `TouchRacerReceiver.exe` di:
   `G:\MasterJangkir project\TouchRacerReceiver\TouchRacerReceiver.exe`
   *(Catatan: Jika Touch Racer lama masih terbuka, klik tombol **"Take vJoy Control"** di aplikasi ini untuk mengambil alih vJoy Device 1).*
2. **Lihat IP PC Anda:**
   Di bagian atas aplikasi `TouchRacerReceiver.exe`, perhatikan tulisan `PC IP: 192.168.x.x`.
3. **Hubungkan dari HP:**
   - Di HP Android, buka aplikasi **Touch Racer Enhanced**.
   - Masuk ke **SETTINGS**, masukkan IP PC tersebut, lalu simpan.
   - Tekan **CONNECT**.
4. Status di PC akan langsung berubah menjadi **Connected** hijau dan angka Hz akan berdetak (~70–80 Hz).
5. Gerakkan HP Anda atau tekan gas/rem/volume shifter, semua indikator bar dan tombol di PC akan bergerak dan menyala secara instan!
6. Buka game balap favorit Anda dan nikmati kontrol balap presisi!
