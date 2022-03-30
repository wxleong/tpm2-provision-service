package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultGetRandom;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.Utility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.infineon.tpm20.Constants.SCRIPT_CLEAN;
import static com.infineon.tpm20.Constants.SCRIPT_GET_RANDOM;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetCleanTests {

    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;

    /**
     * Test the complete sequence: Start -> Xfer -> Stop
     */
    //@Disabled("Need Windows machine with TPM")
    @Test
    void test1() {
        SessionManager sessionManager = new SessionManager(webClient, serverPort, sessionRepoService);
        String json = sessionManager.executeScript(SCRIPT_CLEAN);
        Assertions.assertEquals("", json);
    }
}