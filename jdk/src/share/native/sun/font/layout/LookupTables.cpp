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

#include "LETypes.h"
#include "LayoutTables.h"
#include "LookupTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

/*
    These are the rolled-up versions of the uniform binary search.
    Someday, if we need more performance, we can un-roll them.

    Note: I put these in the base class, so they only have to
    be written once. Since the base class doesn't define the
    segment table, these routines assume that it's right after
    the binary search header.

    Another way to do this is to put each of these routines in one
    of the derived classes, and implement it in the others by casting
    the "this" pointer to the type that has the implementation.
*/
const LookupSegment *BinarySearchLookupTable::lookupSegment(const LETableReference &base, const LookupSegment *segments, LEGlyphID glyph, LEErrorCode &success) const
{

    le_uint16  unity = SWAPW(unitSize);
    le_uint16  probe = SWAPW(searchRange);
    le_uint16  extra = SWAPW(rangeShift);
    TTGlyphID ttGlyph = (TTGlyphID) LE_GET_GLYPH(glyph);
    LEReferenceTo<LookupSegment> entry(base, success, segments);
    LEReferenceTo<LookupSegment> trial(entry, success, extra);

    if(LE_FAILURE(success)) return NULL;

    if (SWAPW(trial->lastGlyph) <= ttGlyph) {
        entry = trial;
    }

    while (probe > unity && LE_SUCCESS(success)) {
        probe >>= 1;
        trial = entry; // copy
        trial.addOffset(probe, success);

        if (SWAPW(trial->lastGlyph) <= ttGlyph) {
            entry = trial;
        }
    }

    if (SWAPW(entry->firstGlyph) <= ttGlyph) {
      return entry.getAlias();
    }

    return NULL;
}

const LookupSingle *BinarySearchLookupTable::lookupSingle(const LETableReference &base, const LookupSingle *entries, LEGlyphID glyph, LEErrorCode &success) const
{
    le_uint16  unity = SWAPW(unitSize);
    le_uint16  probe = SWAPW(searchRange);
    le_uint16  extra = SWAPW(rangeShift);
    TTGlyphID ttGlyph = (TTGlyphID) LE_GET_GLYPH(glyph);
    LEReferenceTo<LookupSingle> entry(base, success, entries);
    LEReferenceTo<LookupSingle> trial(entry, success, extra);

    if (!LE_SUCCESS(success)) {
        return NULL;
    }

    if (SWAPW(trial->glyph) <= ttGlyph) {
        entry = trial;
    }

    while (probe > unity && LE_SUCCESS(success)) {
        probe >>= 1;
        trial = entry;
        trial.addOffset(probe, success);

        if (SWAPW(trial->glyph) <= ttGlyph) {
            entry = trial;
        }
    }

    if (SWAPW(entry->glyph) == ttGlyph) {
      return entry.getAlias();
    }

    return NULL;
}

U_NAMESPACE_END
