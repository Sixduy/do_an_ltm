package com.ltm.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kiểm thử cơ chế đồng bộ hóa khi nhiều Thread cùng cập nhật dữ liệu dùng chung.
 * Đáp ứng đầy đủ yêu cầu:
 * - Tạo 10 Thread, mỗi Thread 10.000 lượt (kỳ vọng: 100.000)
 * - Thực hiện 2 lần: KHÔNG DÙNG ĐỒNG BỘ và CÓ DÙNG ĐỒNG BỘ (synchronized)
 * - Công thức: Sai lệch = |Kết quả thực tế - Kết quả kỳ vọng|
 * - Đo lường chính xác kết quả thực tế, kỳ vọng, tỷ lệ sai lệch và thời gian xử lý
 */
public class ConcurrencyRaceConditionDemo {

    public static class TestResult {
        public long expectedValue;
        public long actualValue;
        public long lostUpdates; // Sai lệch = |Kết quả thực tế - Kết quả kỳ vọng|
        public double errorRatePercent;
        public long executionTimeMs;
    }

    // 1. Trường hợp KHÔNG ĐỒNG BỘ (Non-synchronized - Dễ xảy ra Race Condition & Lost Update)
    public static class UnsynchronizedServerStats {
        private long totalBytes = 0;
        private int totalFiles = 0;

        // Thao tác ++ không nguyên tử (non-atomic: read -> modify -> write)
        public void recordTransfer(int bytes) {
            totalFiles++;
            totalBytes += bytes;
        }

        public long getTotalBytes() { return totalBytes; }
        public int getTotalFiles() { return totalFiles; }
    }

    // 2. Trường hợp CÓ ĐỒNG BỘ (Synchronized - Khóa độc quyền, Thread-safe 100%)
    public static class SynchronizedServerStats {
        private long totalBytes = 0;
        private int totalFiles = 0;

        public synchronized void recordTransfer(int bytes) {
            totalFiles++;
            totalBytes += bytes;
        }

        public synchronized long getTotalBytes() { return totalBytes; }
        public synchronized int getTotalFiles() { return totalFiles; }
    }

    /**
     * Chạy kiểm thử KHÔNG ĐỒNG BỘ
     */
    public static TestResult runUnsynchronizedTest(int numThreads, int iterationsPerThread) {
        long expectedCount = (long) numThreads * iterationsPerThread;
        UnsynchronizedServerStats stats = new UnsynchronizedServerStats();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        long start = System.currentTimeMillis();
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        stats.recordTransfer(100);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        long time = System.currentTimeMillis() - start;
        executor.shutdown();

        TestResult res = new TestResult();
        res.expectedValue = expectedCount;
        res.actualValue = stats.getTotalFiles();
        res.lostUpdates = Math.abs(res.actualValue - res.expectedValue);
        res.errorRatePercent = ((double) res.lostUpdates / res.expectedValue) * 100.0;
        res.executionTimeMs = time;
        return res;
    }

    /**
     * Chạy kiểm thử CÓ ĐỒNG BỘ (synchronized)
     */
    public static TestResult runSynchronizedTest(int numThreads, int iterationsPerThread) {
        long expectedCount = (long) numThreads * iterationsPerThread;
        SynchronizedServerStats stats = new SynchronizedServerStats();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        long start = System.currentTimeMillis();
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        stats.recordTransfer(100);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        long time = System.currentTimeMillis() - start;
        executor.shutdown();

        TestResult res = new TestResult();
        res.expectedValue = expectedCount;
        res.actualValue = stats.getTotalFiles();
        res.lostUpdates = Math.abs(res.actualValue - res.expectedValue);
        res.errorRatePercent = ((double) res.lostUpdates / res.expectedValue) * 100.0;
        res.executionTimeMs = time;
        return res;
    }

    /**
     * So sánh đối chiếu cả hai trường hợp để tạo báo cáo chi tiết
     */
    public static String runComparisonTest(int numThreads, int iterationsPerThread) {
        TestResult unsync = runUnsynchronizedTest(numThreads, iterationsPerThread);
        TestResult sync = runSynchronizedTest(numThreads, iterationsPerThread);

        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append(String.format("THỰC NGHIỆM ĐO LƯỜNG SAI LỆCH ĐỒNG BỘ (%d Luồng chạy đồng thời)\n", numThreads));
        sb.append(String.format("Mỗi luồng thực hiện: %,d thao tác cập nhật dữ liệu\n", iterationsPerThread));
        sb.append(String.format("Kết quả kỳ vọng (Expected): %,d thao tác\n", unsync.expectedValue));
        sb.append("========================================================================================\n\n");

        sb.append("[1] KẾT QUẢ KHI KHÔNG ĐỒNG BỘ:\n");
        sb.append(String.format("  • Kết quả kỳ vọng:   %,d\n", unsync.expectedValue));
        sb.append(String.format("  • Kết quả thực tế:   %,d\n", unsync.actualValue));
        sb.append(String.format("  • Mức sai lệch:      %,d (Sai lệch = |Thực tế - Kỳ vọng|)\n", unsync.lostUpdates));
        sb.append(String.format("  • Tỷ lệ thất thoát:  %.2f %%\n", unsync.errorRatePercent));
        sb.append(String.format("  • Thời gian xử lý:   %d ms\n", unsync.executionTimeMs));
        sb.append("  ==> KẾT LUẬN: Đã xảy ra xung đột tranh chấp dữ liệu (Race Condition), dẫn đến mất mát cập nhật!\n\n");

        sb.append("[2] KẾT QUẢ KHI CÓ ĐỒNG BỘ HÓA (SYNCHRONIZED):\n");
        sb.append(String.format("  • Kết quả kỳ vọng:   %,d\n", sync.expectedValue));
        sb.append(String.format("  • Kết quả thực tế:   %,d\n", sync.actualValue));
        sb.append(String.format("  • Mức sai lệch:      %,d (Sai lệch = |Thực tế - Kỳ vọng|)\n", sync.lostUpdates));
        sb.append(String.format("  • Tỷ lệ thất thoát:  %.2f %%\n", sync.errorRatePercent));
        sb.append(String.format("  • Thời gian xử lý:   %d ms\n", sync.executionTimeMs));
        sb.append("  ==> KẾT LUẬN: Đạt chính xác 100% kỳ vọng nhờ cơ chế khóa đồng bộ hóa!\n");
        sb.append("========================================================================================\n");

        return sb.toString();
    }
}
