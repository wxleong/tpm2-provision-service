package com.infineon.tpm20.script;

import com.google.common.primitives.Bytes;
import com.infineon.tpm20.model.v1.session.ArgsCreateCsrSha256Rsa2048;
import com.infineon.tpm20.model.v1.session.ResultCreateCsrSha256Rsa2048;
import com.infineon.tpm20.service.CAService;
import com.infineon.tpm20.util.CertUtil;
import com.infineon.tpm20.util.MiscUtil;
import org.springframework.context.ApplicationContext;
import tss.Helpers;
import tss.Tpm;
import tss.Tss;
import tss.tpm.*;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import static com.infineon.tpm20.util.TpmUtil.cleanSlots;

public class CommandSetCreateCsrSha256Rsa2048 extends AbstractCommandSet {

    public static String name = "create-csr-sha256-rsa2048";

    public CAService caService;

    public CommandSetCreateCsrSha256Rsa2048(ApplicationContext applicationContext, String args) {
        super(applicationContext, MiscUtil.JsonToObject(args, ArgsCreateCsrSha256Rsa2048.class));
        caService = getApplicationContext().getBean(CAService.class);
    }

    @Override
    public void run(Tpm tpm) {
        try {
            ArgsCreateCsrSha256Rsa2048 argsCreateKeyRsa2048AndSign = (ArgsCreateCsrSha256Rsa2048) getArgs();
            TPM_HANDLE ekPersistentHandle = new TPM_HANDLE(0x81010001);
            byte[] standardEKPolicy = Helpers.fromHex("837197674484b3f81a90cc8d46a5d724fd52d76e06520b64f2a1da1b331469aa");
            TPMT_PUBLIC ekPub, signPub;

            /* clear loaded session */

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

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

                cleanSlots(tpm, TPM_HT.LOADED_SESSION);
                cleanSlots(tpm, TPM_HT.TRANSIENT);

                ekPub = rsaEk.outPublic;
            } else {
                ekPub = rpResp.outPublic;
            }

            try {
                readAndVerifyEkCert(tpm, ekPub);
            } catch (Exception e) {
                setResult(new ResultCreateCsrSha256Rsa2048(""));
                return;
            }

            /* check signing key persistent handle exist */

            TPM_HANDLE signKeyPersistentHandle;

            if (argsCreateKeyRsa2048AndSign == null || argsCreateKeyRsa2048AndSign.getKeyHandle() == null)
                signKeyPersistentHandle = new TPM_HANDLE(0x81000100);
            else
                signKeyPersistentHandle = new TPM_HANDLE(Long.decode(argsCreateKeyRsa2048AndSign.getKeyHandle()).intValue());

            rpResp = tpm._allowErrors().ReadPublic(signKeyPersistentHandle);
            rc = tpm._getLastResponseCode();
            if (rc != TPM_RC.SUCCESS) {
                setResult(new ResultCreateCsrSha256Rsa2048(""));
                return;
            } else {
                signPub = rpResp.outPublic;
            }

            /* create a secret */

            byte[] challenge = Helpers.RandomBytes(20);

            /* make credential to wrap the secret using software lib */

            Tss.ActivationCredential credential = Tss.createActivationCredential(ekPub,
                    signPub.getName(), challenge);

            /* activate credential to unwrap the secret using TPM */

            byte[] nonceCaller = Helpers.RandomBytes(20);
            StartAuthSessionResponse policySession = tpm.StartAuthSession(TPM_HANDLE.NULL, TPM_HANDLE.NULL,
                    nonceCaller, new byte[0], TPM_SE.POLICY, new TPMT_SYM_DEF(), TPM_ALG_ID.SHA256);
            tpm.PolicySecret(tpm._EndorsementHandle, policySession.handle, new byte[0],
                    new byte[0], new byte[0], 0);
            byte[] policyDigest = tpm.PolicyGetDigest(policySession.handle);
            if (!Arrays.equals(policyDigest, standardEKPolicy)) {
                setResult(new ResultCreateCsrSha256Rsa2048(""));
                return;
            }
            tpm._withSessions(TPM_HANDLE.pwSession(new byte[0]), policySession.handle);
            byte[] recoveredChallenge = tpm.ActivateCredential(signKeyPersistentHandle, ekPersistentHandle,
                    credential.CredentialBlob, credential.Secret);

            /* verify the challenge, this will guarantee the signing key resides in an authentic TPM */

            if (!Arrays.equals(challenge, recoveredChallenge)) {
                setResult(new ResultCreateCsrSha256Rsa2048(""));
                return;
            }

            /* get the CSR content for signing */

            TPM2B_PUBLIC_KEY_RSA rsaPub = (TPM2B_PUBLIC_KEY_RSA) signPub.unique;
            byte[] content = CertUtil.genCsrContent(rsaPub.buffer, null);
            byte[] digest = TPMT_HA.fromHashOf(TPM_ALG_ID.SHA256, content).digest;

            /* sign CSR content */

            TPMU_SIGNATURE signature = tpm.Sign(signKeyPersistentHandle,
                    digest,
                    new TPMS_SIG_SCHEME_RSASSA(TPM_ALG_ID.SHA256),
                    new TPMT_TK_HASHCHECK());

            TPMS_SIGNATURE_RSASSA rsaSig = (TPMS_SIGNATURE_RSASSA) signature;

            /* build the CSR */

            byte[] csr = CertUtil.genCsrContent(rsaPub.buffer, rsaSig.sig);
            if (csr == null)
                setResult(new ResultCreateCsrSha256Rsa2048(""));

            /* set result */

            setResult(new ResultCreateCsrSha256Rsa2048(MiscUtil.byteArrayToBase64(csr)));

        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }

