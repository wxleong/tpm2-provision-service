package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultEkBasedAuth;
import com.infineon.tpm20.model.v1.session.ResultRsaSigning;
import com.infineon.tpm20.util.Utility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tss.Tpm;
import tss.TpmDeviceTbs;
import tss.tpm.TPMT_PUBLIC;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetKeyRsa2048CreateAndSignTests {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * The test is conducted directly on CommandSetKeyRsa2048CreateAndSign.class instead of using Session
     * endpoints to make method mocking possible.
     */
    @Disabled("Need Windows machine with TPM and administrator access (TPM2_ActivateCredential)")
    @Test
    void test1() {

        /*
           "spy" on CommandSetKeyRsa2048CreateAndSign.class so nothing is "mocked" yet
           - Mockito.mock(): all methods are mocked, unless specify.
             By default, for all methods that return a value, a mock will return either null,
             a primitive/primitive wrapper value, or an empty collection, as appropriate
           - Mockito.spy(): all methods are original (CallRealMethod), unless specify.
         */
        CommandSetKeyRsa2048CreateAndSign commandSetKeyRsa2048CreateAndSignOrig = new CommandSetKeyRsa2048CreateAndSign(applicationContext);
        CommandSetKeyRsa2048CreateAndSign commandSetKeyRsa2048CreateAndSign = Mockito.spy(commandSetKeyRsa2048CreateAndSignOrig);

        /*
           avoid verification of platform EK certificate
           mock the EK certificate verification method to "doNothing"
         */
        Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetKeyRsa2048CreateAndSign).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

        /* initialize platform TPM */
        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());
        Tpm tpm = new Tpm();
        tpm._setDevice(tpmDeviceTbs);

        /* execute the program */
        commandSetKeyRsa2048CreateAndSign.run(tpm);

        /* verify mocked method is called only once */
        Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetKeyRsa2048CreateAndSign, Mockito.times(1))
                .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

        /* verify the execution is successful */
        ResultRsaSigning resultRsaSigning = (ResultRsaSigning) commandSetKeyRsa2048CreateAndSign.getResult();
        Assertions.assertEquals(256, Utility.base64ToByteArray(resultRsaSigning.getEkPub()).length);
        Assertions.assertEquals(256, Utility.base64ToByteArray(resultRsaSigning.getPub()).length);
    }
}
