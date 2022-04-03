package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsGetPubKey;
import com.infineon.tpm20.model.v1.session.ArgsSigning;
import com.infineon.tpm20.model.v1.session.ResultGetPubKey;
import com.infineon.tpm20.model.v1.session.ResultRsaSigning;
import com.infineon.tpm20.service.SessionRepoService;
import com.infineon.tpm20.util.MiscUtil;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import tss.Tpm;
import tss.TpmDeviceTbs;
import tss.tpm.*;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import static com.infineon.tpm20.Constants.SCRIPT_GET_PUBKEY;
import static com.infineon.tpm20.util.TpmUtil.cleanSlots;
import static com.infineon.tpm20.util.TpmUtil.evict;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Overlay the default application properties with properties from test
@ActiveProfiles("test")
public class CommandSetKeyRsa2048CreateAndSignTests {

    @Autowired
    private ApplicationContext applicationContext;
    @LocalServerPort
    private int serverPort;
    @Autowired
    private WebTestClient webClient;
    @Autowired
    private SessionRepoService sessionRepoService;

    /**
     * The test is conducted directly on CommandSetKeyRsa2048CreateAndSign.class instead of using Session
     * endpoints to make method mocking possible.
     */
    @Disabled("Need Windows machine with TPM and administrator access (TPM2_ActivateCredential)")
    @Test
    void test1() {

        /*
           "spy" on script CommandSetKeyRsa2048CreateAndSign.class.

           - Mockito.mock(): all methods are mocked, unless specify.
             By default, for all methods that return a value, a mock will return either null,
             a primitive/primitive wrapper value, or an empty collection, as appropriate
           - Mockito.spy(): all methods are original (CallRealMethod), unless specify.
         */
        CommandSetKeyRsa2048CreateAndSign commandSetKeyRsa2048CreateAndSignOrig = new CommandSetKeyRsa2048CreateAndSign(applicationContext, null);
        CommandSetKeyRsa2048CreateAndSign commandSetKeyRsa2048CreateAndSign = Mockito.spy(commandSetKeyRsa2048CreateAndSignOrig);

        /*
           avoid verifying platform EK certificate
           mock the EK certificate verification method to "doNothing"
         */
        Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetKeyRsa2048CreateAndSign).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

        /* initialize platform TPM */
        TpmDeviceTbs tpmDeviceTbs = new TpmDeviceTbs();
        Assertions.assertTrue(tpmDeviceTbs.connect());
        Tpm tpm = new Tpm();
        tpm._setDevice(tpmDeviceTbs);

        /* execute the script */
        commandSetKeyRsa2048CreateAndSign.run(tpm);

