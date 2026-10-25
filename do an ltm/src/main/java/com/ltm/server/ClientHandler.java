package com.ltm.server;

import com.ltm.common.FileInfo;
import com.ltm.common.User;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Luồng phục vụ từng Client kết nối qua TCP Socket
 * Xử lý:
 * - Bước 1 & 2: Nhận đăng ký người dùng -> Kiểm tra -> Lưu tập trung vào users.csv
 * - Bước 3 & 4: Nhận các đoạn 100 byte -> Ghép tệp hoàn chỉnh -> Ghi server_log.txt -> Phát Multicast
 */
public class ClientHandler implements Runnable {
    private static final String LOG_FILE_PATH = "data/server_log.txt";
    private static final String UPLOAD_DIR = "data/uploads";
    private static final Object LOG_LOCK = new Object();

    // Quản lý kênh kết nối nhận tin riêng của từng User (Đảm bảo chỉ đúng User đó nhận tin)
    private static final java.util.Map<String, DataOutputStream> activeUserStreams = new java.util.concurrent.ConcurrentHashMap<>();

    // Listener đẩy log trực tiếp lên giao diện ServerGUI theo thời gian thực
    public static java.util.function.Consumer<String> serverLogListener = null;

    private final Socket clientSocket;
    private final ServerStats serverStats;
    private String listeningUserId = null;

    public ClientHandler(Socket clientSocket, ServerStats serverStats) {
        this.clientSocket = clientSocket;
        this.serverStats = serverStats;
    }

    /**
     * Lấy danh sách mã User đang online thực tế
     */
    public static java.util.List<String> getOnlineUserList() {
        return new java.util.ArrayList<>(activeUserStreams.keySet());
    }

