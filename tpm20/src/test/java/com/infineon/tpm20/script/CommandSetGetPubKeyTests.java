package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.*;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.Utility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.infineon.tpm20.Constants.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetGetPubKeyTests {

    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;

    /**
     * Test the complete sequence: Start -> Xfer -> Stop
     */
    @Disabled("Need Windows machine with TPM and administrator access (TPM2_EvictControl)")
    @Test
    void test1() {
        try {
            SessionManager sessionManager = new SessionManager(webClient, serverPort, sessionRepoService);

            /* create RSA key */
            String json = sessionManager.executeScript(SCRIPT_KEY_RSA2048_CREATE_AND_SIGN,
                    Utility.objectToJson(new ArgsSigning("0x81000111","","","")));
            ResultRsaSigning resultRsaSigning = Utility.JsonToObject(json, ResultRsaSigning.class);
            Assertions.assertNotEquals("", resultRsaSigning.getPub());

            /* get pubkey */
            json = sessionManager.executeScript(SCRIPT_GET_PUBKEY,
                    Utility.objectToJson(new ArgsGetPubKey("0x81000111")));
            ResultGetPubKey resultGetPubKey = Utility.JsonToObject(json, ResultGetPubKey.class);
            Assertions.assertEquals("rsa", resultGetPubKey.getAlgo());
            Assertions.assertNotEquals("", resultGetPubKey.getPubKey());

            /* remove RSA key */
            String[] handles = new String[]{"0x81000111"};
            json = sessionManager.executeScript(SCRIPT_CLEAN,
                    Utility.objectToJson(new ArgsClean(handles)));
            Assertions.assertEquals("", json);

        } catch (Exception e) { Assertions.assertTrue(false); }
    }
}
