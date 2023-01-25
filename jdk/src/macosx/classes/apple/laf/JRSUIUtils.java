/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

package apple.laf;

import com.apple.laf.AquaImageFactory.NineSliceMetrics;

import apple.laf.JRSUIConstants.*;
import sun.security.action.GetPropertyAction;

import java.security.AccessController;

public class JRSUIUtils {
    static boolean isLeopard = isMacOSXLeopard();
    static boolean isSnowLeopardOrBelow = isMacOSXSnowLeopardOrBelow();

    static boolean isMacOSXLeopard() {
        return isCurrentMacOSXVersion(5);
    }

    static boolean isMacOSXSnowLeopardOrBelow() {
        return currentMacOSXVersionMatchesGivenVersionRange(6, true, true, false);
    }

    static boolean isCurrentMacOSXVersion(final int version) {
        return currentMacOSXVersionMatchesGivenVersionRange(version, true, false, false);
    }

    static boolean currentMacOSXVersionMatchesGivenVersionRange(final int version, final boolean inclusive, final boolean matchBelow, final boolean matchAbove) {
        // split the "10.x.y" version number
        String osVersion = AccessController.doPrivileged(new GetPropertyAction("os.version"));
        String[] fragments = osVersion.split("\\.");

        // sanity check the "10." part of the version
        if (!fragments[0].equals("10")) return false;
        if (fragments.length < 2) return false;

        // check if os.version matches the given version using the given match method
        try {
            int minorVers = Integer.parseInt(fragments[1]);

            if (inclusive && minorVers == version) return true;
            if (matchBelow && minorVers < version) return true;
            if (matchAbove && minorVers > version) return true;

        } catch (NumberFormatException e) {
            // was not an integer
        }
        return false;
    }

    public static class TabbedPane {
        public static boolean useLegacyTabs() {
            return isLeopard;
        }
        public static boolean shouldUseTabbedPaneContrastUI() {
            return !isSnowLeopardOrBelow;
        }
    }

    public static class InternalFrame {
        public static boolean shouldUseLegacyBorderMetrics() {
            return isSnowLeopardOrBelow;
        }
    }

    public static class Tree {
        public static boolean useLegacyTreeKnobs() {
            return isLeopard;
        }
    }

    public static class ScrollBar {
        private static native boolean shouldUseScrollToClick();

        public static boolean useScrollToClick() {
            return shouldUseScrollToClick();
        }

        public static void getPartBounds(final double[] rect, final JRSUIControl control, final double x, final double y, final double w, final double h, final ScrollBarPart part) {
            control.getPartBounds(rect, x, y, w, h, part.ordinal);
        }

        public static double getNativeOffsetChange(final JRSUIControl control, final double x, final double y, final double w, final double h, final int offset, final int visibleAmount, final int extent) {
            return control.getScrollBarOffsetChange(x, y, w, h, offset, visibleAmount, extent);
        }
    }

    public static class Images {
        public static boolean shouldUseLegacySecurityUIPath() {
            return isSnowLeopardOrBelow;
        }
    }

    public static class HitDetection {
        public static Hit getHitForPoint(final JRSUIControl control, final double x, final double y, final double w, final double h, final double hitX, final double hitY) {
            return control.getHitForPoint(x, y, w, h, hitX, hitY);
        }
    }

    public interface NineSliceMetricsProvider {
        public NineSliceMetrics getNineSliceMetricsForState(JRSUIState state);
    }
}
