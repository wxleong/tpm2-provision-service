package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.AbstractResult;

import org.springframework.context.ApplicationContext;
import tss.TpmSyncRunnable;

public abstract class CommandSet implements TpmSyncRunnable.TpmCommandSet {
    private AbstractResult result;
    private ApplicationContext applicationContext;

    public CommandSet(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public AbstractResult getResult() {
        return result;
    }
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setResult(AbstractResult result) {
        this.result = result;
    }
}