        /* verify mocked method is called only once */
        Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetKeyRsa2048CreateAndSign, Mockito.times(1))
                .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

        /* verify the execution is successful */
        ResultRsaSigning resultRsaSigning = (ResultRsaSigning) commandSetKeyRsa2048CreateAndSign.getResult();
        Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultRsaSigning.getEkPub()).length);
        Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultRsaSigning.getPub()).length);
    }

    //@Disabled("Need Windows machine with TPM")
    @Test
    void generateCSR() {

        SessionManager sessionManager = new SessionManager(webClient, serverPort, sessionRepoService);
        Tpm tpm = new Tpm();
        tpm._setDevice(sessionManager.tpmDeviceTbs);

        /* create RSA key */

        cleanSlots(tpm, TPM_HT.LOADED_SESSION);
        cleanSlots(tpm, TPM_HT.TRANSIENT);

        TPM_HANDLE keyHandle = new TPM_HANDLE(0x81000111);
        TPMT_PUBLIC rsaTemplate = new TPMT_PUBLIC(TPM_ALG_ID.SHA256,
                new TPMA_OBJECT(TPMA_OBJECT.sign, TPMA_OBJECT.sensitiveDataOrigin, TPMA_OBJECT.userWithAuth),
                new byte[0],
                new TPMS_RSA_PARMS(new TPMT_SYM_DEF_OBJECT(TPM_ALG_ID.NULL,  0, TPM_ALG_ID.NULL),
                        new TPMS_NULL_ASYM_SCHEME(),
                        2048, 65537),
                new TPM2B_PUBLIC_KEY_RSA());

        TPMS_SENSITIVE_CREATE sens = new TPMS_SENSITIVE_CREATE(new byte[0], new byte[0]);

        CreatePrimaryResponse rsaPrimary = tpm.CreatePrimary(TPM_HANDLE.from(TPM_RH.OWNER), sens,
                rsaTemplate, new byte[0], new TPMS_PCR_SELECTION[0]);

        evict(tpm, keyHandle);
        tpm.EvictControl(TPM_HANDLE.from(TPM_RH.OWNER), rsaPrimary.handle, keyHandle);

        /* get pubkey */

        String json = sessionManager.executeScript(SCRIPT_GET_PUBKEY,
                MiscUtil.objectToJson(new ArgsGetPubKey("0x81000111")));
        ResultGetPubKey resultGetPubKey = MiscUtil.JsonToObject(json, ResultGetPubKey.class);
        Assertions.assertEquals("rsa", resultGetPubKey.getAlgo());
        TPM2B_PUBLIC_KEY_RSA rsa = (TPM2B_PUBLIC_KEY_RSA) rsaPrimary.outPublic.unique;
        Assertions.assertEquals(MiscUtil.byteArrayToBase64(rsa.buffer), resultGetPubKey.getPubKey());
        TPMS_RSA_PARMS rsaParms = (TPMS_RSA_PARMS) rsaPrimary.outPublic.parameters;
        Assertions.assertEquals(65537, rsaParms.exponent);

        PublicKey publicKey = null;
        try {
            publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(new BigInteger(1, rsa.buffer), BigInteger.valueOf(65537)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Assertions.assertTrue(false);
        }

        /* generate CSR content */

        TpmContentSigner contentSigner = new TpmContentSigner();

        X500Principal x500Principal = new X500Principal("CN=TPM, O=TPM, OU=TPM, C=SG, L=Singapore");
        PKCS10CertificationRequestBuilder p10Builder =
                new JcaPKCS10CertificationRequestBuilder(x500Principal, publicKey);
        PKCS10CertificationRequest csr = p10Builder.build(contentSigner);

        /* get the CSR content for signing */

        byte[] content = contentSigner.getContent();

        /*
           avoid verifying platform EK certificate
           mock the EK certificate verification method to "doNothing" in script CommandSetKeyRsa2048CreateAndSign
         */

        CommandSetKeyRsa2048CreateAndSign commandSetKeyRsa2048CreateAndSignOrig = new CommandSetKeyRsa2048CreateAndSign(applicationContext,
                MiscUtil.objectToJson(new ArgsSigning("0x81000111", "pkcs", MiscUtil.byteArrayToBase64(content),"")));
        CommandSetKeyRsa2048CreateAndSign commandSetKeyRsa2048CreateAndSign = Mockito.spy(commandSetKeyRsa2048CreateAndSignOrig);

        Assertions.assertDoesNotThrow(() -> Mockito.doNothing().when(commandSetKeyRsa2048CreateAndSign).readAndVerifyEkCert(Mockito.any(), Mockito.any()));

        /* execute the script */

        commandSetKeyRsa2048CreateAndSign.run(tpm);

        /* verify mocked method is called only once */

        Assertions.assertDoesNotThrow(() -> Mockito.verify(commandSetKeyRsa2048CreateAndSign, Mockito.times(1))
                .readAndVerifyEkCert(Mockito.any(Tpm.class), Mockito.any(TPMT_PUBLIC.class)));

        /* verify the execution is successful */

        ResultRsaSigning resultRsaSigning = (ResultRsaSigning) commandSetKeyRsa2048CreateAndSign.getResult();
        Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultRsaSigning.getEkPub()).length);
        Assertions.assertEquals(256, MiscUtil.base64ToByteArray(resultRsaSigning.getPub()).length);
        Assertions.assertNotEquals("", resultRsaSigning.getSig());

        /* set CSR content signature */

        contentSigner.setSig(MiscUtil.base64ToByteArray(resultRsaSigning.getSig()));

        /* rebuild CSR with signature */

        csr = p10Builder.build(contentSigner);

        /* verify CSR signature */

        try {
            SubjectPublicKeyInfo subjectPublicKeyInfo = csr.getSubjectPublicKeyInfo();
            ContentVerifierProvider contentVerifierProvider = new JcaContentVerifierProviderBuilder().build(subjectPublicKeyInfo);
            csr.isSignatureValid(contentVerifierProvider);
        } catch (Exception e) {
            Assertions.assertTrue(false);
        }

        /* remove RSA key */

        evict(tpm, keyHandle);

        /* print the PEM encoded CSR */

        try {
            PemObject pemObject = new PemObject("CERTIFICATE REQUEST", csr.getEncoded());
            StringWriter stringWriter = new StringWriter();
            JcaPEMWriter jcaPEMWriter = new JcaPEMWriter (stringWriter);
            jcaPEMWriter.writeObject(pemObject);
            jcaPEMWriter.close();
            stringWriter.close();
            System.out.println(stringWriter);
            Assertions.assertTrue(true);
        } catch (IOException e) {
            Assertions.assertTrue(false);
        }
    }

    private class TpmContentSigner implements ContentSigner {
        private ByteArrayOutputStream outputStream;
        private AlgorithmIdentifier sigAlgId;
        private byte[] sig;

        public TpmContentSigner() {
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
}
