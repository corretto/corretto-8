/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include <winsock2.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <sys/types.h>
#include <process.h>
#include <iphlpapi.h>
#include <icmpapi.h>
#include <WinError.h>

#include "java_net_InetAddress.h"
#include "java_net_Inet4AddressImpl.h"
#include "net_util.h"
#include "icmp.h"


/*
 * Returns true if hostname is in dotted IP address format. Note that this
 * function performs a syntax check only. For each octet it just checks that
 * the octet is at most 3 digits.
 */
jboolean isDottedIPAddress(const char *hostname, unsigned int *addrp) {
    char *c = (char *)hostname;
    int octets = 0;
    unsigned int cur = 0;
    int digit_cnt = 0;

    while (*c) {
        if (*c == '.') {
            if (digit_cnt == 0) {
                return JNI_FALSE;
            } else {
                if (octets < 4) {
                    addrp[octets++] = cur;
                    cur = 0;
                    digit_cnt = 0;
                } else {
                    return JNI_FALSE;
                }
            }
            c++;
            continue;
        }

        if ((*c < '0') || (*c > '9')) {
            return JNI_FALSE;
        }

        digit_cnt++;
        if (digit_cnt > 3) {
            return JNI_FALSE;
        }

        /* don't check if current octet > 255 */
        cur = cur*10 + (*c - '0');

        /* Move onto next character and check for EOF */
        c++;
        if (*c == '\0') {
            if (octets < 4) {
                addrp[octets++] = cur;
            } else {
                return JNI_FALSE;
            }
        }
    }

    return (jboolean)(octets == 4);
}

/*
 * Inet4AddressImpl
 */

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    getLocalHostName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getLocalHostName (JNIEnv *env, jobject this) {
    char hostname[256];

    if (gethostname(hostname, sizeof hostname) == -1) {
        strcpy(hostname, "localhost");
    }
    return JNU_NewStringPlatform(env, hostname);
}

/*
 * Find an internet address for a given hostname.  Not this this
 * code only works for addresses of type INET. The translation
 * of %d.%d.%d.%d to an address (int) occurs in java now, so the
 * String "host" shouldn't be a %d.%d.%d.%d string. The only
 * exception should be when any of the %d are out of range and
 * we fallback to a lookup.
 *
 * Class:     java_net_Inet4AddressImpl
 * Method:    lookupAllHostAddr
 * Signature: (Ljava/lang/String;)[[B
 *
 * This is almost shared code
 */

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet4AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    struct hostent *hp;
    unsigned int addr[4];

    jobjectArray ret = NULL;

    initInetAddressIDs(env);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host argument");
        return NULL;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

    /*
     * The NT/2000 resolver tolerates a space in front of localhost. This
     * is not consistent with other implementations of gethostbyname.
     * In addition we must do a white space check on Solaris to avoid a
     * bug whereby 0.0.0.0 is returned if any host name has a white space.
     */
    if (isspace(hostname[0])) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
        goto cleanupAndReturn;
    }

    /*
     * If the format is x.x.x.x then don't use gethostbyname as Windows
     * is unable to handle octets which are out of range.
     */
    if (isDottedIPAddress(hostname, &addr[0])) {
        unsigned int address;
        jobject iaObj;

        /*
         * Are any of the octets out of range?
         */
        if (addr[0] > 255 || addr[1] > 255 || addr[2] > 255 || addr[3] > 255) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
            goto cleanupAndReturn;
        }

        /*
         * Return an byte array with the populated address.
         */
        address = (addr[3]<<24) & 0xff000000;
        address |= (addr[2]<<16) & 0xff0000;
        address |= (addr[1]<<8) & 0xff00;
        address |= addr[0];

        ret = (*env)->NewObjectArray(env, 1, ia_class, NULL);

        if (IS_NULL(ret)) {
            goto cleanupAndReturn;
        }

        iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
        if (IS_NULL(iaObj)) {
          ret = NULL;
          goto cleanupAndReturn;
        }
        setInetAddress_addr(env, iaObj, ntohl(address));
        if ((*env)->ExceptionCheck(env))
            goto cleanupAndReturn;
        (*env)->SetObjectArrayElement(env, ret, 0, iaObj);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return ret;
    }

    /*
     * Perform the lookup
     */
    if ((hp = gethostbyname((char*)hostname)) != NULL) {
        struct in_addr **addrp = (struct in_addr **) hp->h_addr_list;
        int len = sizeof(struct in_addr);
        int i = 0;

        while (*addrp != (struct in_addr *) 0) {
            i++;
            addrp++;
        }

        ret = (*env)->NewObjectArray(env, i, ia_class, NULL);

        if (IS_NULL(ret)) {
            goto cleanupAndReturn;
        }

        addrp = (struct in_addr **) hp->h_addr_list;
        i = 0;
        while (*addrp != (struct in_addr *) 0) {
          jobject iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
          if (IS_NULL(iaObj)) {
            ret = NULL;
            goto cleanupAndReturn;
          }
          setInetAddress_addr(env, iaObj, ntohl((*addrp)->s_addr));
          if ((*env)->ExceptionCheck(env))
              goto cleanupAndReturn;
          setInetAddress_hostName(env, iaObj, host);
          if ((*env)->ExceptionCheck(env))
              goto cleanupAndReturn;
          (*env)->SetObjectArrayElement(env, ret, i, iaObj);
          addrp++;
          i++;
        }
    } else if (WSAGetLastError() == WSATRY_AGAIN) {
        NET_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "UnknownHostException",
                                     hostname);
    } else {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
    }

