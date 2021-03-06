package com.infineon.tpm20.service;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;

@Service
public class CAService {

    private final int CERT_CHAIN_DEPTH_MAX = 5;
    private final String[] TRUSTED_CA_SITES = {"pki.infineon.com"};
    HashMap<String, X509Certificate> CAs;

    @Value("classpath:certificates/*.crt")
    private Resource[] resourceOptigaRootCACert;

    public CAService() {
        CAs = new HashMap<>();
    }

    @PostConstruct
    private void CAService() throws Exception {
        try {
            for (Resource resource:resourceOptigaRootCACert) {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                X509Certificate rootCa = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(resource.getInputStream().readAllBytes()));
                verifyAndStoreRootCACert(rootCa);
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    public void verify(X509Certificate toe) throws Exception {
        try {
            X509Certificate issuer;
            int depth = CERT_CHAIN_DEPTH_MAX;

            do {
                if (depth-- < 0)
                    throw new Exception("Certificate chain is longer than acceptable length of " + CERT_CHAIN_DEPTH_MAX);

                String toeDN = toe.getSubjectX500Principal().getName();
                String issuerDN = toe.getIssuerX500Principal().getName();

                /* check if we reached the root of the certificate chain */
                if (toeDN.equals(issuerDN)) {
                    issuer = CAs.get(issuerDN);
                    toe.checkValidity();
                    toe.verify(issuer.getPublicKey());
                    break;
                }

                /* do we know the issuer? */
                issuer = CAs.get(issuerDN);

                /* try to download the issuer certificate */
                if (issuer == null) {
                    int i;
                    String link = getIssuerUrl(toe);

                    if (link == null)
                        throw new Exception("Unable to retrieve uri from Authority Information Access field.");

                    for (i = 0; i < TRUSTED_CA_SITES.length; i++) {
                        if (link.startsWith("http://" + TRUSTED_CA_SITES[i]))
                            break;
                        if (link.startsWith("https://" + TRUSTED_CA_SITES[i]))
                            break;
                    }

                    if (i >= TRUSTED_CA_SITES.length)
                        throw new Exception("URI to download issuer's certificate is not trusted.");

                    URL url = new URL(link);
                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    issuer = (X509Certificate) certFactory.generateCertificate(url.openStream());
                    if (!issuer.getSubjectX500Principal().getName().equals(issuerDN))
                        throw new Exception("CA server returns bad certificate.");

                    verifyAndStoreIssuerCert(true, toe, issuer);
                } else {
                    verifyAndStoreIssuerCert(false, toe, issuer);
                }

                toe = issuer;
            } while(true);
        } catch (Exception e) {
            // CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException
            throw e;
        }
    }

    /**
     * Convert Base64 encoded DER X.509 digital certificate
     * to X509Certificate object
     * @param base64
     * @return
     */
    public static X509Certificate base64(String base64) throws CertificateException {
        byte der[] = Base64.getDecoder().decode(base64);
        ByteArrayInputStream bytes = new ByteArrayInputStream(der);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate eKCert = (X509Certificate)certFactory.generateCertificate(bytes);
        return eKCert;
    }

    private static String getIssuerUrl(X509Certificate certificate) throws Exception {

        byte[] bytes = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(bytes));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        ASN1Primitive obj = aIn.readObject();
        AuthorityInformationAccess authorityInformationAccess = AuthorityInformationAccess.getInstance(obj);

        AccessDescription[] accessDescriptions = authorityInformationAccess.getAccessDescriptions();
        for (AccessDescription accessDescription : accessDescriptions) {

            GeneralName name = accessDescription.getAccessLocation();
            if (name.getTagNo() != GeneralName.uniformResourceIdentifier) {
                continue;
            }

            DERIA5String derStr = DERIA5String.getInstance((ASN1TaggedObject) name.toASN1Primitive(), false);
            return derStr.getString();
        }
        return null;
    }

    static public String print(X509Certificate cert) {
        String out = "";

        try {
            out += "Version: V" + cert.getVersion() + ", ";
            out += "Format: " + cert.getType() + "\n";

            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
                out += "Thumbprint: " + Hex.toHexString(messageDigest.digest(cert.getEncoded())) + "\n";
            } catch (NoSuchAlgorithmException e) {
            }

            out += "Serial Number: " + cert.getSerialNumber().toString(16) + "\n";
            out += "Subject: " + cert.getSubjectX500Principal().toString() + "\n";
            out += "Issuer: "+ cert.getIssuerX500Principal().toString() + "\n";
            out += "Validity: [From: " + cert.getNotBefore().toString() +
                    ", To: " + cert.getNotAfter().toString() + "]\n";
            out += "Signature Algorithm: "+ cert.getSigAlgName() + "\n";
            out += "Public Key: "+ cert.getPublicKey().toString() + "\n";
            out += "Signature: "+ Hex.toHexString(cert.getSignature()) + "\n";
        } catch (Exception e) {
        }
        return out;
    }

    /**
     * Verify root CA self-signed certificate and remember the cert
     * @param rootCa
     * @return
     */
    private void verifyAndStoreRootCACert(X509Certificate rootCa) throws Exception {
        if (!rootCa.getIssuerX500Principal().equals(rootCa.getSubjectX500Principal()))
            throw new Exception("root CA cert is not a self-signed cert");
        rootCa.checkValidity();
        rootCa.verify(rootCa.getPublicKey());
        CAs.put(rootCa.getIssuerX500Principal().getName(), rootCa);
    }

    private void verifyAndStoreIssuerCert(boolean toStore, X509Certificate cert, X509Certificate issuerCert) throws Exception {
        if (!cert.getIssuerX500Principal().equals(issuerCert.getSubjectX500Principal()))
            throw new Exception("certificate issuer mismatch");
        cert.checkValidity();
        issuerCert.checkValidity();
        cert.verify(issuerCert.getPublicKey());
        if (toStore)
            CAs.put(issuerCert.getSubjectX500Principal().getName(), issuerCert);
    }
}
