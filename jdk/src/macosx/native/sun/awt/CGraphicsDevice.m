/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#import "LWCToolkit.h"
#import "ThreadUtilities.h"

/*
 * Convert the mode string to the more convinient bits per pixel value
 */
static int getBPPFromModeString(CFStringRef mode)
{
    if ((CFStringCompare(mode, CFSTR(kIO30BitDirectPixels), kCFCompareCaseInsensitive) == kCFCompareEqualTo)) {
        // This is a strange mode, where we using 10 bits per RGB component and pack it into 32 bits
        // Java is not ready to work with this mode but we have to specify it as supported
        return 30;
    }
    else if (CFStringCompare(mode, CFSTR(IO32BitDirectPixels), kCFCompareCaseInsensitive) == kCFCompareEqualTo) {
        return 32;
    }
    else if (CFStringCompare(mode, CFSTR(IO16BitDirectPixels), kCFCompareCaseInsensitive) == kCFCompareEqualTo) {
        return 16;
    }
    else if (CFStringCompare(mode, CFSTR(IO8BitIndexedPixels), kCFCompareCaseInsensitive) == kCFCompareEqualTo) {
        return 8;
    }

    return 0;
}

static BOOL isValidDisplayMode(CGDisplayModeRef mode){
    return (1 < CGDisplayModeGetWidth(mode) && 1 < CGDisplayModeGetHeight(mode));
}

static CFMutableArrayRef getAllValidDisplayModes(jint displayID){
    CFArrayRef allModes = CGDisplayCopyAllDisplayModes(displayID, NULL);

    CFIndex numModes = CFArrayGetCount(allModes);
    CFMutableArrayRef validModes = CFArrayCreateMutable(kCFAllocatorDefault, numModes + 1, &kCFTypeArrayCallBacks);

    CFIndex n;
    for (n=0; n < numModes; n++) {
        CGDisplayModeRef cRef = (CGDisplayModeRef) CFArrayGetValueAtIndex(allModes, n);
        if (cRef != NULL && isValidDisplayMode(cRef)) {
            CFArrayAppendValue(validModes, cRef);
        }
    }
    CFRelease(allModes);
    
    CGDisplayModeRef currentMode = CGDisplayCopyDisplayMode(displayID);

    BOOL containsCurrentMode = NO;
    numModes = CFArrayGetCount(validModes);
    for (n=0; n < numModes; n++) {
        if(CFArrayGetValueAtIndex(validModes, n) == currentMode){
            containsCurrentMode = YES;
            break;
        }
    }

    if (!containsCurrentMode) {
        CFArrayAppendValue(validModes, currentMode);
    }
    CGDisplayModeRelease(currentMode);

    return validModes;
}

/*
 * Find the best possible match in the list of display modes that we can switch to based on
 * the provided parameters.
 */
static CGDisplayModeRef getBestModeForParameters(CFArrayRef allModes, int w, int h, int bpp, int refrate) {
    CGDisplayModeRef bestGuess = NULL;
    CFIndex numModes = CFArrayGetCount(allModes), n;

    for(n = 0; n < numModes; n++ ) {
        CGDisplayModeRef cRef = (CGDisplayModeRef) CFArrayGetValueAtIndex(allModes, n);
        if(cRef == NULL) {
            continue;
        }
        CFStringRef modeString = CGDisplayModeCopyPixelEncoding(cRef);
        int thisBpp = getBPPFromModeString(modeString);
        CFRelease(modeString);
        int thisH = (int)CGDisplayModeGetHeight(cRef);
        int thisW = (int)CGDisplayModeGetWidth(cRef);
        if (thisBpp != bpp || thisH != h || thisW != w) {
            // One of the key parameters does not match
            continue;
        }

        if (refrate == 0) { // REFRESH_RATE_UNKNOWN
            return cRef;
        }

        // Refresh rate might be 0 in display mode and we ask for specific display rate
        // but if we do not find exact match then 0 refresh rate might be just Ok
        int thisRefrate = (int)CGDisplayModeGetRefreshRate(cRef);
        if (thisRefrate == refrate) {
            // Exact match
            return cRef;
        }
        if (thisRefrate == 0) {
            // Not exactly what was asked for, but may fit our needs if we don't find an exact match
            bestGuess = cRef;
        }
    }
    return bestGuess;
}

