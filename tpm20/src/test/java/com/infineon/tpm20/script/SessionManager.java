package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.*;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.Utility;
import org.junit.jupiter.api.Assertions;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tss.TpmDeviceTbs;

import static com.infineon.tpm20.Constants.*;

public class SessionManager {

    public String uuid;
    public WebTestClient webClient;
    public int serverPort;
    public SessionRepoService sessionRepoService;
    public TpmDeviceTbs tpmDeviceTbs;

    public SessionManager(WebTestClient webClient, int serverPort,
                          SessionRepoService sessionRepoService) {
        this.webClient = webClient;
        this.serverPort = serverPort;
        this.sessionRepoService = sessionRepoService;

        tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());
    }

    public String executeScript(String script, String args) {

        int seq;
        byte[] baCmd, baResp;
        String b64Cmd, b64Resp;

        Assertions.assertEquals(sessionRepoService.count(), 0);

        /* send Session Start to receive the first TPM command */
        EntityExchangeResult<StartResponse> startResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest(script, args)), StartRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StartResponse.class)
                .returnResult();
        Assertions.assertTrue(startResponse.getResponseBody().getCmd().length() > 0);
        Assertions.assertEquals(startResponse.getResponseBody().getSeq(), 0);
        Assertions.assertEquals(startResponse.getResponseBody().getEmsg(), "");
        Assertions.assertNotEquals(startResponse.getResponseBody().getUuid(), "");
        Assertions.assertFalse(startResponse.getResponseBody().isEnded());
        uuid = startResponse.getResponseBody().getUuid();
        seq = startResponse.getResponseBody().getSeq();
        baCmd = Utility.base64ToByteArray(startResponse.getResponseBody().getCmd());

        Assertions.assertEquals(sessionRepoService.count(), 1);

        /* exchange command-response pair */
        while(true) {
            /* feed the command to Windows' TPM to obtain a response */
            Assertions.assertNotNull(baCmd);
            tpmDeviceTbs.dispatchCommand(baCmd);
            while(!tpmDeviceTbs.responseReady())
                try { Thread.sleep(100); } catch (Exception e) {}
            baResp = tpmDeviceTbs.getResponse();
            Assertions.assertNotNull(baResp);

            /* send the response back to server */
            b64Resp = Utility.byteArrayToBase64(baResp);
            EntityExchangeResult<XferResponse> xferResponse = webClient
                    .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Mono.just(new XferRequest(uuid, ++seq, b64Resp)), XferRequest.class)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody(XferResponse.class)
                    .returnResult();
            Assertions.assertEquals(seq + 1, xferResponse.getResponseBody().getSeq());
            seq++;
            Assertions.assertEquals(uuid, xferResponse.getResponseBody().getUuid());
            Assertions.assertEquals("", xferResponse.getResponseBody().getEmsg());
            if (xferResponse.getResponseBody().isEnded()) {
                Assertions.assertEquals(xferResponse.getResponseBody().getCmd(), "");
                break;
            } else {
                Assertions.assertTrue(xferResponse.getResponseBody().getCmd().length() > 0);
                baCmd = Utility.base64ToByteArray(xferResponse.getResponseBody().getCmd());
            }
        }

        /* send Session Stop to retrieve result from server */
        EntityExchangeResult<StopResponse> stopResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_STOP)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StopRequest(uuid)), StopRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StopResponse.class)
                .returnResult();
        Assertions.assertEquals("", stopResponse.getResponseBody().getEmsg());
        Assertions.assertEquals(script, stopResponse.getResponseBody().getScript());
        Assertions.assertNotNull(stopResponse.getResponseBody().getResult());
        String json = stopResponse.getResponseBody().getResult();

        return json;
    }
}
