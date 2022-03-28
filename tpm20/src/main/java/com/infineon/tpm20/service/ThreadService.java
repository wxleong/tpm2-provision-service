package com.infineon.tpm20.service;

import com.infineon.tpm20.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Optional;

@Service
public class ThreadService {

    @Autowired
    private Constants constants;

    public ThreadGroup threadGroup;
    public int threadMax;

    public ThreadService() {}

    @PostConstruct
    public void TPMThreadService() {
        threadGroup = new ThreadGroup("ThreadService");
        threadMax = constants.THREAD_POOL_MAX;
    }

    public Thread execute(Runnable runnable) {
        if (threadGroup.activeCount() >= threadMax) {
            return null;
        }

        ScriptThread thread = new ScriptThread(threadGroup, runnable);
        thread.start();
        return thread;
    }

    public int count() {
        return threadGroup.activeCount();
    }

    public Thread getThread(long id) {
        Thread threads[] = new Thread[threadGroup.activeCount()];
        threadGroup.enumerate(threads);
        Optional<Thread> thread = Arrays.stream(threads)
                .filter(t -> t.getId() == id)
                .findAny();
        if (thread.isEmpty())
            return null;
        return thread.get();
    }

    public Runnable getRunnable(long id) {
        ScriptThread scriptThread = (ScriptThread) getThread(id);
        if (scriptThread == null)
            return null;
        return scriptThread.runnable;
    }
    
    public static Runnable getRunnable(Thread thread) {
        ScriptThread scriptThread = (ScriptThread) thread;
        return scriptThread.runnable;
    }

    private class ScriptThread extends Thread {
        public Runnable runnable;

        public ScriptThread(ThreadGroup threadGroup, Runnable runnable) {
            super(threadGroup, runnable);
            this.runnable = runnable;
        }
    }
}
