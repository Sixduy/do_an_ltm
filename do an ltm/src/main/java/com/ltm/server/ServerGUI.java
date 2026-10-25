package com.ltm.server;

import com.ltm.common.FileInfo;
import com.ltm.common.IStatisticService;
import com.ltm.common.User;
import com.ltm.concurrency.ConcurrencyRaceConditionDemo;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Giao diện đồ họa Form (JFrame) Server - Quản lý tập trung người dùng và tệp tin
 */
public class ServerGUI extends JFrame {
    private static final int TCP_PORT = 8888;
    private static final int RMI_PORT = 1099;
    private static final String RMI_SERVICE_NAME = "StatsService";

    private final ServerStats serverStats = new ServerStats();
    private ServerSocket serverSocket;
    private Registry rmiRegistry;
    private IStatisticService rmiService;
    private volatile boolean isRunning = false;

    // GUI Components
    private JLabel lblStatus;
    private JLabel lblStatsUsers;
    private JLabel lblStatsOnline;
    private JLabel lblStatsFiles;
    private JLabel lblStatsBytes;
    private DefaultTableModel userTableModel;
    private DefaultTableModel fileTableModel;
    private JTextArea txtServerLog;
    private JTextField txtBroadcastMsg;
    private JButton btnStartServer;
    private JButton btnStopServer;

    // Tab 4: Kiểm thử đa luồng & đồng bộ hóa
    private JTextField txtThreads;
    private JTextField txtIterations;
    private JLabel lblSyncExpected;
    private JLabel lblSyncActual;
    private JLabel lblSyncDiff;
    private JLabel lblSyncTime;
    private JTextArea txtSyncLog;

    public ServerGUI() {
        initComponents();
        initServerLogListener();
        loadExistingLogFile();
        refreshAllData();
        startServer();
    }

