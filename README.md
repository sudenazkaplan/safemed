# SafeMed - Microservice-Based Health Data Anonymization Network

SafeMed, sağlık kuruluşlarından gelen hassas tıbbi verilerin (HL7, FHIR, JSON, XML) güvenli bir şekilde alınmasını, KVKK ve GDPR standartlarına uygun olarak anonimleştirilmesini ve araştırmacılara güvenli bir şekilde sunulmasını sağlayan distributed bir backend altyapısıdır.

## Proje Mimarisi ve Servisler

Proje, birbirlerinden bağımsız çalışan 5 temel microservice'den oluşmaktadır:

1. **Medical Record Ingestion Service (Java / Spring Boot):** Hastanelerden gelen ham verileri güvenli bir şekilde karşılar, doğrular ve asenkron işlenmek üzere RabbitMQ kuyruğuna aktarır.
2. **Schema Registry & Adapter Service:** Farklı formatlardaki (FHIR, XML vb.) verileri SafeMed'in ortak iç formatına dönüştürür.
3. **Redaction & Anonymization Engine (Python / FastAPI):** Microsoft Presidio SDK ve Diferansiyel Gizlilik (Differential Privacy) kullanarak kişisel verileri (PII) maskeler ve temizler.
4. **Audit & Compliance Logger (Java / Spring Boot):** Sidecar pattern kullanarak sistemdeki tüm veri hareketlerini immutable (değiştirilemez) olarak PostgreSQL üzerinde loglar.
5. **Researcher API Gateway (Java / Spring Boot):** Araştırmacıların sisteme güvenli erişimini (JWT Auth), gRPC haberleşmesini ve Webhook entegrasyonunu yönetir.

## Teknoloji Yığını (Tech Stack)

- **Diller:** Java (Spring Boot), Python (FastAPI)
- **Veritabanları:** MongoDB (Anonim Veriler), PostgreSQL (Audit Logları), Redis (Cache)
- **Mesaj Kuyruğu:** RabbitMQ (Asenkron Pipeline)
- **İletişim:** gRPC (İç Haberleşme), REST (Dış Dünya)
- **Konteynerleştirme:** Docker & Docker Compose

## Kurulum ve Çalıştırma

*(Proje ilerledikçe buraya Docker çalıştırma komutları eklenecektir)*