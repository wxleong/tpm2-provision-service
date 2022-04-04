package com.infineon.tpm20.util;

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public class CsrContentSigner implements ContentSigner {
    private ByteArrayOutputStream outputStream;
    private AlgorithmIdentifier sigAlgId;
    private byte[] sig;

    public CsrContentSigner() {
        this.outputStream = new ByteArrayOutputStream();
        this.sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WITHRSA");
        sig = new byte[0];
    }

    public void setSig(byte[] sig) {
        this.sig = sig;
    }

    public byte[] getContent() {
        return outputStream.toByteArray();
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return sigAlgId;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public byte[] getSignature() {
        return sig;
    }
}
