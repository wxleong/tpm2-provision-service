package com.infineon.tpm20.util;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

public class CertUtil {

    public static byte[] genCsrContent(byte[] pubKey, byte[] sig, String subject) {
        PublicKey publicKey = null;
        try {
            publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(new BigInteger(1, pubKey), BigInteger.valueOf(65537)));

            /* set CSR builder */

            CsrContentSigner contentSigner = new CsrContentSigner();

            X500Principal x500Principal = new X500Principal(subject);
            PKCS10CertificationRequestBuilder p10Builder =
                    new JcaPKCS10CertificationRequestBuilder(x500Principal, publicKey);

            if (sig != null && sig.length > 0)
                contentSigner.setSig(sig);

            /* generate CSR */

            PKCS10CertificationRequest csr = p10Builder.build(contentSigner);

            if (sig == null || sig.length == 0)
                return contentSigner.getContent();

            /* verify CSR signature */

            SubjectPublicKeyInfo subjectPublicKeyInfo = csr.getSubjectPublicKeyInfo();
            ContentVerifierProvider contentVerifierProvider = new JcaContentVerifierProviderBuilder().build(subjectPublicKeyInfo);
            if (!csr.isSignatureValid(contentVerifierProvider)) {
                return null;
            }

            return csr.getEncoded();
        } catch (Exception e) {
            return null;
        }
    }
}
