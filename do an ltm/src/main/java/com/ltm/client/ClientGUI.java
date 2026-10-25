package com.ltm.client;

import com.ltm.common.FileInfo;
import com.ltm.common.IStatisticService;
import com.ltm.common.User;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Giao diện chính Client sau khi Đăng Nhập thành công
 * Bao gồm các chức năng:
 * 1. Nhắn tin riêng 1 - 1 qua TCP Socket
 * 2. Truyền tệp nhị phân khối 100 byte
 * 3. Tra cứu thống kê tập trung từ xa qua Java RMI
 * 4. Nhận thông báo phát sóng Multicast (Tham gia / Rời nhóm)
 */
public class ClientGUI extends JFrame {
    private String serverHost;
    private int tcpPort;
    private int rmiPort = 1099;

    // Thông tin người dùng đã xác thực
    private final User currentUser;

    // Kênh nhận tin nhắn riêng qua TCP
    private Socket directSocket;
    private volatile boolean directListening = true;

    // Multicast components (Hỗ trợ Tham gia / Rời nhóm)
    private JLabel lblMulticastAlert;
    private JButton btnJoinMulticast;
    private JButton btnLeaveMulticast;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private volatile boolean isMulticastJoined = true;
    private volatile boolean multicastRunning = true;

    // Tab 1: Nhắn tin riêng 1-1
    private JTextField txtTargetUserId;
    private JTextField txtPrivateMsgContent;
    private JTextArea txtPrivateChatHistory;

    // Tab 2: Truyền tệp cắt đoạn 100B
    private File selectedFile;
    private JLabel lblSelectedFileInfo;
    private JProgressBar progressFileTransfer;
    private JTextArea txtFileLog;
    private JButton btnSendFile;

    // Tab 3: Tra cứu RMI
    private JLabel lblRmiFiles;
    private JLabel lblRmiBytes;
    private JTextArea txtRmiStatsOutput;
    private DefaultTableModel rmiFileTableModel;

    public ClientGUI(User user, String host, int port) {
        this.currentUser = user;
        this.serverHost = host;
        this.tcpPort = port;

        initComponents();
        startDirectMessageListener();
        startMulticastListener();
    }

