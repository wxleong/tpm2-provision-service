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

package com.infineon.tpm20;

import com.infineon.tpm20.script.CommandSetCreateEkRsa2048;
import com.infineon.tpm20.script.CommandSetEkRsa2048BasedAuth;
import com.infineon.tpm20.script.CommandSetGetRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class Constants {
    public static final String URL_API_V1_PING = "/api/v1/ping";
    public static final String URL_API_V1_SCRIPTS = "/api/v1/scripts";
    public static final String URL_API_V1_SESSION_START = "/api/v1/session/start";
    public static final String URL_API_V1_SESSION_XFER = "/api/v1/session/xfer";
    public static final String URL_API_V1_SESSION_STOP = "/api/v1/session/stop";
    public static final String URL_WHITELABEL_ERROR = "/error";

    /* collection of TPM provisioning script */
    public static final String SCRIPT_GET_RANDOM = CommandSetGetRandom.name;
    public static final String SCRIPT_CREATE_EK_RSA2048 = CommandSetCreateEkRsa2048.name;
    public static final String SCRIPT_EK_RSA2048_BASED_AUTHENTICATION = CommandSetEkRsa2048BasedAuth.name;
    public Map<String, Class<?>> scripts  = new HashMap<>() {{
        put(SCRIPT_GET_RANDOM, CommandSetGetRandom.class);
        put(SCRIPT_CREATE_EK_RSA2048, CommandSetCreateEkRsa2048.class);
        put(SCRIPT_EK_RSA2048_BASED_AUTHENTICATION, CommandSetEkRsa2048BasedAuth.class);
    }};

    @Value("${management.server.port}")
    public int MANAGEMENT_PORT;
    @Value("${custom-prop.thread-pool.max}")
    public int THREAD_POOL_MAX;
    @Value("${custom-prop.thread-pool.timeout}")
    public int THREAD_POOL_TIMEOUT;
    @Value("${custom-prop.tpm-command.timeout}")
    public int TPM_COMMAND_TIMEOUT;
}

