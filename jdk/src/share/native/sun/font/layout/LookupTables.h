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

#ifndef __LOOKUPTABLES_H
#define __LOOKUPTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LayoutTables.h"
#include "LETableReference.h"

U_NAMESPACE_BEGIN

enum LookupTableFormat
{
    ltfSimpleArray      = 0,
    ltfSegmentSingle    = 2,
    ltfSegmentArray     = 4,
    ltfSingleTable      = 6,
    ltfTrimmedArray     = 8
};

typedef le_int16 LookupValue;

struct LookupTable
{
    le_int16 format;
};

struct LookupSegment
{
    TTGlyphID   lastGlyph;
    TTGlyphID   firstGlyph;
    LookupValue value;
};

struct LookupSingle
{
    TTGlyphID   glyph;
    LookupValue value;
};

struct BinarySearchLookupTable : LookupTable
{
    le_uint16 unitSize;
    le_uint16 nUnits;
    le_uint16 searchRange;
    le_uint16 entrySelector;
    le_uint16 rangeShift;

    const LookupSegment *lookupSegment(const LETableReference &base, const LookupSegment *segments, LEGlyphID glyph, LEErrorCode &success) const;

    const LookupSingle *lookupSingle(const LETableReference &base, const LookupSingle *entries, LEGlyphID glyph, LEErrorCode &success) const;
};

struct SimpleArrayLookupTable : LookupTable
{
    LookupValue valueArray[ANY_NUMBER];
};
LE_VAR_ARRAY(SimpleArrayLookupTable, valueArray)

struct SegmentSingleLookupTable : BinarySearchLookupTable
{
    LookupSegment segments[ANY_NUMBER];
};
LE_VAR_ARRAY(SegmentSingleLookupTable, segments)

struct SegmentArrayLookupTable : BinarySearchLookupTable
{
    LookupSegment segments[ANY_NUMBER];
};
LE_VAR_ARRAY(SegmentArrayLookupTable, segments)

struct SingleTableLookupTable : BinarySearchLookupTable
{
    LookupSingle entries[ANY_NUMBER];
};
LE_VAR_ARRAY(SingleTableLookupTable, entries)

struct TrimmedArrayLookupTable : LookupTable
{
    TTGlyphID   firstGlyph;
    TTGlyphID   glyphCount;
    LookupValue valueArray[ANY_NUMBER];
};
LE_VAR_ARRAY(TrimmedArrayLookupTable, valueArray)

U_NAMESPACE_END
#endif
