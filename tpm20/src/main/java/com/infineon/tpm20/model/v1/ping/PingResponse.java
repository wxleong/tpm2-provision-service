package com.infineon.tpm20.model.v1.ping;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PingResponse {
    private final String ping = "pong";
}
