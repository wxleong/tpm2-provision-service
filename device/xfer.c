#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <poll.h>
#include <unistd.h>

int main(int argc, char **argv) {

    size_t len = 0;
    unsigned char *cmd = NULL;
    unsigned char *resp_payload = NULL;

    if (argc != 2)
    {
        fprintf(stderr, "error: missing input arg\n");
        goto exit;
    } else if (argc > 2)
    {
        fprintf(stderr, "error: too much arg\n");
        goto exit;
    }

    len = strlen(argv[1]);
    len /= 2;

    cmd = malloc(len);
    if (cmd == NULL)
    {
        fprintf(stderr, "error: malloc\n");
        goto exit;
    }

    for (size_t i = 0; i < len; i++) {
        sscanf(argv[1] + (i*2), "%2hhx", cmd + i);
    }

    /**
     *  Remark:
     *
     *  TPM device node connection.
     *
     *  /dev/tpmrm0 cannot be used here, because connection is closed after each TPM command,
     *  the resource manager will free all allocated resources on exit. This will create problems for
     *  some TPM commands, e.g., tpm2_createprimary (the created key at transient handle 0x80ffffff will be lost)
     *
     *  Workaround:
     *   - Use /dev/tpm0
     *   - To use /dev/tpmrm0, you need a single executable that does not close the connection after each TPM command
     */

    int fd = open ("/dev/tpm0", O_RDWR | O_NONBLOCK);
    if (fd < 0)
    {
        fprintf(stderr, "error: open\n");
        goto exit;
    }

    /* write command */

    ssize_t written = write(fd, cmd, len);
    if (written < 0)
    {
        fprintf(stderr, "error: write\n");
        goto exit;
    }

    /* polling */

    struct pollfd fds;
    int rc_poll, nfds = 1, millisec = 5000;
    fds.fd = fd;
    fds.events = POLLIN;
    rc_poll = poll(&fds, nfds, millisec);
    if (rc_poll < 0)
    {
        fprintf(stderr, "error: poll\n");
        goto exit;
    }

    /* read response header */

    unsigned char resp_header[10];
    len = 10;
    ssize_t header_len = read(fd, resp_header, len);
    if (header_len != len)
    {
        fprintf(stderr, "error: read response header\n");
        goto exit;
    }

    /* read response payload */

    len = ( resp_header[2] << 24 | resp_header[3] << 16 | resp_header[4] << 8 | resp_header[5] ) - len;

    resp_payload = malloc(len);
    if (resp_payload == NULL)
    {
        fprintf(stderr, "error: malloc\n");
        goto exit;
    }

    ssize_t payload_len = read(fd, resp_payload, len);
    if (payload_len != len)
    {
        fprintf(stderr, "error: read response payload\n");
        goto exit;
    }

    /* stdout resp_header */

    for (size_t i = 0; i < header_len; i++)
    {
        fprintf(stdout, "%02x", resp_header[i]);
    }

    /* stdout resp_payload */

    for (size_t i = 0; i < payload_len; i++)
    {
        fprintf(stdout, "%02x", resp_payload[i]);
    }

exit:
    close(fd);
    free(cmd);
    free(resp_payload);
    return 0;
}