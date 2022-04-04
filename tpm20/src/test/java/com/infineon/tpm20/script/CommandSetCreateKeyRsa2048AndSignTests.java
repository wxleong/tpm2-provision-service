package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsCreateKeyRsa2048AndSign;
import com.infineon.tpm20.model.v1.session.ResultCreateKeyRsa2048AndSign;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.MiscUtil;
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
import tss.tpm.TPMT_PUBLIC;
import tss.tpm.TPM_HANDLE;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetCreateKeyRsa2048AndSignTests {

    @Autowired
    private ApplicationContext applicationContext;
    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;

    /**
     * The test is conducted directly on CommandSetKeyRsa2048CreateAndSign.class instead of using Session
     * endpoints to make method mocking possible.
     */
    //@Disabled("Need Windows machine with TPM and administrator access (TPM2_ActivateCredential)")
    @Test
    void test1() {
        TPM_HANDLE keyHandle = new TPM_HANDLE(0x81000100);
        byte[] data = new byte[]{0, 1, 2, 3, 4};

        TpmTools tpmTools = new TpmTools();
        Tpm tpm = tpmTools.tpm;

        try {
            /*
               "spy" on script CommandSetKeyRsa2048CreateAndSign.class.

               - Mockito.mock(): all methods are mocked, unless specify.
                 By default, for all methods that return a value, a mock will return either null,
                 a primitive/primitive wrapper value, or an empty collection, as appropriate
               - Mockito.spy(): all methods are original (CallRealMethod), unless specify.
             */
            CommandSetCreateKeyRsa2048AndSign commandSetCreateKeyRsa2048AndSignOrig = new CommandSetCreateKeyRsa2048AndSign(applicationContext,
                    MiscUtil.objectToJson(new ArgsCreateKeyRsa2048AndSign(MiscUtil.toHexString(keyHandle.handle),
                            "pkcs", MiscUtil.byteArrayToBase64(data), "")));
            CommandSetCreateKeyRsa2048AndSign commandSetCreateKeyRsa2048AndSign = Mockito.spy(commandSetCreateKeyRsa2048AndSignOrig);

            /*
               avoid verifying platform EK certificate
               mock the EK certificate verification method to "doNothing"
             */
            Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetCreateKeyRsa2048AndSign).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

            /* execute the script */
            commandSetCreateKeyRsa2048AndSign.run(tpm);

            /* verify mocked method is called only once */
            Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetCreateKeyRsa2048AndSign, Mockito.times(1))
                    .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

            /* verify the execution is successful */
            ResultCreateKeyRsa2048AndSign resultCreateKeyRsa2048AndSign = (ResultCreateKeyRsa2048AndSign) commandSetCreateKeyRsa2048AndSign.getResult();
            byte[] pub = MiscUtil.base64ToByteArray(resultCreateKeyRsa2048AndSign.getPub());
            byte[] sig = MiscUtil.base64ToByteArray(resultCreateKeyRsa2048AndSign.getSig());

            Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultCreateKeyRsa2048AndSign.getEkPub()).length);
            Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultCreateKeyRsa2048AndSign.getPub()).length);
            Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultCreateKeyRsa2048AndSign.getSig()).length);
            Assertions.assertEquals("0x81000100", resultCreateKeyRsa2048AndSign.getKeyHandle());

            /* verify signature */

            PublicKey publicKey = null;
            publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(new BigInteger(1, pub), BigInteger.valueOf(65537)));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data);
            Assertions.assertTrue(signature.verify(sig));
        } catch (Exception e) {
            Assertions.assertTrue(false);
        } finally {
            tpmTools.evict(keyHandle);
            tpmTools.clean();
        }
    }
}
