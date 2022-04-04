package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsCreateCsrSha256Rsa2048;
import com.infineon.tpm20.model.v1.session.ResultCreateCsrSha256Rsa2048;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.MiscUtil;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import tss.Tpm;
import tss.tpm.*;

import java.io.StringWriter;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetCreateCsrSha256Rsa2048Tests {

    @Autowired
    private ApplicationContext applicationContext;
    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;

    /**
     * The test is conducted directly on CommandSetCreateCsrSha256Rsa2048.class instead of using Session
     * endpoints to make method mocking possible.
     */
    //@Disabled("Need Windows machine with TPM and administrator access (TPM2_ActivateCredential)")
    @Test
    void test1() {
        TPM_HANDLE keyHandle = new TPM_HANDLE(0x81000111);
        TpmTools tpmTools = new TpmTools();
        Tpm tpm = tpmTools.tpm;

        try {
            tpmTools.clean();

            /* create RSA key */

            TPMT_PUBLIC rsaTemplate = new TPMT_PUBLIC(TPM_ALG_ID.SHA256,
                    new TPMA_OBJECT(TPMA_OBJECT.sign, TPMA_OBJECT.sensitiveDataOrigin, TPMA_OBJECT.userWithAuth),
                    new byte[0],
                    new TPMS_RSA_PARMS(new TPMT_SYM_DEF_OBJECT(TPM_ALG_ID.NULL,  0, TPM_ALG_ID.NULL),
                            new TPMS_SIG_SCHEME_RSASSA(TPM_ALG_ID.SHA256),  2048, 65537),
                    new TPM2B_PUBLIC_KEY_RSA());

            TPMS_SENSITIVE_CREATE sens = new TPMS_SENSITIVE_CREATE(new byte[0], new byte[0]);

            CreatePrimaryResponse rsaPrimary = tpm.CreatePrimary(TPM_HANDLE.from(TPM_RH.OWNER), sens,
                    rsaTemplate, new byte[0], new TPMS_PCR_SELECTION[0]);

            tpmTools.evict(keyHandle);
            tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), rsaPrimary.handle, keyHandle);

            /* "spy" on script CommandSetCreateCsrSha256Rsa2048.class */
            CommandSetCreateCsrSha256Rsa2048 commandSetCreateCsrSha256Rsa2048Orig = new CommandSetCreateCsrSha256Rsa2048(applicationContext,
                    MiscUtil.objectToJson(new ArgsCreateCsrSha256Rsa2048(MiscUtil.toHexString(keyHandle.handle),
                            "CN=TPM, O=TPM, OU=TPM, C=SG, L=Singapore")));
            CommandSetCreateCsrSha256Rsa2048 commandSetCreateCsrSha256Rsa2048 = Mockito.spy(commandSetCreateCsrSha256Rsa2048Orig);

            /*
               avoid verifying platform EK certificate
               mock the EK certificate verification method to "doNothing"
             */
            Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetCreateCsrSha256Rsa2048).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

            /* execute the script */
            commandSetCreateCsrSha256Rsa2048.run(tpm);

            /* verify mocked method is called only once */
            Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetCreateCsrSha256Rsa2048, Mockito.times(1))
                    .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

            /* verify the execution is successful */
            ResultCreateCsrSha256Rsa2048 resultCreateCsrSha256Rsa2048 = (ResultCreateCsrSha256Rsa2048) commandSetCreateCsrSha256Rsa2048.getResult();
            byte[] csr = MiscUtil.base64ToByteArray(resultCreateCsrSha256Rsa2048.getCsr());
            Assertions.assertNotEquals(0, csr.length);

            /* print the PEM encoded CSR */

            PemObject pemObject = new PemObject("CERTIFICATE REQUEST", csr);
            StringWriter stringWriter = new StringWriter();
            JcaPEMWriter jcaPEMWriter = new JcaPEMWriter (stringWriter);
            jcaPEMWriter.writeObject(pemObject);
            jcaPEMWriter.close();
            stringWriter.close();
            System.out.println(stringWriter);
            Assertions.assertTrue(true);

        } catch (Exception e) {
            Assertions.assertTrue(false);
        } finally {
            tpmTools.evict(keyHandle);
            tpmTools.clean();
        }
    }
}
