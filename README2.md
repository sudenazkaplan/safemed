1. Aşama: Sahte Uç Noktaları (Mock Endpoints) Gerçekleştirme
Şu an sistemde sahte yanıt dönen Ingestion Service endpoint'lerini gerçek veritabanı sorgularına bağlamalıyız.
Görev 1: /api/v1/ingestion/status/{trackingId}: trackingId ile PostgreSQL audit_logs tablosuna sorgu atıp verinin o anki durumunu gerçek zamanlı döndürecek servisi yazın.
Görev 2: /api/v1/ingestion/records: Veritabanındaki tüm ham kayıt metadatalarını sayfalama (pagination) özelliği ile listeleyen metodu tamamlayın.
2. Aşama: Servisler Arası Entegrasyon Testleri (End-to-End Test)
Sistemdeki mikroservisler birbiriyle başarılı şekilde konuşuyor mu bunu otomatize etmeliyiz.
Görev 3: Bir "Klinik İstemci" (Clinical Client) gibi davranıp sisteme bir veri gönderen ve verinin Ingestion -> RabbitMQ -> Anonymization -> MongoDB yolculuğunu başarıyla tamamlayıp tamamlamadığını doğrulayan bir entegrasyon testi (Postman koleksiyonu veya Cypress) oluşturun.
Görev 4: Bir veri işlenirken sistemin Audit servisine doğru logları (Başlangıç, Başarılı, Anomali vs.) atıp atmadığını test edin.
3. Aşama: Güvenlik ve Kimlik Doğrulama (Authentication & Authorization)
Proje bir sağlık verisi sistemi ve "KVKK-compliant" (KVKK uyumlu) olduğu belirtiliyor.
Görev 5: Sistemin girişine, örneğin Ingestion Service ve Audit Service API'lerine, bir JWT (JSON Web Token) kimlik doğrulama mekanizması ekleyin. Sadece yetkili hastanelerin veri gönderebilmesini sağlayın.
4. Aşama: Hata Yönetimi (Error Handling) ve Alarm (Alerting) Sistemi
Görev 6: Eğer Anonymization servisinde Python tarafı çökerse veya RabbitMQ bağlantısı koparsa, sistemin veriyi kaybetmemesini sağlayacak (Dead Letter Queue - DLQ) yapısını kurun.
Görev 7: Audit servisine düşen "ANOMALY" (Anomali) durumları için ilgili sistem yöneticilerine e-posta veya Slack mesajı atan basit bir bildirim mekanizması kurgulayın.
