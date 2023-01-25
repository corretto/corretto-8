/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/divnode.hpp"
#include "opto/locknode.hpp"
#include "opto/loopnode.hpp"
#include "opto/machnode.hpp"
#include "opto/memnode.hpp"
#include "opto/mathexactnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/multnode.hpp"
#include "opto/node.hpp"
#include "opto/rootnode.hpp"
#include "opto/subnode.hpp"
#include "opto/vectornode.hpp"

// ----------------------------------------------------------------------------
// Build a table of virtual functions to map from Nodes to dense integer
// opcode names.
int Node::Opcode() const { return Op_Node; }
#define macro(x) int x##Node::Opcode() const { return Op_##x; }
#include "classes.hpp"
#undef macro
