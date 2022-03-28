/**
 * MIT License
 *
 * Copyright (c) 2020 Infineon Technologies AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.infineon.tpm20.controller;

import com.infineon.tpm20.model.v1.ping.PingResponse;
import com.infineon.tpm20.model.v1.scripts.ScriptsResponse;
import com.infineon.tpm20.model.v1.session.*;
import com.infineon.tpm20.service.CoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

import static com.infineon.tpm20.Constants.*;

@RestController
public class RestApiController {

    @Autowired
    private CoreService coreService;

    @GetMapping(URL_API_V1_PING)
    public PingResponse processApiV1Ping() {
        return new PingResponse();
    }

    @GetMapping(URL_API_V1_SCRIPTS)
    public ScriptsResponse processApiV1Scripts(HttpServletResponse servletResponse) {
        return coreService.processApiV1Scripts(servletResponse);
    }

    @PostMapping(URL_API_V1_SESSION_START)
    public StartResponse processApiV1SessionStart(@RequestBody StartRequest startRequest, HttpServletResponse servletResponse) {
        return coreService.processApiV1SessionStart(startRequest, servletResponse);
    }

    @PostMapping(URL_API_V1_SESSION_XFER)
    public XferResponse processApiV1SessionXfer(@RequestBody XferRequest xferRequest, HttpServletResponse servletResponse) {
        return coreService.processApiV1SessionXfer(xferRequest, servletResponse);
    }

    @PostMapping(URL_API_V1_SESSION_STOP)
    public StopResponse processApiV1SessionStop(@RequestBody StopRequest stopRequest, HttpServletResponse servletResponse) {
        return coreService.processApiV1SessionStop(stopRequest, servletResponse);
    }
}
