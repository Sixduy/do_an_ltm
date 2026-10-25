package com.ltm.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote Interface phục vụ kỹ thuật RMI theo đúng yêu cầu đồ án:
 * - getStats(): Báo cáo thống kê chuỗi tổng hợp
 * - getTotalFiles(): Trả về tổng số tệp đã nhận thành công
 * - getTotalBytes(): Trả về tổng dung lượng dữ liệu đã xử lý
 * - getRemoteFileList(): Danh sách các đối tượng tệp trên Server
 * - getOnlineUsers(): Danh sách các UserID đang online
 */
public interface IStatisticService extends Remote {

    String getStats() throws RemoteException;

    int getTotalFiles() throws RemoteException;

    long getTotalBytes() throws RemoteException;

    List<FileInfo> getRemoteFileList() throws RemoteException;

    List<String> getOnlineUsers() throws RemoteException;
}
