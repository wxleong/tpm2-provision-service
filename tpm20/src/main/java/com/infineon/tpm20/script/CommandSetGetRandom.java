package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ResultGetRandom;
import com.infineon.tpm20.util.Utility;
import tss.Tpm;

public class CommandSetGetRandom extends CommandSet {

    public static String name = "get-random";

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
