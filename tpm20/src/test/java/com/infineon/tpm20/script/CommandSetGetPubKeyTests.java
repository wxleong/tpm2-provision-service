package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsGetPubKey;
import com.infineon.tpm20.model.v1.session.ResultGetPubKey;
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
import tss.Tpm;
import tss.tpm.*;

import static com.infineon.tpm20.Constants.SCRIPT_GET_PUBKEY;
import static com.infineon.tpm20.util.TpmUtil.cleanSlots;
import static com.infineon.tpm20.util.TpmUtil.evict;

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
    //@Disabled("Need Windows machine with TPM")
    @Test
    void test1() {
        try {
            SessionManager sessionManager = new SessionManager(webClient, serverPort, sessionRepoService);
            Tpm tpm = new Tpm();
            tpm._setDevice(sessionManager.tpmDeviceTbs);

            /* create RSA key */

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

            TPM_HANDLE keyHandle = new TPM_HANDLE(0x81000111);
            TPMT_PUBLIC rsaTemplate = new TPMT_PUBLIC(TPM_ALG_ID.SHA256,
                    new TPMA_OBJECT(TPMA_OBJECT.sign, TPMA_OBJECT.sensitiveDataOrigin, TPMA_OBJECT.userWithAuth),
                    new byte[0],
                    new TPMS_RSA_PARMS(new TPMT_SYM_DEF_OBJECT(TPM_ALG_ID.NULL,  0, TPM_ALG_ID.NULL),
                            new TPMS_SIG_SCHEME_RSASSA(TPM_ALG_ID.SHA256),  2048, 65537),
                    new TPM2B_PUBLIC_KEY_RSA());

            TPMS_SENSITIVE_CREATE sens = new TPMS_SENSITIVE_CREATE(new byte[0], new byte[0]);

            CreatePrimaryResponse rsaPrimary = tpm.CreatePrimary(TPM_HANDLE.from(TPM_RH.OWNER), sens,
                    rsaTemplate, new byte[0], new TPMS_PCR_SELECTION[0]);

            evict(tpm, keyHandle);
            tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), rsaPrimary.handle, keyHandle);

            /* get pubkey */
            String json = sessionManager.executeScript(SCRIPT_GET_PUBKEY,
                    Utility.objectToJson(new ArgsGetPubKey("0x81000111")));
            ResultGetPubKey resultGetPubKey = Utility.JsonToObject(json, ResultGetPubKey.class);
            Assertions.assertEquals("rsa", resultGetPubKey.getAlgo());
            TPM2B_PUBLIC_KEY_RSA rsa = (TPM2B_PUBLIC_KEY_RSA) rsaPrimary.outPublic.unique;
            Assertions.assertEquals(Utility.byteArrayToBase64(rsa.buffer), resultGetPubKey.getPubKey());

            /* remove RSA key */
            evict(tpm, keyHandle);

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

        } catch (Exception e) { Assertions.assertTrue(false); }
    }
}
