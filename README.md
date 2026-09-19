# MD04_SS08: Microservices Circuit Breaker & Fallback Method (Resilience4j)

Dự án Microservices mẫu minh họa cơ chế **Circuit Breaker** (Ngắt mạch khi sập nguồn) và **Fallback Method** (Phương án dự phòng) sử dụng **Spring Boot 3**, **Java 21**, **Gradle** và thư viện **Resilience4j**.

---

## 1. Kiến trúc Hệ thống

Hệ thống bao gồm 2 dịch vụ độc lập:
1. **`doctor-service`** (Cổng `8082`):
   - Quản lý và cung cấp API kiểm tra lịch trực của các bác sĩ.
   - Endpoint: `GET /doctors/{doctorId}/schedule`

2. **`appointment-service`** (Cổng `8083`):
   - Tiếp nhận yêu cầu đặt lịch hẹn từ bệnh nhân (`POST /api/v1/appointments`).
   - Gọi sang `doctor-service:8082` để kiểm tra lịch trực của bác sĩ trước khi tạo lịch hẹn.
   - Tích hợp **Circuit Breaker** (`doctorServiceCB`) và **Fallback Method** (`getDoctorFallback`) bằng Resilience4j.
   - Khi `doctor-service` gặp sự cố hoặc mạch `OPEN`, hệ thống tự động trả về đối tượng lỗi chuẩn `ApiResponseError` với HTTP status `503 Service Unavailable` thay vì báo lỗi 500 hoặc bị treo.

```
                      +-----------------------------+
                      |   Client / Postman          |
                      +--------------+--------------+
                                     |
                                     | POST /api/v1/appointments
                                     | { "patientName": "...", "doctorId": 1, "reason": "..." }
                                     v
                 +---------------------------------------+
                 |       Appointment-Service (8083)      |
                 |                                       |
                 |      [Circuit Breaker: doctorServiceCB]|
                 |          - CLOSED / OPEN / HALF_OPEN  |
                 |          - Fallback: getDoctorFallback|
                 |            -> ApiResponseError (503)  |
                 +-------------------+-------------------+
                                     | 
                     (Ngắt khi sập)  | Gọi REST API
                                     v
                 +---------------------------------------+
                 |         Doctor-Service (8082)         |
                 |  - GET /doctors/{id}/schedule         |
                 +---------------------------------------+
```

---

## 2. Cấu hình Circuit Breaker (`application.properties`)

File cấu hình tại `appointment-service/src/main/resources/application.properties`:

```properties
spring.application.name=appointment-service
server.port=8083

# Endpoint kết nối sang Doctor-Service
doctor.service.url=http://localhost:8082

# Cấu hình Resilience4j Circuit Breaker cho instance "doctorServiceCB"
resilience4j.circuitbreaker.instances.doctorServiceCB.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.doctorServiceCB.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.doctorServiceCB.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.doctorServiceCB.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.doctorServiceCB.sliding-window-size=5
resilience4j.circuitbreaker.instances.doctorServiceCB.sliding-window-type=COUNT_BASED

# Actuator Endpoints theo dõi trạng thái
management.endpoints.web.exposure.include=health,info,circuitbreakers,circuitbreakerevents
management.endpoint.health.show-details=always
management.health.circuitbreakers.enabled=true

# Ghi log DEBUG chi tiết của Resilience4j
logging.level.io.github.resilience4j=DEBUG
logging.level.com.example.appointmentservice=INFO
```

---

## 3. Nội dung Bài tập 2: Fallback Method (Phương án dự phòng)

### 3.1. DTO `ApiResponseError`
Được trả về khi `doctor-service` gặp sự cố hoặc mạch ngắt:
```java
public class ApiResponseError {
    private String message;
    private int status;
    private String error;
    private String timestamp;
}
```

### 3.2. Cài đặt Annotation & Hàm Fallback
Trong `AppointmentService.java`:
```java
@CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "getDoctorFallback")
public Object checkDoctor(Long doctorId) {
    String url = doctorServiceUrl + "/doctors/" + doctorId + "/schedule";
    return restTemplate.getForObject(url, DoctorScheduleDto.class);
}

public ApiResponseError getDoctorFallback(Exception e) {
    log.warn("[FALLBACK] getDoctorFallback duoc kich hoat: {}", e.getMessage());
    return ApiResponseError.ofDoctorServiceError();
}
```

---

## 4. Hướng dẫn chạy & Thực hành

