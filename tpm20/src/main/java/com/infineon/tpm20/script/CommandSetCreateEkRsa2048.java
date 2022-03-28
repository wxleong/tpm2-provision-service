package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultCreateEk;
import tss.Helpers;
import tss.Tpm;
import tss.tpm.*;

public class CommandSetCreateEkRsa2048 extends CommandSet {

    public static String name = "create-ek-rsa2048";

    @Override
    public void run(Tpm tpm) {
        try {
            TPM_HANDLE ekPersistentHandle = TPM_HANDLE.persistent(0x00010001); // 0x81010001

            /* check EK persistent handle exist */

            ReadPublicResponse rpResp = tpm._allowErrors().ReadPublic(ekPersistentHandle);
            TPM_RC rc = tpm._getLastResponseCode();
            if (rc == TPM_RC.SUCCESS) {
                /* evict the handle */
                tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), ekPersistentHandle, ekPersistentHandle);
            }

            /* create the EK */

            byte[] standardEKPolicy = Helpers.fromHex("837197674484b3f81a90cc8d46a5d724fd52d76e06520b64f2a1da1b331469aa");

            TPMT_PUBLIC rsaEkTemplate = new TPMT_PUBLIC(TPM_ALG_ID.SHA256,
                    new TPMA_OBJECT(TPMA_OBJECT.fixedTPM, TPMA_OBJECT.fixedParent, TPMA_OBJECT.sensitiveDataOrigin,
                            TPMA_OBJECT.adminWithPolicy, TPMA_OBJECT.restricted, TPMA_OBJECT.decrypt),
                    standardEKPolicy,
                    new TPMS_RSA_PARMS(new TPMT_SYM_DEF_OBJECT(TPM_ALG_ID.AES,  128, TPM_ALG_ID.CFB),
                            new TPMS_NULL_ASYM_SCHEME(),  2048, 0),
                    new TPM2B_PUBLIC_KEY_RSA(new byte[256]));

            CreatePrimaryResponse rsaEk = tpm.CreatePrimary(TPM_HANDLE.from(TPM_RH.ENDORSEMENT),
                    new TPMS_SENSITIVE_CREATE(), rsaEkTemplate, new byte[0], new TPMS_PCR_SELECTION[0]);

            /* persist the EK */

            tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), rsaEk.handle, ekPersistentHandle);

            /* set result */

            setResult(new ResultCreateEk("0x" + Integer.toHexString(ekPersistentHandle.handle)));

        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }
}
