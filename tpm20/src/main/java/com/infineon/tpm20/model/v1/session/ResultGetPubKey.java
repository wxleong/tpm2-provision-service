package com.infineon.tpm20.model.v1.session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultGetPubKey implements IResult {
    private String algo;
    private String pubKey;
}