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

#ifndef __LIGATURESUBSTITUTIONSUBTABLES_H
#define __LIGATURESUBSTITUTIONSUBTABLES_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LEGlyphFilter.h"
#include "OpenTypeTables.h"
#include "GlyphSubstitutionTables.h"
#include "GlyphIterator.h"

U_NAMESPACE_BEGIN

struct LigatureSetTable
{
    le_uint16 ligatureCount;
    Offset    ligatureTableOffsetArray[ANY_NUMBER];
};
LE_VAR_ARRAY(LigatureSetTable, ligatureTableOffsetArray)

struct LigatureTable
{
    TTGlyphID ligGlyph;
    le_uint16 compCount;
    TTGlyphID componentArray[ANY_NUMBER];
};
LE_VAR_ARRAY(LigatureTable, componentArray)

struct LigatureSubstitutionSubtable : GlyphSubstitutionSubtable
{
    le_uint16 ligSetCount;
    Offset    ligSetTableOffsetArray[ANY_NUMBER];

    le_uint32  process(const LETableReference &base, GlyphIterator *glyphIterator, LEErrorCode &success, const LEGlyphFilter *filter = NULL) const;
};
LE_VAR_ARRAY(LigatureSubstitutionSubtable, ligSetTableOffsetArray)

U_NAMESPACE_END
#endif