/*
 * Create a new java.awt.DisplayMode instance based on provided CGDisplayModeRef
 */
static jobject createJavaDisplayMode(CGDisplayModeRef mode, JNIEnv *env, jint displayID) {
    jobject ret = NULL;
    jint h, w, bpp, refrate;
    JNF_COCOA_ENTER(env);
    CFStringRef currentBPP = CGDisplayModeCopyPixelEncoding(mode);
    bpp = getBPPFromModeString(currentBPP);
    refrate = CGDisplayModeGetRefreshRate(mode);
    h = CGDisplayModeGetHeight(mode);
    w = CGDisplayModeGetWidth(mode);
    CFRelease(currentBPP);
    static JNF_CLASS_CACHE(jc_DisplayMode, "java/awt/DisplayMode");
    static JNF_CTOR_CACHE(jc_DisplayMode_ctor, jc_DisplayMode, "(IIII)V");
    ret = JNFNewObject(env, jc_DisplayMode_ctor, w, h, bpp, refrate);
    JNF_COCOA_EXIT(env);
    return ret;
}


/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeGetXResolution
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL
Java_sun_awt_CGraphicsDevice_nativeGetXResolution
  (JNIEnv *env, jclass class, jint displayID)
{
    // TODO: this is the physically correct answer, but we probably want
    // to use NSScreen API instead...
    CGSize size = CGDisplayScreenSize(displayID);
    CGRect rect = CGDisplayBounds(displayID);
    // 1 inch == 25.4 mm
    jfloat inches = size.width / 25.4f;
    jfloat dpi = rect.size.width / inches;
    return dpi;
}

/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeGetYResolution
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL
Java_sun_awt_CGraphicsDevice_nativeGetYResolution
  (JNIEnv *env, jclass class, jint displayID)
{
    // TODO: this is the physically correct answer, but we probably want
    // to use NSScreen API instead...
    CGSize size = CGDisplayScreenSize(displayID);
    CGRect rect = CGDisplayBounds(displayID);
    // 1 inch == 25.4 mm
    jfloat inches = size.height / 25.4f;
    jfloat dpi = rect.size.height / inches;
    return dpi;
}

/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeGetScreenInsets
 * Signature: (I)D
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_CGraphicsDevice_nativeGetScreenInsets
  (JNIEnv *env, jclass class, jint displayID)
{
    jobject ret = NULL;
    __block NSRect frame = NSZeroRect;
    __block NSRect visibleFrame = NSZeroRect;
JNF_COCOA_ENTER(env);
    
    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        NSArray *screens = [NSScreen screens];
        for (NSScreen *screen in screens) {
            NSDictionary *screenInfo = [screen deviceDescription];
            NSNumber *screenID = [screenInfo objectForKey:@"NSScreenNumber"];
            if ([screenID pointerValue] == displayID){
                frame = [screen frame];
                visibleFrame = [screen visibleFrame];
                break;
            }
        }
    }];
    // Convert between Cocoa's coordinate system and Java.
    jint bottom = visibleFrame.origin.y - frame.origin.y;
    jint top = frame.size.height - visibleFrame.size.height - bottom;
    jint left = visibleFrame.origin.x - frame.origin.x;
    jint right = frame.size.width - visibleFrame.size.width - left;
    
    static JNF_CLASS_CACHE(jc_Insets, "java/awt/Insets");
    static JNF_CTOR_CACHE(jc_Insets_ctor, jc_Insets, "(IIII)V");
    ret = JNFNewObject(env, jc_Insets_ctor, top, left, bottom, right);

JNF_COCOA_EXIT(env);

    return ret;
}

/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeResetDisplayMode
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_CGraphicsDevice_nativeResetDisplayMode
(JNIEnv *env, jclass class)
{
    CGRestorePermanentDisplayConfiguration();
}