cleanupAndReturn:
    JNU_ReleaseStringPlatformChars(env, host, hostname);
    return ret;
}

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    getHostByAddr
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getHostByAddr(JNIEnv *env, jobject this,
                                            jbyteArray addrArray) {
    struct hostent *hp;
    jbyte caddr[4];
    jint addr;
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    addr = ((caddr[0]<<24) & 0xff000000);
    addr |= ((caddr[1] <<16) & 0xff0000);
    addr |= ((caddr[2] <<8) & 0xff00);
    addr |= (caddr[3] & 0xff);
    addr = htonl(addr);

    hp = gethostbyaddr((char *)&addr, sizeof(addr), AF_INET);
    if (hp == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", 0);
        return NULL;
    }
    if (hp->h_name == NULL) { /* Deal with bug in Windows XP */
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", 0);
        return NULL;
    }
    return JNU_NewStringPlatform(env, hp->h_name);
}


static BOOL
WindowsVersionCheck(WORD wMajorVersion, WORD wMinorVersion, WORD wServicePackMajor) {
    OSVERSIONINFOEXW osvi = { sizeof(osvi), 0, 0, 0, 0, {0}, 0, 0 };
    DWORDLONG const dwlConditionMask = VerSetConditionMask(
        VerSetConditionMask(
        VerSetConditionMask(
                0, VER_MAJORVERSION, VER_GREATER_EQUAL),
                   VER_MINORVERSION, VER_GREATER_EQUAL),
                   VER_SERVICEPACKMAJOR, VER_GREATER_EQUAL);

    osvi.dwMajorVersion = wMajorVersion;
    osvi.dwMinorVersion = wMinorVersion;
    osvi.wServicePackMajor = wServicePackMajor;

    return VerifyVersionInfoW(&osvi, VER_MAJORVERSION | VER_MINORVERSION | VER_SERVICEPACKMAJOR, dwlConditionMask) != FALSE;
}

static BOOL
isVistaSP1OrGreater() {
    return WindowsVersionCheck(HIBYTE(_WIN32_WINNT_VISTA), LOBYTE(_WIN32_WINNT_VISTA), 1);
}

