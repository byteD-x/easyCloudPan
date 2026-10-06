package com.easypan.performance;

import com.easypan.entity.query.FileInfoQuery;
import com.easypan.service.FileInfoService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Manual execution only for performance testing")
public class PerformanceTest {

    @Autowired
    private FileInfoService fileInfoService;

    @Test
    @DisplayName("Concurrent File List Query Performance")
    void testConcurrentFileListQuery() throws InterruptedException {
        int threadCount = 50;
        int loopCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        String userId = "test_user_123"; // Make sure this user exists or mock it if using MockBean

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < loopCount; j++) {
                        FileInfoQuery query = new FileInfoQuery();
                        query.setUserId(userId);
                        query.setFilePid("0");
                        fileInfoService.findListByPage(query);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        stopWatch.stop();

        System.out.println("Performance Test-Concurrent Query:");
        System.out.println("Threads: " + threadCount);
        System.out.println("Loops per thread: " + loopCount);
        System.out.println("Total Requests: " + (threadCount * loopCount));
        System.out.println("Total Time: " + stopWatch.getTotalTimeMillis() + " ms");
        System.out.println("Success: " + successCount.get());
        System.out.println("Failed: " + failCount.get());
        System.out.println("TPS: " + ((threadCount * loopCount) / stopWatch.getTotalTimeSeconds()));
    }
}
