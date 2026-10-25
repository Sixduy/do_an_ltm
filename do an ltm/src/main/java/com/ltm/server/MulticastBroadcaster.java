package com.ltm.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lớp phát thông điệp quảng bá Multicast (Chức năng 4)
 * Server sử dụng lớp này để gửi tin khẩn / thông báo nhóm tới tất cả các Client
 */
public class MulticastBroadcaster {
    public static final String MULTICAST_GROUP_IP = "230.0.0.1";
    public static final int MULTICAST_PORT = 9999;

    /**
     * Phát một thông báo tới toàn bộ các Client trong cùng nhóm Multicast
     */
    public static void broadcast(String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP_IP);
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);
            socket.send(packet);
            System.out.println("[Multicast Broadcaster] Da phat thong bao nhom: \"" + message + "\" toi " + MULTICAST_GROUP_IP + ":" + MULTICAST_PORT);
        } catch (IOException e) {
            System.err.println("[Multicast Broadcaster] Loi khi phat thong bao: " + e.getMessage());
        }
    }
}
