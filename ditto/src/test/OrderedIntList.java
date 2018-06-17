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
 * 
 */
package test;

import runtime.Dispatch;

/**
 * @author aj
 *
 */
public class OrderedIntList {
	IntListElem head;
	boolean doInvariants = true;
	int realsum;
	OrderedIntList() { head = null; }
	Boolean isOrdered(IntListElem n) {
		Dispatch.invCount++;
		
		if (n == null || n.next == null)
			return true;
		if (n.value > n.next.value)
			return false;
		return isOrdered(n.next);
	}
	
	Integer sum(IntListElem n) {
		Dispatch.invCount++;
		if (n == null)
			return 0;
		return n.value + sum(n.next);
	}

	int size() {
		int s = 0;
		for (IntListElem i = head ; i != null ; i = i.next) s++;
		return s;
	}
	
	
	void invariants() {
		//System.out.println("list is now " + this);
		if (doInvariants) { 
		if (! isOrdered(head)) {
			System.out.println("List is not ordered!");
			System.out.println("Invariant count is " + Dispatch.invCount);
			System.exit(1);
		} 
//			int s = sum(head);
//			if (s != realsum) {
//				System.out.println("Computed sum " + s + " differs from real sum " + realsum);
//				System.exit(1);
//			}
				
		}
		// System.out.println("Invariant count is " + InvCount);
	}
	 
	int shift() {
		invariants();
		//System.out.println("Shifting...");
		if (head == null) {
			//System.out.println("WTF?");
			invariants();
			return 0;
		}
		IntListElem n = head;
		head = head.next;
//		realsum -= n.value;
		invariants();
		return n.value; 
	} 
	
	// removes a single element with value a
	void remove(int a) {
		invariants();
		//System.out.println("Removing " + a);
		IntListElem n = head;
		if (n != null && n.value == a) {
			head = n.next;
//			realsum -= n.value;
			invariants();
			return;
		}
		while (n != null && n.next != null && n.next.value < a) {
			n = n.next;
		}
		
		if (n != null && n.next != null && n.next.value == a) {
			//realsum -= n.next.value;
			n.next = n.next.next;
		}
		invariants();
	}
	
	// inserts while preserving order
	void insert(int a) {
		//System.out.println("Top invariants");
		invariants(); 
		//realsum += a;
		//System.out.println("Inserting " + a);
		IntListElem nw = new IntListElem(a);
		if (head == null || head.value >= a) {
			if (head != null)
				nw.next = head;
			head = nw;
			invariants(); 
			return;
		}
		IntListElem n = head;
		while (n != null && n.next != null && n.next.value < a)
			n = n.next;
		
		nw.next = n.next;
		n.next = nw;
		//System.out.println("Invariants at bottom");
		invariants(); 
	}
	
	public void barf() {
		invariants(); 
		System.out.println("About to barf!");
		head.value = head.next.value + 1;
		invariants(); 
	}
	
	public String toString() {
		StringBuffer b = new StringBuffer("[ ");
		IntListElem m = head;
		while (m != null) {
			b.append(m.value + " ");
			m = m.next;
		}
		b.append("]");
		return b.toString();
	}
	
	boolean hasElements() { 
		return head != null; 
	}
}
