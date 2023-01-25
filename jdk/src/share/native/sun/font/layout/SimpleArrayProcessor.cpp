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
#include "MorphTables.h"
#include "SubtableProcessor.h"
#include "NonContextualGlyphSubst.h"
#include "NonContextualGlyphSubstProc.h"
#include "SimpleArrayProcessor.h"
#include "LEGlyphStorage.h"
#include "LESwaps.h"

U_NAMESPACE_BEGIN

UOBJECT_DEFINE_RTTI_IMPLEMENTATION(SimpleArrayProcessor)

SimpleArrayProcessor::SimpleArrayProcessor()
{
}

SimpleArrayProcessor::SimpleArrayProcessor(const LEReferenceTo<MorphSubtableHeader> &morphSubtableHeader, LEErrorCode &success)
  : NonContextualGlyphSubstitutionProcessor(morphSubtableHeader, success)
{
  LEReferenceTo<NonContextualGlyphSubstitutionHeader> header(morphSubtableHeader, success);
  simpleArrayLookupTable = LEReferenceTo<SimpleArrayLookupTable>(morphSubtableHeader, success, (const SimpleArrayLookupTable*)&header->table);
}

SimpleArrayProcessor::~SimpleArrayProcessor()
{
}

void SimpleArrayProcessor::process(LEGlyphStorage &glyphStorage, LEErrorCode &success)
{
    if (LE_FAILURE(success)) return;

    le_int32 glyphCount = glyphStorage.getGlyphCount();
    le_int32 glyph;

    LEReferenceToArrayOf<LookupValue> valueArray(simpleArrayLookupTable, success, (const LookupValue*)&simpleArrayLookupTable->valueArray, LE_UNBOUNDED_ARRAY);

    for (glyph = 0; LE_SUCCESS(success) && (glyph < glyphCount); glyph += 1) {
        LEGlyphID thisGlyph = glyphStorage[glyph];
        if (LE_GET_GLYPH(thisGlyph) < 0xFFFF) {
          TTGlyphID newGlyph = SWAPW(valueArray.getObject(LE_GET_GLYPH(thisGlyph),success));
          glyphStorage[glyph] = LE_SET_GLYPH(thisGlyph, newGlyph);
        }
    }
}

U_NAMESPACE_END
