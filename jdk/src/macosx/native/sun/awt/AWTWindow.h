/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _AWTWINDOW_H
#define _AWTWINDOW_H

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "CMenuBar.h"
#import "LWCToolkit.h"


@class AWTView;

@interface AWTWindow : NSObject <NSWindowDelegate> {
@private
    JNFWeakJObjectWrapper *javaPlatformWindow;
    CMenuBar *javaMenuBar;
    NSSize javaMinSize;
    NSSize javaMaxSize;
    jint styleBits;
    BOOL isEnabled;
    NSWindow *nsWindow;
    AWTWindow *ownerWindow;
    jint preFullScreenLevel;
    BOOL isMinimizing;
}

// An instance of either AWTWindow_Normal or AWTWindow_Panel
@property (nonatomic, retain) NSWindow *nsWindow;

@property (nonatomic, retain) JNFWeakJObjectWrapper *javaPlatformWindow;
@property (nonatomic, retain) CMenuBar *javaMenuBar;
@property (nonatomic, retain) AWTWindow *ownerWindow;
@property (nonatomic) NSSize javaMinSize;
@property (nonatomic) NSSize javaMaxSize;
@property (nonatomic) jint styleBits;
@property (nonatomic) BOOL isEnabled;
@property (nonatomic) jint preFullScreenLevel;
@property (nonatomic) BOOL isMinimizing;


- (id) initWithPlatformWindow:(JNFWeakJObjectWrapper *)javaPlatformWindow
                  ownerWindow:owner
                    styleBits:(jint)styleBits
                    frameRect:(NSRect)frameRect
                  contentView:(NSView *)contentView;

- (BOOL) isTopmostWindowUnderMouse;

// NSWindow overrides delegate methods
- (BOOL) canBecomeKeyWindow;
- (BOOL) canBecomeMainWindow;
- (BOOL) worksWhenModal;
- (void)sendEvent:(NSEvent *)event;

+ (void) setLastKeyWindow:(AWTWindow *)window;
+ (AWTWindow *) lastKeyWindow;

@end

@interface AWTWindow_Normal : NSWindow
- (id) initWithDelegate:(AWTWindow *)delegate
              frameRect:(NSRect)rect
              styleMask:(NSUInteger)styleMask
            contentView:(NSView *)view;
@end

@interface AWTWindow_Panel : NSPanel
- (id) initWithDelegate:(AWTWindow *)delegate
              frameRect:(NSRect)rect
              styleMask:(NSUInteger)styleMask
            contentView:(NSView *)view;
@end

#endif _AWTWINDOW_H
