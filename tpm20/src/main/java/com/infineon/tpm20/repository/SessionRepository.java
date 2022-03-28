package com.infineon.tpm20.repository;

import com.infineon.tpm20.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * JpaRepository already comes with default queries (e.g. findById, findAll, count, exists, ...)
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    Session findByUuid(String uuid);
    Session findByTid(long tid);
    List<Session> findAllByExpiryBefore(Date date);
    void deleteByUuid(String uuid);
    void deleteByTid(long tid);
}
