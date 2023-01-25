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
 * (C) Copyright IBM Corp. 1998-2013 - All Rights Reserved
 *
 */

#ifndef __LIGATURESUBSTITUTION_H
#define __LIGATURESUBSTITUTION_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "LayoutTables.h"
#include "StateTables.h"
#include "MorphTables.h"
#include "MorphStateTables.h"

U_NAMESPACE_BEGIN

struct LigatureSubstitutionHeader : MorphStateTableHeader
{
    ByteOffset ligatureActionTableOffset;
    ByteOffset componentTableOffset;
    ByteOffset ligatureTableOffset;
};

struct LigatureSubstitutionHeader2 : MorphStateTableHeader2
{
    le_uint32 ligActionOffset;
    le_uint32 componentOffset;
    le_uint32 ligatureOffset;
};

enum LigatureSubstitutionFlags
{
    lsfSetComponent     = 0x8000,
    lsfDontAdvance      = 0x4000,
    lsfActionOffsetMask = 0x3FFF, // N/A in morx
    lsfPerformAction    = 0x2000
};

struct LigatureSubstitutionStateEntry : StateEntry
{
};

struct LigatureSubstitutionStateEntry2
{
    le_uint16 nextStateIndex;
    le_uint16 entryFlags;
    le_uint16 ligActionIndex;
};

typedef le_uint32 LigatureActionEntry;

enum LigatureActionFlags
{
    lafLast                 = 0x80000000,
    lafStore                = 0x40000000,
    lafComponentOffsetMask  = 0x3FFFFFFF
};

U_NAMESPACE_END
#endif
