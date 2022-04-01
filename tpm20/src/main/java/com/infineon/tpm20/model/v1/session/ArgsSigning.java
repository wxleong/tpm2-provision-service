package com.infineon.tpm20.model.v1.session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArgsSigning implements IArgs {
    private String keyHandle;
    private String padding;
    private String data;
    private String digest;
}
