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
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#include "LETypes.h"
#include "LEFontInstance.h"
#include "OpenTypeTables.h"
#include "AnchorTables.h"
#include "MarkArrays.h"
#include "GlyphPositioningTables.h"
#include "AttachmentPosnSubtables.h"
#include "MarkToLigaturePosnSubtables.h"
#include "GlyphIterator.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

LEGlyphID MarkToLigaturePositioningSubtable::findLigatureGlyph(GlyphIterator *glyphIterator) const
{
    if (glyphIterator->prev()) {
        return glyphIterator->getCurrGlyphID();
    }

    return 0xFFFF;
}

le_int32 MarkToLigaturePositioningSubtable::process(const LETableReference &base, GlyphIterator *glyphIterator, const LEFontInstance *fontInstance, LEErrorCode &success) const
{
    if (LE_FAILURE(success)) {
        return 0;
    }

    LEGlyphID markGlyph = glyphIterator->getCurrGlyphID();
    le_int32 markCoverage = getGlyphCoverage(base, (LEGlyphID) markGlyph, success);

    if (LE_FAILURE(success)) {
      return 0;
    }

    if (markCoverage < 0) {
        // markGlyph isn't a covered mark glyph
        return 0;
    }

    LEPoint markAnchor;
    LEReferenceTo<MarkArray> markArray(base, success,  SWAPW(markArrayOffset));
    if( LE_FAILURE(success) ) {
      return 0;
    }
    le_int32 markClass = markArray->getMarkClass(markArray, markGlyph, markCoverage, fontInstance, markAnchor, success);
    le_uint16 mcCount = SWAPW(classCount);

    if (markClass < 0 || markClass >= mcCount) {
        // markGlyph isn't in the mark array or its
        // mark class is too big. The table is mal-formed!
        return 0;
    }

    // FIXME: we probably don't want to find a ligature before a previous base glyph...
    GlyphIterator ligatureIterator(*glyphIterator, (le_uint16) (lfIgnoreMarks /*| lfIgnoreBaseGlyphs*/));
    LEGlyphID ligatureGlyph = findLigatureGlyph(&ligatureIterator);
    if (ligatureGlyph == 0xFFFF) {
        return 0;
    }
    le_int32 ligatureCoverage = getBaseCoverage(base, (LEGlyphID) ligatureGlyph, success);
    LEReferenceTo<LigatureArray> ligatureArray(base, success, SWAPW(baseArrayOffset));
    if (LE_FAILURE(success)) { return 0; }
    le_uint16 ligatureCount = SWAPW(ligatureArray->ligatureCount);

    if (ligatureCoverage < 0 || ligatureCoverage >= ligatureCount) {
        // The ligature glyph isn't covered, or the coverage
        // index is too big. The latter means that the
        // table is mal-formed...
        return 0;
    }

    le_int32 markPosition = glyphIterator->getCurrStreamPosition();
    Offset ligatureAttachOffset = SWAPW(ligatureArray->ligatureAttachTableOffsetArray[ligatureCoverage]);
    LEReferenceTo<LigatureAttachTable> ligatureAttachTable(ligatureArray, success, ligatureAttachOffset);
    if (LE_FAILURE(success)) { return 0; }
    le_int32 componentCount = SWAPW(ligatureAttachTable->componentCount);
    le_int32 component = ligatureIterator.getMarkComponent(markPosition);

    if (component >= componentCount) {
        // should really just bail at this point...
        component = componentCount - 1;
    }

    LEReferenceTo<ComponentRecord> componentRecord(base, success, &ligatureAttachTable->componentRecordArray[component * mcCount]);
    if (LE_FAILURE(success)) { return 0; }
    LEReferenceToArrayOf<Offset> ligatureAnchorTableOffsetArray(base, success, &(componentRecord->ligatureAnchorTableOffsetArray[0]), mcCount);
    if( LE_FAILURE(success) ) { return 0; }
    Offset anchorTableOffset = SWAPW(componentRecord->ligatureAnchorTableOffsetArray[markClass]);
    LEReferenceTo<AnchorTable> anchorTable(ligatureAttachTable, success, anchorTableOffset);
    if (LE_FAILURE(success)) { return 0; }
    LEPoint ligatureAnchor, markAdvance, pixels;

    anchorTable->getAnchor(anchorTable, ligatureGlyph, fontInstance, ligatureAnchor, success);

    fontInstance->getGlyphAdvance(markGlyph, pixels);
    fontInstance->pixelsToUnits(pixels, markAdvance);

    float anchorDiffX = ligatureAnchor.fX - markAnchor.fX;
    float anchorDiffY = ligatureAnchor.fY - markAnchor.fY;

    glyphIterator->setCurrGlyphBaseOffset(ligatureIterator.getCurrStreamPosition());

    if (glyphIterator->isRightToLeft()) {
        glyphIterator->setCurrGlyphPositionAdjustment(anchorDiffX, anchorDiffY, -markAdvance.fX, -markAdvance.fY);
    } else {
        LEPoint ligatureAdvance;

        fontInstance->getGlyphAdvance(ligatureGlyph, pixels);
        fontInstance->pixelsToUnits(pixels, ligatureAdvance);

        glyphIterator->setCurrGlyphPositionAdjustment(anchorDiffX - ligatureAdvance.fX, anchorDiffY - ligatureAdvance.fY, -markAdvance.fX, -markAdvance.fY);
    }

    return 1;
}

U_NAMESPACE_END
