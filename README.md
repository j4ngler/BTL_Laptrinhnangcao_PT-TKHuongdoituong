## Ứng dụng quản lý văn bản (Desktop CLI, Java + PostgreSQL + MongoDB GridFS)

### Yêu cầu
- Java 17+
- PostgreSQL (đã cài, có DB `docmgmt` hoặc URL tuỳ chọn)
- MongoDB (đã cài, dùng GridFS)

### Biến môi trường (có giá trị mặc định)
- `PG_URL` (mặc định: `jdbc:postgresql://localhost:5432/docmgmt`)
- `PG_USER` (mặc định: `postgres`)
- `PG_PASS` (mặc định: `postgres`)
- `MONGO_URI` (mặc định: `mongodb://localhost:27017`)
- `MONGO_DB` (mặc định: `docmgmt`)
- `MONGO_BUCKET` (mặc định: `files`)

Windows PowerShell ví dụ:
```powershell
$env:PG_URL="jdbc:postgresql://localhost:5432/docmgmt"
$env:PG_USER="postgres"
$env:PG_PASS="postgres"
$env:MONGO_URI="mongodb://localhost:27017"
$env:MONGO_DB="docmgmt"
$env:MONGO_BUCKET="files"
```

### Build & chạy
```powershell
# Cách 1: Gradle (khuyến nghị nếu đã có wrapper)
./gradlew clean build
./gradlew run --args "--help"

# Cách 2: Maven (không cần Gradle)
mvn -v
mvn clean package
mvn exec:java -Dexec.args="--help"
# hoặc chạy JAR đóng gói kèm phụ thuộc
java -jar target/docmgmt-0.1.0-jar-with-dependencies.jar --help

# Chạy giao diện Desktop (JavaFX)
# PowerShell cần đặt biến môi trường trước (PG_URL, PG_USER, ...)
mvn -P jfx -Djavafx.platform=win javafx:run  # nếu dùng plugin javafx-maven-plugin
# hoặc
mvn -Dexec.mainClass=com.example.docmgmt.gui.GuiApp exec:java

# Chạy giao diện Desktop đơn giản (Swing, không cần JavaFX)
mvn -q -DskipTests package
java -cp target/docmgmt-0.1.0-jar-with-dependencies.jar com.example.docmgmt.gui.SwingApp
```

### Lệnh CLI chính
- Thêm văn bản (tạo metadata ở PostgreSQL, tệp vào GridFS):
```powershell
./gradlew run --args "--add C:\\duongdan\\vanban.pdf --title \"Quyết định 123\""
```

- Liệt kê văn bản:
```powershell
./gradlew run --args "--list"
```

- Xuất tệp văn bản theo id:
```powershell
./gradlew run --args "--export 1:C:\\output\\vanban-1.pdf"
```

- Tìm kiếm theo tiêu đề:
```powershell
./gradlew run --args "--search \"Quyết định\""
```

- Thêm phiên bản và xuất theo phiên bản:
```powershell
./gradlew run --args "--add-version 1:C:\\duongdan\\vanban-sua.pdf --title \"Quyết định 123 (sửa)\""
./gradlew run --args "--export-version 1:2:C:\\output\\vanban-1-v2.pdf"
```

- Chuyển trạng thái theo lưu đồ (submit → classify → approve → issue → archive):
```powershell
./gradlew run --args "--submit 1:alice:gui-xet-duyet"
./gradlew run --args "--classify 1:bob:noi-bo|mat|ghi-chu-phan-loai"  # format: actor:phanloai|domat|ghichu
./gradlew run --args "--approve 1:carol:duyet-noi-dung"
./gradlew run --args "--issue 1:dave:ban-hanh"  # tự cấp số văn bản tăng theo năm (doc_number/doc_year)
./gradlew run --args "--archive 1:eve:luu-tru"
```

### Quản lý người dùng và vai trò
- Thêm/cập nhật người dùng với vai trò: `CREATOR`, `CLASSIFIER`, `APPROVER`, `PUBLISHER`, `ARCHIVER`
```powershell
./gradlew run --args "--add-user alice:CREATOR"
./gradlew run --args "--add-user bob:CLASSIFIER"
./gradlew run --args "--add-user carol:APPROVER"
./gradlew run --args "--add-user dave:PUBLISHER"
./gradlew run --args "--add-user eve:ARCHIVER"
```

- Liệt kê người dùng:
```powershell
./gradlew run --args "--list-users"
```

Khi thực hiện chuyển trạng thái, CLI sẽ kiểm tra vai trò phù hợp của người thực hiện.

### Kiến trúc rút gọn bám lưu đồ
- PostgreSQL: lưu metadata `documents` (trạng thái, thời gian), `document_versions` (lịch sử tệp), `audit_logs` (hành động + người thực hiện + ghi chú)
- MongoDB GridFS: lưu nội dung tệp văn bản, trả về `fileId` liên kết vào metadata
- Các trạng thái hỗ trợ: `DRAFT` → `SUBMITTED` → `CLASSIFIED` → `APPROVED` → `ISSUED` → `ARCHIVED`
  - Công cụ CLI đã chặn chuyển trạng thái sai thứ tự, ví dụ không thể `approve` khi chưa `classify`.
  - Khi `classify`, hệ thống lưu `classification` và `security_level`.
  - Khi `issue`, hệ thống tự gán `doc_number` tăng dần theo `doc_year` hiện tại.

### Thư mục mã nguồn
- `src/main/java/com/example/docmgmt/App.java`: CLI
- `src/main/java/com/example/docmgmt/config/Config.java`: cấu hình kết nối
- `src/main/java/com/example/docmgmt/domain/Models.java`: domain model + trạng thái
- `src/main/java/com/example/docmgmt/repo/DocumentRepository.java`: PostgreSQL (metadata, version, audit)
- `src/main/java/com/example/docmgmt/repo/GridFsRepository.java`: MongoDB GridFS
- `src/main/java/com/example/docmgmt/service/DocumentService.java`: thêm/list/export
- `src/main/java/com/example/docmgmt/service/WorkflowService.java`: chuyển trạng thái + audit

### Ghi chú
- Lần đầu chạy, bảng sẽ được tạo tự động (migrate đơn giản trong repository).
- Nếu muốn thêm phiên bản mới cho văn bản, hiện có thể gọi `--add` với cùng `--title` rồi tự cập nhật. API thêm version chuyên biệt có thể bổ sung sau.