    private void initComponents() {
        setTitle("MÁY CHỦ TRUNG TÂM (SERVER) - QUẢN LÝ HỆ THỐNG MẠNG LAN");
        setSize(1000, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 1. Header Banner
        JPanel pnlHeader = new JPanel(new BorderLayout());
        pnlHeader.setBackground(new Color(225, 238, 248));
        pnlHeader.setBorder(new EmptyBorder(12, 15, 12, 15));

        JLabel lblTitle = new JLabel("MÁY CHỦ TRUNG TÂM: QUẢN LÝ NGƯỜI DÙNG & TRUYỀN TỆP");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(Color.BLACK);

        lblStatus = new JLabel("Khởi động...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblStatus.setForeground(new Color(24, 76, 120));

        pnlHeader.add(lblTitle, BorderLayout.WEST);
        pnlHeader.add(lblStatus, BorderLayout.EAST);
        add(pnlHeader, BorderLayout.NORTH);

        // 2. Thẻ hiển thị bộ đếm thống kê tập trung
        JPanel pnlStats = new JPanel(new GridLayout(1, 4, 10, 10));
        pnlStats.setBorder(new EmptyBorder(0, 10, 0, 10));

        lblStatsUsers = createStatCard("1. Tổng Người Dùng", "0 người", new Color(40, 167, 69));
        lblStatsOnline = createStatCard("2. Người Dùng Online", "0 người", new Color(0, 123, 255));
        lblStatsFiles = createStatCard("3. Tệp Đã Ghép Hoàn Chỉnh", "0 tệp", new Color(23, 162, 184));
        lblStatsBytes = createStatCard("4. Dung Lượng Đã Tiếp Nhận", "0 bytes", new Color(108, 117, 125));

        pnlStats.add(lblStatsUsers.getParent());
        pnlStats.add(lblStatsOnline.getParent());
        pnlStats.add(lblStatsFiles.getParent());
        pnlStats.add(lblStatsBytes.getParent());

        // 3. Center Panel: Bảng dữ liệu + Log + Kiểm thử đa luồng
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Tab 1: Người dùng
        JPanel pnlUsers = new JPanel(new BorderLayout(5, 5));
        pnlUsers.setBorder(new EmptyBorder(8, 8, 8, 8));
        String[] userCols = {"Mã Người Dùng", "Họ Và Tên", "Địa Chỉ Email"};
        userTableModel = new DefaultTableModel(userCols, 0);
        JTable userTable = new JTable(userTableModel);
        userTable.setRowHeight(24);
        pnlUsers.add(new JScrollPane(userTable), BorderLayout.CENTER);
        tabbedPane.addTab("1. Danh Sách Người Dùng", pnlUsers);

        // Tab 2: Tệp tin đã ghép
        JPanel pnlFiles = new JPanel(new BorderLayout(5, 5));
        pnlFiles.setBorder(new EmptyBorder(8, 8, 8, 8));
        String[] fileCols = {"Tên Tệp Tin", "Kích Thước (Bytes)", "Số Khối 100B", "Người Gửi", "Thời Gian Nhận"};
        fileTableModel = new DefaultTableModel(fileCols, 0);
        JTable fileTable = new JTable(fileTableModel);
        fileTable.setRowHeight(24);
        pnlFiles.add(new JScrollPane(fileTable), BorderLayout.CENTER);
        tabbedPane.addTab("2. Danh Sách Tệp Đã Nhận", pnlFiles);

        // Tab 3: Nhật ký hoạt động
        JPanel pnlLog = new JPanel(new BorderLayout(5, 5));
        pnlLog.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel pnlLogTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JButton btnReloadLog = new JButton("Đọc Lại File Log");
        btnReloadLog.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnReloadLog.addActionListener(e -> loadExistingLogFile());

        JButton btnClearLogView = new JButton("Xóa Màn Hình Log");
        btnClearLogView.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnClearLogView.addActionListener(e -> txtServerLog.setText(""));

        pnlLogTools.add(btnReloadLog);
        pnlLogTools.add(btnClearLogView);
        pnlLog.add(pnlLogTools, BorderLayout.NORTH);

        txtServerLog = new JTextArea();
        txtServerLog.setEditable(false);
        txtServerLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        pnlLog.add(new JScrollPane(txtServerLog), BorderLayout.CENTER);
        tabbedPane.addTab("3. Nhật Ký Hoạt Động Server", pnlLog);

        // Tab 4: Kiểm thử đa luồng & đồng bộ hóa
        tabbedPane.addTab("4. Kiểm Thử Đa Luồng & Đồng Bộ", createSynchronizationTestPanel());

        JPanel pnlCenter = new JPanel(new BorderLayout(10, 10));
        pnlCenter.add(pnlStats, BorderLayout.NORTH);
        pnlCenter.add(tabbedPane, BorderLayout.CENTER);
        add(pnlCenter, BorderLayout.CENTER);

        // 4. Bottom Panel: Multicast và Nút thao tác
        JPanel pnlBottom = new JPanel(new BorderLayout(10, 10));
        pnlBottom.setBorder(new EmptyBorder(5, 10, 10, 10));

        JPanel pnlBroadcast = new JPanel(new BorderLayout(8, 8));
        pnlBroadcast.setBorder(new TitledBorder("Phát Thông Báo Nhóm Multicast Tức Thời (230.0.0.1:9999)"));

        txtBroadcastMsg = new JTextField("[SERVER] Hệ thống mạng LAN đang hoạt động ổn định!");
        txtBroadcastMsg.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JButton btnSendBroadcast = new JButton("Phát Thông Báo");
        btnSendBroadcast.setBackground(new Color(255, 193, 7));
        btnSendBroadcast.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSendBroadcast.addActionListener(e -> {
            String msg = txtBroadcastMsg.getText().trim();
            if (!msg.isEmpty()) {
                MulticastBroadcaster.broadcast(msg);
                log("[Multicast] Đã phát sóng thông báo: \"" + msg + "\"");
            }
        });

        pnlBroadcast.add(new JLabel("Nội dung thông báo:"), BorderLayout.WEST);
        pnlBroadcast.add(txtBroadcastMsg, BorderLayout.CENTER);
        pnlBroadcast.add(btnSendBroadcast, BorderLayout.EAST);
        pnlBottom.add(pnlBroadcast, BorderLayout.NORTH);

        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnReloadData = new JButton("Làm Mới Dữ Liệu");
        btnReloadData.addActionListener(e -> refreshAllData());

        btnStartServer = new JButton("Khởi Động Server");
        btnStartServer.setEnabled(false);
        btnStartServer.addActionListener(e -> startServer());

        btnStopServer = new JButton("Dừng Server");
        btnStopServer.addActionListener(e -> stopServer());

        JButton btnOpenUploads = new JButton("Mở Thư Mục Tệp");
        btnOpenUploads.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File("data/uploads"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Chưa có thư mục hoặc tệp nào.");
            }
        });

        pnlActions.add(btnReloadData);
        pnlActions.add(btnOpenUploads);
        pnlActions.add(btnStartServer);
        pnlActions.add(btnStopServer);
        pnlBottom.add(pnlActions, BorderLayout.SOUTH);

        add(pnlBottom, BorderLayout.SOUTH);
    }

