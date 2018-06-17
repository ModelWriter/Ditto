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

package test;

import runtime.Dispatch;

public class IntHashSet {
	ObjListElem[] buckets;
	
	int size;
	boolean doInvariants;
	
	public IntHashSet(int n) {
		buckets = new ObjListElem[n/4];
		ObjListElem prev = null;
		for (int q = 0 ; q < buckets.length ; q++) {
			ObjListElem e = new ObjListElem(null);
			if (prev != null)
				prev.next = e;
			buckets[q] = e;
			prev = e;
		}
	}
	
	Boolean codesMatchBuckets(int i, int len) {
		Dispatch.invCount++;
		if (i >= buckets.length)
			return true;
		boolean b1 = codesMatchElements((IntListElem)buckets[i].value, i, len),
			b2 = codesMatchBuckets(i+1, len);
		return b1 && b2;
	}
	
	Boolean codesMatchElements(IntListElem e, int i, int len) {
		Dispatch.invCount++;
		if (e == null) 
				return true;
		return (e.value % len == i) && 
			codesMatchElements(e.next, i, len);
	}
	
	void invariants() {
		//System.out.println("list is now " + this);
		if (doInvariants) { 
			if (! codesMatchBuckets(0, buckets.length)) {
				System.out.println("Hash element in wrong bucket!");
				System.out.println("Invariant count is " + Dispatch.invCount);
				System.exit(1);
			}
		}
	}
		
	void insert(Integer n) {
		invariants();
		int bucket = n.hashCode() % buckets.length;
		//System.out.println("Inserting element " + n + " into bucket " + bucket);
		IntListElem e = new IntListElem(n);
		e.next = (IntListElem) buckets[bucket].value;
		buckets[bucket].value = e;
		size++;
		invariants();
	}
	
	void barf() {
		IntListElem n = (IntListElem) buckets[1].value;
		n = n.next;
		n.value++;
		invariants();
	}
	void remove(Integer n) {
		invariants();
		int bucket = n.hashCode() % buckets.length;
		IntListElem iter = (IntListElem) buckets[bucket].value;
		if (iter == null) {
			System.out.println("Not in table!");
			return;
		}
		if (iter.value == n) {
			buckets[bucket].value = iter.next;
			size--;
		} else {
			while (iter.next != null && iter.next.value != n)
				iter = iter.next;
			if (iter.next == null) {
				System.out.println("Not in table!");
				return;
			} else {
				iter.next = iter.next.next;
				size--;
			}
		}
		invariants();
	}
}