### Bước 1: Khởi động 2 dịch vụ

- **Terminal 1: Khởi động Doctor-Service (8082)**
  ```powershell
  .\gradlew :doctor-service:bootRun
  ```

- **Terminal 2: Khởi động Appointment-Service (8083)**
  ```powershell
  .\gradlew :appointment-service:bootRun
  ```

---

### Bước 2: Kiểm tra khi hệ thống bình thường (Doctor-Service hoạt động)

Gửi request tạo lịch hẹn:
```powershell
curl -X POST http://localhost:8083/api/v1/appointments `
  -H "Content-Type: application/json" `
  -d '{\"patientName\": \"Nguyễn Văn A\", \"doctorId\": 1, \"reason\": \"Đau bụng\"}'
```

**Kết quả nhận được (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Dat lich hen thanh cong cho benh nhan Nguyễn Văn A voi bac si Dr. Nguyen Van A",
  "appointmentId": 4582,
  "doctorSchedule": {
    "doctorId": 1,
    "doctorName": "Dr. Nguyen Van A",
    "specialty": "Tim mach",
    "availableTime": "08:00 - 11:30",
    "available": true
  },
  "circuitBreakerState": "CLOSED"
}
```

---

### Bước 3: Kiểm tra Fallback khi Doctor-Service bị tắt (Bài tập 2)

1. Tắt dịch vụ `doctor-service` (bấm `Ctrl + C` ở Terminal 1).
2. Gửi request đặt lịch hẹn tới `appointment-service`:
```powershell
curl -X POST http://localhost:8083/api/v1/appointments `
  -H "Content-Type: application/json" `
  -d '{\"patientName\": \"Nguyễn Văn A\", \"doctorId\": 1, \"reason\": \"Đau bụng\"}'
```

**Kết quả nhận được (HTTP 503 Service Unavailable):**
```json
{
  "message": "Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây",
  "status": 503,
  "error": "Doctor Service Error",
  "timestamp": "2026-03-10T20:42:53.2542232"
}
```

> **Nhận xét**: Client nhận được JSON lỗi chuẩn (status 503) và thông báo chuyên nghiệp kể cả khi hệ thống lõi đang gặp sự cố, hệ thống không bị crash hay trả về lỗi 500 không xác định.

---

### Bước 4: Kiểm tra ngắt mạch Circuit Breaker (Bài tập 1)

Thực hiện gọi API 5 lần liên tiếp khi `doctor-service` đang tắt:
```powershell
1..5 | ForEach-Object {
    Write-Host "--- Lần gọi $_ ---"
    curl -X POST http://localhost:8083/api/v1/appointments `
      -H "Content-Type: application/json" `
      -d '{\"patientName\": \"Nguyễn Văn A\", \"doctorId\": 1, \"reason\": \"Đau bụng\"}'
    Start-Sleep -Milliseconds 200
}
```

Quan sát log của `appointment-service`:
```text
⚡⚡⚡ [CIRCUIT BREAKER: doctorServiceCB] CHUYEN TRANG THAI: CLOSED -> OPEN ⚡⚡⚡
```
Kiểm tra trạng thái mạch:
```powershell
curl http://localhost:8083/appointments/circuit-breaker-status
```
Kết quả hiển thị: `"state": "OPEN"`. Từ thời điểm này, mọi request đến lập tức được ngắt mạch và trả về Fallback ngay (0ms).

---

## 5. Chạy Kiểm Thử Tự Động (Automated Testing)

Dự án đã tích hợp đầy đủ test suite cho cả 2 bài tập với JUnit 5 & MockMvc:
```powershell
.\gradlew test
```
Tất cả các ca kiểm thử:
- `testFallbackWhenDoctorServiceIsDown`: Xác minh API trả về HTTP 503 và `ApiResponseError` đúng chuẩn đề bài.
- `testDirectServiceFallbackMethod`: Xác minh hàm `getDoctorFallback(Exception e)` trả về đúng dữ liệu.
- `testSuccessWhenDoctorServiceIsUp`: Xác minh luồng thành công khi service UP.
- `testCircuitBreakerOpensAfterFiveFailures`: Xác minh chuyển trạng thái mạch `CLOSED -> OPEN` sau 5 lần lỗi.
- `testHalfOpenTransition`: Xác minh chuyển đổi trạng thái sang `HALF_OPEN`.

Kết quả: **100% Tests Passed**.