static jboolean
tcp_ping4(JNIEnv *env,
          jbyteArray addrArray,
          jint timeout,
          jbyteArray ifArray,
          jint ttl)
{
    jint addr;
    jbyte caddr[4];
    jint fd;
    struct sockaddr_in him;
    struct sockaddr_in* netif = NULL;
    struct sockaddr_in inf;
    int len = 0;
    WSAEVENT hEvent;
    int connect_rv = -1;
    int sz;

    /**
     * Convert IP address from byte array to integer
     */
    sz = (*env)->GetArrayLength(env, addrArray);
    if (sz != 4) {
        return JNI_FALSE;
    }
    memset((char *) &him, 0, sizeof(him));
    memset((char *) caddr, 0, sizeof(caddr));
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    addr = ((caddr[0]<<24) & 0xff000000);
    addr |= ((caddr[1] <<16) & 0xff0000);
    addr |= ((caddr[2] <<8) & 0xff00);
    addr |= (caddr[3] & 0xff);
    addr = htonl(addr);
    /**
     * Socket address
     */
    him.sin_addr.s_addr = addr;
    him.sin_family = AF_INET;
    len = sizeof(him);

    /**
     * If a network interface was specified, let's convert its address
     * as well.
     */
    if (!(IS_NULL(ifArray))) {
        memset((char *) caddr, 0, sizeof(caddr));
        (*env)->GetByteArrayRegion(env, ifArray, 0, 4, caddr);
        addr = ((caddr[0]<<24) & 0xff000000);
        addr |= ((caddr[1] <<16) & 0xff0000);
        addr |= ((caddr[2] <<8) & 0xff00);
        addr |= (caddr[3] & 0xff);
        addr = htonl(addr);
        inf.sin_addr.s_addr = addr;
        inf.sin_family = AF_INET;
        inf.sin_port = 0;
        netif = &inf;
    }

    /*
     * Can't create a raw socket, so let's try a TCP socket
     */
    fd = NET_Socket(AF_INET, SOCK_STREAM, 0);
    if (fd == JVM_IO_ERR) {
        /* note: if you run out of fds, you may not be able to load
         * the exception class, and get a NoClassDefFoundError
         * instead.
         */
        NET_ThrowNew(env, WSAGetLastError(), "Can't create socket");
        return JNI_FALSE;
    }
    if (ttl > 0) {
        setsockopt(fd, IPPROTO_IP, IP_TTL, (const char *)&ttl, sizeof(ttl));
    }
    /*
     * A network interface was specified, so let's bind to it.
     */
    if (netif != NULL) {
        if (bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in)) < 0) {
            NET_ThrowNew(env, WSAGetLastError(), "Can't bind socket");
            closesocket(fd);
            return JNI_FALSE;
        }
    }

    /*
     * Make the socket non blocking so we can use select/poll.
     */
    hEvent = WSACreateEvent();
    WSAEventSelect(fd, hEvent, FD_READ|FD_CONNECT|FD_CLOSE);

    /* no need to use NET_Connect as non-blocking */
    him.sin_port = htons(7);    /* Echo */
    connect_rv = connect(fd, (struct sockaddr *)&him, len);

    /**
     * connection established or refused immediately, either way it means
     * we were able to reach the host!
     */
    if (connect_rv == 0 || WSAGetLastError() == WSAECONNREFUSED) {
        WSACloseEvent(hEvent);
        closesocket(fd);
        return JNI_TRUE;
    } else {
        int optlen;

        switch (WSAGetLastError()) {
        case WSAEHOSTUNREACH:   /* Host Unreachable */
        case WSAENETUNREACH:    /* Network Unreachable */
        case WSAENETDOWN:       /* Network is down */
        case WSAEPFNOSUPPORT:   /* Protocol Family unsupported */
            WSACloseEvent(hEvent);
            closesocket(fd);
            return JNI_FALSE;
        }

        if (WSAGetLastError() != WSAEWOULDBLOCK) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                                         "connect failed");
            WSACloseEvent(hEvent);
            closesocket(fd);
            return JNI_FALSE;
        }

        timeout = NET_Wait(env, fd, NET_WAIT_CONNECT, timeout);

        /* has connection been established */

        if (timeout >= 0) {
            optlen = sizeof(connect_rv);
            if (getsockopt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv,
                           &optlen) <0) {
                connect_rv = WSAGetLastError();
            }

            if (connect_rv == 0 || connect_rv == WSAECONNREFUSED) {
                WSACloseEvent(hEvent);
                closesocket(fd);
                return JNI_TRUE;
            }
        }
    }
    WSACloseEvent(hEvent);
    closesocket(fd);
    return JNI_FALSE;
}

