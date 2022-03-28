package com.infineon.tpm20.controller;

import com.infineon.tpm20.model.Response;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.infineon.tpm20.Constants.URL_WHITELABEL_ERROR;

/**
 * Unmanaged MediaType (found in the "Accept" header of HTTP request) will receive error status: 406 Not Acceptable
 */
@Controller
public class WhitelabelErrorController {

    @RestController
    public class RestApiController implements ErrorController {

        // Only respond to known MediaType
        @RequestMapping(value = URL_WHITELABEL_ERROR, produces = MediaType.APPLICATION_JSON_VALUE)
        public Response<Object> RestWhitelabelError() {
            return new Response<>("unknown request");
        }
    }
}
