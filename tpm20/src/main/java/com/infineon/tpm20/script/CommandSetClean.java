package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultGetRandom;
import com.infineon.tpm20.util.Utility;
import org.springframework.context.ApplicationContext;
import tss.Tpm;
import tss.tpm.*;

public class CommandSetClean extends CommandSet {

    public static String name = "clean";

    public CommandSetClean(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public void run(Tpm tpm) {
        try {

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

            /* EK: 0x81010001 */
            evict(tpm, TPM_HANDLE.persistent(0x00010001));

            /* CommandSetKeyRsa2048CreateAndSign: SRK: 0x81000100 */
            evict(tpm, TPM_HANDLE.persistent(0x00000100));

            /* CommandSetKeyRsa2048CreateAndSign: signing key: 0x81000101 */
            evict(tpm, TPM_HANDLE.persistent(0x00000101));

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }

    private void evict(Tpm tpm, TPM_HANDLE handle) {
        ReadPublicResponse rpResp = tpm._allowErrors().ReadPublic(handle);
        TPM_RC rc = tpm._getLastResponseCode();
        if (rc == TPM_RC.SUCCESS) {
            /* evict the handle */
            tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), handle, handle);
        }
    }
}