    // ==========================================
    // TAB 4: GIAO DIỆN KIỂM THỬ ĐA LUỒNG & ĐỒNG BỘ HÓA
    // ==========================================
    private JPanel createSynchronizationTestPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Form thiết lập số luồng & số lượt cập nhật
        JPanel pnlConfig = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        pnlConfig.setBorder(new TitledBorder("Cấu Hình Thử Nghiệm Xung Đột Đa Luồng"));

        txtThreads = new JTextField("10", 6);
        txtThreads.setFont(new Font("Segoe UI", Font.BOLD, 13));

        txtIterations = new JTextField("10000", 8);
        txtIterations.setFont(new Font("Segoe UI", Font.BOLD, 13));

        pnlConfig.add(new JLabel("Số Luồng (Threads):"));
        pnlConfig.add(txtThreads);
        pnlConfig.add(new JLabel("Số lượt cập nhật mỗi Luồng:"));
        pnlConfig.add(txtIterations);

        JButton btnRunUnsync = new JButton("Chạy Không Đồng Bộ");
        btnRunUnsync.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRunUnsync.setBackground(new Color(255, 215, 215));
        btnRunUnsync.setForeground(Color.BLACK);
        btnRunUnsync.setFocusPainted(false);
        btnRunUnsync.addActionListener(e -> runSyncTest(false));

        JButton btnRunSync = new JButton("Chạy Có Đồng Bộ Hóa");
        btnRunSync.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRunSync.setBackground(new Color(210, 245, 220));
        btnRunSync.setForeground(Color.BLACK);
        btnRunSync.setFocusPainted(false);
        btnRunSync.addActionListener(e -> runSyncTest(true));

        JButton btnRunBoth = new JButton("Chạy So Sánh Đối Chiếu");
        btnRunBoth.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnRunBoth.setBackground(new Color(215, 235, 255));
        btnRunBoth.setForeground(Color.BLACK);
        btnRunBoth.setFocusPainted(false);
        btnRunBoth.addActionListener(e -> runBothSyncTests());

        pnlConfig.add(btnRunUnsync);
        pnlConfig.add(btnRunSync);
        pnlConfig.add(btnRunBoth);

        // Bảng kết quả thẻ số liệu
        JPanel pnlCards = new JPanel(new GridLayout(1, 4, 10, 10));
        lblSyncExpected = createCardLabel("1. Kết Quả Kỳ Vọng", "100,000", new Color(108, 117, 125));
        lblSyncActual = createCardLabel("2. Kết Quả Thực Tế", "---", new Color(0, 123, 255));
        lblSyncDiff = createCardLabel("3. Sai Lệch Dữ Liệu", "---", new Color(220, 53, 69));
        lblSyncTime = createCardLabel("4. Thời Gian Xử Lý", "---", new Color(23, 162, 184));

        pnlCards.add(lblSyncExpected.getParent());
        pnlCards.add(lblSyncActual.getParent());
        pnlCards.add(lblSyncDiff.getParent());
        pnlCards.add(lblSyncTime.getParent());

        JPanel pnlNorth = new JPanel(new BorderLayout(8, 8));
        pnlNorth.add(pnlConfig, BorderLayout.NORTH);
        pnlNorth.add(pnlCards, BorderLayout.SOUTH);
        panel.add(pnlNorth, BorderLayout.NORTH);

        txtSyncLog = new JTextArea();
        txtSyncLog.setEditable(false);
        txtSyncLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtSyncLog.setText("=== GIAO DIỆN KIỂM THỬ ĐỒNG BỘ HÓA ĐA LUỒNG TRÊN SERVER ===\n" +
                "• Bấm 'Chạy Không Đồng Bộ' để quan sát hiện tượng mất mát dữ liệu do nhiều luồng cùng ghi.\n" +
                "• Bấm 'Chạy Có Đồng Bộ Hóa' để kiểm chứng tính toàn vẹn và an toàn dữ liệu.\n" +
                "• Bấm 'Chạy So Sánh Đối Chiếu' để xem bảng thống kê so sánh toàn diện.\n");
        panel.add(new JScrollPane(txtSyncLog), BorderLayout.CENTER);

