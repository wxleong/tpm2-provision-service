package com.infineon.tpm20.service;

import com.infineon.tpm20.Constants;
import com.infineon.tpm20.entity.Session;
import com.infineon.tpm20.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class SessionRepoService {

    @Autowired
    private Constants constants;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private ThreadService threadService;

    @Transactional(readOnly = true)
    public Session findByUuid(String uuid) {
        return sessionRepository.findByUuid(uuid);
    }

    @Transactional(readOnly = true)
    public Session findByTid(long tid) {
        return sessionRepository.findByTid(tid);
    }

    @Transactional(readOnly = true)
    public long count() {
        return sessionRepository.count();
    }

    @Transactional
    public void deleteByUuid(String uuid) {
        sessionRepository.deleteByUuid(uuid);
    }

    @Transactional
    public void deleteByTid(long tid) {
        sessionRepository.deleteByTid(tid);
    }

    @Transactional
    public void deleteAll() {
        sessionRepository.deleteAll();
    }

    @Transactional
    public void save(Session session) {
        /* housekeeping */
        deleteExpiredSession();

        Date expiry = new Date(System.currentTimeMillis() + constants.THREAD_POOL_TIMEOUT);
        session.setExpiry(expiry);
        sessionRepository.save(session);
    }

    //@Scheduled(fixedRate = 150000)
    @Transactional
    public void deleteExpiredSession() {
        if (sessionRepository.count() > constants.THREAD_POOL_MAX) {
            List<Session> sessionList = sessionRepository.findAllByExpiryBefore(new Date());
            sessionList.forEach(s -> {
                Runnable runnable = threadService.getRunnable(s.getTid());
                if (runnable == null)
                    sessionRepository.deleteByTid(s.getTid());
            });
        }
    }
}
