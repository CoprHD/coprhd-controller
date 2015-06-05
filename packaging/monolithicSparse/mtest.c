
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/uio.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <errno.h>

main(int argc, char **argv) {
    static char buf[64*1024];
    struct iovec iov;
    iov.iov_base = (void *)buf;
    while (1) {
        ssize_t n = read(0, buf, sizeof(buf));
        if (n == -1) {
            perror("read");
            exit(1);
        } else if (n == 0) {
            close(1);
            exit(0);
        } else {
            iov.iov_len = n;
            if (writev(1, &iov, 1) != n) {
                perror("writev");
                exit(1);
             }
        }
    }
}
