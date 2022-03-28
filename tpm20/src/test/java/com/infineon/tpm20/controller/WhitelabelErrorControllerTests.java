package com.infineon.tpm20.controller;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.util.Collections;

import static com.infineon.tpm20.Constants.URL_WHITELABEL_ERROR;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class WhitelabelErrorControllerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void HttpWhitelabelError() {
        String randomUrl = RandomStringUtils.random(32, true, false);;

        /* Test 1: Invalid url request with header "Accept": text/html
           Request will be rejected by Spring Security, the MediaType is not mapped so ends with HTTP_NOT_ACCEPTABLE */
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/" + randomUrl, HttpMethod.GET, request, String.class);

        Assertions.assertEquals(response.getStatusCodeValue(), HttpURLConnection.HTTP_NOT_ACCEPTABLE);
        Assertions.assertEquals(response.getBody(), null);

        /* Test 2: Invalid url request with header "Accept": application/json
           Request will be rejected by Spring Security then land on WHITELABEL_ERROR_URL */
        headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        request = new HttpEntity<>(headers);
        response = restTemplate.exchange(
                "http://localhost:" + port + "/" + randomUrl, HttpMethod.GET, request, String.class);

        Assertions.assertEquals(response.getStatusCodeValue(), HttpURLConnection.HTTP_NOT_FOUND);
        Assertions.assertTrue(response.getBody().contains("unknown request"));

        /* Test 3: Invalid url request with header "Accept": multipart/form-data
           Request will be rejected by Spring Security, the MediaType is not mapped so ends with HTTP_NOT_ACCEPTABLE */
        headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.MULTIPART_FORM_DATA));
        request = new HttpEntity<>(headers);
        response = restTemplate.exchange(
                "http://localhost:" + port + "/" + randomUrl, HttpMethod.GET, request, String.class);

        Assertions.assertEquals(response.getStatusCodeValue(), HttpURLConnection.HTTP_NOT_ACCEPTABLE);
        Assertions.assertEquals(response.getBody(), null);

        /* Test 4: WHITELABEL_ERROR_URL request with header "Accept": application/json
           Request WHITELABEL_ERROR_URL directly */
        headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        request = new HttpEntity<>(headers);
        response = restTemplate.exchange(
                "http://localhost:" + port + URL_WHITELABEL_ERROR, HttpMethod.GET, request, String.class);

        Assertions.assertEquals(response.getStatusCodeValue(), HttpURLConnection.HTTP_OK);
        Assertions.assertTrue(response.getBody().contains("unknown request"));

        /* Test 5: HTTP_EXCEPTION_URL request with header "Accept": text/html
           The requested MediaType is not mapped so ends with HTTP_NOT_ACCEPTABLE */
        headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        request = new HttpEntity<>(headers);
        response = restTemplate.exchange(
                "http://localhost:" + port + URL_WHITELABEL_ERROR, HttpMethod.GET, request, String.class);

        Assertions.assertEquals(response.getStatusCodeValue(), HttpURLConnection.HTTP_NOT_ACCEPTABLE);
        Assertions.assertEquals(response.getBody(), null);
    }

}
