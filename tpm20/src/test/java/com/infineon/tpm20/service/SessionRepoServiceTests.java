package com.infineon.tpm20.service;

import com.infineon.tpm20.Constants;
import com.infineon.tpm20.entity.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// Overlay the default application properties with properties from test
@ActiveProfiles("test1")
public class SessionRepoServiceTests {

    @Autowired
    private Constants constants;
    @Autowired
    private SessionRepoService sessionRepoService;
    @Autowired
    private ThreadService threadService;

    @Test
    void saveTest() {
        String uuid = java.util.UUID.randomUUID().toString();

        Session session = new Session();
        session.setUuid(uuid);
        sessionRepoService.save(session);

        Session sessionFound = sessionRepoService.findByUuid(uuid);
        Assertions.assertNotNull(sessionFound);

        try { Thread.sleep(1); } catch (Exception e) {}

        Date date = sessionFound.getExpiry();
        Assertions.assertTrue(date.after(new Date()));

        sessionRepoService.deleteByUuid(uuid);
    }

    /**
     * Test sessionRepoService.deleteExpiredSession().
     */
    @Test
    void expiryTest1() {
        Runnable runnable1 = new ShortRunnable();
        Runnable runnable2 = new ShortRunnable();
        Thread thread1 = threadService.execute(runnable1);
        Thread thread2 = threadService.execute(runnable2);
        Assertions.assertNotNull(thread1);
        Assertions.assertNotNull(thread2);
        String uuid1 = java.util.UUID.randomUUID().toString();
        String uuid2 = java.util.UUID.randomUUID().toString();
        String uuid3 = java.util.UUID.randomUUID().toString();

        Session session1 = new Session();
        session1.setUuid(uuid1);
        session1.setTid(thread1.getId());
        sessionRepoService.save(session1);
        Assertions.assertEquals(1, sessionRepoService.count());

        Session session2 = new Session();
        session2.setUuid(uuid2);
        session2.setTid(thread2.getId());
        sessionRepoService.save(session2);
        Assertions.assertEquals(2, sessionRepoService.count());

        /* dummy entry to fulfil deleteExpiredSession() condition */
        Session session3 = new Session();
        session2.setUuid(uuid3);
        session2.setTid(thread2.getId() + 1);
        sessionRepoService.save(session3);
        Assertions.assertEquals(3, sessionRepoService.count());

        /* insert a delay, so runnable1 and runnable2 will complete */
        try { Thread.sleep(constants.THREAD_POOL_TIMEOUT + 100); } catch (Exception e) {}
        Assertions.assertEquals(0, threadService.count());

        Assertions.assertEquals(3, sessionRepoService.count());
        sessionRepoService.deleteExpiredSession();
        Assertions.assertEquals(0, sessionRepoService.count());
    }

    /**
     * Test sessionRepoService.deleteExpiredSession().
     */
    @Test
    void expiryTest2() {
        Runnable runnable1 = new ShortRunnable();
        Runnable runnable2 = new InfiniteRunnable();
        Thread thread1 = threadService.execute(runnable1);
        Thread thread2 = threadService.execute(runnable2);
        Assertions.assertNotNull(thread1);
        Assertions.assertNotNull(thread2);
        String uuid1 = java.util.UUID.randomUUID().toString();
        String uuid2 = java.util.UUID.randomUUID().toString();
        String uuid3 = java.util.UUID.randomUUID().toString();

        Session session1 = new Session();
        session1.setUuid(uuid1);
        session1.setTid(thread1.getId());
        sessionRepoService.save(session1);
        Assertions.assertEquals(1, sessionRepoService.count());

        Session session2 = new Session();
        session2.setUuid(uuid2);
        session2.setTid(thread2.getId());
        sessionRepoService.save(session2);
        Assertions.assertEquals(2, sessionRepoService.count());

        /* dummy entry to fulfil deleteExpiredSession() condition */
        Session session3 = new Session();
        session2.setUuid(uuid3);
        session2.setTid(thread2.getId() + 1);
        sessionRepoService.save(session3);
        Assertions.assertEquals(3, sessionRepoService.count());

        /* insert a delay, so runnable1 will complete */
        try { Thread.sleep(constants.THREAD_POOL_TIMEOUT + 100); } catch (Exception e) {}
        Assertions.assertEquals(1, threadService.count());

        Assertions.assertEquals(3, sessionRepoService.count());
        sessionRepoService.deleteExpiredSession();
        Assertions.assertEquals(1, sessionRepoService.count());
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
                Thread.sleep(constants.THREAD_POOL_TIMEOUT);
            } catch (Exception e) {
            }
        }
    }
}
