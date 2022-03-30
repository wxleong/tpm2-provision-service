package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.IResult;
import tss.TpmSyncRunnable;

public class CommandSetRunnable extends TpmSyncRunnable  {

    private AbstractCommandSet abstractCommandSet;

    public CommandSetRunnable(AbstractCommandSet abstractCommandSet, int timeout) {
        super(abstractCommandSet, timeout);
        this.abstractCommandSet = abstractCommandSet;
    }

    public IResult getResult() {
        return abstractCommandSet.getResult();
    }
}