    private void initComponents() {
        setTitle(String.format("CLIENT MẠNG LAN - ĐÃ ĐĂNG NHẬP: [%s] %s", currentUser.getUserId(), currentUser.getFullName()));
        setSize(1020, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // 1. TOP PANEL: Thông tin người dùng & Multicast Bar
        JPanel pnlTop = new JPanel(new BorderLayout(5, 5));
        pnlTop.setBorder(new EmptyBorder(8, 10, 0, 10));

        JPanel pnlUserBar = new JPanel(new BorderLayout());
        pnlUserBar.setBackground(new Color(230, 243, 255));
        pnlUserBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 210, 240), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel lblUserBanner = new JLabel(String.format("TÀI KHOẢN ĐANG ĐĂNG NHẬP: %s - %s (%s)  |  Server TCP: %s:%d",
                currentUser.getUserId(), currentUser.getFullName(), currentUser.getEmail(), serverHost, tcpPort));
        lblUserBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUserBanner.setForeground(new Color(24, 76, 120));

        JButton btnLogout = new JButton("Đăng Xuất (Đổi Tài Khoản)");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLogout.setBackground(new Color(255, 235, 235));
        btnLogout.setFocusPainted(false);
        btnLogout.addActionListener(e -> logoutAndRelogin());

        pnlUserBar.add(lblUserBanner, BorderLayout.WEST);
        pnlUserBar.add(btnLogout, BorderLayout.EAST);
        pnlTop.add(pnlUserBar, BorderLayout.NORTH);

        // Khung thông báo chung Multicast (Có nút Tham gia / Rời nhóm)
        JPanel pnlAlert = new JPanel(new BorderLayout(8, 5));
        pnlAlert.setBackground(new Color(255, 248, 220));
        pnlAlert.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(230, 180, 50), 1),
            new EmptyBorder(6, 12, 6, 12)
        ));

        lblMulticastAlert = new JLabel("[MULTICAST 230.0.0.1:9999]: Đang tham gia nhóm (Sẵn sàng nhận thông báo)");
        lblMulticastAlert.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblMulticastAlert.setForeground(new Color(150, 90, 0));

        JPanel pnlMulticastControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pnlMulticastControls.setOpaque(false);

        btnJoinMulticast = new JButton("Tham Gia Nhóm");
        btnJoinMulticast.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnJoinMulticast.setEnabled(false); // Mặc định đã tham gia
        btnJoinMulticast.addActionListener(e -> joinMulticast());

        btnLeaveMulticast = new JButton("Rời Nhóm");
        btnLeaveMulticast.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnLeaveMulticast.addActionListener(e -> leaveMulticast());

        pnlMulticastControls.add(btnJoinMulticast);
        pnlMulticastControls.add(btnLeaveMulticast);

        pnlAlert.add(lblMulticastAlert, BorderLayout.CENTER);
        pnlAlert.add(pnlMulticastControls, BorderLayout.EAST);
        pnlTop.add(pnlAlert, BorderLayout.SOUTH);

        add(pnlTop, BorderLayout.NORTH);

        // 2. CENTER PANEL: 3 Tab chức năng Client
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("1. Nhắn Tin Riêng", createPrivateChatPanel());
        tabbedPane.addTab("2. Truyền Tệp Khối 100 Byte", createFileTransferPanel());
        tabbedPane.addTab("3. Tra Cứu Thống Kê Từ Xa", createRmiPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ==========================================
    // TAB 1: NHẮN TIN RIÊNG
    // ==========================================
    private JPanel createPrivateChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel pnlTarget = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        pnlTarget.setBorder(new TitledBorder("Gửi Tin Nhắn Riêng Tới Người Dùng"));

        txtTargetUserId = new JTextField("U002", 10);
        txtTargetUserId.setFont(new Font("Segoe UI", Font.BOLD, 13));

        pnlTarget.add(new JLabel("Gửi riêng tới UserID đích:"));
        pnlTarget.add(txtTargetUserId);

        JButton btnCheckOnline = new JButton("Danh Sách Online");
        btnCheckOnline.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCheckOnline.addActionListener(e -> {
            new Thread(() -> {
                try {
                    Registry registry = LocateRegistry.getRegistry(serverHost, rmiPort);
                    IStatisticService service = (IStatisticService) registry.lookup("StatsService");
                    List<String> list = service.getOnlineUsers();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "Danh sách User đang online hiện tại: " + list + "\n(Hãy chọn một mã trong danh sách này để gửi tin)",
                            "Người Dùng Đang Online", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Lỗi kiểm tra: " + ex.getMessage()));
                }
            }).start();
        });
        pnlTarget.add(btnCheckOnline);
        panel.add(pnlTarget, BorderLayout.NORTH);

        txtPrivateChatHistory = new JTextArea();
        txtPrivateChatHistory.setEditable(false);
        txtPrivateChatHistory.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtPrivateChatHistory.setText("=== CỬA SỔ TIN NHẮN RIÊNG ===\n");
        panel.add(new JScrollPane(txtPrivateChatHistory), BorderLayout.CENTER);

        JPanel pnlInput = new JPanel(new BorderLayout(8, 8));
        txtPrivateMsgContent = new JTextField();
        txtPrivateMsgContent.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtPrivateMsgContent.addActionListener(e -> sendPrivateMessage());

        JButton btnSend = new JButton("Gửi Tin Nhắn");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setBackground(new Color(215, 235, 255));
        btnSend.setForeground(Color.BLACK);
        btnSend.setFocusPainted(false);
        btnSend.addActionListener(e -> sendPrivateMessage());

        pnlInput.add(txtPrivateMsgContent, BorderLayout.CENTER);
        pnlInput.add(btnSend, BorderLayout.EAST);
        panel.add(pnlInput, BorderLayout.SOUTH);

        return panel;
    }

    private void sendPrivateMessage() {
        String targetUser = txtTargetUserId.getText().trim();
        String message = txtPrivateMsgContent.getText().trim();

        if (targetUser.isEmpty() || message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập User đích và nội dung tin nhắn!");
            return;
        }

        if (targetUser.equalsIgnoreCase(currentUser.getUserId())) {
            JOptionPane.showMessageDialog(this, "Bạn đang gửi tin nhắn cho chính mình!");
        }

        new Thread(() -> {
            try (Socket socket = new Socket(serverHost, tcpPort);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                dos.writeUTF("SEND_PRIVATE_MSG");
                dos.writeUTF(currentUser.getUserId());
                dos.writeUTF(targetUser);
                dos.writeUTF(message);
                dos.flush();

                String response = dis.readUTF();
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                SwingUtilities.invokeLater(() -> {
                    if (response.startsWith("OK")) {
                        txtPrivateChatHistory.append(String.format("[%s] Tôi: %s\n", time, message));
                        txtPrivateMsgContent.setText("");
                    } else {
                        txtPrivateChatHistory.append(String.format("[%s] [LỖI]: %s\n", time, response));
                    }
                    txtPrivateChatHistory.setCaretPosition(txtPrivateChatHistory.getDocument().getLength());
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtPrivateChatHistory.append("[LỖI KẾT NỐI SERVER]: " + ex.getMessage() + "\n");
                });
            }
        }).start();
    }

    private void startDirectMessageListener() {
        new Thread(() -> {
            try {
                directSocket = new Socket(serverHost, tcpPort);
                DataOutputStream dos = new DataOutputStream(directSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(directSocket.getInputStream());

                dos.writeUTF("LISTEN_DIRECT");
                dos.writeUTF(currentUser.getUserId());
                dos.flush();

                String status = dis.readUTF();
                if ("CONNECTED_DIRECT".equals(status)) {
                    System.out.println("[Client] Đã kết nối kênh nhận tin riêng cho User: " + currentUser.getUserId());
                }

                while (directListening && !directSocket.isClosed()) {
                    String type = dis.readUTF();
                    if ("INCOMING_PRIVATE_MSG".equals(type)) {
                        String fromUser = dis.readUTF();
                        String content = dis.readUTF();
                        String time = dis.readUTF();

                        SwingUtilities.invokeLater(() -> {
                            Toolkit.getDefaultToolkit().beep();
                            txtPrivateChatHistory.append(String.format("[%s] %s: %s\n", time, fromUser, content));
                            txtPrivateChatHistory.setCaretPosition(txtPrivateChatHistory.getDocument().getLength());
                        });
                    }
                }
            } catch (IOException e) {
                if (directListening) {
                    System.out.println("[Client] Kênh tin riêng ngắt kết nối: " + e.getMessage());
                }
            }
        }, "Direct-Message-Listener").start();
    }

    // ==========================================
    // TAB 2: Truyền tệp chia đoạn 100B
    // ==========================================
    private JPanel createFileTransferPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel pnlChoose = new JPanel(new BorderLayout(8, 8));
        pnlChoose.setBorder(new TitledBorder("Bước 1: Chọn Tệp Cần Truyền"));

        JPanel pnlInfoBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        lblSelectedFileInfo = new JLabel("Chưa chọn tệp (Bấm 'Chọn tệp...' để chọn file cần gửi)");
        lblSelectedFileInfo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pnlInfoBox.add(lblSelectedFileInfo);

        JButton btnBrowse = new JButton("Chọn Tệp...");
        btnBrowse.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("data"));
            int ret = chooser.showOpenDialog(this);
            if (ret == JFileChooser.APPROVE_OPTION) {
                selectedFile = chooser.getSelectedFile();
                long totalChunks = (selectedFile.length() + 99) / 100;

                lblSelectedFileInfo.setText(String.format("Tệp: %s | Dung lượng: %,d bytes | Chia: %d đoạn 100B",
                        selectedFile.getName(), selectedFile.length(), totalChunks));
                btnSendFile.setEnabled(true);
            }
        });

        pnlChoose.add(btnBrowse, BorderLayout.WEST);
        pnlChoose.add(pnlInfoBox, BorderLayout.CENTER);
        panel.add(pnlChoose, BorderLayout.NORTH);

        JPanel pnlMiddle = new JPanel(new BorderLayout(8, 8));
        pnlMiddle.setBorder(new TitledBorder("Bước 2: Tiến Trình Truyền Tệp"));

        progressFileTransfer = new JProgressBar(0, 100);
        progressFileTransfer.setStringPainted(true);

        txtFileLog = new JTextArea();
        txtFileLog.setEditable(false);
        txtFileLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtFileLog);

        pnlMiddle.add(progressFileTransfer, BorderLayout.NORTH);
        pnlMiddle.add(scrollLog, BorderLayout.CENTER);
        panel.add(pnlMiddle, BorderLayout.CENTER);

        btnSendFile = new JButton("Bắt Đầu Truyền Tệp");
        btnSendFile.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSendFile.setBackground(new Color(210, 245, 220));
        btnSendFile.setForeground(Color.BLACK);
        btnSendFile.setFocusPainted(false);
        btnSendFile.addActionListener(e -> doSendFile());

        panel.add(btnSendFile, BorderLayout.SOUTH);
        return panel;
    }

    private void doSendFile() {
        if (selectedFile == null || !selectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp trước!");
            return;
        }

        btnSendFile.setEnabled(false);
        progressFileTransfer.setValue(0);
        txtFileLog.setText("");

        new Thread(() -> {
            try (Socket socket = new Socket(serverHost, tcpPort);
                 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 FileInputStream fis = new FileInputStream(selectedFile)) {

                long fileSize = selectedFile.length();

                SwingUtilities.invokeLater(() -> {
                    txtFileLog.append("=== BẮT ĐẦU TRUYỀN TỆP QUA TCP SOCKET (CẮT ĐOẠN 100 BYTE) ===\n");
                    txtFileLog.append(String.format("• Tên tệp: %s\n• Dung lượng: %,d bytes\n\n",
                            selectedFile.getName(), fileSize));
                });

                dos.writeUTF("FILE");
                dos.writeUTF(currentUser.getUserId());
                dos.writeUTF(selectedFile.getName());
                dos.writeLong(fileSize);
                dos.flush();

                byte[] buffer = new byte[100];
                int bytesRead;
                long totalSent = 0;
                int chunkIndex = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.writeInt(bytesRead);
                    dos.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                    chunkIndex++;

                    int percent = (int) ((totalSent * 100) / fileSize);
                    final int p = percent;
                    final int ci = chunkIndex;
                    final long ts = totalSent;
                    final int curBytes = bytesRead;

                    SwingUtilities.invokeLater(() -> {
                        progressFileTransfer.setValue(p);
                        txtFileLog.append(String.format("-> Đã gửi đoạn #%03d (%3d bytes) | Đã truyền: %,d / %,d bytes\n",
                                ci, curBytes, ts, fileSize));
                        txtFileLog.setCaretPosition(txtFileLog.getDocument().getLength());
                    });
                }

                dos.writeInt(-1);
                dos.flush();

                String serverResponse = dis.readUTF();

                SwingUtilities.invokeLater(() -> {
                    txtFileLog.append("\n=======================================================\n");
                    txtFileLog.append("[XÁC NHẬN TỪ SERVER]:\n" + serverResponse + "\n");
                    txtFileLog.append("=======================================================\n");
                    txtFileLog.setCaretPosition(txtFileLog.getDocument().getLength());
                    btnSendFile.setEnabled(true);
                    JOptionPane.showMessageDialog(this, serverResponse, "Truyền & Ghép Tệp Thành Công", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtFileLog.append("[LỖI TRUYỀN TỆP]: " + ex.getMessage() + "\n");
                    btnSendFile.setEnabled(true);
                });
            }
        }).start();
    }

    // ==========================================
    // TAB 3: Tra cứu thống kê từ xa
    // ==========================================
    private JPanel createRmiPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Thanh thẻ thống kê số liệu trực quan
        JPanel pnlCards = new JPanel(new GridLayout(1, 2, 10, 10));
        lblRmiFiles = createCardLabel("1. Tổng Tệp Đã Nhận", "0 tệp", new Color(40, 167, 69));
        lblRmiBytes = createCardLabel("2. Tổng Dung Lượng Đã Nhận", "0 bytes", new Color(0, 123, 255));
        pnlCards.add(lblRmiFiles.getParent());
        pnlCards.add(lblRmiBytes.getParent());

        txtRmiStatsOutput = new JTextArea();
        txtRmiStatsOutput.setEditable(false);
        txtRmiStatsOutput.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtRmiStatsOutput.setBackground(new Color(248, 249, 250));

        JPanel pnlStats = new JPanel(new BorderLayout());
        pnlStats.setBorder(new TitledBorder("Báo Cáo Chi Tiết Hoạt Động Server"));
        pnlStats.add(new JScrollPane(txtRmiStatsOutput), BorderLayout.CENTER);

        String[] fileCols = {"Tên Tệp", "Dung Lượng (Bytes)", "Số Khối 100B", "User Gửi", "Thời Gian Ghép"};
        rmiFileTableModel = new DefaultTableModel(fileCols, 0);
        JTable table = new JTable(rmiFileTableModel);
        table.setRowHeight(24);

        JPanel pnlTable = new JPanel(new BorderLayout());
        pnlTable.setBorder(new TitledBorder("Danh Sách Tệp Quản Lý Trên Server"));
        pnlTable.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pnlStats, pnlTable);
        splitPane.setResizeWeight(0.4);

        JPanel pnlCenter = new JPanel(new BorderLayout(8, 8));
        pnlCenter.add(pnlCards, BorderLayout.NORTH);
        pnlCenter.add(splitPane, BorderLayout.CENTER);
        panel.add(pnlCenter, BorderLayout.CENTER);

        JPanel pnlBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        JButton btnGetStats = new JButton("Xem Báo Cáo Thống Kê");
        btnGetStats.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnGetStats.setBackground(new Color(210, 245, 220));
        btnGetStats.setForeground(Color.BLACK);
        btnGetStats.setFocusPainted(false);
        btnGetStats.addActionListener(e -> callRmiGetStats());

        JButton btnQueryFiles = new JButton("Làm Mới Danh Sách Tệp");
        btnQueryFiles.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnQueryFiles.setBackground(new Color(225, 238, 248));
        btnQueryFiles.setForeground(Color.BLACK);
        btnQueryFiles.setFocusPainted(false);
        btnQueryFiles.addActionListener(e -> callRmiGetFiles());

        pnlBtns.add(btnGetStats);
        pnlBtns.add(btnQueryFiles);
        panel.add(pnlBtns, BorderLayout.SOUTH);

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
        lblVal.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblVal.setForeground(color);

        pnl.add(lblTitle, BorderLayout.NORTH);
        pnl.add(lblVal, BorderLayout.CENTER);
        return lblVal;
    }

    private void callRmiGetStats() {
        new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(serverHost, rmiPort);
                IStatisticService service = (IStatisticService) registry.lookup("StatsService");

                String report = service.getStats();
                int totalFiles = service.getTotalFiles();
                long totalBytes = service.getTotalBytes();

                SwingUtilities.invokeLater(() -> {
                    txtRmiStatsOutput.setText(report);
                    lblRmiFiles.setText(String.format("%,d tệp", totalFiles));
                    lblRmiBytes.setText(String.format("%,d bytes", totalBytes));
                });
                callRmiGetFiles();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> txtRmiStatsOutput.setText("Lỗi RMI: " + ex.getMessage()));
            }
        }).start();
    }

    private void callRmiGetFiles() {
        new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(serverHost, rmiPort);
                IStatisticService service = (IStatisticService) registry.lookup("StatsService");
                List<FileInfo> files = service.getRemoteFileList();

                SwingUtilities.invokeLater(() -> {
                    rmiFileTableModel.setRowCount(0);
                    for (FileInfo f : files) {
                        rmiFileTableModel.addRow(new Object[]{
                            f.getFileName(),
                            String.format("%,d", f.getFileSize()),
                            f.getChunkCount(),
                            f.getUploader(),
                            f.getUploadTime()
                        });
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    // ==========================================
    // LẮNG NGHE MULTICAST & THAM GIA / RỜI NHÓM
    // ==========================================
    private void startMulticastListener() {
        new Thread(() -> {
            try {
                multicastSocket = new MulticastSocket(9999);
                multicastGroup = InetAddress.getByName("230.0.0.1");
                try {
                    multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, 9999), null);
                } catch (Exception fallback) {
                    multicastSocket.joinGroup(multicastGroup);
                }

                byte[] buf = new byte[2048];
                while (multicastRunning) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    multicastSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                    if (isMulticastJoined) {
                        SwingUtilities.invokeLater(() -> {
                            lblMulticastAlert.setText("[MULTICAST] " + msg);
                            callRmiGetFiles();
                        });
                    }
                }
            } catch (Exception ignored) {}
        }, "Multicast-Listener").start();
    }

    private void joinMulticast() {
        if (!isMulticastJoined) {
            try {
                try {
                    multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, 9999), null);
                } catch (Exception fallback) {
                    multicastSocket.joinGroup(multicastGroup);
                }
                isMulticastJoined = true;
                btnJoinMulticast.setEnabled(false);
                btnLeaveMulticast.setEnabled(true);
                lblMulticastAlert.setText("[MULTICAST 230.0.0.1:9999]: Đang tham gia nhóm (Sẵn sàng nhận thông báo)");
                JOptionPane.showMessageDialog(this, "Đã tham gia nhóm Multicast 230.0.0.1:9999!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi tham gia nhóm: " + ex.getMessage());
            }
        }
    }

    private void leaveMulticast() {
        if (isMulticastJoined) {
            try {
                try {
                    multicastSocket.leaveGroup(new InetSocketAddress(multicastGroup, 9999), null);
                } catch (Exception fallback) {
                    multicastSocket.leaveGroup(multicastGroup);
                }
                isMulticastJoined = false;
                btnJoinMulticast.setEnabled(true);
                btnLeaveMulticast.setEnabled(false);
                lblMulticastAlert.setText("[MULTICAST 230.0.0.1:9999]: ĐÃ RỜI NHÓM (Không nhận thông báo)");
                JOptionPane.showMessageDialog(this, "Đã rời nhóm Multicast thành công. Bạn sẽ không nhận thông báo tệp!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi rời nhóm: " + ex.getMessage());
            }
        }
    }

    private void logoutAndRelogin() {
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn đăng xuất?", "Xác Nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            directListening = false;
            multicastRunning = false;

            if (directSocket != null && !directSocket.isClosed()) {
                try { directSocket.close(); } catch (Exception ignored) {}
            }
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.close();
            }
            dispose();

            // Mở lại hộp thoại đăng nhập
            SwingUtilities.invokeLater(() -> {
                LoginDialog loginDialog = new LoginDialog(null);
                loginDialog.setVisible(true);
                if (loginDialog.isAuthenticated()) {
                    ClientGUI client = new ClientGUI(loginDialog.getLoggedInUser(), loginDialog.getServerHost(), loginDialog.getTcpPort());
                    client.setVisible(true);
                }
            });
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);

            if (loginDialog.isAuthenticated()) {
                ClientGUI client = new ClientGUI(loginDialog.getLoggedInUser(), loginDialog.getServerHost(), loginDialog.getTcpPort());
                client.setVisible(true);
            } else {
                System.out.println("Đã đóng Client.");
                System.exit(0);
            }
        });
    }
}
