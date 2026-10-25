# HƯỚNG DẪN MỞ VÀ CHẠY DỰ ÁN TRÊN APACHE NETBEANS
## HỆ THỐNG TRUYỀN, QUẢN LÝ VÀ CHIA SẺ TỆP QUA MẠNG LAN

Dự án được xây dựng theo chuẩn **Maven Project**, tích hợp đầy đủ các chức năng theo đúng đề cương môn học, tương thích 100% với **Apache NetBeans**.

---

## 1. Cách Mở Dự Án Vào NetBeans

1. Mở phần mềm **Apache NetBeans**.
2. Trên thanh menu, chọn: **File** $\rightarrow$ **Open Project...** (phím tắt `Ctrl + Shift + O`).
3. Điều hướng tới thư mục: `E:\do an ltm`.
4. NetBeans sẽ tự động nhận diện biểu tượng dự án Maven: **DoAnLTM**.
5. Nhấn nút **Open Project**.

---

## 2. Cách Khởi Động Hệ Thống

### Bước 1: Khởi động Server Trung Tâm (ServerGUI.java)
1. Trong cửa sổ **Projects**, mở rộng:
   `DoAnLTM` $\rightarrow$ `Source Packages` $\rightarrow$ `com.ltm.server`.
2. Nhấp chuột phải vào file **`ServerGUI.java`** $\rightarrow$ Chọn **Run File** (phím tắt **Shift + F6**).
3. Server khởi động và tự động kích hoạt:
   - Cổng TCP Socket: `8888` (Đa luồng tiếp nhận nhiều Client).
   - Cổng Java RMI Registry: `1099` (Dịch vụ `StatsService`).
   - Kênh phát thông báo UDP Multicast: `230.0.0.1:9999`.
   - Dữ liệu tập trung: `data/users.csv`, `data/uploaded_files.csv`, `data/server_log.txt` và thư mục `data/uploads/`.

### Bước 2: Khởi động các Client (ClientGUI.java)
1. Trong cửa sổ **Projects**, mở rộng:
   `DoAnLTM` $\rightarrow$ `Source Packages` $\rightarrow$ `com.ltm.client`.
2. Nhấp chuột phải vào file **`ClientGUI.java`** $\rightarrow$ Chọn **Run File** (**Shift + F6**).
3. Hộp thoại Đăng nhập xuất hiện:
   - Nhập UserID: `U001` (Client A) $\rightarrow$ Bấm **Đăng Nhập Vào Hệ Thống**.
4. Tiếp tục nhấp chuột phải **`ClientGUI.java`** $\rightarrow$ Chọn **Run File** lần 2:
   - Nhập UserID: `U002` (Client B) $\rightarrow$ Bấm **Đăng Nhập Vào Hệ Thống**.

---

## 3. Hướng Dẫn Trình Diễn Chi Tiết Từng Chức Năng

### Chức Năng 1: Đăng nhập & Đăng ký tài khoản người dùng
* **Đăng nhập có sẵn:** Nhập `U001` hoặc `U002` để đăng nhập.
* **Đăng ký mới:** Chọn tab **"📝 Đăng Ký Tài Khoản"**, nhập thông tin (Mã, Họ tên, Email) $\rightarrow$ Bấm **Đăng Ký Tài Khoản**. Server dùng `PrintWriter` lưu tập trung vào file `data/users.csv`.

### Chức Năng 2: Nhắn tin riêng 1 - 1 qua TCP
1. Tại **Client A (`U001`)**, chuyển sang **Tab 1: "💬 1. Nhắn Tin Riêng"**.
2. Bấm **"🔄 Danh Sách Online"** để kiểm tra người dùng đang trực tuyến.
3. Nhập UserID đích: `U002`, nhập tin nhắn: *"Chào bạn U002!"* $\rightarrow$ Bấm **"✉️ Gửi Tin Nhắn"**.
4. **Client B (`U002`)** nhận được tin nhắn riêng và chuông báo. Các Client khác hoàn toàn không nhận được tin này.
5. **Server** ghi nhật ký theo thời gian thực vào bảng nhật ký và file `data/server_log.txt`.

### Chức Năng 3: Truyền tệp nhị phân khối 100 byte
1. Tại giao diện **Client A (`U001`)**, chuyển sang **Tab 2: "📁 2. Truyền Tệp Khối 100 Byte"**.
2. Bấm **"📁 Chọn Tệp..."** $\rightarrow$ Chọn tệp `data/sample_test.pdf` (hoặc ảnh/word bất kỳ).
3. Chương trình tự động tính toán số đoạn (100 byte/đoạn).
4. Bấm **"🚀 Bắt Đầu Truyền Tệp"**.
5. Quan sát thanh tiến trình gửi từng đoạn 100 byte. Server tiếp nhận, ghép lại tệp hoàn chỉnh trong `data/uploads/` và lưu thông tin vào `data/uploaded_files.csv`.

### Chức Năng 4: Tra cứu thống kê từ xa bằng Java RMI
1. Tại **ClientGUI**, chuyển sang **Tab 3: "📊 3. Tra Cứu Thống Kê Từ Xa"**.
2. Bấm **"📊 Xem Báo Cáo Thống Kê"**:
   - Client gọi từ xa dịch vụ RMI qua cổng `1099`.
   - Hiển thị tổng số tệp, tổng dung lượng byte và báo cáo chi tiết thời gian hoạt động của Server.
3. Bấm **"📁 Làm Mới Danh Sách Tệp"**:
   - Tải danh sách chi tiết tất cả các tệp hiện có trên Server.

### Chức Năng 5: Phát & Nhận thông báo nhóm Multicast (230.0.0.1:9999)
1. Trên thanh thông báo đỉnh của Client, có 2 nút: **`🟢 Tham Gia Nhóm`** và **`🔴 Rời Nhóm`**.
2. Tại ServerGUI, nhập nội dung và bấm **`📢 Phát Thông Báo`**.
3. **Client B** (đang tham gia nhóm) lập tức nhận được thông báo tức thời.
4. Bấm **`🔴 Rời Nhóm`** trên Client B $\rightarrow$ Server thử phát thông báo lại $\rightarrow$ Client B không nhận thông báo.

### Chức Năng 6: Kiểm thử đa luồng & Cơ chế đồng bộ hóa (synchronized)
1. Tại **ClientGUI**, chuyển sang **Tab 4: "⚡ 4. Kiểm Thử Đa Luồng & Đồng Bộ"** (hoặc bấm nút trên ServerGUI).
2. Cấu hình mặc định: **10 Luồng**, mỗi luồng **10,000 lượt** (kỳ vọng: **100,000**).
3. Bấm **"⚠️ Chạy Không Đồng Bộ"**:
   - Thao tác bị xung đột tranh chấp dữ liệu (*Race Condition*), kết quả thực tế bị mất mát nghiêm trọng (chỉ đạt ~30,000, sai sót ~70%).
4. Bấm **"🔒 Chạy Có Đồng Bộ Hóa"**:
   - Kết quả thực tế đạt chính xác **100,000**, sai lệch = **0**.
5. Bấm **"⚖️ Chạy So Sánh Đối Chiếu"**: Xem bảng đối chiếu tổng hợp và tỷ lệ sai lệch.

---

## 4. Biên Dịch & Chạy Trực Tiếp Bằng File .BAT (Nếu Cần)

Bạn có thể chạy nhanh không cần NetBeans:
* **`run_server.bat`**: Khởi động Server.
* **`run_client.bat`**: Khởi động Client.
* **`compile.bat`**: Biên dịch lại toàn bộ dự án.
