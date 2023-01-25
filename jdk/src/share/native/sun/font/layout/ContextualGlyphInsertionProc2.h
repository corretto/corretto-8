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
 * (C) Copyright IBM Corp.  and others 2013 - All Rights Reserved
 *
 */

#ifndef __CONTEXTUALGLYPHINSERTIONPROCESSOR2_H
#define __CONTEXTUALGLYPHINSERTIONPROCESSOR2_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "MorphTables.h"
#include "SubtableProcessor2.h"
#include "StateTableProcessor2.h"
#include "ContextualGlyphInsertionProc2.h"
#include "ContextualGlyphInsertion.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;

class ContextualGlyphInsertionProcessor2 : public StateTableProcessor2
{
public:
    virtual void beginStateTable();

    virtual le_uint16 processStateEntry(LEGlyphStorage &glyphStorage,
                                        le_int32 &currGlyph, EntryTableIndex2 index, LEErrorCode &success);

    virtual void endStateTable();

    ContextualGlyphInsertionProcessor2(const LEReferenceTo<MorphSubtableHeader2> &morphSubtableHeader, LEErrorCode &success);
    virtual ~ContextualGlyphInsertionProcessor2();

    /**
     * ICU "poor man's RTTI", returns a UClassID for the actual class.
     *
     * @stable ICU 2.8
     */
    virtual UClassID getDynamicClassID() const;

    /**
     * ICU "poor man's RTTI", returns a UClassID for this class.
     *
     * @stable ICU 2.8
     */
    static UClassID getStaticClassID();

private:
    ContextualGlyphInsertionProcessor2();

    /**
     * Perform the actual insertion
     * @param atGlyph index of glyph to insert at
     * @param index index into the insertionTable (in/out)
     * @param count number of insertions
     * @param isKashidaLike Kashida like (vs Split Vowel like). No effect currently.
     * @param isBefore if true, insert extra glyphs before the marked glyph
     */
    void doInsertion(LEGlyphStorage &glyphStorage,
                              le_int16 atGlyph,
                              le_int16 &index,
                              le_int16 count,
                              le_bool isKashidaLike,
                              le_bool isBefore,
                              LEErrorCode &success);


protected:
    le_int32 markGlyph;
    LEReferenceToArrayOf<le_uint16> insertionTable;
    LEReferenceToArrayOf<ContextualGlyphInsertionStateEntry2> entryTable;
    LEReferenceTo<ContextualGlyphInsertionHeader2> contextualGlyphHeader;
};

U_NAMESPACE_END
#endif