    /**
     * Đóng toàn bộ các kênh kết nối cũ khi Server dừng
     */
    public static void closeAllActiveStreams() {
        for (java.util.Map.Entry<String, DataOutputStream> entry : activeUserStreams.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception ignored) {}
        }
        activeUserStreams.clear();
    }

    /**
     * Tìm kênh gửi tin riêng không phân biệt chữ hoa / chữ thường
     */
    private static DataOutputStream getActiveStream(String userId) {
        if (userId == null) return null;
        String search = userId.trim();
        for (java.util.Map.Entry<String, DataOutputStream> entry : activeUserStreams.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(search)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void run() {
        String clientInfo = clientSocket.getRemoteSocketAddress().toString();

        try (
            DataInputStream dis = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()))
        ) {
            boolean active = true;
            while (active) {
                String commandType;
                try {
                    commandType = dis.readUTF();
                } catch (EOFException e) {
                    break;
                }

                if ("LISTEN_DIRECT".equalsIgnoreCase(commandType)) {
                    // Đăng ký kênh nhận tin nhắn riêng cho đúng User này
                    handleListenDirect(dis, dos, clientInfo);
                    break; // Luồng này giữ kết nối để chờ đẩy tin nhắn riêng đến khi ngắt
                } else if ("SEND_PRIVATE_MSG".equalsIgnoreCase(commandType)) {
                    // Xử lý gửi tin nhắn riêng 1-1: Chỉ gửi đúng User đích, các user khác không nhận
                    handleSendPrivateMessage(dis, dos, clientInfo);
                } else if ("LOGIN".equalsIgnoreCase(commandType)) {
                    // Xác thực đăng nhập qua TCP: Kiểm tra UserID trong users.csv
                    handleUserLogin(dis, dos, clientInfo);
                } else if ("REGISTER_USER".equalsIgnoreCase(commandType)) {
                    // Bước 1 & 2: Client gửi thông tin người dùng qua TCP, Server kiểm tra và lưu users.csv
                    handleUserRegistration(dis, dos, clientInfo);
                } else if ("FILE".equalsIgnoreCase(commandType)) {
                    // Bước 3 & 4: Client truyền tệp 100B, Server ghép
                    handleFileTransfer(dis, dos, clientInfo);
                } else if ("LOG_TEXT".equalsIgnoreCase(commandType) || "TEXT".equalsIgnoreCase(commandType)) {
                    // Client gửi văn bản -> Server ghi trực tiếp vào server_log.txt
                    String fromUser = dis.readUTF().trim();
                    String content = dis.readUTF().trim();
                    String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String logEntry = String.format("[%s] [VĂN BẢN] User %s: \"%s\"", timeStamp, fromUser, content);
                    logToFile(logEntry);
                    dos.writeUTF("OK: Server đã ghi nội dung vào server_log.txt!");
                    dos.flush();
                } else if ("QUIT".equalsIgnoreCase(commandType)) {
                    dos.writeUTF("OK: Đã ngắt kết nối.");
                    dos.flush();
                    active = false;
                } else {
                    dos.writeUTF("ERROR: Lệnh không hợp lệ: " + commandType);
                    dos.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] Client " + clientInfo + " đã kết thúc phiên làm việc.");
        } finally {
            if (listeningUserId != null) {
                activeUserStreams.remove(listeningUserId);
                System.out.println("[Server Routing] Đã đóng kênh nhận tin riêng của User: " + listeningUserId);
            }
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Kích hoạt luồng lắng nghe tin nhắn riêng cho một User cụ thể
     */
    private void handleListenDirect(DataInputStream dis, DataOutputStream dos, String clientInfo) throws IOException {
        String userId = dis.readUTF().trim().toUpperCase();
        this.listeningUserId = userId;
        activeUserStreams.put(userId, dos);
        System.out.println(String.format("[Server Routing] Kích hoạt kênh nhận tin riêng cho User [%s] từ %s. Tổng Online: %d",
                userId, clientInfo, activeUserStreams.size()));

        // Phản hồi đã kết nối kênh riêng
        dos.writeUTF("CONNECTED_DIRECT");
        dos.flush();

        String timeStampConnect = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logConnect = String.format("[%s] [KẾT NỐI] Người dùng [%s] đã mở kênh nhận tin riêng. Tổng online: %d",
                timeStampConnect, userId, activeUserStreams.size());
        logToFile(logConnect);

        // Luồng này giữ kết nối liên tục để phục vụ nhận tin nhắn riêng
        try {
            while (!clientSocket.isClosed()) {
                int signal = dis.read();
                if (signal == -1) break;
            }
        } catch (IOException ignored) {
        } finally {
            activeUserStreams.remove(userId);
            String timeStampDisconnect = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String logDisconnect = String.format("[%s] [NGẮT KẾT NỐI] Người dùng [%s] đã ngắt kết nối. Còn online: %d",
                    timeStampDisconnect, userId, activeUserStreams.size());
            logToFile(logDisconnect);
            System.out.println(logDisconnect);
        }
    }

    /**
     * Định tuyến tin nhắn riêng: CHỈ GỬI ĐÚNG USER ĐÍCH, CÁC USER KHÁC HOÀN TOÀN KHÔNG NHẬN
     */
    private void handleSendPrivateMessage(DataInputStream dis, DataOutputStream dos, String clientInfo) throws IOException {
        String fromUser = dis.readUTF().trim();
        String targetUser = dis.readUTF().trim();
        String message = dis.readUTF().trim();

        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        DataOutputStream targetDos = getActiveStream(targetUser);

        if (targetDos != null) {
            // User đích đang online -> Gửi duy nhất qua luồng TCP của User đích đó
            synchronized (targetDos) {
                try {
                    targetDos.writeUTF("INCOMING_PRIVATE_MSG");
                    targetDos.writeUTF(fromUser);
                    targetDos.writeUTF(message);
                    targetDos.writeUTF(timeStamp);
                    targetDos.flush();
                } catch (IOException e) {
                    activeUserStreams.remove(targetUser.toUpperCase());
                    dos.writeUTF("ERROR: Không thể gửi tin tới [" + targetUser + "], kết nối đã bị ngắt!");
                    dos.flush();
                    return;
                }
            }

            // Ghi log nhật ký tin nhắn riêng trên Server
            String logEntry = String.format("[%s] [TIN NHẮN RIÊNG] Từ User %s gửi riêng tới User %s: \"%s\"",
                    timeStamp, fromUser, targetUser, message);
            logToFile(logEntry);
            System.out.println(logEntry);

            dos.writeUTF(String.format("OK: Đã gửi tin nhắn riêng tới User [%s] thành công!", targetUser));
        } else {
            // User đích không online hoặc không tồn tại
            dos.writeUTF(String.format("ERROR: Người dùng [%s] hiện không online hoặc chưa đăng nhập!\n(Các user đang online: %s)",
                    targetUser, activeUserStreams.keySet()));
        }
        dos.flush();
    }

    /**
     * Xử lý xác thực Đăng nhập từ Client qua TCP Socket
     */
    private void handleUserLogin(DataInputStream dis, DataOutputStream dos, String clientInfo) throws IOException {
        String userId = dis.readUTF().trim();
        User user = serverStats.findUserById(userId);

        if (user != null) {
            dos.writeUTF("OK");
            dos.writeUTF(user.getUserId());
            dos.writeUTF(user.getFullName());
            dos.writeUTF(user.getEmail());
            String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String logEntry = String.format("[%s] [ĐĂNG NHẬP] Người dùng [%s - %s] đăng nhập thành công từ %s",
                    timeStamp, user.getUserId(), user.getFullName(), clientInfo);
            logToFile(logEntry);
            System.out.println(logEntry);
        } else {
            dos.writeUTF("ERROR: Mã người dùng [" + userId + "] chưa tồn tại trong users.csv trên Server! Vui lòng Đăng ký trước.");
        }
        dos.flush();
    }

    /**
     * BƯỚC 2: Server nhận dữ liệu người dùng qua TCP, kiểm tra và ghi vào users.csv
     */
    private void handleUserRegistration(DataInputStream dis, DataOutputStream dos, String clientInfo) throws IOException {
        String userId = dis.readUTF().trim();
        String fullName = dis.readUTF().trim();
        String email = dis.readUTF().trim();

        // Server kiểm tra tính hợp lệ của thông tin nhận được
        if (userId.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
            dos.writeUTF("ERROR: Thông tin không hợp lệ (Mã, Tên và Email không được để trống)!");
            dos.flush();
            return;
        }

        User newUser = new User(userId, fullName, email);
        boolean success = serverStats.registerNewUser(newUser);

        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (success) {
            String logEntry = String.format("[%s] [ĐĂNG KÝ] Server đã ghi nhận người dùng: %s (%s, %s) từ %s",
                    timeStamp, userId, fullName, email, clientInfo);
            logToFile(logEntry);
            System.out.println(logEntry);

            dos.writeUTF(String.format("OK: Đã đăng ký thành công User [%s - %s] vào users.csv trên Server!", userId, fullName));
        } else {
            dos.writeUTF("ERROR: Mã người dùng [" + userId + "] đã tồn tại trong users.csv trên Server!");
        }
        dos.flush();
    }

    /**
     * BƯỚC 3 & 4: Server nhận các đoạn 100 byte, ghép tệp, ghi server_log.txt và phát Multicast
     */
    private void handleFileTransfer(DataInputStream dis, DataOutputStream dos, String clientInfo) throws IOException {
        String userId = dis.readUTF().trim();
        String rawFileName = dis.readUTF().trim();
        long originalFileSize = dis.readLong();

        File uploadFolder = new File(UPLOAD_DIR);
        if (!uploadFolder.exists()) {
            uploadFolder.mkdirs();
        }

        String safeFileName = new File(rawFileName).getName();
        File destinationFile = new File(uploadFolder, safeFileName);

        System.out.println(String.format("[Server TCP] Nhận tệp: %s (%d bytes) từ User: %s [%s]",
                safeFileName, originalFileSize, userId, clientInfo));

        // Nhận từng đoạn 100 byte và ghép lại
        long totalBytesReceived = 0;
        int chunkCount = 0;
        byte[] buffer = new byte[100]; // Mảng đệm 100 byte

        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
            int chunkSize;
            while ((chunkSize = dis.readInt()) != -1) {
                if (chunkSize > 0) {
                    dis.readFully(buffer, 0, chunkSize);
                    fos.write(buffer, 0, chunkSize);
                    totalBytesReceived += chunkSize;
                    chunkCount++;
                }
            }
            fos.flush();
        }

        // Kiểm tra tính toàn vẹn của tệp sau ghép (Kích thước tệp)
        boolean isMatched = (originalFileSize == totalBytesReceived) && (destinationFile.length() == originalFileSize);
        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (isMatched) {
            // Cập nhật bộ đếm đồng bộ trên Server (Chức năng 1 & 6)
            FileInfo fileInfo = new FileInfo(safeFileName, totalBytesReceived, userId, timeStamp, chunkCount);
            serverStats.recordFileTransfer(fileInfo);

            // Ghi nhật ký vào server_log.txt theo yêu cầu đồ án
            String logEntry = String.format("[%s] [FILE] User %s đã gửi tệp %s (%d bytes, %d đoạn 100B) thành công.",
                    timeStamp, userId, safeFileName, totalBytesReceived, chunkCount);
            logToFile(logEntry);

            dos.writeUTF(String.format("OK: Tệp [%s] đã ghép hoàn chỉnh (%d bytes, %d đoạn 100B)!",
                    safeFileName, totalBytesReceived, chunkCount));
        } else {
            dos.writeUTF(String.format("WARNING: Sai lệch dung lượng tệp! Gốc: %d bytes, Nhận: %d bytes",
                    originalFileSize, totalBytesReceived));
        }
        dos.flush();
    }

    public static void logToFile(String entry) {
        synchronized (LOG_LOCK) {
            File logFile = new File(LOG_FILE_PATH);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
                pw.println(entry);
                pw.flush();
            } catch (IOException e) {
                System.err.println("[Server] Lỗi ghi server_log.txt: " + e.getMessage());
            }
        }
        if (serverLogListener != null) {
            serverLogListener.accept(entry);
        }
    }
}
