/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.*;
import java.util.logging.*;

/*
 * @test
 * @bug 8016127 8024131
 * @summary test logging.properties localized
 * @run main/othervm LocalizedLevelName
 */

public class LocalizedLevelName {
    private static Object[] namesMap = {
        "SEVERE",  Locale.ENGLISH, "Severe",        Level.SEVERE,
        "WARNING", Locale.FRENCH,  "Avertissement", Level.WARNING,
        "INFO",    Locale.ITALIAN, "Informazioni",  Level.INFO,
        "SEVERE",  Locale.FRENCH,  "Grave",         Level.SEVERE,
        "CONFIG",  Locale.GERMAN,  "Konfiguration", Level.CONFIG,
        "ALL",     Locale.ROOT,    "All",           Level.ALL,
        "SEVERE",  Locale.ROOT,    "Severe",        Level.SEVERE,
        "WARNING", Locale.ROOT,    "Warning",       Level.WARNING,
        "CONFIG",  Locale.ROOT,    "Config",        Level.CONFIG,
        "INFO",    Locale.ROOT,    "Info",          Level.INFO,
        "FINE",    Locale.ROOT,    "Fine",          Level.FINE,
        "FINER",   Locale.ROOT,    "Finer",         Level.FINER,
        "FINEST",  Locale.ROOT,    "Finest",        Level.FINEST
    };

    public static void main(String args[]) throws Exception {
        Locale defaultLocale = Locale.getDefault();
        for (int i=0; i<namesMap.length; i += 4) {
            final String key = (String) namesMap[i];
            final Locale locale = (Locale) namesMap[i+1];
            final String expectedTranslation = (String) namesMap[i+2];
            final Level level = (Level) namesMap[i+3];

            final String en = getLocalizedMessage(Locale.ENGLISH, key);
            final String other = getLocalizedMessage(locale, key);

            System.out.println(locale + ": " + key + "=" + expectedTranslation
                    + ", (Level." + level.getName() + ")");
            System.out.println("     => localized(" + Locale.ENGLISH + ", "
                    + key + ")=" + en);
            System.out.println("     => localized(" + locale + ", " + key
                    + ")=" + other);
            if (!key.equals(en.toUpperCase(Locale.ROOT))) {
                throw new RuntimeException("Expect " + key
                        + " equals upperCase(" + en + ")");
            }
            if (!Locale.ENGLISH.equals(locale) && !Locale.ROOT.equals(locale)
                    && key.equals(other.toUpperCase(Locale.ROOT))) {
                throw new RuntimeException("Expect " + key
                        + " not equals upperCase(" + other +")");
            }
            if ((Locale.ENGLISH.equals(locale) || Locale.ROOT.equals(locale))
                    && !key.equals(other.toUpperCase(Locale.ROOT))) {
                throw new RuntimeException("Expect " + key
                        + " equals upperCase(" + other +")");
            }
            if (!other.equals(expectedTranslation)) {
                throw new RuntimeException("Expected \"" + expectedTranslation
                        + "\" for '" + locale + "' but got \"" + other + "\"");
            }
            Locale.setDefault(locale);
            final String levelName = level.getLocalizedName();
            System.out.println("Level.getLocalizedName() is: " + levelName);
            if (!levelName.equals(other.toUpperCase(locale))) {
                throw new RuntimeException("Expected \""
                        + other.toUpperCase(locale) + "\" for '"
                        + locale + "' but got \"" + levelName + "\"");
            }
            Locale.setDefault(defaultLocale);
       }
    }

    private static final String RBNAME = "sun.util.logging.resources.logging";
    private static String getLocalizedMessage(Locale locale, String key) {
        ResourceBundle rb = ResourceBundle.getBundle(RBNAME, locale);
        return rb.getString(key);
    }
}
