package com.infineon.tpm20.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class ThreadServiceTests {

    @Autowired
    private ThreadService threadService;

    @Test
    void ThreadServiceTest1() {
        Runnable runnable = new InfiniteRunnable();
        long id = threadService.execute(runnable).getId();
        Assertions.assertDoesNotThrow(() -> {
            Thread thread = threadService.getThread(id);
            Assertions.assertNotNull(thread);
        });
    }

    @Test
    void ThreadServiceTest2() {
        Runnable runnable1 = new InfiniteRunnable();
        Runnable runnable2 = new InfiniteRunnable();
        Runnable runnable3 = new InfiniteRunnable();
        Runnable runnable4 = new InfiniteRunnable();
        Runnable runnable5 = new InfiniteRunnable();
        Assertions.assertNotNull(threadService.execute(runnable1));
        Assertions.assertNotNull(threadService.execute(runnable2));
        Assertions.assertNotNull(threadService.execute(runnable3));
        Assertions.assertNull(threadService.execute(runnable4));
        Assertions.assertNull(threadService.execute(runnable5));
    }

    /**
     * Completed thread will be removed from threadService pool.
     */
    @Test
    void ThreadServiceTest3() {
        Runnable runnable = new ShortRunnable();
        long id = threadService.execute(runnable).getId();
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }
        Assertions.assertDoesNotThrow(() -> {
            Thread thread = threadService.getThread(id);
            Assertions.assertNull(thread);
        });
    }

    private class InfiniteRunnable implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
        }
    }

    private class ShortRunnable implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(1);
            } catch (Exception e) {
            }
        }
    }
}