        return panel;
    }

    private JLabel createCardLabel(String title, String val, Color color) {
        JPanel pnl = new JPanel(new BorderLayout(4, 4));
        pnl.setBackground(Color.WHITE);
        pnl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(Color.GRAY);

        JLabel lblVal = new JLabel(val);
        lblVal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblVal.setForeground(color);

        pnl.add(lblTitle, BorderLayout.NORTH);
        pnl.add(lblVal, BorderLayout.CENTER);
        return lblVal;
    }

    private void runSyncTest(boolean useSync) {
        int threads;
        int iters;
        try {
            threads = Integer.parseInt(txtThreads.getText().trim());
            iters = Integer.parseInt(txtIterations.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số Luồng và Số lượt cập nhật phải là số nguyên dương!");
            return;
        }

        txtSyncLog.append(String.format("\n>> Đang chạy kiểm thử: %s (%d luồng x %,d lượt)...\n",
                useSync ? "CÓ ĐỒNG BỘ HÓA" : "KHÔNG ĐỒNG BỘ HÓA", threads, iters));

        new Thread(() -> {
            ConcurrencyRaceConditionDemo.TestResult res = useSync ?
                    ConcurrencyRaceConditionDemo.runSynchronizedTest(threads, iters) :
                    ConcurrencyRaceConditionDemo.runUnsynchronizedTest(threads, iters);

            SwingUtilities.invokeLater(() -> {
                lblSyncExpected.setText(String.format("%,d", res.expectedValue));
                lblSyncActual.setText(String.format("%,d", res.actualValue));
                lblSyncDiff.setText(String.format("%,d (%.2f%%)", res.lostUpdates, res.errorRatePercent));
                lblSyncTime.setText(String.format("%d ms", res.executionTimeMs));

                txtSyncLog.append(String.format("  • Kết quả kỳ vọng: %,d\n", res.expectedValue));
                txtSyncLog.append(String.format("  • Kết quả thực tế: %,d\n", res.actualValue));
                txtSyncLog.append(String.format("  • Mức sai lệch dữ liệu: %,d (Tỷ lệ sai sót: %.2f%%)\n", res.lostUpdates, res.errorRatePercent));
                txtSyncLog.append(String.format("  • Thời gian xử lý: %d ms\n", res.executionTimeMs));
                if (useSync) {
                    txtSyncLog.append("  ==> ĐÁNH GIÁ: Đạt chính xác kỳ vọng! Dữ liệu được bảo toàn nguyên vẹn.\n");
                } else {
                    txtSyncLog.append("  ==> ĐÁNH GIÁ: Xảy ra xung đột tranh chấp dữ liệu, nhiều lượt cập nhật đã bị mất mát!\n");
                }
                txtSyncLog.setCaretPosition(txtSyncLog.getDocument().getLength());
            });
        }).start();
    }

    private void runBothSyncTests() {
        int threads;
        int iters;
        try {
            threads = Integer.parseInt(txtThreads.getText().trim());
            iters = Integer.parseInt(txtIterations.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số Luồng và Số lượt cập nhật phải là số nguyên dương!");
            return;
        }

        txtSyncLog.append("\n>> Đang thực hiện kiểm thử cả 2 trường hợp để so sánh đối chiếu...\n");

        new Thread(() -> {
            String report = ConcurrencyRaceConditionDemo.runComparisonTest(threads, iters);
            ConcurrencyRaceConditionDemo.TestResult unsync = ConcurrencyRaceConditionDemo.runUnsynchronizedTest(threads, iters);

            SwingUtilities.invokeLater(() -> {
                lblSyncExpected.setText(String.format("%,d", unsync.expectedValue));
                lblSyncActual.setText(String.format("%,d", unsync.actualValue));
                lblSyncDiff.setText(String.format("%,d (%.2f%%)", unsync.lostUpdates, unsync.errorRatePercent));
                lblSyncTime.setText(String.format("%d ms", unsync.executionTimeMs));

                txtSyncLog.append(report);
                txtSyncLog.setCaretPosition(txtSyncLog.getDocument().getLength());
            });
        }).start();
    }

    private JLabel createStatCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel lblCardTitle = new JLabel(title);
        lblCardTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblCardTitle.setForeground(Color.GRAY);

        JLabel lblCardValue = new JLabel(value);
        lblCardValue.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblCardValue.setForeground(color);

        card.add(lblCardTitle, BorderLayout.NORTH);
        card.add(lblCardValue, BorderLayout.CENTER);

        return lblCardValue;
    }

    private void initServerLogListener() {
        ClientHandler.serverLogListener = entry -> {
            SwingUtilities.invokeLater(() -> {
                txtServerLog.append(entry + "\n");
                txtServerLog.setCaretPosition(txtServerLog.getDocument().getLength());
                refreshAllData();
            });
        };
    }

    private void loadExistingLogFile() {
        File logFile = new File("data/server_log.txt");
        if (logFile.exists()) {
            try {
                String content = java.nio.file.Files.readString(logFile.toPath(), StandardCharsets.UTF_8);
                txtServerLog.setText(content);
                if (!content.isEmpty() && !content.endsWith("\n")) {
                    txtServerLog.append("\n");
                }
                txtServerLog.setCaretPosition(txtServerLog.getDocument().getLength());
            } catch (Exception ignored) {}
        }
    }

    public void log(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formatted = "[" + time + "] " + msg;
        SwingUtilities.invokeLater(() -> {
            txtServerLog.append(formatted + "\n");
            txtServerLog.setCaretPosition(txtServerLog.getDocument().getLength());
            refreshAllData();
        });
    }

    private void refreshAllData() {
        // Cập nhật bảng người dùng (users.csv)
        userTableModel.setRowCount(0);
        List<User> users = serverStats.getUserList();
        for (User u : users) {
            userTableModel.addRow(new Object[]{u.getUserId(), u.getFullName(), u.getEmail()});
        }
        lblStatsUsers.setText(users.size() + " người");

        // Cập nhật bảng tệp tin (uploads/)
        fileTableModel.setRowCount(0);
        List<FileInfo> files = serverStats.getUploadedFiles();
        for (FileInfo f : files) {
            fileTableModel.addRow(new Object[]{
                f.getFileName(),
                String.format("%,d", f.getFileSize()),
                f.getChunkCount(),
                f.getUploader(),
                f.getUploadTime()
            });
        }
        // Cập nhật số người online
        java.util.List<String> onlines = ClientHandler.getOnlineUserList();
        lblStatsOnline.setText(onlines.size() + " người " + onlines);

        lblStatsFiles.setText(serverStats.getTotalFiles() + " tệp");
        lblStatsBytes.setText(String.format("%,d bytes", serverStats.getTotalBytes()));
    }

    private synchronized void startServer() {
        if (isRunning) return;
        new File("data/uploads").mkdirs();

        // 1. Mở TCP ServerSocket đồng bộ ngay lập tức với setReuseAddress để tránh nghẽn TIME_WAIT
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(TCP_PORT));
        } catch (IOException e) {
            isRunning = false;
            lblStatus.setText("Lỗi cổng " + TCP_PORT + ": " + e.getMessage());
            lblStatus.setForeground(Color.RED);
            btnStartServer.setEnabled(true);
            btnStopServer.setEnabled(false);
            log("[LỖI] Không thể mở cổng TCP " + TCP_PORT + ": " + e.getMessage());
            return;
        }

        isRunning = true;
        lblStatus.setText("Online (TCP: " + TCP_PORT + " | RMI: " + RMI_PORT + ")");
        lblStatus.setForeground(new Color(0, 128, 0));
        btnStartServer.setEnabled(false);
        btnStopServer.setEnabled(true);
        log("TCP ServerSocket mở thành công tại cổng " + TCP_PORT);

        // 2. Khởi động RMI (Giữ tham chiếu rmiService chống Garbage Collection)
        new Thread(() -> {
            try {
                try {
                    rmiRegistry = LocateRegistry.createRegistry(RMI_PORT);
                } catch (Exception ex) {
                    rmiRegistry = LocateRegistry.getRegistry(RMI_PORT);
                }
                rmiService = new StatisticServiceImpl(serverStats);
                rmiRegistry.rebind(RMI_SERVICE_NAME, rmiService);
                log("RMI Service \"" + RMI_SERVICE_NAME + "\" sẵn sàng tại cổng " + RMI_PORT);
            } catch (Exception e) {
                log("Lỗi RMI: " + e.getMessage());
            }
        }).start();

        // 3. Luồng lắng nghe kết nối TCP từ Client
        new Thread(() -> {
            try {
                while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket, serverStats) {
                        @Override
                        public void run() {
                            super.run();
                            SwingUtilities.invokeLater(() -> refreshAllData());
                        }
                    }).start();
                }
            } catch (IOException e) {
                if (isRunning) log("Lỗi ServerSocket: " + e.getMessage());
            }
        }).start();
    }

    private synchronized void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("Lỗi khi dừng: " + e.getMessage());
        }
        ClientHandler.closeAllActiveStreams();
        lblStatus.setText("Đã dừng");
        lblStatus.setForeground(Color.DARK_GRAY);
        btnStartServer.setEnabled(true);
        btnStopServer.setEnabled(false);
        log("Server đã dừng.");
        refreshAllData();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
