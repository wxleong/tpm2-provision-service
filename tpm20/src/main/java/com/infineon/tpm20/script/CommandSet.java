package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.AbstractResult;
import tss.TpmSyncRunnable;

public abstract class CommandSet implements TpmSyncRunnable.TpmCommandSet {
    private AbstractResult result;

    public AbstractResult getResult() {
        return result;
    }

    public void setResult(AbstractResult result) {
        this.result = result;
    }
}
