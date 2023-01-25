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
 * (C) Copyright IBM Corp.  and others 1998-2013 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "SubtableProcessor2.h"
#include "NonContextualGlyphSubst.h"
#include "NonContextualGlyphSubstProc2.h"
#include "SegmentArrayProcessor2.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(SegmentArrayProcessor2)

SegmentArrayProcessor2::SegmentArrayProcessor2()
{
}

SegmentArrayProcessor2::SegmentArrayProcessor2(const LEReferenceTo<MorphSubtableHeader2> &morphSubtableHeader, LEErrorCode &success)
  : NonContextualGlyphSubstitutionProcessor2(morphSubtableHeader, success)
{
  const LEReferenceTo<NonContextualGlyphSubstitutionHeader2> header(morphSubtableHeader, success);
  segmentArrayLookupTable = LEReferenceTo<SegmentArrayLookupTable>(morphSubtableHeader,  success, &header->table); // don't parent to 'header' as it is on the stack
}

SegmentArrayProcessor2::~SegmentArrayProcessor2()
{
}

void SegmentArrayProcessor2::process(LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) return;

    const LookupSegment *segments = segmentArrayLookupTable->segments;
    le_int32 glyphCount = glyphStorage.getGlyphCount();
    le_int32 glyph;

    if (LE_FAILURE(success)) return;

    for (glyph = 0; glyph < glyphCount; glyph += 1) {
        LEGlyphID thisGlyph = glyphStorage[glyph];
        // lookupSegment already range checked by lookupSegment() function.
        const LookupSegment *lookupSegment = segmentArrayLookupTable->lookupSegment(segmentArrayLookupTable, segments, thisGlyph, success);

        if (lookupSegment != NULL&& LE_SUCCESS(success))  {
            TTGlyphID firstGlyph = SWAPW(lookupSegment->firstGlyph);
            TTGlyphID lastGlyph = SWAPW(lookupSegment->lastGlyph);
            le_int16  offset = SWAPW(lookupSegment->value);
            TTGlyphID thisGlyphId=  LE_GET_GLYPH(thisGlyph);
            LEReferenceToArrayOf<TTGlyphID> glyphArray(subtableHeader, success, offset, lastGlyph - firstGlyph + 1);
            if (offset != 0 && thisGlyphId <= lastGlyph && thisGlyphId >= firstGlyph && LE_SUCCESS(success) ) {
              TTGlyphID   newGlyph   = SWAPW(glyphArray[thisGlyphId]);
              glyphStorage[glyph] = LE_SET_GLYPH(thisGlyph, newGlyph);
            }
        }
    }
}

U_NAMESPACE_END
