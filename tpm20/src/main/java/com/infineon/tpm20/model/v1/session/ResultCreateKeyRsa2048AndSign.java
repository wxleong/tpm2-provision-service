package com.infineon.tpm20.model.v1.session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultCreateKeyRsa2048AndSign implements IResult {
    private String ekPub;
    private String pub;
    private String keyHandle;
    private String sig;
}
