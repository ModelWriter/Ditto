/* 
Copyright (c) 2007, AJ Shankar

All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    * Neither the name of the <ORGANIZATION> nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/**
 * Helper routines for hashing argument arrays. Semantic equality
 * for scalars, pointer equality for everything else.
 */
package runtime;

import gnu.trove.TObjectHashingStrategy;

/**
 * @author aj
 *
 */

class ObjStrategy implements TObjectHashingStrategy {
  private static final long serialVersionUID = 1744234211823134516L;
	public int computeHashCode(Object o) {
		return System.identityHashCode(o);
	}
	public boolean equals(Object a, Object b) {
		return a == b;
	}
}

class ObjArrayStrategy implements TObjectHashingStrategy {

  private static final long serialVersionUID = 4797322892116771690L;

	static public int hash(Object o) {
		int result = 1;
		Object[] ary = (Object[]) o;
		for (int i = 0 ; i < ary.length - Dispatch.ignoreArgs ; i++) {
			Object a = ary[i];
			int hash = 0;
			if (a == null) continue;
			if (a instanceof Integer || a instanceof Boolean || a instanceof Float) {
				hash = a.hashCode();
			} else {
				hash = System.identityHashCode(a);
			}

			result += hash * 37;
//			result += (ary[i] == null) ? 0 : ary[i].hashCode() * 37;
		}
		return result;
	}
	
  public int computeHashCode(Object o) {
  	return hash(o);
	}
  
  public static boolean sEquals(Object a, Object b) {
		if (a == b)
			return true;
		if (a == null || b == null)	
			return false;
		if (! (a instanceof Object[]) || 
			  ! (b instanceof Object[])) {
			return false;
		}
		Object[] ary = (Object[]) a;
		Object[] bry = (Object[]) b;

		//if (ary.length != bry.length)
			//return false;
		for (int i = 0 ; i < ary.length - Dispatch.ignoreArgs ; i++) {
			Object ao = ary[i], bo = bry[i];
			if (ao instanceof Integer) {
				if (((Integer) ao).intValue() != ((Integer) bo).intValue())
					return false;
			} else if (ao instanceof Boolean) {
				if (((Boolean) ao).booleanValue() != ((Boolean) bo).booleanValue())
					return false;
			} else if (ao instanceof Float) {
				if (((Float) ao).floatValue() != ((Float) bo).floatValue())
					return false;
			} else {
				if (ary[i] != bry[i])
					return false;
			}
		}
		return true;
  	
  }
  
	public boolean equals(Object a, Object b) {
		return sEquals(a, b);
	}
  
}
