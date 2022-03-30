package com.infineon.tpm20.service;

import com.infineon.tpm20.Constants;
import com.infineon.tpm20.entity.Session;
import com.infineon.tpm20.model.v1.scripts.ScriptsResponse;
import com.infineon.tpm20.model.v1.session.*;
import com.infineon.tpm20.script.AbstractCommandSet;
import com.infineon.tpm20.script.CommandSetRunnable;
import com.infineon.tpm20.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletResponse;

@Slf4j
@Service
public class CoreService {

    @Autowired
    private Constants constants;
    @Autowired
    private ThreadService threadService;
    @Autowired
    private SessionRepoService sessionRepoService;
    @Autowired
    private ApplicationContext applicationContext;

    public ScriptsResponse processApiV1Scripts(HttpServletResponse servletResponse) {

        try {
            String[] scripts = constants.scripts.keySet().toArray(new String[0]);
            return new ScriptsResponse(scripts);
        } catch (Exception e) {
            log.error("",e);
        }

        servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return null;
    }

    /**
     * Start a session/thread and assign a UUID for each session.
     * The UUID is used to find the associated thread.
     * @param startRequest
     * @param servletResponse
     * @return
     */
    public StartResponse processApiV1SessionStart(@RequestBody StartRequest startRequest,
                                                  HttpServletResponse servletResponse) {

        try {
            CommandSetRunnable runnable = null;
            Thread thread = null;

            Class<?> c = constants.scripts.get(startRequest.getScript());
            if (c == null) {
                /* provisioning script not found */
                return new StartResponse("", 0, "", true, "Provisioning script not found.");
            }

            AbstractCommandSet abstractCommandSet = (AbstractCommandSet) c.getConstructor(ApplicationContext.class, String.class)
                    .newInstance(applicationContext, startRequest.getArgs());
            runnable = new CommandSetRunnable(abstractCommandSet, constants.THREAD_POOL_TIMEOUT);

            if (runnable != null) {
                if ((thread = threadService.execute(runnable)) == null) {
                    /* out of resources, try again later */
                    return new StartResponse("", 0, "", true, "Out of resources, try again later.");
                }

                if (!runnable.waitForCommandOrEnding(constants.TPM_COMMAND_TIMEOUT))
                    /* should not happen, server may be overloaded */
                    new Exception("Timeout occurred, waited for TPM command.");

                if (runnable.isEnded())
                    /* should not happen, thread ended unexpectedly */
                    new Exception("Thread ended unexpectedly.");

                String uuid = java.util.UUID.randomUUID().toString();

                /* update repository */
                Session session = new Session();
                session.setUuid(uuid);
                session.setTid(thread.getId());
                session.setSeq(0);
                session.setScript(startRequest.getScript());
                sessionRepoService.save(session);

                byte[] command = runnable.getCommandBuffer();
                return new StartResponse(uuid, 0,
                        Utility.byteArrayToBase64(command), false, "");
            }
        } catch (Exception e) {
            log.error("",e);
        }

        servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return null;
    }

    public XferResponse processApiV1SessionXfer(@RequestBody XferRequest xferRequest, HttpServletResponse servletResponse) {

        try {
            String uuid = xferRequest.getUuid();
            Session session = sessionRepoService.findByUuid(uuid);

            /* check uuid validity */
            if (session == null) {
                return new XferResponse("", 0, "", true, "Invalid uuid.");
            }

            /* check if result is available */
            if (session.getResult() != null) {
                /* client should send SessionStop to retrieve result */
                return new XferResponse(uuid, session.getSeq(), "", true, "");
            }

            CommandSetRunnable runnable = (CommandSetRunnable)threadService.getRunnable(session.getTid());

            /* check if thread is still active */
            if (runnable == null) {
                sessionRepoService.deleteByUuid(uuid);
                return new XferResponse(uuid, session.getSeq(), "", true, "Timeout.");
            }

            /* check sequence number */
            if (session.getSeq() == xferRequest.getSeq()) {
                /* replay previous command */

                byte[] command = runnable.getCommandBuffer();
                return new XferResponse(uuid, session.getSeq(),
                        Utility.byteArrayToBase64(command), false, "");
            } else if (session.getSeq() + 1 == xferRequest.getSeq()) {
                /* proceed */

                String base64 = xferRequest.getResp();
                byte[] resp = Utility.base64ToByteArray(base64);
                runnable.responseReady(resp);

                if (!runnable.waitForCommandOrEnding(constants.TPM_COMMAND_TIMEOUT)) {
                    sessionRepoService.deleteByUuid(uuid);
                    return new XferResponse(uuid, session.getSeq(), "", true, "Timeout.");
                }

                if (runnable.isEnded()) {
                    if (runnable.isEndedOk()) {
                        /* update repository */
                        IResult result = runnable.getResult();
                        if (result != null) {
                            String json = Utility.objectToJson(result);
                            session.setResult(json);
                        } else {
                            session.setResult("");
                        }
                        session.setSeq(xferRequest.getSeq() + 1);
                        sessionRepoService.save(session);

                        return new XferResponse(uuid, xferRequest.getSeq() + 1, "", true, "");
                    } else {
                        sessionRepoService.deleteByUuid(uuid);
                        return new XferResponse(uuid, session.getSeq(), "", true, "Script execution error.");
                    }
                }

                /* update repository */
                session.setSeq(xferRequest.getSeq() + 1);
                sessionRepoService.save(session);

                byte[] command = runnable.getCommandBuffer();
                return new XferResponse(uuid, xferRequest.getSeq() + 1,
                        Utility.byteArrayToBase64(command), false, "");
            } else {
                /* out-of-sync */
                return new XferResponse(uuid, session.getSeq(), "", false, "Sequence out of sync, please correct the sequence number.");
            }
        } catch (Exception e) {
            log.error("",e);
        }

        servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return null;
    }

    public StopResponse processApiV1SessionStop(@RequestBody StopRequest stopRequest, HttpServletResponse servletResponse) {
        try {
            String uuid = stopRequest.getUuid();
            Session session = sessionRepoService.findByUuid(uuid);
            CommandSetRunnable runnable;
            String result;

            /* check uuid validity */
            if (session == null) {
                return new StopResponse("", "", "Invalid uuid.");
            }

            /* check if result is available */
            if (session.getResult() == null) {
                result = "";
            } else {
                result = session.getResult();
            }

            runnable = (CommandSetRunnable)threadService.getRunnable(session.getTid());

            /* check if thread is still alive */
            if (runnable != null) {
                /* try to kill the thread */
                runnable.interrupt();
            }

            /* remove the session from repo */
            sessionRepoService.deleteByUuid(uuid);

            return new StopResponse(session.getScript(), result, "");

        } catch (Exception e) {
            log.error("",e);
        }
        servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return null;
    }
}