/**
 * ping implementation.
 * Send a ICMP_ECHO_REQUEST packet every second until either the timeout
 * expires or a answer is received.
 * Returns true is an ECHO_REPLY is received, otherwise, false.
 */
static jboolean
ping4(JNIEnv *env,
      unsigned long src_addr,
      unsigned long dest_addr,
      jint timeout,
      HANDLE hIcmpFile)
{
    // See https://msdn.microsoft.com/en-us/library/aa366050%28VS.85%29.aspx

    DWORD dwRetVal = 0;
    char SendData[32] = {0};
    LPVOID ReplyBuffer = NULL;
    DWORD ReplySize = 0;
    jboolean ret = JNI_FALSE;

    // https://msdn.microsoft.com/en-us/library/windows/desktop/aa366051%28v=vs.85%29.aspx
    ReplySize = sizeof(ICMP_ECHO_REPLY)   // The buffer should be large enough
                                          // to hold at least one ICMP_ECHO_REPLY
                                          // structure
                + sizeof(SendData)        // plus RequestSize bytes of data.
                + 8;                      // This buffer should also be large enough
                                          // to also hold 8 more bytes of data
                                          // (the size of an ICMP error message)

    ReplyBuffer = (VOID*) malloc(ReplySize);
    if (ReplyBuffer == NULL) {
        IcmpCloseHandle(hIcmpFile);
        NET_ThrowNew(env, -1, "Unable to allocate memory");
        return JNI_FALSE;
    }

    if (src_addr == 0) {
        dwRetVal = IcmpSendEcho(hIcmpFile,  // HANDLE IcmpHandle,
                                dest_addr,  // IPAddr DestinationAddress,
                                SendData,   // LPVOID RequestData,
                                sizeof(SendData),   // WORD RequestSize,
                                NULL,       // PIP_OPTION_INFORMATION RequestOptions,
                                ReplyBuffer,// LPVOID ReplyBuffer,
                                ReplySize,  // DWORD ReplySize,
                                // Note: IcmpSendEcho and its derivatives
                                // seem to have an undocumented minimum
                                // timeout of 1000ms below which the
                                // api behaves inconsistently.
                                (timeout < 1000) ? 1000 : timeout);   // DWORD Timeout
    } else {
        dwRetVal = IcmpSendEcho2Ex(hIcmpFile,  // HANDLE IcmpHandle,
                                   NULL,       // HANDLE Event
                                   NULL,       // PIO_APC_ROUTINE ApcRoutine
                                   NULL,       // ApcContext
                                   src_addr,   // IPAddr SourceAddress,
                                   dest_addr,  // IPAddr DestinationAddress,
                                   SendData,   // LPVOID RequestData,
                                   sizeof(SendData),   // WORD RequestSize,
                                   NULL,       // PIP_OPTION_INFORMATION RequestOptions,
                                   ReplyBuffer,// LPVOID ReplyBuffer,
                                   ReplySize,  // DWORD ReplySize,
                                   (timeout < 1000) ? 1000 : timeout);   // DWORD Timeout
    }

    if (dwRetVal == 0) { // if the call failed
        TCHAR *buf;
        DWORD err = WSAGetLastError();
        switch (err) {
            case ERROR_NO_NETWORK:
            case ERROR_NETWORK_UNREACHABLE:
            case ERROR_HOST_UNREACHABLE:
            case ERROR_PROTOCOL_UNREACHABLE:
            case ERROR_PORT_UNREACHABLE:
            case ERROR_REQUEST_ABORTED:
            case ERROR_INCORRECT_ADDRESS:
            case ERROR_HOST_DOWN:
            case ERROR_INVALID_COMPUTERNAME:
            case ERROR_INVALID_NETNAME:
            case WSAEHOSTUNREACH:   /* Host Unreachable */
            case WSAENETUNREACH:    /* Network Unreachable */
            case WSAENETDOWN:       /* Network is down */
            case WSAEPFNOSUPPORT:   /* Protocol Family unsupported */
            case IP_REQ_TIMED_OUT:
                break;
            default:
                FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
                        NULL, err, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                        (LPTSTR)&buf, 0, NULL);
                NET_ThrowNew(env, err, buf);
                LocalFree(buf);
                break;
        }
    } else {
        PICMP_ECHO_REPLY pEchoReply = (PICMP_ECHO_REPLY)ReplyBuffer;

        // This is to take into account the undocumented minimum
        // timeout mentioned in the IcmpSendEcho call above.
        // We perform an extra check to make sure that our
        // roundtrip time was less than our desired timeout
        // for cases where that timeout is < 1000ms.
        if (pEchoReply->Status == IP_SUCCESS
                && (int)pEchoReply->RoundTripTime <= timeout)
        {
            ret = JNI_TRUE;
        }
    }

    free(ReplyBuffer);
    IcmpCloseHandle(hIcmpFile);

    return ret;
}

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    isReachable0
 * Signature: ([bI[bI)Z
 */
JNIEXPORT jboolean JNICALL
Java_java_net_Inet4AddressImpl_isReachable0(JNIEnv *env, jobject this,
                                            jbyteArray addrArray,
                                            jint timeout,
                                            jbyteArray ifArray,
                                            jint ttl) {

    if (isVistaSP1OrGreater()) {
        jint src_addr = 0;
        jint dest_addr = 0;
        jbyte caddr[4];
        int sz;
        HANDLE hIcmpFile;

        /**
         * Convert IP address from byte array to integer
         */
        sz = (*env)->GetArrayLength(env, addrArray);
        if (sz != 4) {
          return JNI_FALSE;
        }
        memset((char *) caddr, 0, sizeof(caddr));
        (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
        dest_addr = ((caddr[0]<<24) & 0xff000000);
        dest_addr |= ((caddr[1] <<16) & 0xff0000);
        dest_addr |= ((caddr[2] <<8) & 0xff00);
        dest_addr |= (caddr[3] & 0xff);
        dest_addr = htonl(dest_addr);

        /**
         * If a network interface was specified, let's convert its address
         * as well.
         */
        if (!(IS_NULL(ifArray))) {
            memset((char *) caddr, 0, sizeof(caddr));
            (*env)->GetByteArrayRegion(env, ifArray, 0, 4, caddr);
            src_addr = ((caddr[0]<<24) & 0xff000000);
            src_addr |= ((caddr[1] <<16) & 0xff0000);
            src_addr |= ((caddr[2] <<8) & 0xff00);
            src_addr |= (caddr[3] & 0xff);
            src_addr = htonl(src_addr);
        }

        hIcmpFile = IcmpCreateFile();
        if (hIcmpFile == INVALID_HANDLE_VALUE) {
            int err = WSAGetLastError();
            if (err == ERROR_ACCESS_DENIED) {
                // fall back to TCP echo if access is denied to ICMP
                return tcp_ping4(env, addrArray, timeout, ifArray, ttl);
            } else {
                NET_ThrowNew(env, err, "Unable to create ICMP file handle");
                return JNI_FALSE;
            }
        } else {
            return ping4(env, src_addr, dest_addr, timeout, hIcmpFile);
        }
    } else {
        tcp_ping4(env, addrArray, timeout, ifArray, ttl);
    }
}
