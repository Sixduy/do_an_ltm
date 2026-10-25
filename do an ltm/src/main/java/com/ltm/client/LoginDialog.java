package com.ltm.client;

import com.ltm.common.User;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Hộp thoại Đăng Nhập & Đăng Ký (Bắt buộc đăng nhập để vào Client)
 */
public class LoginDialog extends JDialog {
    private String serverHost = "localhost";
    private int tcpPort = 8888;
    private User loggedInUser = null;
    private boolean authenticated = false;

    // Components Tab Đăng Nhập
    private JTextField txtLoginHost;
    private JTextField txtLoginPort;
    private JTextField txtLoginUserId;

    // Components Tab Đăng Ký
    private JTextField txtRegHost;
    private JTextField txtRegPort;
    private JTextField txtRegUserId;
    private JTextField txtRegFullName;
    private JTextField txtRegEmail;

    public LoginDialog(Frame parent) {
        super(parent, "ĐĂNG NHẬP / ĐĂNG KÝ HỆ THỐNG MẠNG LAN", true);
        initComponents();
    }

    private void initComponents() {
        setSize(480, 420);
        setLocationRelativeTo(getParent());
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // Header
        JPanel pnlHeader = new JPanel(new BorderLayout());
        pnlHeader.setBackground(new Color(225, 238, 248));
        pnlHeader.setBorder(new EmptyBorder(12, 15, 12, 15));

        JLabel lblTitle = new JLabel("XÁC THỰC NGƯỜI DÙNG CLIENT");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(Color.BLACK);

        JLabel lblSub = new JLabel("Vui lòng đăng nhập để định danh khi truyền tệp và nhận thông báo");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(60, 60, 60));

        pnlHeader.add(lblTitle, BorderLayout.NORTH);
        pnlHeader.add(lblSub, BorderLayout.SOUTH);
        add(pnlHeader, BorderLayout.NORTH);

        // TabbedPane
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabs.addTab("Đăng Nhập", createLoginPanel());
        tabs.addTab("Đăng Ký Tài Khoản", createRegisterPanel());

        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel pnlForm = new JPanel(new GridLayout(3, 2, 8, 12));
        pnlForm.setBorder(new TitledBorder("Thông Tin Đăng Nhập"));

        txtLoginHost = new JTextField("localhost");
        txtLoginPort = new JTextField("8888");
        txtLoginUserId = new JTextField("U001"); // Mẫu: U001 hoặc U002

        pnlForm.add(new JLabel("Server IP:"));
        pnlForm.add(txtLoginHost);
        pnlForm.add(new JLabel("Cổng TCP Server:"));
        pnlForm.add(txtLoginPort);
        pnlForm.add(new JLabel("Mã Người Dùng (UserID):"));
        pnlForm.add(txtLoginUserId);

        panel.add(pnlForm, BorderLayout.CENTER);

        JPanel pnlBottom = new JPanel(new BorderLayout(5, 5));
        JLabel lblHint = new JLabel("Gợi ý tài khoản có sẵn: U001 hoặc U002");
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblHint.setForeground(Color.GRAY);
        pnlBottom.add(lblHint, BorderLayout.NORTH);

        JButton btnLogin = new JButton("Đăng Nhập Vào Hệ Thống");
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setBackground(new Color(210, 245, 220));
        btnLogin.setForeground(Color.BLACK);
        btnLogin.setFocusPainted(false);
        btnLogin.addActionListener(e -> doLogin());
        pnlBottom.add(btnLogin, BorderLayout.SOUTH);

        panel.add(pnlBottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel pnlForm = new JPanel(new GridLayout(5, 2, 8, 8));
        pnlForm.setBorder(new TitledBorder("Thông Tin Đăng Ký Tài Khoản Mới"));

        txtRegHost = new JTextField("localhost");
        txtRegPort = new JTextField("8888");
        txtRegUserId = new JTextField("U003");
        txtRegFullName = new JTextField("Le Van C");
        txtRegEmail = new JTextField("c@gmail.com");

        pnlForm.add(new JLabel("Server IP:"));
        pnlForm.add(txtRegHost);
        pnlForm.add(new JLabel("Cổng TCP:"));
        pnlForm.add(txtRegPort);
        pnlForm.add(new JLabel("Mã User (UserID):"));
        pnlForm.add(txtRegUserId);
        pnlForm.add(new JLabel("Họ Và Tên:"));
        pnlForm.add(txtRegFullName);
        pnlForm.add(new JLabel("Địa Chỉ Email:"));
        pnlForm.add(txtRegEmail);

        panel.add(pnlForm, BorderLayout.CENTER);

        JButton btnRegister = new JButton("Đăng Ký Tài Khoản");
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRegister.setBackground(new Color(215, 235, 255));
        btnRegister.setForeground(Color.BLACK);
        btnRegister.setFocusPainted(false);
        btnRegister.addActionListener(e -> doRegister());

        panel.add(btnRegister, BorderLayout.SOUTH);
        return panel;
    }

    private void doLogin() {
        String host = txtLoginHost.getText().trim();
        int port = 8888;
        try { port = Integer.parseInt(txtLoginPort.getText().trim()); } catch (Exception ignored) {}
        String userId = txtLoginUserId.getText().trim();

        if (userId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã người dùng (UserID)!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // Gửi lệnh LOGIN qua TCP Socket
            dos.writeUTF("LOGIN");
            dos.writeUTF(userId);
            dos.flush();

            String status = dis.readUTF();
            if ("OK".equalsIgnoreCase(status)) {
                String id = dis.readUTF();
                String name = dis.readUTF();
                String email = dis.readUTF();

                this.serverHost = host;
                this.tcpPort = port;
                this.loggedInUser = new User(id, name, email);
                this.authenticated = true;

                JOptionPane.showMessageDialog(this, "Xin chào " + name + " [" + id + "]!\nĐăng nhập thành công vào mạng LAN.",
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, status, "Đăng Nhập Thất Bại", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối Server: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doRegister() {
        String host = txtRegHost.getText().trim();
        int port = 8888;
        try { port = Integer.parseInt(txtRegPort.getText().trim()); } catch (Exception ignored) {}
        String id = txtRegUserId.getText().trim();
        String name = txtRegFullName.getText().trim();
        String email = txtRegEmail.getText().trim();

        if (id.isEmpty() || name.isEmpty() || email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Mã, Họ tên và Email!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // Gửi lệnh REGISTER_USER qua TCP
            dos.writeUTF("REGISTER_USER");
            dos.writeUTF(id);
            dos.writeUTF(name);
            dos.writeUTF(email);
            dos.flush();

            String reply = dis.readUTF();
            if (reply.startsWith("OK")) {
                this.serverHost = host;
                this.tcpPort = port;
                this.loggedInUser = new User(id, name, email);
                this.authenticated = true;

                JOptionPane.showMessageDialog(this, reply + "\nĐã tự động đăng nhập với tài khoản " + id + "!",
                        "Đăng Ký Thành Công", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, reply, "Đăng Ký Thất Bại", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối Server: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public User getLoggedInUser() {
        return loggedInUser;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getTcpPort() {
        return tcpPort;
    }
}
