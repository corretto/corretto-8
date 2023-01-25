/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;


import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.classfile.*;
import java.util.Objects;

/**
 * This class represents a local variable within a method. It contains its
 * scope, name and type. The generated LocalVariable object can be obtained
 * with getLocalVariable which needs the instruction list and the constant
 * pool as parameters.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     LocalVariable
 * @see     MethodGen
 */
public class LocalVariableGen
  implements InstructionTargeter, NamedAndTyped, Cloneable,
             java.io.Serializable
{
  private final int   index;
  private String      name;
  private Type        type;
  private InstructionHandle start, end;

  /**
   * Generate a local variable that with index `index'. Note that double and long
   * variables need two indexs. Index indices have to be provided by the user.
   *
   * @param index index of local variable
   * @param name its name
   * @param type its type
   * @param start from where the instruction is valid (null means from the start)
   * @param end until where the instruction is valid (null means to the end)
   */
  public LocalVariableGen(int index, String name, Type type,
                          InstructionHandle start, InstructionHandle end) {
    if((index < 0) || (index > Constants.MAX_SHORT))
      throw new ClassGenException("Invalid index index: " + index);

    this.name  = name;
    this.type  = type;
    this.index  = index;
    setStart(start);
    setEnd(end);
  }

  /**
   * Get LocalVariable object.
   *
   * This relies on that the instruction list has already been dumped to byte code or
   * or that the `setPositions' methods has been called for the instruction list.
   *
   * Note that for local variables whose scope end at the last
   * instruction of the method's code, the JVM specification is ambiguous:
   * both a start_pc+length ending at the last instruction and
   * start_pc+length ending at first index beyond the end of the code are
   * valid.
   *
   * @param il instruction list (byte code) which this variable belongs to
   * @param cp constant pool
   */
  public LocalVariable getLocalVariable(ConstantPoolGen cp) {
    int start_pc        = start.getPosition();
    int length          = end.getPosition() - start_pc;

    if(length > 0)
      length += end.getInstruction().getLength();

    int name_index      = cp.addUtf8(name);
    int signature_index = cp.addUtf8(type.getSignature());

    return new LocalVariable(start_pc, length, name_index,
                             signature_index, index, cp.getConstantPool());
  }

  public int         getIndex()                  { return index; }
  @Override
  public void        setName(String name)        { this.name = name; }
  @Override
  public String      getName()                   { return name; }
  @Override
  public void        setType(Type type)          { this.type = type; }
  @Override
  public Type        getType()                   { return type; }

  public InstructionHandle getStart()                  { return start; }
  public InstructionHandle getEnd()                    { return end; }

  /**
   * Remove this from any known HashSet in which it might be registered.
   */
  void notifyTargetChanging() {
    // hashCode depends on 'index', 'start', and 'end'.
    // Therefore before changing any of these values we
    // need to unregister 'this' from any HashSet where
    // this is registered, and then we need to add it
    // back...

    // Unregister 'this' from the HashSet held by 'start'.
    BranchInstruction.notifyTargetChanging(this.start, this);
    if (this.end != this.start) {
        // Since hashCode() is going to change we need to unregister
        // 'this' both form 'start' and 'end'.
        // Unregister 'this' from the HashSet held by 'end'.
        BranchInstruction.notifyTargetChanging(this.end, this);
    }
  }

  /**
   * Add back 'this' in all HashSet in which it should be registered.
   **/
  void notifyTargetChanged() {
    // hashCode depends on 'index', 'start', and 'end'.
    // Therefore before changing any of these values we
    // need to unregister 'this' from any HashSet where
    // this is registered, and then we need to add it
    // back...

    // Register 'this' in the HashSet held by start.
    BranchInstruction.notifyTargetChanged(this.start, this);
    if (this.end != this.start) {
        // Since hashCode() has changed we need to register
        // 'this' again in 'end'.
        // Add back 'this' in the HashSet held by 'end'.
        BranchInstruction.notifyTargetChanged(this.end, this);
    }
  }

  public final void setStart(InstructionHandle start) {

    // Call notifyTargetChanging *before* modifying this,
    // as the code triggered by notifyTargetChanging
    // depends on this pointing to the 'old' start.
    notifyTargetChanging();

    this.start = start;

    // call notifyTargetChanged *after* modifying this,
    // as the code triggered by notifyTargetChanged
    // depends on this pointing to the 'new' start.
    notifyTargetChanged();
  }

  public final void setEnd(InstructionHandle end) {
    // call notifyTargetChanging *before* modifying this,
    // as the code triggered by notifyTargetChanging
    // depends on this pointing to the 'old' end.
    // Unregister 'this' from the HashSet held by the 'old' end.
    notifyTargetChanging();

    this.end = end;

    // call notifyTargetChanged *after* modifying this,
    // as the code triggered by notifyTargetChanged
    // depends on this pointing to the 'new' end.
    // Register 'this' in the HashSet held by the 'new' end.
    notifyTargetChanged();

  }

  /**
   * @param old_ih old target, either start or end
   * @param new_ih new target
   */
  @Override
  public void updateTarget(InstructionHandle old_ih, InstructionHandle new_ih) {
    boolean targeted = false;

    if(start == old_ih) {
      targeted = true;
      setStart(new_ih);
    }

    if(end == old_ih) {
      targeted = true;
      setEnd(new_ih);
    }

    if(!targeted)
      throw new ClassGenException("Not targeting " + old_ih + ", but {" + start + ", " +
                                  end + "}");
  }

  /**
   * @return true, if ih is target of this variable
   */
  @Override
  public boolean containsTarget(InstructionHandle ih) {
    return (start == ih) || (end == ih);
  }

  /**
   * We consider two local variables to be equal, if they use the same index and
   * are valid in the same range.
   */
  @Override
  public boolean equals(Object o) {
    if (o==this)
      return true;

    if(!(o instanceof LocalVariableGen))
      return false;

    LocalVariableGen l = (LocalVariableGen)o;
    return (l.index == index) && (l.start == start) && (l.end == end);
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 59 * hash + this.index;
    hash = 59 * hash + Objects.hashCode(this.start);
    hash = 59 * hash + Objects.hashCode(this.end);
    return hash;
  }

  @Override
  public String toString() {
    return "LocalVariableGen(" + name +  ", " + type +  ", " + start + ", " + end + ")";
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch(CloneNotSupportedException e) {
      System.err.println(e);
      return null;
    }
  }
}
