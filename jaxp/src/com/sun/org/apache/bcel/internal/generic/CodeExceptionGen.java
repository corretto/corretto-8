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


import com.sun.org.apache.bcel.internal.classfile.*;

/**
 * This class represents an exception handler, i.e., specifies the  region where
 * a handler is active and an instruction where the actual handling is done.
 * pool as parameters. Opposed to the JVM specification the end of the handled
 * region is set to be inclusive, i.e. all instructions between start and end
 * are protected including the start and end instructions (handles) themselves.
 * The end of the region is automatically mapped to be exclusive when calling
 * getCodeException(), i.e., there is no difference semantically.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     MethodGen
 * @see     CodeException
 * @see     InstructionHandle
 */
public final class CodeExceptionGen
  implements InstructionTargeter, Cloneable, java.io.Serializable {
  private InstructionHandle start_pc;
  private InstructionHandle end_pc;
  private InstructionHandle handler_pc;
  private ObjectType        catch_type;

  /**
   * Add an exception handler, i.e., specify region where a handler is active and an
   * instruction where the actual handling is done.
   *
   * @param start_pc Start of handled region (inclusive)
   * @param end_pc End of handled region (inclusive)
   * @param handler_pc Where handling is done
   * @param catch_type which exception is handled, null for ANY
   */
  public CodeExceptionGen(InstructionHandle start_pc, InstructionHandle end_pc,
                          InstructionHandle handler_pc, ObjectType catch_type) {
    setStartPC(start_pc);
    setEndPC(end_pc);
    setHandlerPC(handler_pc);
    this.catch_type = catch_type;
  }

  /**
   * Get CodeException object.<BR>
   *
   * This relies on that the instruction list has already been dumped
   * to byte code or or that the `setPositions' methods has been
   * called for the instruction list.
   *
   * @param cp constant pool
   */
  public CodeException getCodeException(ConstantPoolGen cp) {
    return new CodeException(start_pc.getPosition(),
                             end_pc.getPosition() + end_pc.getInstruction().getLength(),
                             handler_pc.getPosition(),
                             (catch_type == null)? 0 : cp.addClass(catch_type));
  }

  /* Set start of handler
   * @param start_pc Start of handled region (inclusive)
   */
  public final void setStartPC(InstructionHandle start_pc) {
    BranchInstruction.notifyTargetChanging(this.start_pc, this);
    this.start_pc = start_pc;
    BranchInstruction.notifyTargetChanged(this.start_pc, this);
  }

  /* Set end of handler
   * @param end_pc End of handled region (inclusive)
   */
  public final void setEndPC(InstructionHandle end_pc) {
    BranchInstruction.notifyTargetChanging(this.end_pc, this);
    this.end_pc = end_pc;
    BranchInstruction.notifyTargetChanged(this.end_pc, this);
  }

  /* Set handler code
   * @param handler_pc Start of handler
   */
  public final void setHandlerPC(InstructionHandle handler_pc) {
    BranchInstruction.notifyTargetChanging(this.handler_pc, this);
    this.handler_pc = handler_pc;
    BranchInstruction.notifyTargetChanged(this.handler_pc, this);
  }

  /**
   * @param old_ih old target, either start or end
   * @param new_ih new target
   */
  @Override
  public void updateTarget(InstructionHandle old_ih, InstructionHandle new_ih) {
    boolean targeted = false;

    if(start_pc == old_ih) {
      targeted = true;
      setStartPC(new_ih);
    }

    if(end_pc == old_ih) {
      targeted = true;
      setEndPC(new_ih);
    }

    if(handler_pc == old_ih) {
      targeted = true;
      setHandlerPC(new_ih);
    }

    if(!targeted)
      throw new ClassGenException("Not targeting " + old_ih + ", but {" + start_pc + ", " +
                                  end_pc + ", " + handler_pc + "}");
  }

  /**
   * @return true, if ih is target of this handler
   */
  @Override
  public boolean containsTarget(InstructionHandle ih) {
    return (start_pc == ih) || (end_pc == ih) || (handler_pc == ih);
  }

  /** Sets the type of the Exception to catch. Set 'null' for ANY. */
  public void              setCatchType(ObjectType catch_type)        { this.catch_type = catch_type; }
  /** Gets the type of the Exception to catch, 'null' for ANY. */
  public ObjectType        getCatchType()                             { return catch_type; }

  /** @return start of handled region (inclusive)
   */
  public InstructionHandle getStartPC()                               { return start_pc; }

  /** @return end of handled region (inclusive)
   */
  public InstructionHandle getEndPC()                                 { return end_pc; }

  /** @return start of handler
   */
  public InstructionHandle getHandlerPC()                             { return handler_pc; }

  @Override
  public String toString() {
    return "CodeExceptionGen(" + start_pc + ", " + end_pc + ", " + handler_pc + ")";
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
