package com.ltm.common;

import java.io.Serializable;

/**
 * Đối tượng lưu thông tin tệp tin được truyền và quản lý trên Server (Phục vụ RMI)
 */
public class FileInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileName;
    private long fileSize;
    private String uploader;
    private String uploadTime;
    private int chunkCount; // Số đoạn 100 byte đã cắt

    public FileInfo() {}

    public FileInfo(String fileName, long fileSize, String uploader, String uploadTime, int chunkCount) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploader = uploader;
        this.uploadTime = uploadTime;
        this.chunkCount = chunkCount;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getUploader() { return uploader; }
    public void setUploader(String uploader) { this.uploader = uploader; }

    public String getUploadTime() { return uploadTime; }
    public void setUploadTime(String uploadTime) { this.uploadTime = uploadTime; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    @Override
    public String toString() {
        return String.format("[%s] Tệp: %s (%d bytes, %d khối 100B) - Người tải: %s",
                uploadTime, fileName, fileSize, chunkCount, uploader);
    }

    public String toCsvLine() {
        return escapeCsv(fileName) + "," + fileSize + "," + chunkCount + "," + escapeCsv(uploader) + "," + escapeCsv(uploadTime);
    }

    private static String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    public static FileInfo fromCsvLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        java.util.List<String> tokens = parseCsvLine(line);
        if (tokens.size() >= 5) {
            try {
                String name = tokens.get(0).trim();
                long size = Long.parseLong(tokens.get(1).trim());
                int chunks = Integer.parseInt(tokens.get(2).trim());
                String uploader = tokens.get(3).trim();
                String time = tokens.get(4).trim();
                return new FileInfo(name, size, uploader, time, chunks);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static java.util.List<String> parseCsvLine(String line) {
        java.util.List<String> list = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString());
        return list;
    }
}
