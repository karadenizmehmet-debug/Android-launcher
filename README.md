# Sade Ev v1.1

Philips Android TV için sade ana ekran (launcher). TV kumandasıyla yönetilir.

Mehmet Karadeniz, 2026-2027

## Görünüm
- Tam ekran duvar kâğıdı (gece / gündüz için ayrı)
- Altta tek sıra uygulama kutucuğu (16:9 banner), odaklanınca büyür ve beyaz çerçeve alır
- Sol üstte ad + sürüm, sağ üstte saydam şerit: Gece/Gündüz, Paylaş, Ayarlar, saat

## Özellikler
- **+ Uygulama Ekle** kutucuğu: listeden işaretle, Kaydet
- Kutucukta **OK'a uzun bas** (veya Menü tuşu): Sola taşı / Sağa taşı / Kaldır / Uygulama bilgisi
- Seçimler ve tema TV'de kalıcı olarak saklanır
- Kaldırılan uygulamalar listeden kendiliğinden düşer

## Derleme
`main` dalına her push'ta GitHub Actions APK derler (~3 dk).
APK: Releases > **latest** > `SadeEv.apk`

## TV'ye kurulum
1. TV'de Google Play'den **Downloader** (AFTVnews) uygulamasını kur.
2. Ayarlar > Gizlilik/Güvenlik > **Bilinmeyen kaynaklar**: Downloader'a izin ver.
3. Downloader'a şu adresi yaz:
   `https://github.com/karadenizmehmet-debug/Android-launcher/releases/latest/download/SadeEv.apk`
4. Kur, aç.

## Varsayılan ana ekran yapma
- Kurduktan sonra kumandada **Home** tuşuna bas. Seçim penceresi çıkarsa "Sade Ev" > **Her zaman**.
- Pencere çıkmazsa: Ayarlar > Uygulamalar > Varsayılan uygulamalar > Ana ekran uygulaması.
- Philips bazı modellerde Home tuşunu kendi ekranına kilitler. O durumda Sade Ev, uygulama listesinden açılır.
  Bilgisayardan ADB ile zorlamak mümkün (geri alınabilir):
  ```
  adb connect TV_IP:5555
  adb shell cmd package set-home-activity com.karadeniz.sadeev/.MainActivity
  ```
