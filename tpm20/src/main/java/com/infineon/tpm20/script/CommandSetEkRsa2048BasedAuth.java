package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultCreateEk;
import com.infineon.tpm20.model.v1.session.ResultEkBasedAuth;
import com.infineon.tpm20.service.CAService;
import org.springframework.beans.factory.annotation.Autowired;
import tss.Helpers;
import tss.Tpm;
import tss.Tss;
import tss.tpm.*;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public class CommandSetEkRsa2048BasedAuth extends CommandSet {

    @Autowired
    private CAService caService;

    public static String name = "ek-rsa2048-based-auth";

    @Override
    public void run(Tpm tpm) {
        try {
            TPM_HANDLE ekPersistentHandle = TPM_HANDLE.persistent(0x00010001); // 0x81010001
            byte[] standardEKPolicy = Helpers.fromHex("837197674484b3f81a90cc8d46a5d724fd52d76e06520b64f2a1da1b331469aa");
            TPMT_PUBLIC ekPub;
            byte[] ekPubName;

            /* check EK persistent handle exist */

            ReadPublicResponse rpResp = tpm._allowErrors().ReadPublic(ekPersistentHandle);
            TPM_RC rc = tpm._getLastResponseCode();
            if (rc != TPM_RC.SUCCESS) {
                /* create the EK */

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

                ekPub = rsaEk.outPublic;
                ekPubName = rsaEk.name;
            } else {
                ekPub = rpResp.outPublic;
                ekPubName = rpResp.name;
            }

            /* read EK (RSA2048) certificate */

            TPM_HANDLE nvHandle = TPM_HANDLE.NV(0x1c00002);
            NV_ReadPublicResponse nvPub = tpm.NV_ReadPublic(nvHandle);
            byte[] baEkCert = tpm.NV_Read(nvHandle, nvHandle,  nvPub.nvPublic.dataSize,  0);

            /* verify the certificate */

            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                X509Certificate ekCert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(baEkCert));
                caService.verify(ekCert);
            } catch (Exception e) {
                setResult(new ResultEkBasedAuth(false));
                return;
            }

            /* create a secret */

            byte[] challenge = Helpers.RandomBytes(20);

            /* make credential to wrap the secret using software lib */

            Tss.ActivationCredential credential = Tss.createActivationCredential(ekPub,
                    ekPubName, challenge);

            /* activate credential to unwrap the secret using TPM */

            byte[] nonceCaller = Helpers.RandomBytes(20);
            StartAuthSessionResponse policySession = tpm.StartAuthSession(TPM_HANDLE.NULL, TPM_HANDLE.NULL,
                    nonceCaller, new byte[0], TPM_SE.POLICY, new TPMT_SYM_DEF(), TPM_ALG_ID.SHA256);
            tpm.PolicySecret(tpm._EndorsementHandle, policySession.handle, new byte[0],
                    new byte[0], new byte[0], 0);
            byte[] policyDigest = tpm.PolicyGetDigest(policySession.handle);
            if (!Arrays.equals(policyDigest, standardEKPolicy)) {
                setResult(new ResultEkBasedAuth(false));
                return;
            }
            tpm._withSessions(TPM_HANDLE.pwSession(new byte[0]), policySession.handle);
            byte[] recoveredChallenge = tpm.ActivateCredential(ekPersistentHandle, ekPersistentHandle,
                    credential.CredentialBlob, credential.Secret);

            /* verify the challenge */
            if (Arrays.equals(challenge, recoveredChallenge))
                setResult(new ResultEkBasedAuth(true));
            else
                setResult(new ResultEkBasedAuth(false));

        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }
}
