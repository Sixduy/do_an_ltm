package com.ltm.server;

import com.ltm.common.FileInfo;
import com.ltm.common.IStatisticService;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * Cài đặt Remote Interface cho dịch vụ RMI theo chuẩn đồ án
 */
public class StatisticServiceImpl extends UnicastRemoteObject implements IStatisticService {
    private static final long serialVersionUID = 1L;

    private final ServerStats serverStats;

    public StatisticServiceImpl(ServerStats serverStats) throws RemoteException {
        super();
        this.serverStats = serverStats;
    }

    @Override
    public String getStats() throws RemoteException {
        System.out.println("[RMI Server] Client gọi hàm getStats() từ xa.");
        return serverStats.getStatsReport();
    }

    @Override
    public int getTotalFiles() throws RemoteException {
        return serverStats.getTotalFiles();
    }

    @Override
    public long getTotalBytes() throws RemoteException {
        return serverStats.getTotalBytes();
    }

    @Override
    public List<FileInfo> getRemoteFileList() throws RemoteException {
        return serverStats.getUploadedFiles();
    }

    @Override
    public List<String> getOnlineUsers() throws RemoteException {
        return ClientHandler.getOnlineUserList();
    }
}
