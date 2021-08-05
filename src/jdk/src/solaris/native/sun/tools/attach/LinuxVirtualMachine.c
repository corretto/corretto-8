/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>

#include "sun_tools_attach_LinuxVirtualMachine.h"

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

/*
 * Defines a callback that is invoked for each process
 */
typedef void (*ProcessCallback)(const pid_t pid, void* user_data);

/*
 * Invokes the callback function for each process
 */
static void forEachProcess(ProcessCallback f, void* user_data) {
    DIR* dir;
    struct dirent* ptr;

    /*
     * To locate the children we scan /proc looking for files that have a
     * position integer as a filename.
     */
    if ((dir = opendir("/proc")) == NULL) {
        return;
    }
    while ((ptr = readdir(dir)) != NULL) {
        pid_t pid;

        /* skip current/parent directories */
        if (strcmp(ptr->d_name, ".") == 0 || strcmp(ptr->d_name, "..") == 0) {
            continue;
        }

        /* skip files that aren't numbers */
        pid = (pid_t)atoi(ptr->d_name);
        if ((int)pid <= 0) {
            continue;
        }

        /* invoke the callback */
        (*f)(pid, user_data);
    }
    closedir(dir);
}


/*
 * Returns the parent pid of a given pid, or -1 if not found
 */
static pid_t getParent(pid_t pid) {
    char state;
    FILE* fp;
    char stat[2048];
    int statlen;
    char fn[32];
    int i, p;
    char* s;

    /*
     * try to open /proc/%d/stat
     */
    sprintf(fn, "/proc/%d/stat", pid);
    fp = fopen(fn, "r");
    if (fp == NULL) {
        return -1;
    }

    /*
     * The format is: pid (command) state ppid ...
     * As the command could be anything we must find the right most
     * ")" and then skip the white spaces that follow it.
     */
    statlen = fread(stat, 1, 2047, fp);
    stat[statlen] = '\0';
    fclose(fp);
    s = strrchr(stat, ')');
    if (s == NULL) {
        return -1;
    }
    do s++; while (isspace(*s));
    i = sscanf(s, "%c %d", &state, &p);
    return (pid_t)p;
}


/*
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    socket
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_LinuxVirtualMachine_socket
  (JNIEnv *env, jclass cls)
{
    int fd = socket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "socket");
    }
    return (jint)fd;
}

/*
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    connect
 * Signature: (ILjava/lang/String;)I
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_LinuxVirtualMachine_connect
  (JNIEnv *env, jclass cls, jint fd, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct sockaddr_un addr;
        int err = 0;

        addr.sun_family = AF_UNIX;
        strcpy(addr.sun_path, p);

        if (connect(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
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
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    isLinuxThreads
 * Signature: ()V
 */
JNIEXPORT jboolean JNICALL Java_sun_tools_attach_LinuxVirtualMachine_isLinuxThreads
  (JNIEnv *env, jclass cls)
{
# ifdef MUSL_LIBC
   return JNI_FALSE;
# else
# ifndef _CS_GNU_LIBPTHREAD_VERSION
# define _CS_GNU_LIBPTHREAD_VERSION 3
# endif
    size_t n;
    char* s;
    jboolean res;

    n = confstr(_CS_GNU_LIBPTHREAD_VERSION, NULL, 0);
    if (n <= 0) {
       /* glibc before 2.3.2 only has LinuxThreads */
       return JNI_TRUE;
    }

    s = (char *)malloc(n);
    if (s == NULL) {
        JNU_ThrowOutOfMemoryError(env, "malloc failed");
        return JNI_TRUE;
    }
    confstr(_CS_GNU_LIBPTHREAD_VERSION, s, n);

    /*
     * If the LIBPTHREAD version include "NPTL" then we know we
     * have the new threads library and not LinuxThreads
     */
    res = (jboolean)(strstr(s, "NPTL") == NULL);
    free(s);
    return res;
# endif
}

/*
 * Structure and callback function used to count the children of
 * a given process, and record the pid of the "manager thread".
 */
typedef struct {
    pid_t ppid;
    int count;
    pid_t mpid;
} ChildCountContext;

static void ChildCountCallback(const pid_t pid, void* user_data) {
    ChildCountContext* context = (ChildCountContext*)user_data;
    if (getParent(pid) == context->ppid) {
        context->count++;
        /*
         * Remember the pid of the first child. If the final count is
         * one then this is the pid of the LinuxThreads manager.
         */
        if (context->count == 1) {
            context->mpid = pid;
        }
    }
}

/*
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    getLinuxThreadsManager
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_LinuxVirtualMachine_getLinuxThreadsManager
  (JNIEnv *env, jclass cls, jint pid)
{
    ChildCountContext context;

    /*
     * Iterate over all processes to find how many children 'pid' has
     */
    context.ppid = pid;
    context.count = 0;
    context.mpid = (pid_t)0;
    forEachProcess(ChildCountCallback, (void*)&context);

    /*
     * If there's no children then this is likely the pid of the primordial
     * created by the launcher - in that case the LinuxThreads manager is the
     * parent of this process.
     */
    if (context.count == 0) {
        pid_t parent = getParent(pid);
        if ((int)parent > 0) {
            return (jint)parent;
        }
    }

    /*
     * There's one child so this is likely the embedded VM case where the
     * the primordial thread == LinuxThreads initial thread. The LinuxThreads
     * manager in that case is the child.
     */
    if (context.count == 1) {
        return (jint)context.mpid;
    }

    /*
     * If we get here it's most likely we were given the wrong pid
     */
    JNU_ThrowIOException(env, "Unable to get pid of LinuxThreads manager thread");
    return -1;
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
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    sendQuitToChildrenOf
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_LinuxVirtualMachine_sendQuitToChildrenOf
  (JNIEnv *env, jclass cls, jint pid)
{
    SendQuitContext context;
    context.ppid = (pid_t)pid;

    /*
     * Iterate over all children of 'pid' and send a QUIT signal to each.
     */
    forEachProcess(SendQuitCallback, (void*)&context);
}

/*
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    sendQuitTo
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_LinuxVirtualMachine_sendQuitTo
  (JNIEnv *env, jclass cls, jint pid)
{
    if (kill((pid_t)pid, SIGQUIT)) {
        JNU_ThrowIOExceptionWithLastError(env, "kill");
    }
}

/*
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    checkPermissions
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_LinuxVirtualMachine_checkPermissions
  (JNIEnv *env, jclass cls, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct stat64 sb;
        uid_t uid, gid;
        int res;

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
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_LinuxVirtualMachine_close
  (JNIEnv *env, jclass cls, jint fd)
{
    int res;
    RESTARTABLE(close(fd), res);
}

/*
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    read
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_LinuxVirtualMachine_read
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
 * Class:     sun_tools_attach_LinuxVirtualMachine
 * Method:    write
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_LinuxVirtualMachine_write
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
