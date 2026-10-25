package com.ltm.server;

import com.ltm.common.FileInfo;
import com.ltm.common.User;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Lớp quản lý dữ liệu tập trung trên Server (Đồng bộ hóa Thread-safe)
 */
public class ServerStats {
    private static final String CSV_FILE_PATH = "data/users.csv";
    private static final String FILES_METADATA_PATH = "data/uploaded_files.csv";
    private static final String UPLOAD_DIR = "data/uploads";
    private static final Object USER_LOCK = new Object();
    private static final Object FILE_LOCK = new Object();

    private int totalUsers = 0;
    private int totalFiles = 0;
    private long totalBytes = 0;

    private final List<User> userList = new ArrayList<>();
    private final List<FileInfo> uploadedFiles = new ArrayList<>();
    private final LocalDateTime startTime = LocalDateTime.now();

    public ServerStats() {
        loadUsersFromCsv();
        loadUploadedFiles();
    }

    /**
     * Server đọc danh sách người dùng từ data/users.csv khi khởi động (Scanner)
     */
    public synchronized void loadUsersFromCsv() {
        userList.clear();
        File file = new File(CSV_FILE_PATH);
        if (!file.exists()) return;

        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8.name())) {
            boolean isHeader = true;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                if (isHeader && (line.toLowerCase().startsWith("userid") || line.toLowerCase().startsWith("ma"))) {
                    isHeader = false;
                    continue;
                }
                isHeader = false;

                User u = User.fromCsvLine(line);
                if (u != null) {
                    userList.add(u);
                }
            }
            this.totalUsers = userList.size();
            System.out.println("[Server Storage] Đã nạp " + totalUsers + " người dùng từ users.csv.");
        } catch (IOException e) {
            System.err.println("[Server Storage] Lỗi đọc users.csv: " + e.getMessage());
        }
    }

    /**
     * Nạp danh sách tệp tin đã nhận từ metadata (data/uploaded_files.csv) 
     * và đồng bộ hóa tự động với thư mục vật lý data/uploads/
     */
    public synchronized void loadUploadedFiles() {
        uploadedFiles.clear();
        totalFiles = 0;
        totalBytes = 0;

        File metaFile = new File(FILES_METADATA_PATH);
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 1. Đọc từ uploaded_files.csv nếu đã có
        if (metaFile.exists()) {
            try (Scanner scanner = new Scanner(metaFile, StandardCharsets.UTF_8.name())) {
                boolean isHeader = true;
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;
                    if (isHeader && (line.toLowerCase().startsWith("filename") || line.toLowerCase().startsWith("ten"))) {
                        isHeader = false;
                        continue;
                    }
                    isHeader = false;
                    FileInfo info = FileInfo.fromCsvLine(line);
                    if (info != null) {
                        File phys = new File(uploadDir, info.getFileName());
                        if (phys.exists()) {
                            uploadedFiles.add(info);
                            totalBytes += info.getFileSize();
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[Server Storage] Lỗi đọc uploaded_files.csv: " + e.getMessage());
            }
        }

        // 2. Quét thư mục data/uploads/ để tự động đồng bộ bất kỳ tệp nào đã có sẵn trong thư mục
        File[] physicalFiles = uploadDir.listFiles(f -> f.isFile() && !f.getName().equalsIgnoreCase("uploaded_files.csv"));
        if (physicalFiles != null && physicalFiles.length > 0) {
            boolean hasNew = false;
            java.util.Map<String, String[]> logHistory = readUploadHistoryFromLog();

            for (File pf : physicalFiles) {
                boolean alreadyTracked = uploadedFiles.stream()
                        .anyMatch(fi -> fi.getFileName().equalsIgnoreCase(pf.getName()));
                if (!alreadyTracked) {
                    long size = pf.length();
                    int chunks = (int) ((size + 99) / 100);
                    String uploader = "Client";
                    String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(pf.lastModified()));

                    if (logHistory.containsKey(pf.getName().toLowerCase())) {
                        String[] h = logHistory.get(pf.getName().toLowerCase());
                        uploader = h[0];
                        time = h[1];
                    }

                    FileInfo newInfo = new FileInfo(pf.getName(), size, uploader, time, chunks);
                    uploadedFiles.add(newInfo);
                    totalBytes += size;
                    hasNew = true;
                }
            }

            // Lưu lại metadata đồng bộ ra file CSV
            if (hasNew || !metaFile.exists()) {
                saveAllUploadedFilesToCsv();
            }
        }

        totalFiles = uploadedFiles.size();
        System.out.println("[Server Storage] Đã nạp " + totalFiles + " tệp tin (" + totalBytes + " bytes) từ thư mục uploads.");
    }

    private java.util.Map<String, String[]> readUploadHistoryFromLog() {
        java.util.Map<String, String[]> map = new java.util.HashMap<>();
        File logFile = new File("data/server_log.txt");
        if (!logFile.exists()) return map;

        try (Scanner sc = new Scanner(logFile, StandardCharsets.UTF_8.name())) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(.*?)\\] \\[FILE\\] User (\\S+) đã gửi tệp (.*?) \\((\\d+) bytes");
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                java.util.regex.Matcher m = p.matcher(line);
                if (m.find()) {
                    String time = m.group(1).trim();
                    String user = m.group(2).trim();
                    String fname = m.group(3).trim();
                    map.put(fname.toLowerCase(), new String[]{user, time});
                }
            }
        } catch (Exception e) {
            // bỏ qua nếu lỗi đọc log
        }
        return map;
    }

    private synchronized void saveAllUploadedFilesToCsv() {
        synchronized (FILE_LOCK) {
            File file = new File(FILES_METADATA_PATH);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
                pw.println("FileName,FileSize,ChunkCount,Uploader,UploadTime");
                for (FileInfo fi : uploadedFiles) {
                    pw.println(fi.toCsvLine());
                }
                pw.flush();
            } catch (IOException e) {
                System.err.println("[Server Storage] Lỗi lưu uploaded_files.csv: " + e.getMessage());
            }
        }
    }

    /**
     * Server kiểm tra và lưu người dùng mới vào users.csv (PrintWriter + Synchronized)
     */
    public synchronized boolean registerNewUser(User newUser) {
        if (newUser == null || newUser.getUserId() == null || newUser.getUserId().trim().isEmpty()) {
            return false;
        }

        // Kiểm tra trùng UserID
        for (User u : userList) {
            if (u.getUserId().equalsIgnoreCase(newUser.getUserId().trim())) {
                return false;
            }
        }

        synchronized (USER_LOCK) {
            File file = new File(CSV_FILE_PATH);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            boolean needHeader = !file.exists() || file.length() == 0;

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                if (needHeader) {
                    pw.println("UserID,FullName,Email");
                }
                pw.println(newUser.toCsvLine());
                pw.flush();
            } catch (IOException e) {
                System.err.println("[Server Storage] Lỗi ghi users.csv: " + e.getMessage());
                return false;
            }
        }

        userList.add(newUser);
        this.totalUsers = userList.size();
        return true;
    }

    /**
     * Tìm kiếm người dùng theo UserID để phục vụ đăng nhập
     */
    public synchronized User findUserById(String userId) {
        if (userId == null) return null;
        for (User u : userList) {
            if (u.getUserId().equalsIgnoreCase(userId.trim())) {
                return u;
            }
        }
        return null;
    }

    /**
     * Ghi nhận một tệp được truyền và ghép thành công (synchronized + lưu file CSV)
     */
    public synchronized void recordFileTransfer(FileInfo fileInfo) {
        this.totalFiles++;
        this.totalBytes += fileInfo.getFileSize();
        this.uploadedFiles.add(fileInfo);

        synchronized (FILE_LOCK) {
            File file = new File(FILES_METADATA_PATH);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            boolean needHeader = !file.exists() || file.length() == 0;

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                if (needHeader) {
                    pw.println("FileName,FileSize,ChunkCount,Uploader,UploadTime");
                }
                pw.println(fileInfo.toCsvLine());
                pw.flush();
            } catch (IOException e) {
                System.err.println("[Server Storage] Lỗi ghi uploaded_files.csv: " + e.getMessage());
            }
        }
    }

    public synchronized int getTotalUsers() {
        return totalUsers;
    }

    public synchronized int getTotalFiles() {
        return totalFiles;
    }

    public synchronized long getTotalBytes() {
        return totalBytes;
    }

    public synchronized List<User> getUserList() {
        return new ArrayList<>(userList);
    }

    public synchronized List<FileInfo> getUploadedFiles() {
        return new ArrayList<>(uploadedFiles);
    }

    /**
     * Tạo báo cáo thống kê phục vụ truy vấn RMI từ xa
     */
    public synchronized String getStatsReport() {
        Duration duration = Duration.between(startTime, LocalDateTime.now());
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        sb.append("================ BÁO CÁO THỐNG KÊ TỪ XA TẬP TRUNG ================\n");
        sb.append(String.format("• Thời gian máy chủ hoạt động: %02d:%02d:%02d (Bắt đầu: %s)\n",
                hours, minutes, secs, startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        sb.append(String.format("• Tổng số người dùng đăng ký tập trung: %d người dùng\n", totalUsers));
        sb.append(String.format("• Tổng số tệp đã nhận và ghép: %d tệp\n", totalFiles));
        sb.append(String.format("• Tổng dung lượng dữ liệu đã xử lý: %,d bytes\n", totalBytes));
        sb.append("========================================================================");
        return sb.toString();
    }
}
