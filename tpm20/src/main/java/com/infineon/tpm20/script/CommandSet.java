package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.AbstractResult;

import org.springframework.context.ApplicationContext;
import tss.Tpm;
import tss.TpmSyncRunnable;
import tss.tpm.*;

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

    public void cleanSlots(Tpm tpm, TPM_HT slotType)
    {
        GetCapabilityResponse caps = tpm.GetCapability(TPM_CAP.HANDLES, slotType.toInt() << 24, 8);
        TPML_HANDLE handles = (TPML_HANDLE)caps.capabilityData;

        if (handles.handle.length == 0)
            return;

        for (TPM_HANDLE h : handles.handle)
        {
            tpm.FlushContext(h);
        }
    }
}
