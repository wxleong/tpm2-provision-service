package com.infineon.tpm20.controller;

import com.infineon.tpm20.Constants;
import com.infineon.tpm20.entity.Session;
import com.infineon.tpm20.model.v1.scripts.ScriptsResponse;
import com.infineon.tpm20.model.v1.session.*;
import com.infineon.tpm20.script.SessionManager;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.service.ThreadService;
import com.infineon.tpm20.util.MiscUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tss.TpmDeviceTbs;

import java.util.Random;

import static com.infineon.tpm20.Constants.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class RestApiControllerTests {

    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;
    @Autowired
    private ThreadService threadService;
    @Autowired
    private Constants constants;

    @Test
    void processApiV1Ping() {
        webClient
                .get().uri("http://localhost:" + serverPort + URL_API_V1_PING)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ping").isEqualTo("pong");
    }

    @Test
    void processApiV1Scripts() {
        EntityExchangeResult<ScriptsResponse> result = webClient
                .get().uri("http://localhost:" + serverPort + URL_API_V1_SCRIPTS)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ScriptsResponse.class)
                .returnResult();

        String scripts[] = result.getResponseBody().getScripts();

        Assertions.assertEquals(constants.scripts.size(), result.getResponseBody().getScripts().length);

        for (String script : scripts) {
            Assertions.assertNotNull(constants.scripts.get(script));
        }
    }

    /**
     * Test invalid script
     */
    @Test
    void processApiV1SessionStart1() {
        EntityExchangeResult<StartResponse> result = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest("invalid-script", null)), StartRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StartResponse.class)
                .returnResult();
        Assertions.assertEquals(result.getResponseBody().getCmd(), "");
        Assertions.assertEquals(result.getResponseBody().getSeq(), 0);
        Assertions.assertEquals(result.getResponseBody().getEmsg(), "Provisioning script not found.");
        Assertions.assertEquals(result.getResponseBody().getUuid(), "");
        Assertions.assertTrue(result.getResponseBody().isEnded());
    }

    /**
     * Test get-random script
     */
    @Test
    void processApiV1SessionStart2() {
        Assertions.assertEquals(sessionRepoService.count(), 0);

        EntityExchangeResult<StartResponse> result = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest(SCRIPT_GET_RANDOM,
                                                 MiscUtil.objectToJson(new ArgsGetRandom(8)))),
                                StartRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StartResponse.class)
                .returnResult();

        Assertions.assertTrue(result.getResponseBody().getCmd().length() > 0);
        Assertions.assertEquals(result.getResponseBody().getSeq(), 0);
        Assertions.assertEquals(result.getResponseBody().getEmsg(), "");
        Assertions.assertNotEquals(result.getResponseBody().getUuid(), "");
        Assertions.assertFalse(result.getResponseBody().isEnded());

        String uuid = result.getResponseBody().getUuid();
        Session session1 = sessionRepoService.findByUuid(uuid);
        Session session2 = sessionRepoService.findByTid(session1.getTid());

        Assertions.assertEquals(sessionRepoService.count(), 1);
        Assertions.assertEquals(session1.getUuid(), session2.getUuid());
        Assertions.assertEquals(session1.getTid(), session2.getTid());

        sessionRepoService.deleteAll();
        Assertions.assertEquals(sessionRepoService.count(), 0);
    }

    /**
     * Send xfer request without incrementing the seq number,
     * server should return previous TPM command
     */
    @Test
    void processApiV1SessionXfer1() {
        String uuid;
        int seq;
        byte[] baCmd, baResp;
        String b64Cmd, b64Resp;
        String savedCmd;

        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());

        Assertions.assertEquals(sessionRepoService.count(), 0);

        /* send Session Start to receive the first TPM command */
        EntityExchangeResult<StartResponse> startResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest(SCRIPT_GET_RANDOM,
                                MiscUtil.objectToJson(new ArgsGetRandom(8)))),
                      StartRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StartResponse.class)
                .returnResult();
        Assertions.assertTrue(startResponse.getResponseBody().getCmd().length() > 0);
        savedCmd = startResponse.getResponseBody().getCmd();
        Assertions.assertEquals(startResponse.getResponseBody().getSeq(), 0);
        Assertions.assertEquals(startResponse.getResponseBody().getEmsg(), "");
        Assertions.assertNotEquals(startResponse.getResponseBody().getUuid(), "");
        Assertions.assertFalse(startResponse.getResponseBody().isEnded());
        uuid = startResponse.getResponseBody().getUuid();
        seq = startResponse.getResponseBody().getSeq();

        Assertions.assertEquals(sessionRepoService.count(), 1);

        /* exchange command-response pair */

        /* feed the command to Windows' TPM to obtain a response */
        baCmd = MiscUtil.base64ToByteArray(startResponse.getResponseBody().getCmd());
        Assertions.assertNotNull(baCmd);
        tpmDeviceTbs.dispatchCommand(baCmd);
        while(!tpmDeviceTbs.responseReady())
            try { Thread.sleep(100); } catch (Exception e) {}
        baResp = tpmDeviceTbs.getResponse();
        Assertions.assertNotNull(baResp);

        /* send the response back to server */
        b64Resp = MiscUtil.byteArrayToBase64(baResp);
        EntityExchangeResult<XferResponse> xferResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                /* seq number is not incremented */
                .body(Mono.just(new XferRequest(uuid, seq, b64Resp)), XferRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(XferResponse.class)
                .returnResult();
        Assertions.assertEquals(0, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals(uuid, xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("", xferResponse.getResponseBody().getEmsg());
        Assertions.assertFalse(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals(savedCmd, xferResponse.getResponseBody().getCmd());
    }

    /**
     * Recoverable error:
     * 1. Send xfer request with an invalid seq number
     * 2. Resend the xfer request without incrementing the seq number
     * 3. Resend the xfer request with corrected seq number
     */
    @Test
    void processApiV1SessionXfer2() {
        String uuid;
        int seq;
        byte[] baCmd, baResp;
        String b64Cmd, b64Resp;
        String savedCmd;

        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());

        Assertions.assertEquals(sessionRepoService.count(), 0);

        /* send Session Start to receive the first TPM command */
        EntityExchangeResult<StartResponse> startResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest(SCRIPT_GET_RANDOM,
                                MiscUtil.objectToJson(new ArgsGetRandom(8)))),
                      StartRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StartResponse.class)
                .returnResult();
        Assertions.assertTrue(startResponse.getResponseBody().getCmd().length() > 0);
        savedCmd = startResponse.getResponseBody().getCmd();
        Assertions.assertEquals(startResponse.getResponseBody().getSeq(), 0);
        Assertions.assertEquals(startResponse.getResponseBody().getEmsg(), "");
        Assertions.assertNotEquals(startResponse.getResponseBody().getUuid(), "");
        Assertions.assertFalse(startResponse.getResponseBody().isEnded());
        uuid = startResponse.getResponseBody().getUuid();
        seq = startResponse.getResponseBody().getSeq();

        Assertions.assertEquals(sessionRepoService.count(), 1);

        /* feed the command to Windows' TPM to obtain a response */
        baCmd = MiscUtil.base64ToByteArray(startResponse.getResponseBody().getCmd());
        Assertions.assertNotNull(baCmd);
        tpmDeviceTbs.dispatchCommand(baCmd);
        while(!tpmDeviceTbs.responseReady())
            try { Thread.sleep(100); } catch (Exception e) {}
        baResp = tpmDeviceTbs.getResponse();
        Assertions.assertNotNull(baResp);

        /* 1. send the response back to server (with invalid seq) */
        b64Resp = MiscUtil.byteArrayToBase64(baResp);
        EntityExchangeResult<XferResponse> xferResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                /* set invalid seq number */
                .body(Mono.just(new XferRequest(uuid, seq + 2, b64Resp)), XferRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(XferResponse.class)
                .returnResult();
        Assertions.assertEquals(0, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals(uuid, xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("Sequence out of sync, please correct the sequence number.", xferResponse.getResponseBody().getEmsg());
        Assertions.assertFalse(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals("", xferResponse.getResponseBody().getCmd());

        /* 2. re-send the response back to server (with valid seq but not incremented) */
        b64Resp = MiscUtil.byteArrayToBase64(baResp);
        xferResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new XferRequest(uuid, seq, b64Resp)), XferRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(XferResponse.class)
                .returnResult();
        Assertions.assertEquals(seq, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals(uuid, xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("", xferResponse.getResponseBody().getEmsg());
        Assertions.assertFalse(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals(savedCmd, xferResponse.getResponseBody().getCmd());

        /* 3. re-send the response back to server (with valid seq) */
        b64Resp = MiscUtil.byteArrayToBase64(baResp);
        xferResponse = webClient
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
        Assertions.assertEquals(uuid, xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("", xferResponse.getResponseBody().getEmsg());
        Assertions.assertTrue(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals("", xferResponse.getResponseBody().getCmd());
    }

    /**
     * unrecoverable error:
     * Send corrupted response back to server
     */
    @Test
    void processApiV1SessionXfer3() {
        String uuid;
        int seq;
        byte[] baCmd, baResp;
        String b64Cmd, b64Resp;

        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());

        Assertions.assertEquals(sessionRepoService.count(), 0);

        /* send Session Start to receive the first TPM command */
        EntityExchangeResult<StartResponse> startResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest(SCRIPT_GET_RANDOM,
                                MiscUtil.objectToJson(new ArgsGetRandom(8)))),
                      StartRequest.class)
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

        Assertions.assertEquals(sessionRepoService.count(), 1);

        /* exchange command-response pair */

        /* feed the command to Windows' TPM to obtain a response */
        baCmd = MiscUtil.base64ToByteArray(startResponse.getResponseBody().getCmd());
        Assertions.assertNotNull(baCmd);
        tpmDeviceTbs.dispatchCommand(baCmd);
        while(!tpmDeviceTbs.responseReady())
            try { Thread.sleep(100); } catch (Exception e) {}
        baResp = tpmDeviceTbs.getResponse();
        Assertions.assertNotNull(baResp);

        /* corrupt the response */
        byte[] baRespCorrupted = new byte[baResp.length];
        Random random = new Random();
        random.nextBytes(baRespCorrupted);

        /* send the corrupted response back to server */
        b64Resp = MiscUtil.byteArrayToBase64(baRespCorrupted);
        EntityExchangeResult<XferResponse> xferResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new XferRequest(uuid, seq + 1, b64Resp)), XferRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(XferResponse.class)
                .returnResult();
        Assertions.assertEquals(0, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals(uuid, xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("Script execution error.", xferResponse.getResponseBody().getEmsg());
        Assertions.assertTrue(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals("", xferResponse.getResponseBody().getCmd());

        /* send the good response back to server */
        b64Resp = MiscUtil.byteArrayToBase64(baResp);
        xferResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new XferRequest(uuid, seq + 1, b64Resp)), XferRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(XferResponse.class)
                .returnResult();
        Assertions.assertEquals(0, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals("", xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("Invalid uuid.", xferResponse.getResponseBody().getEmsg());
        Assertions.assertTrue(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals("", xferResponse.getResponseBody().getCmd());
    }

    /**
     * test invalid uuid
     */
    @Test
    void processApiV1SessionXfer4() {
        String uuid = java.util.UUID.randomUUID().toString();
        byte[] baResp = new byte[5];
        Random random = new Random();
        random.nextBytes(baResp);

        /* send session xfer with invalid uuid */
        String b64Resp = MiscUtil.byteArrayToBase64(baResp);
        EntityExchangeResult<XferResponse>xferResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_XFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new XferRequest(uuid, 1, b64Resp)), XferRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(XferResponse.class)
                .returnResult();
        Assertions.assertEquals(0, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals("", xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("Invalid uuid.", xferResponse.getResponseBody().getEmsg());
        Assertions.assertTrue(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals("", xferResponse.getResponseBody().getCmd());
    }

    /**
     * Test the complete sequence: Start -> Xfer -> Stop -> Stop (error)
     */
    @Test
    void processApiV1SessionStop1() {
        SessionManager sessionManager = new SessionManager(webClient, serverPort, sessionRepoService);
        String json = sessionManager.executeScript(SCRIPT_GET_RANDOM, MiscUtil.objectToJson(new ArgsGetRandom(8)));
        try {
            ResultGetRandom resultGetRandom = MiscUtil.JsonToObject(json, ResultGetRandom.class);
            byte[] random = MiscUtil.base64ToByteArray(resultGetRandom.getRandom());
            Assertions.assertEquals(8, random.length);
        } catch (Exception e) { Assertions.assertTrue(false); }

        /* send Session Stop again, should receive error */
        EntityExchangeResult<StopResponse> stopResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_STOP)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StopRequest(sessionManager.uuid)), StopRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StopResponse.class)
                .returnResult();
        Assertions.assertEquals("", stopResponse.getResponseBody().getScript());
        Assertions.assertEquals("Invalid uuid.", stopResponse.getResponseBody().getEmsg());
        Assertions.assertEquals("", stopResponse.getResponseBody().getResult());
    }

    /**
     * Stop a session prematurely: Start -> Stop -> Xfer
     */
    @Test
    void processApiV1SessionStop2() {
        String uuid;
        int seq;
        byte[] baCmd, baResp;
        String b64Cmd, b64Resp;

        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());

        Assertions.assertEquals(sessionRepoService.count(), 0);

        /* send Session Start to receive the first TPM command */
        EntityExchangeResult<StartResponse> startResponse = webClient
                .post().uri("http://localhost:" + serverPort + URL_API_V1_SESSION_START)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(new StartRequest(SCRIPT_GET_RANDOM,
                                MiscUtil.objectToJson(new ArgsGetRandom(8)))),
                      StartRequest.class)
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

        Assertions.assertEquals(1, sessionRepoService.count());
        Assertions.assertEquals(1, threadService.count());

        /* feed the command to Windows' TPM to obtain a response */
        baCmd = MiscUtil.base64ToByteArray(startResponse.getResponseBody().getCmd());
        Assertions.assertNotNull(baCmd);
        tpmDeviceTbs.dispatchCommand(baCmd);
        while(!tpmDeviceTbs.responseReady())
            try { Thread.sleep(100); } catch (Exception e) {}
        baResp = tpmDeviceTbs.getResponse();
        Assertions.assertNotNull(baResp);

        /* prematurely stop the session */
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

        Assertions.assertEquals(SCRIPT_GET_RANDOM, stopResponse.getResponseBody().getScript());
        Assertions.assertEquals("", stopResponse.getResponseBody().getEmsg());
        Assertions.assertEquals("", stopResponse.getResponseBody().getResult());

        Assertions.assertEquals(0, sessionRepoService.count());
        Assertions.assertEquals(0, threadService.count());

        /* send the response back to server */
        b64Resp = MiscUtil.byteArrayToBase64(baResp);
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
        Assertions.assertEquals(0, xferResponse.getResponseBody().getSeq());
        Assertions.assertEquals("", xferResponse.getResponseBody().getUuid());
        Assertions.assertEquals("Invalid uuid.", xferResponse.getResponseBody().getEmsg());
        Assertions.assertTrue(xferResponse.getResponseBody().isEnded());
        Assertions.assertEquals("", xferResponse.getResponseBody().getCmd());
    }

    /**
     * test request with invalid uuid
     */
    @Test
    void processApiV1SessionStop3() {
        String uuid = java.util.UUID.randomUUID().toString();

        /* send session stop with invalid uuid */
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
        Assertions.assertEquals("", stopResponse.getResponseBody().getScript());
        Assertions.assertEquals("Invalid uuid.", stopResponse.getResponseBody().getEmsg());
        Assertions.assertEquals("", stopResponse.getResponseBody().getResult());
    }
}
