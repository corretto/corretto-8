/*
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2015 SAP AG. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <signal.h>
#include <dirent.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>

/*
 * Based on 'LinuxVirtualMachine.c'. Non-relevant code has been removed and all
 * occurrences of the string "Linux" have been replaced by "Aix".
 */

#include "sun_tools_attach_AixVirtualMachine.h"

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)


/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    socket
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_AixVirtualMachine_socket
  (JNIEnv *env, jclass cls)
{
    int fd = socket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "socket");
    }
    /* added time out values */
    else {
        struct timeval tv;
        tv.tv_sec = 2 * 60;
        tv.tv_usec = 0;

        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (char*)&tv, sizeof(tv));
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, (char*)&tv, sizeof(tv));
    }
    return (jint)fd;
}

/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    connect
 * Signature: (ILjava/lang/String;)I
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_AixVirtualMachine_connect
  (JNIEnv *env, jclass cls, jint fd, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct sockaddr_un addr;
        int err = 0;

        /* added missing structure initialization */
        memset(&addr,0, sizeof(addr));
        addr.sun_family = AF_UNIX;
        strcpy(addr.sun_path, p);
        /* We must call bind with the actual socketaddr length. This is obligatory for AS400. */
        if (connect(fd, (struct sockaddr*)&addr, SUN_LEN(&addr)) == -1) {
            err = errno;
        }

        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, p);
        }

        /*
         * If the connect failed then we throw the appropriate exception
         * here (can't throw it before releasing the string as can't call
         * JNI with pending exception)
         */
        if (err != 0) {
            if (err == ENOENT) {
                JNU_ThrowByName(env, "java/io/FileNotFoundException", NULL);
            } else {
                char* msg = strdup(strerror(err));
                JNU_ThrowIOException(env, msg);
                if (msg != NULL) {
                    free(msg);
                }
            }
        }
    }
}


/*
 * Structure and callback function used to send a QUIT signal to all
 * children of a given process
 */
typedef struct {
    pid_t ppid;
} SendQuitContext;

static void SendQuitCallback(const pid_t pid, void* user_data) {
    SendQuitContext* context = (SendQuitContext*)user_data;
    pid_t parent = getParent(pid);
    if (parent == context->ppid) {
        kill(pid, SIGQUIT);
    }
}

/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    sendQuitTo
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_AixVirtualMachine_sendQuitTo
  (JNIEnv *env, jclass cls, jint pid)
{
    if (kill((pid_t)pid, SIGQUIT)) {
        JNU_ThrowIOExceptionWithLastError(env, "kill");
    }
}

/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    checkPermissions
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_AixVirtualMachine_checkPermissions
  (JNIEnv *env, jclass cls, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct stat64 sb;
        uid_t uid, gid;
        int res;
        /* added missing initialization of the stat64 buffer */
        memset(&sb, 0, sizeof(struct stat64));

        /*
         * Check that the path is owned by the effective uid/gid of this
         * process. Also check that group/other access is not allowed.
         */
        uid = geteuid();
        gid = getegid();

        res = stat64(p, &sb);
        if (res != 0) {
            /* save errno */
            res = errno;
        }

        if (res == 0) {
            char msg[100];
            jboolean isError = JNI_FALSE;
            if (sb.st_uid != uid) {
                jio_snprintf(msg, sizeof(msg)-1,
                    "file should be owned by the current user (which is %d) but is owned by %d", uid, sb.st_uid);
                isError = JNI_TRUE;
            } else if (sb.st_gid != gid) {
                jio_snprintf(msg, sizeof(msg)-1,
                    "file's group should be the current group (which is %d) but the group is %d", gid, sb.st_gid);
                isError = JNI_TRUE;
            } else if ((sb.st_mode & (S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH)) != 0) {
                jio_snprintf(msg, sizeof(msg)-1,
                    "file should only be readable and writable by the owner but has 0%03o access", sb.st_mode & 0777);
                isError = JNI_TRUE;
            }
            if (isError) {
                char buf[256];
                jio_snprintf(buf, sizeof(buf)-1, "well-known file %s is not secure: %s", p, msg);
                JNU_ThrowIOException(env, buf);
            }
        } else {
            char* msg = strdup(strerror(res));
            JNU_ThrowIOException(env, msg);
            if (msg != NULL) {
                free(msg);
            }
        }

        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, p);
        }
    }
}

/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_AixVirtualMachine_close
  (JNIEnv *env, jclass cls, jint fd)
{
    int res;
    /* Fixed deadlock when this call of close by the client is not seen by the attach server
     * which has accepted the (very short) connection already and is waiting for the request. But read don't get a byte,
     * because the close is lost without shutdown.
     */
    shutdown(fd, 2);
    RESTARTABLE(close(fd), res);
}

/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    read
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_AixVirtualMachine_read
  (JNIEnv *env, jclass cls, jint fd, jbyteArray ba, jint off, jint baLen)
{
    unsigned char buf[128];
    size_t len = sizeof(buf);
    ssize_t n;

    size_t remaining = (size_t)(baLen - off);
    if (len > remaining) {
        len = remaining;
    }

    RESTARTABLE(read(fd, buf, len), n);
    if (n == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "read");
    } else {
        if (n == 0) {
            n = -1;     // EOF
        } else {
            (*env)->SetByteArrayRegion(env, ba, off, (jint)n, (jbyte *)(buf));
        }
    }
    return n;
}

/*
 * Class:     sun_tools_attach_AixVirtualMachine
 * Method:    write
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_AixVirtualMachine_write
  (JNIEnv *env, jclass cls, jint fd, jbyteArray ba, jint off, jint bufLen)
{
    size_t remaining = bufLen;
    do {
        unsigned char buf[128];
        size_t len = sizeof(buf);
        int n;

        if (len > remaining) {
            len = remaining;
        }
        (*env)->GetByteArrayRegion(env, ba, off, len, (jbyte *)buf);

        RESTARTABLE(write(fd, buf, len), n);
        if (n > 0) {
            off += n;
            remaining -= n;
        } else {
            JNU_ThrowIOExceptionWithLastError(env, "write");
            return;
        }

    } while (remaining > 0);
}
