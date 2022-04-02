package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.IArgs;
import com.infineon.tpm20.model.v1.session.IResult;

import org.springframework.context.ApplicationContext;
import tss.Tpm;
import tss.TpmSyncRunnable;
import tss.tpm.*;

public abstract class AbstractCommandSet implements TpmSyncRunnable.TpmCommandSet {
    private IResult result;
    private IArgs args;
    private ApplicationContext applicationContext;

    public AbstractCommandSet(ApplicationContext applicationContext, IArgs args) {
        this.applicationContext = applicationContext;
        this.args = args;
    }

    public IResult getResult() {
        return result;
    }
    public IArgs getArgs() {
        return args;
    }
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setResult(IResult result) {
        this.result = result;
    }
}
