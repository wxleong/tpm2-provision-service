package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsGetRandom;
import com.infineon.tpm20.model.v1.session.ResultGetRandom;
import com.infineon.tpm20.util.MiscUtil;
import org.springframework.context.ApplicationContext;
import tss.Tpm;

public class CommandSetGetRandom extends AbstractCommandSet {

    public static String name = "get-random";

    public CommandSetGetRandom(ApplicationContext applicationContext, String args) {
        super(applicationContext, MiscUtil.JsonToObject(args, ArgsGetRandom.class));
    }

    @Override
    public void run(Tpm tpm) {
        try {
            ArgsGetRandom argsGetRandom = (ArgsGetRandom) getArgs();
            int len = argsGetRandom.getBytes();
            byte[] r = tpm.GetRandom(len);
            setResult(new ResultGetRandom(MiscUtil.byteArrayToBase64(r)));
        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }
}
