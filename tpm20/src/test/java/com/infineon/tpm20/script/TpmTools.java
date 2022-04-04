package com.infineon.tpm20.script;

import com.infineon.tpm20.util.MiscUtil;
import com.infineon.tpm20.util.TpmUtil;
import org.junit.jupiter.api.Assertions;
import tss.Tpm;
import tss.TpmDeviceTbs;
import tss.tpm.TPM_HANDLE;
import tss.tpm.TPM_HT;

import static com.infineon.tpm20.util.TpmUtil.cleanSlots;

public class TpmTools {
    public Tpm tpm;

    public TpmTools() {
        /* initialize platform TPM */
        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());
        tpm = new Tpm();
        tpm._setDevice(tpmDeviceTbs);
    }

    public void clean() {
        cleanSlots(tpm, TPM_HT.LOADED_SESSION);
        cleanSlots(tpm, TPM_HT.TRANSIENT);
    }

    public void evict(TPM_HANDLE keyHandle) {
        TpmUtil.evict(this.tpm, keyHandle);
    }

    public void evict(int keyHandle) {
        TpmUtil.evict(this.tpm, new TPM_HANDLE(keyHandle));
    }

    public void evict(String keyHandle) {
        TpmUtil.evict(this.tpm, new TPM_HANDLE(MiscUtil.hexStringToInt(keyHandle)));
    }
}
