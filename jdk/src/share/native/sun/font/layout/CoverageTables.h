/*
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
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#ifndef __COVERAGETABLES_H
#define __COVERAGETABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

struct CoverageTable
{
    le_uint16 coverageFormat;

    le_int32 getGlyphCoverage(const LETableReference &base, LEGlyphID glyphID, LEErrorCode &success) const;
};

struct CoverageFormat1Table : CoverageTable
{
    le_uint16  glyphCount;
    TTGlyphID glyphArray[ANY_NUMBER];

    le_int32 getGlyphCoverage(LEReferenceTo<CoverageFormat1Table> &base, LEGlyphID glyphID, LEErrorCode &success) const;
};
LE_VAR_ARRAY(CoverageFormat1Table, glyphArray)


struct CoverageFormat2Table : CoverageTable
{
    le_uint16        rangeCount;
    GlyphRangeRecord rangeRecordArray[ANY_NUMBER];

    le_int32 getGlyphCoverage(LEReferenceTo<CoverageFormat2Table> &base, LEGlyphID glyphID, LEErrorCode &success) const;
};
LE_VAR_ARRAY(CoverageFormat2Table, rangeRecordArray)

U_NAMESPACE_END
#endif
