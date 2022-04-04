package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsCreateKeyRsa2048;
import com.infineon.tpm20.model.v1.session.ResultCreateKeyRsa2048;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.MiscUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
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
import tss.TpmDeviceTbs;
import tss.tpm.TPMT_PUBLIC;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetKeyRsa2048CreateTests {

    @Autowired
    private ApplicationContext applicationContext;
    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;

    /**
     * The test is conducted directly on CommandSetKeyRsa2048Create.class instead of using Session
     * endpoints to make method mocking possible.
     */
    @Disabled("Need Windows machine with TPM and administrator access (TPM2_ActivateCredential)")
    @Test
    void test1() {

        String keyHandle = "0x81000100";

        /*
           "spy" on script CommandSetKeyRsa2048CreateAndSign.class.

           - Mockito.mock(): all methods are mocked, unless specify.
             By default, for all methods that return a value, a mock will return either null,
             a primitive/primitive wrapper value, or an empty collection, as appropriate
           - Mockito.spy(): all methods are original (CallRealMethod), unless specify.
         */
        CommandSetCreateKeyRsa2048 commandSetCreateKeyRsa2048Orig = new CommandSetCreateKeyRsa2048(applicationContext,
                MiscUtil.objectToJson(new ArgsCreateKeyRsa2048(keyHandle)));
        CommandSetCreateKeyRsa2048 commandSetCreateKeyRsa2048 = Mockito.spy(commandSetCreateKeyRsa2048Orig);

        /*
           avoid verifying platform EK certificate
           mock the EK certificate verification method to "doNothing"
         */
        Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetCreateKeyRsa2048).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

        /* initialize platform TPM */
        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());
        Tpm tpm = new Tpm();
        tpm._setDevice(tpmDeviceTbs);

        /* execute the script */
        commandSetCreateKeyRsa2048.run(tpm);

        /* verify mocked method is called only once */
        Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetCreateKeyRsa2048, Mockito.times(1))
                .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

        /* verify the execution is successful */
        ResultCreateKeyRsa2048 resultCreateKeyRsa2048 = (ResultCreateKeyRsa2048) commandSetCreateKeyRsa2048.getResult();
        Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultCreateKeyRsa2048.getEkPub()).length);
        Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultCreateKeyRsa2048.getPub()).length);
        Assertions.assertEquals(keyHandle, resultCreateKeyRsa2048.getKeyHandle());
    }
}
