# do_an_ltm
---

## 📌 1. Giới Thiệu Tổng Quan
Đồ án xây dựng một hệ thống mạng hoàn chỉnh theo kiến trúc **Client - Server** bằng ngôn ngữ **Java**, tích hợp đầy đủ các kỹ thuật cốt lõi của môn học Lập trình mạng:
* **Giao thức hướng nối TCP Socket:** Định tuyến nhắn tin riêng 1 - 1, đăng nhập/đăng ký người dùng và truyền tệp nhị phân chia khối 100 byte.
* **Giao thức UDP Multicast:** Phát thông báo tức thời từ Server đến các Client trong nhóm (`230.0.0.1:9999`), hỗ trợ Client chủ động Tham gia hoặc Rời nhóm.
* **Java RMI (Remote Method Invocation):** Cung cấp dịch vụ triệu gọi từ xa trên cổng `1099` để Client tra cứu thống kê dữ liệu tập trung.
* **Lập trình đa luồng & Đồng bộ hóa (Concurrency & Synchronization):** Xử lý đồng thời nhiều Client và có giao diện đo lường thực nghiệm xung đột dữ liệu (*Race Condition* vs *Thread-safe*).
* **Lưu trữ dữ liệu bền vững:** Quản lý người dùng qua `users.csv`, danh sách tệp qua `uploaded_files.csv`, và nhật ký hoạt động qua `server_log.txt`.

---

## 📂 2. Cấu Trúc Mã Nguồn (Đã Sắp Xếp Chuẩn Hóa)

```
e:\do an ltm
│
├── compile.bat                          # Biên dịch nhanh dự án với 1 cú click
├── run_server.bat                       # Khởi động Server GUI
├── run_client.bat                       # Khởi động Client GUI
├── pom.xml                              # Cấu hình chuẩn Maven Project (NetBeans / Eclipse / IntelliJ)
│
├── data/                                # Thư mục dữ liệu tập trung
│   ├── users.csv                        # Danh sách tài khoản người dùng
│   ├── uploaded_files.csv               # Danh sách tệp tin đã tiếp nhận
│   ├── server_log.txt                   # Nhật ký hoạt động toàn hệ thống
│   ├── sample_test.pdf                  # Tệp PDF mẫu để thử nghiệm truyền nhận
│   └── uploads/                         # Thư mục chứa các tệp tin vật lý sau khi ghép
│
└── src/main/java/com/ltm/               # Mã nguồn Java chuẩn phân lớp
    ├── client/                          # GIAO DIỆN & TIẾN TRÌNH CLIENT
    │   ├── ClientGUI.java               # Màn hình chính Client (4 Tab chức năng)
    │   └── LoginDialog.java             # Cửa sổ Đăng nhập & Đăng ký tài khoản
    │
    ├── server/                          # DỊCH VỤ & TIẾN TRÌNH SERVER
    │   ├── ServerGUI.java               # Màn hình quản trị Server trung tâm
    │   ├── ClientHandler.java           # Luồng TCP phục vụ từng Client kết nối
    │   ├── ServerStats.java             # Bộ nhớ thống kê tập trung & đồng bộ dữ liệu
    │   ├── StatisticServiceImpl.java    # Cài đặt dịch vụ triệu gọi từ xa RMI
    │   └── MulticastBroadcaster.java    # Bộ phát sóng thông báo nhóm UDP Multicast
    │
    ├── common/                          # CÁC ĐỐI TƯỢNG DÙNG CHUNG (SHARED)
    │   ├── User.java                    # Đối tượng Người dùng (Mã, Tên, Email)
    │   ├── FileInfo.java                # Đối tượng thông tin tệp tin
    │   └── IStatisticService.java       # Interface RMI triệu gọi từ xa
    │
    └── concurrency/                     # ĐO LƯỜNG ĐA LUỒNG & ĐỒNG BỘ HÓA
        └── ConcurrencyRaceConditionDemo.java # Thực nghiệm so sánh Có vs Không đồng bộ
```

---

## 🚀 3. Hướng Dẫn Chạy Chương Trình

### Cách 1: Chạy trực tiếp bằng 1 cú Click (Nhanh nhất trên Windows)
1. Chạy **`run_server.bat`**: Màn hình Server sẽ tự động mở lên và kích hoạt toàn bộ cổng mạng (TCP 8888, RMI 1099, Multicast 230.0.0.1:9999).
2. Chạy **`run_client.bat`**: Mở Client thứ nhất $\rightarrow$ Nhập UserID: `U001` $\rightarrow$ Bấm **Đăng Nhập Vào Hệ Thống**.
3. Chạy **`run_client.bat`** lần 2: Mở Client thứ hai $\rightarrow$ Nhập UserID: `U002` $\rightarrow$ Bấm **Đăng Nhập Vào Hệ Thống**.

### Cách 2: Mở và chạy trên Apache NetBeans
1. Mở NetBeans $\rightarrow$ **File** $\rightarrow$ **Open Project...** $\rightarrow$ Chọn thư mục `e:\do an ltm`.
2. Mở cây thư mục: `Source Packages` $\rightarrow$ `com.ltm.server`.
3. Nhấp chuột phải vào file **`ServerGUI.java`** $\rightarrow$ Chọn **Run File** (`Shift + F6`).
4. Mở `Source Packages` $\rightarrow$ `com.ltm.client`.
5. Nhấp chuột phải vào file **`ClientGUI.java`** $\rightarrow$ Chọn **Run File** (`Shift + F6`) để mở các Client.

---

## 📋 4. Hướng Dẫn Thao Tác & Trình Diễn Từng Chức Năng

