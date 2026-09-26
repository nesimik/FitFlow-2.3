# FitFlow Pro 2.3 — Kurulum

Bu klasör eksiksiz bir Android projesidir (Kotlin + Jetpack Compose, tek modül, tek aktivite).
Uygulama kimliği: `com.aistudio.fitflowpro3.kgryzq` — önceki sürümlerle aynı.

## Önemli: GitHub → AI Studio yolu Android için çalışmaz
Google AI Studio'da Android projeleri yalnızca Build modunda "Android" seçilerek oluşturulur.
GitHub'dan içe aktarılan projeler web uygulaması olarak açılır; bu yüzden ekranlar
"masaüstü gibi" görünür. Bu projedeki bir eksiklikten kaynaklanmaz.

## Yol 1 — Mevcut AI Studio projesini güncelle (önerilen, veri korunur)
`fitflow-2.3-guncelleme-paketi.zip` içindeki 27 dosyayı, AI Studio'daki mevcut FitFlow
projesinin Code sekmesine dosya dosya yapıştır, sonra derle ve "Install on Device" ile yükle.

## Yol 2 — Android Studio ile derle ve USB ile yükle
1. Telefondaki uygulamadan verini JSON olarak dışa aktar (Profil ve ayarlar → Yedekleme).
2. Bu klasörü Android Studio'da aç (File → Open). Gradle wrapper yoksa Android Studio
   oluşturmayı önerir; kabul et.
3. Telefonu USB ile bağla (Geliştirici seçenekleri → USB hata ayıklama açık), Run'a bas.
4. "Paket mevcut paketle çakışıyor" uyarısı çıkarsa: eski sürüm AI Studio anahtarıyla
   imzalandığı için üzerine kurulamaz. Eski uygulamayı kaldır, yenisini kur, JSON yedeğini
   içe aktar.

## Yol 3 — GitHub derlesin, APK'yı telefona indir (bilgisayara kurulum gerekmez)
1. Bu klasörün içindekileri GitHub reposuna yükle (`.github` klasörü dahil).
2. Repoda **Actions** sekmesine gir; "APK derle" otomatik başlar (yaklaşık 5–10 dk).
   Başlamazsa: Actions → APK derle → **Run workflow**.
3. Yeşil tik çıkınca çalışmaya tıkla → en altta **Artifacts → FitFlow-Pro-APK** indir.
4. İnen zip'in içindeki `.apk` dosyasını telefona at ve aç ("bilinmeyen kaynaklara izin ver").

`debug.keystore` projeye bilerek eklendi: her derleme aynı anahtarla imzalanır, böylece sonraki
sürümler telefondaki uygulamanın üzerine sorunsuz güncellenir. (AI Studio'dan kurulan eski sürüm
farklı anahtarla imzalı olduğu için ilk kurulumda bir kez kaldırıp yedeği geri yüklemen gerekir.)