/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeSetDisplayMode
 * Signature: (IIIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_CGraphicsDevice_nativeSetDisplayMode
(JNIEnv *env, jclass class, jint displayID, jint w, jint h, jint bpp, jint refrate)
{
    JNF_COCOA_ENTER(env);
    CFArrayRef allModes = getAllValidDisplayModes(displayID);
    CGDisplayModeRef closestMatch = getBestModeForParameters(allModes, (int)w, (int)h, (int)bpp, (int)refrate);
    
    __block CGError retCode = kCGErrorSuccess;
    if (closestMatch != NULL) {
        CGDisplayModeRetain(closestMatch);
        [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
            CGDisplayConfigRef config;
            retCode = CGBeginDisplayConfiguration(&config);
            if (retCode == kCGErrorSuccess) {
                CGConfigureDisplayWithDisplayMode(config, displayID, closestMatch, NULL);
                retCode = CGCompleteDisplayConfiguration(config, kCGConfigureForAppOnly);
            }
            CGDisplayModeRelease(closestMatch);
        }];
    } else {
        [JNFException raise:env as:kIllegalArgumentException reason:"Invalid display mode"];
    }

    if (retCode != kCGErrorSuccess){
        [JNFException raise:env as:kIllegalArgumentException reason:"Unable to set display mode!"];
    }
    CFRelease(allModes);
    JNF_COCOA_EXIT(env);
}
/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeGetDisplayMode
 * Signature: (I)Ljava/awt/DisplayMode
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_CGraphicsDevice_nativeGetDisplayMode
(JNIEnv *env, jclass class, jint displayID)
{
    jobject ret = NULL;
    CGDisplayModeRef currentMode = CGDisplayCopyDisplayMode(displayID);
    ret = createJavaDisplayMode(currentMode, env, displayID);
    CGDisplayModeRelease(currentMode);
    return ret;
}

/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeGetDisplayMode
 * Signature: (I)[Ljava/awt/DisplayModes
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_awt_CGraphicsDevice_nativeGetDisplayModes
(JNIEnv *env, jclass class, jint displayID)
{
    jobjectArray jreturnArray = NULL;
    JNF_COCOA_ENTER(env);
    CFArrayRef allModes = getAllValidDisplayModes(displayID);

    CFIndex numModes = CFArrayGetCount(allModes);
    static JNF_CLASS_CACHE(jc_DisplayMode, "java/awt/DisplayMode");

    jreturnArray = JNFNewObjectArray(env, &jc_DisplayMode, (jsize) numModes);
    if (!jreturnArray) {
        NSLog(@"CGraphicsDevice can't create java array of DisplayMode objects");
        return nil;
    }

    CFIndex n;
    for (n=0; n < numModes; n++) {
        CGDisplayModeRef cRef = (CGDisplayModeRef) CFArrayGetValueAtIndex(allModes, n);
        if (cRef != NULL) {
            jobject oneMode = createJavaDisplayMode(cRef, env, displayID);
            (*env)->SetObjectArrayElement(env, jreturnArray, n, oneMode);
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
                continue;
            }
            (*env)->DeleteLocalRef(env, oneMode);
        }
    }
    CFRelease(allModes);
    JNF_COCOA_EXIT(env);

    return jreturnArray;
}

/*
 * Class:     sun_awt_CGraphicsDevice
 * Method:    nativeGetScaleFactor
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL
Java_sun_awt_CGraphicsDevice_nativeGetScaleFactor
(JNIEnv *env, jclass class, jint displayID)
{
    __block jdouble ret = 1.0f;

JNF_COCOA_ENTER(env);

    [ThreadUtilities performOnMainThreadWaiting:YES block:^(){
        NSArray *screens = [NSScreen screens];
        for (NSScreen *screen in screens) {
            NSDictionary *screenInfo = [screen deviceDescription];
            NSNumber *screenID = [screenInfo objectForKey:@"NSScreenNumber"];
            if ([screenID pointerValue] == displayID){
                if ([screen respondsToSelector:@selector(backingScaleFactor)]) {
                    ret = [screen backingScaleFactor];
                }
                break;
            }
        }
    }];

JNF_COCOA_EXIT(env);
    return ret;
}
