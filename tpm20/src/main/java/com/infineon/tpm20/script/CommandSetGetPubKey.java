package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsGetPubKey;
import com.infineon.tpm20.model.v1.session.ResultGetPubKey;
import com.infineon.tpm20.util.MiscUtil;
import org.springframework.context.ApplicationContext;
import tss.Tpm;
import tss.tpm.*;

public class CommandSetGetPubKey extends AbstractCommandSet {

    public static String name = "get-pubkey";

    public CommandSetGetPubKey(ApplicationContext applicationContext, String args) {
        super(applicationContext, MiscUtil.JsonToObject(args, ArgsGetPubKey.class));
    }

    @Override
    public void run(Tpm tpm) {
        try {
            ArgsGetPubKey argsGetRandom = (ArgsGetPubKey) getArgs();

            TPM_HANDLE keyHandle = new TPM_HANDLE(Long.decode(argsGetRandom.getKeyHandle()).intValue());

            ReadPublicResponse rpResp = tpm._allowErrors().ReadPublic(keyHandle);
            TPM_RC rc = tpm._getLastResponseCode();

            if (rc == TPM_RC.SUCCESS) {
                if (rpResp.outPublic.unique.GetUnionSelector() == TPM_ALG_ID.RSA) {
                    TPM2B_PUBLIC_KEY_RSA rsa = (TPM2B_PUBLIC_KEY_RSA) rpResp.outPublic.unique;
                    setResult(new ResultGetPubKey("rsa", MiscUtil.byteArrayToBase64(rsa.buffer)));
                    return;
                }
            }

            setResult(new ResultGetPubKey("", ""));
        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }
}
