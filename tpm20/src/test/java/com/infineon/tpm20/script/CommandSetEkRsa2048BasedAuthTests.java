package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultCreateEk;
import com.infineon.tpm20.model.v1.session.ResultEkBasedAuth;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.Utility;
import com.netflix.discovery.converters.Auto;
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

import static com.infineon.tpm20.Constants.SCRIPT_CREATE_EK_RSA2048;
import static com.infineon.tpm20.Constants.SCRIPT_EK_RSA2048_BASED_AUTHENTICATION;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetEkRsa2048BasedAuthTests {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * The test is conducted directly on CommandSetEkRsa2048BasedAuth.class instead of using Session
     * endpoints to make method mocking possible.
     */
    @Disabled("Need Windows machine with TPM and administrator access (TPM2_ActivateCredential)")
    @Test
    void test1() {

        /*
           "spy" on CommandSetEkRsa2048BasedAuth.class so nothing is "mocked" yet
           - Mockito.mock(): all methods are mocked, unless specify.
             By default, for all methods that return a value, a mock will return either null,
             a primitive/primitive wrapper value, or an empty collection, as appropriate
           - Mockito.spy(): all methods are original (CallRealMethod), unless specify.
         */
        CommandSetEkRsa2048BasedAuth commandSetEkRsa2048BasedAuthOrig = new CommandSetEkRsa2048BasedAuth(applicationContext, null);
        CommandSetEkRsa2048BasedAuth commandSetEkRsa2048BasedAuth = Mockito.spy(commandSetEkRsa2048BasedAuthOrig);

        /*
           avoid verification of platform EK certificate
           mock the EK certificate verification method to "doNothing"
         */
        Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetEkRsa2048BasedAuth).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

        /* initialize platform TPM */
        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());
        Tpm tpm = new Tpm();
        tpm._setDevice(tpmDeviceTbs);

        /* execute the program */
        commandSetEkRsa2048BasedAuth.run(tpm);

        /* verify mocked method is called only once */
        Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetEkRsa2048BasedAuth, Mockito.times(1))
                .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

        /* verify the execution is successful */
        ResultEkBasedAuth resultEkBasedAuth = (ResultEkBasedAuth) commandSetEkRsa2048BasedAuth.getResult();
        Assertions.assertTrue(resultEkBasedAuth.getPassed());
        Assertions.assertEquals(256, Utility.base64ToByteArray(resultEkBasedAuth.getEkPub()).length);
    }
}