### 1. Đăng Nhập & Đăng Ký Tài Khoản
* **Tài khoản có sẵn:** `U001` (Nguyễn Văn A), `U002` (Trần Văn B).
* **Đăng ký tài khoản mới:** Chọn tab **"📝 Đăng Ký Tài Khoản"**, nhập Mã (ví dụ: `U003`), Họ tên và Email $\rightarrow$ Bấm **Đăng Ký Tài Khoản**. Server sẽ tự động ghi dữ liệu vào `data/users.csv` và đăng nhập ngay.

### 2. Nhắn Tin Riêng 1 - 1 (Tab 1: 💬 Nhắn Tin Riêng)
* **Mục đích:** Gửi tin nhắn bí mật tới đích danh một người dùng qua TCP. Các người dùng khác hoàn toàn không nhận được tin này.
* **Thao tác:**
  1. Bấm **"🔄 Danh Sách Online"** để xem danh sách những ai đang mở app.
  2. Nhập mã User đích (ví dụ: `U002`) và nội dung tin nhắn.
  3. Bấm **"✉️ Gửi Tin Nhắn"**.
  4. Người nhận sẽ có chuông thông báo và hiển thị tin nhắn riêng. Server ghi log thời gian thực vào bảng nhật ký.

### 3. Truyền Tệp Khối 100 Byte (Tab 2: 2. Truyền Tệp Khối 100 Byte trên Client)
* **Mục đích:** Cắt tệp nhị phân bất kỳ thành các khối nhỏ 100 byte để truyền qua mạng, Server tiếp nhận và ghép lại tệp gốc 100% nguyên vẹn.
* **Thao tác:**
  1. Bấm **"Chọn Tệp..."** (có thể chọn tệp mẫu `data/sample_test.pdf` hoặc file ảnh/word bất kỳ).
  2. Bấm **"Bắt Đầu Truyền Tệp"**.
  3. Quan sát thanh tiến trình phần trăm (%) và nhật ký gửi từng khối 100B.
  4. Server tiếp nhận, tự động ghép lại file vào thư mục `data/uploads/`, đồng thời ghi nhận vào danh sách tệp Server.

### 4. Tra Cứu Thống Kê Từ Xa (Tab 3: 3. Tra Cứu Thống Kê Từ Xa trên Client)
* **Mục đích:** Sử dụng công nghệ Java RMI để truy vấn số liệu thống kê từ xa mà không cần tải toàn bộ tệp về.
* **Thao tác:**
  1. Bấm **"Xem Báo Cáo Thống Kê"**: Xem thời gian máy chủ đã chạy, tổng người dùng, tổng số tệp và tổng byte đã xử lý.
  2. Bấm **"Làm Mới Danh Sách Tệp"**: Tải danh sách chi tiết tất cả các tệp hiện có trên Server (kèm kích thước, người gửi và ngày giờ).

### 5. Phát & Nhận Thông Báo Nhóm (Multicast UDP)
* **Mục đích:** Phát sóng tin tức từ Server đến tất cả các máy trong mạng LAN cùng một lúc.
* **Thao tác:**
  1. Tại ServerGUI, nhập nội dung vào ô phát thông báo và bấm **"Phát Thông Báo"**.
  2. Tất cả Client đang bật sẽ nhận được thông báo tức thì trên thanh thông báo đỉnh màn hình.
  3. Thử nghiệm tính năng Rời nhóm: Bấm **"Rời Nhóm"** trên một Client $\rightarrow$ Server phát tin lại $\rightarrow$ Client đã rời nhóm sẽ không nhận được thông báo.

### 6. Kiểm Thử Đa Luồng & Đồng Bộ Hóa (Tab 4: 4. Kiểm Thử Đa Luồng & Đồng Bộ trên Server)
* **Mục đích:** Đặt trực tiếp trên Server để đo lường thực nghiệm hiện tượng tranh chấp dữ liệu (*Race Condition*) khi nhiều luồng cùng ghi và tính an toàn của từ khóa `synchronized`.
* **Thao tác:**
  1. Tại ServerGUI, chuyển sang **Tab 4: "4. Kiểm Thử Đa Luồng & Đồng Bộ"**.
  2. Thiết lập cấu hình (mặc định: 10 Luồng, mỗi luồng 10,000 lượt $\rightarrow$ Kỳ vọng: 100,000).
  3. Bấm **"Chạy Không Đồng Bộ"**: Quan sát kết quả thực tế bị mất mát dữ liệu nghiêm trọng (chỉ đạt ~30,000, sai lệch ~70,000).
  4. Bấm **"Chạy Có Đồng Bộ Hóa"**: Đạt chính xác 100,000 / 100,000 (Sai lệch = 0).
  5. Bấm **"Chạy So Sánh Đối Chiếu"**: Xem bảng đối chiếu tổng hợp và tỷ lệ sai sót.

---

## 🛠️ 5. Quản Lý Hệ Thống Trên Server
* **Tab 1 - Danh Sách Người Dùng:** Xem toàn bộ tài khoản đăng ký trong hệ thống (`data/users.csv`).
* **Tab 2 - Danh Sách Tệp Đã Nhận:** Xem toàn bộ tệp đã tiếp nhận, kích thước và người tải lên. Dữ liệu được lưu bền vững, không bị mất khi tắt/bật lại Server (`data/uploaded_files.csv`).
* **Tab 3 - Nhật Ký Hoạt Động Server:** Hiển thị toàn bộ nhật ký theo thời gian thực. Có nút **"Đọc Lại File Log"** và **"Xóa Màn Hình Log"**.
* **Tab 4 - Kiểm Thử Đa Luồng & Đồng Bộ:** Giao diện đo lường chuyên sâu hiệu năng và xung đột đa luồng.
* **Nút "Mở Thư Mục Tệp":** Mở trực tiếp thư mục `data/uploads/` trên Windows Explorer.
