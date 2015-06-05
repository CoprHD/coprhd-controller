/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */

#define _XOPEN_SOURCE 500
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

#define __USE_GNU 1
#include <dlfcn.h>

static void _debug(const char *fmt, ...) {
    char fmtbuf[1024];
    va_list ap;

    va_start(ap, fmt);
    sprintf(fmtbuf, "DEBUG: %s\n", fmt);
    vfprintf(stderr, fmtbuf, ap);
}

#if DEBUG
#define debug if (1) _debug
#else
#define debug if (0) _debug
#endif

static void fatal(const char *fmt, ...) {
    char fmtbuf[1024];
    va_list ap;

    va_start(ap, fmt);
    sprintf(fmtbuf, "FATAL: %s\n", fmt);
    vfprintf(stderr, fmtbuf, ap);
    exit(99);
}

static off_t __lseek(int fd, off_t offset, int whence) {
    off_t off = lseek(fd, offset, whence);
    if (off == (off_t)-1) {
        fatal("lseek(%d, %lld, %d): %s", fd, offset, whence, strerror(errno));
    }
    return off;
}

static ssize_t __pwrite(int fd, const void *buf, size_t size, off_t offset) {
    ssize_t n = pwrite(fd, buf, size, offset);
    if (n != size) {
        fatal("pwrite(%d, 0x%llx, %llu, %lld): %s", fd, buf, size, offset, strerror(errno));
    }
    return n;
}

static int __fstat(int fd, struct stat *st) {
    if (fstat(fd, st) == -1) {
        fatal("fstat(%d, %llx): %s", fd, st, strerror(errno));
    }
    return 0;
}

static int __ftruncate(int fd, off_t offset) {
    if (ftruncate(fd, offset) == -1) {
        fatal("ftruncate(%d, %lld): %s", fd, offset, strerror(errno));
    }
    return 0;
}

typedef int     (*close_t)(int);
typedef ssize_t (*writev_t)(int, const struct iovec *, int);
typedef ssize_t (*pwritev_t)(int, const struct iovec *, int, off_t);

static int __close(int fd) {
    static close_t libc_close = NULL;

    if (libc_close == NULL && (libc_close = (close_t)(dlsym(RTLD_NEXT, "close"))) == NULL) {
        fatal("dlsym(\"close\"): %s\n", dlerror());
    }

    return libc_close(fd);
}

static ssize_t __writev(int fd, const struct iovec *iov, int iovcnt) {
    static writev_t libc_writev = NULL;

    if (libc_writev == NULL && (libc_writev = (writev_t)(dlsym(RTLD_NEXT, "writev"))) == NULL) {
        fatal("dlsym(\"writev\"): %s\n", dlerror());
    }

    return libc_writev(fd, iov, iovcnt);
}

static ssize_t __pwritev(int fd, const struct iovec *iov, int iovcnt, off_t off) {
    static pwritev_t libc_pwritev = NULL;

    if (libc_pwritev == NULL && (libc_pwritev = (pwritev_t)(dlsym(RTLD_NEXT, "pwritev"))) == NULL) {
        fatal("dlsym(\"pwritev\"): %s\n", dlerror());
    }

    return libc_pwritev(fd, iov, iovcnt, off);
}


static off_t __offsets[1024];

static void __adjsize(int fd) {
    struct stat st;

    if (fd > 1023 || __offsets[fd] == 0) {
        return;
    }

    __fstat(fd, &st);
    if (st.st_size < __offsets[fd]) {
        __ftruncate(fd, __offsets[fd]);
    }

    __offsets[fd] = 0;
}

static int __zerocmp(const char *buf, size_t buflen) {
    int i;
    for (i=0; i < buflen; i++) {
        if (buf[i]) {
            return 0;
        }
    }
    debug("__zerocmp(): 1");
    return 1;
}

static ssize_t __sparse_pwrite(int fd, const char *buf, const size_t size, off_t off) { 
    static const size_t BS = 4096;
    int    z   = -1;
    size_t pos = 0;
    size_t len = 0;

    debug("__sparse_pwritev(%d, size=%llu, off=%lld)", fd, size, off);
 
    if (off < 0) {
        fatal("sparse_pwritev(%d, off=%lld)", fd, off);
    }

#define min(a,b) ((a) < (b) ? (a) : (b))
#define max(a,b) ((a) > (b) ? (a) : (b))

    for (;;) {
        size_t n = min(size - pos - len, BS);
        
        if (pos == 0 && len == 0) {
            n = min(n, BS - off % BS);
            z = __zerocmp(buf + pos + len, n);
            debug("pos=%d len=%d n=%d z=%d [%d]: 000", pos, len, n, z, __zerocmp(buf + pos + len, n));
            len = n;
        } else if (n != 0 && __zerocmp(buf + pos + len, n) == z) {
            debug("pos=%d len=%d n=%d z=%d [%d]: 111", pos, len, n, z, __zerocmp(buf + pos + len, n));
            len += n;
        } else {
            debug("pos=%d len=%d n=%d z=%d [%d]: 222", pos, len, n, z, __zerocmp(buf + pos + len, n));
            if (len == 0) {
                ;
            } else if(z == 1) {
                debug("pos=%d len=%d n=%d z=%d [%d]: skip",   pos, len, n, z, __zerocmp(buf + pos + len, n));
            } else {
                debug("pos=%d len=%d n=%d z=%d [%d]: pwrite", pos, len, n, z, __zerocmp(buf + pos + len, n));
                __pwrite(fd, buf + pos, len, off);
            }

            off += len;
            __offsets[fd] = max(__offsets[fd], off);

            if (n == 0) {
                return size;
            }

            pos += len;
            len  = n;
            z    = 1 - z;
        }
    }
}  

int close(int fd) {
    __adjsize(fd);
    return __close(fd);
}

ssize_t writev(int fd, const struct iovec *iov, int iovcnt) {
    debug("writev(%d, %d)", fd, iovcnt);

    if (fd > 1023 || iovcnt != 1 || iov->iov_len == 0) {
        return __writev(fd, iov, iovcnt);
    }

    __lseek(fd, __sparse_pwrite(fd, (char *)iov->iov_base, iov->iov_len, __lseek(fd, 0, SEEK_CUR)), SEEK_CUR);
    return iov->iov_len;
}

ssize_t pwritev64(int fd, const struct iovec *iov, int iovcnt, off_t off) {
    debug("pwritev(%d, %d)", fd, iovcnt);

    if (fd > 1023 || iovcnt != 1 || iov->iov_len == 0) {
        return __pwritev(fd, iov, iovcnt, off);
    }
  
    return __sparse_pwrite(fd, (char *)iov->iov_base, iov->iov_len, off);
}

