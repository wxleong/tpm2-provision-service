package com.infineon.tpm20.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "session") /* table to use to persist the entity in the database */
@Getter
@Setter
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private Long id; /* primary key */

    @Column(name = "expiry")
    @Temporal(TemporalType.TIMESTAMP)
    Date expiry;

    @Column(name = "uuid", length = 36)
    private String uuid; /* session id */

    @Column(name = "tid")
    private long tid; /* thread id */

    @Column(name = "seq")
    private int seq;

    @Column(name = "result", length = 2048)
    private String result;

    @Column(name = "script")
    private String script;
}
