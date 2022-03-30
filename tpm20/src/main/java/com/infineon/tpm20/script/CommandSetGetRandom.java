package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultGetRandom;
import com.infineon.tpm20.util.Utility;
import org.springframework.context.ApplicationContext;
import tss.Tpm;

public class CommandSetGetRandom extends AbstractCommandSet {

    public static String name = "get-random";

    public CommandSetGetRandom(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @Override
    public void run(Tpm tpm) {
        try {
            byte[] r = tpm.GetRandom(8);
            setResult(new ResultGetRandom(Utility.byteArrayToBase64(r)));
        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }
}
