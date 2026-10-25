package com.ltm.common;

import java.io.Serializable;

/**
 * Lớp đại diện cho Người dùng (UserID, FullName, Email)
 * Lưu tập trung tại Server trong file data/users.csv
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String fullName;
    private String email;

    public User() {}

    public User(String userId, String fullName, String email) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String toCsvLine() {
        return userId + "," + fullName + "," + email;
    }

    public static User fromCsvLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        String[] parts = line.split(",", -1);
        if (parts.length >= 3) {
            return new User(parts[0].trim(), parts[1].trim(), parts[2].trim());
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", userId, fullName, email);
    }
}
