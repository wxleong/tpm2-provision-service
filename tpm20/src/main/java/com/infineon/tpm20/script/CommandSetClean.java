package com.infineon.tpm20.script;

import com.infineon.tpm20.model.v1.session.ArgsClean;
import com.infineon.tpm20.util.MiscUtil;
import org.springframework.context.ApplicationContext;
import tss.Tpm;
import tss.tpm.*;

import static com.infineon.tpm20.util.TpmUtil.cleanSlots;
import static com.infineon.tpm20.util.TpmUtil.evict;

public class CommandSetClean extends AbstractCommandSet {

    public static String name = "clean";

    public CommandSetClean(ApplicationContext applicationContext, String args) {
        super(applicationContext, MiscUtil.JsonToObject(args, ArgsClean.class));
    }

    @Override
    public void run(Tpm tpm) {
        try {

            ArgsClean argsClean = (ArgsClean) getArgs();

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

            if (argsClean == null)
                return;

            for (String handle: argsClean.getHandles()) {
                evict(tpm, new TPM_HANDLE(Long.decode(handle).intValue()));
            }

            cleanSlots(tpm, TPM_HT.LOADED_SESSION);
            cleanSlots(tpm, TPM_HT.TRANSIENT);

        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        }
    }
}
