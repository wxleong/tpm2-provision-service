package com.infineon.tpm20.util;

import tss.tpm.*;

public class TpmUtil {
    public static void cleanSlots(tss.Tpm tpm, TPM_HT slotType)
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

    public static void evict(tss.Tpm tpm, TPM_HANDLE handle) {
        ReadPublicResponse rpResp = tpm._allowErrors().ReadPublic(handle);
        TPM_RC rc = tpm._getLastResponseCode();
        if (rc == TPM_RC.SUCCESS) {
            /* evict the handle */
            tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), handle, handle);
        }
    }
}
