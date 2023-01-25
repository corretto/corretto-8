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
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "GlyphIterator.h"
#include "LookupProcessor.h"
#include "SubstitutionLookups.h"
#include "CoverageTables.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

/*
    NOTE: This could be optimized somewhat by keeping track
    of the previous sequenceIndex in the loop and doing next()
    or prev() of the delta between that and the current
    sequenceIndex instead of always resetting to the front.
*/
void SubstitutionLookup::applySubstitutionLookups(
        LookupProcessor *lookupProcessor,
        SubstitutionLookupRecord *substLookupRecordArray,
        le_uint16 substCount,
        GlyphIterator *glyphIterator,
        const LEFontInstance *fontInstance,
        le_int32 position,
        LEErrorCode& success)
{
    if (LE_FAILURE(success)) {
        return;
    }

    GlyphIterator tempIterator(*glyphIterator);

    for (le_uint16 subst = 0; subst < substCount && LE_SUCCESS(success); subst += 1) {
        le_uint16 sequenceIndex = SWAPW(substLookupRecordArray[subst].sequenceIndex);
        le_uint16 lookupListIndex = SWAPW(substLookupRecordArray[subst].lookupListIndex);

        tempIterator.setCurrStreamPosition(position);
        if (!tempIterator.next(sequenceIndex)) {
            success = LE_INTERNAL_ERROR;
            return;
        }

        lookupProcessor->applySingleLookup(lookupListIndex, &tempIterator, fontInstance, success);
    }
}

U_NAMESPACE_END
