# Personal Finance REST API

RESTful backend untuk manajemen keuangan pribadi, dibangun dengan **Spring Boot 3**, **Spring Data JPA**, dan **PostgreSQL**.

## Arsitektur

```
com.finance.api
├── controller/       # Layer HTTP request/response
├── service/          # Layer business logic & validasi
├── repository/       # Layer akses database (Spring Data JPA)
├── entity/           # Domain model / JPA Entity
├── dto/              # Data Transfer Object (request & response)
└── exception/        # Custom exception & global error handler
```

## Teknologi

- Java 17
- Spring Boot 3.2
- Spring Data JPA (Hibernate)
- PostgreSQL
- Maven
- Lombok

---

## Setup & Menjalankan

### 1. Buat Database PostgreSQL
```sql
CREATE DATABASE personal_finance_db;
```

### 2. Konfigurasi `application.properties`
Edit file `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/personal_finance_db
spring.datasource.username=postgres
spring.datasource.password=your_password
```

### 3. Jalankan Aplikasi
```bash
mvn spring-boot:run
```

Tabel akan dibuat otomatis oleh Hibernate (`ddl-auto=update`).

---

## API Endpoints

### 1. Buat Akun Baru
**POST** `/api/accounts`
```json
// Request Body
{
  "name": "Bank BCA",
  "initialBalance": 5000000.00
}

// Response 201 Created
{
  "id": 1,
  "name": "Bank BCA",
  "currentBalance": 5000000.00
}
```

### 2. Daftarkan Kategori Anggaran
**POST** `/api/categories`
```json
// Request Body
{
  "name": "Gaji",
  "type": "INCOME"
}

// Response 201 Created
{
  "id": 1,
  "name": "Gaji",
  "type": "INCOME"
}
```
> Nama kategori duplikat akan ditolak dengan **400 Bad Request**.

### 3. Catat Transaksi Keuangan
**POST** `/api/transactions`
```json
// Request Body
{
  "amount": 500000.00,
  "transactionDate": "2024-01-15",
  "description": "Makan siang",
  "accountId": 1,
  "categoryId": 2
}

// Response 201 Created
{
  "id": 1,
  "amount": 500000.00,
  "transactionDate": "2024-01-15",
  "description": "Makan siang",
  "accountId": 1,
  "accountName": "Bank BCA",
  "categoryId": 2,
  "categoryName": "Makanan",
  "categoryType": "EXPENSE"
}
```
> **Business Rules yang diterapkan:**
> - Amount harus > 0
> - EXPENSE tidak boleh melebihi saldo akun (Overdraft Protection → 400)
> - Penyimpanan transaksi + update saldo adalah satu atomic DB transaction

### 4. Ringkasan Akun
**GET** `/api/accounts/{accountId}`
```json
// Response 200 OK
{
  "id": 1,
  "name": "Bank BCA",
  "currentBalance": 4500000.00,
  "recentTransactions": [
    {
      "id": 1,
      "amount": 500000.00,
      "transactionDate": "2024-01-15",
      "description": "Makan siang",
      ...
    }
    // ... hingga 5 transaksi terbaru
  ]
}
```

### 5. Filter Transaksi per Rentang Tanggal
**GET** `/api/transactions?startDate=2024-01-01&endDate=2024-01-31&page=0&size=20`
```json
// Response 200 OK (Paginated)
{
  "content": [...],
  "totalElements": 50,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

### 6. Laporan Keuangan Bulanan
**GET** `/api/reports/monthly?month=1&year=2024`
```json
// Response 200 OK
{
  "month": 1,
  "year": 2024,
  "totalIncome": 10000000.00,
  "totalExpense": 3500000.00,
  "netSavings": 6500000.00
}
```

---

## Business Rules (Service Layer)

| Rule | Detail |
|------|--------|
| Positive Amounts | Amount transaksi harus > 0 (validasi DTO + service) |
| Overdraft Protection | EXPENSE melebihi saldo → 400 Bad Request |
| Transactional Integrity | Jika update saldo gagal, insert transaksi di-rollback |
| No Duplicate Category | Nama kategori harus unik → 400 Bad Request |

## Error Response Format

```json
{
  "status": 400,
  "message": "Insufficient balance. Current balance: 4500000.00, Requested expense: 5000000.00"
}
```