    public void readAndVerifyEkCert(Tpm tpm, TPMT_PUBLIC ekPub) throws Exception {

        byte[] baEkCert = new byte[0];

        /* read TPM2_PT_NV_BUFFER_MAX */

        GetCapabilityResponse cap = tpm.GetCapability(TPM_CAP.TPM_PROPERTIES, TPM_PT.NV_BUFFER_MAX.toInt(), 1);
        TPML_TAGGED_TPM_PROPERTY prop = (TPML_TAGGED_TPM_PROPERTY) (cap.capabilityData);
        int nv_buffer_max = prop.tpmProperty[0].value;

        /* read EK (RSA2048) certificate */

        TPM_HANDLE nvHandle = TPM_HANDLE.NV(0xC00002); // 0x1C00002
        NV_ReadPublicResponse nvPub = tpm.NV_ReadPublic(nvHandle);
        int dataSize = nvPub.nvPublic.dataSize;
        int offset = 0;
        do {
            int size = (dataSize <= nv_buffer_max) ? dataSize : nv_buffer_max;
            byte[] temp = tpm._allowErrors().NV_Read(nvHandle, nvHandle, size, offset);
            baEkCert = Bytes.concat(baEkCert, temp);
            dataSize -= size;
            offset += size;
        } while (dataSize > 0);

        /* verify the certificate */

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate ekCert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(baEkCert));
        caService.verify(ekCert);

        /* verify certificate pub == ekPub */

        TPM2B_PUBLIC_KEY_RSA ekRsaPub = (TPM2B_PUBLIC_KEY_RSA) ekPub.unique;
        byte[] baEkRsaPub = ekRsaPub.buffer;

        RSAPublicKey rsaPublicKey = (RSAPublicKey) ekCert.getPublicKey();
        byte[] baCertPub = rsaPublicKey.getModulus().toByteArray();
        byte[] baCertPub2 = baCertPub;
        if (baCertPub[0] == 0)
            baCertPub2 = Arrays.copyOfRange(baCertPub, 1, baCertPub.length);

        if (!Arrays.equals(baCertPub2, baEkRsaPub))
            throw new Exception("EK certificate public key is not equal to EK public key.");
    }
}
