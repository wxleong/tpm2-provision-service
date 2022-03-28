package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.AbstractResult;
import tss.TpmSyncRunnable;

public class CommandSetRunnable extends TpmSyncRunnable  {

    private CommandSet commandSet;

    public CommandSetRunnable(CommandSet commandSet, int timeout) {
        super(commandSet, timeout);
        this.commandSet = commandSet;
    }

    public AbstractResult getResult() {
        return commandSet.getResult();
    }
}
