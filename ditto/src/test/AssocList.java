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

public class AssocList {
	ListElem head;
	boolean doInvariants = true;
	int size;
	Boolean isEven(ListElem e) {
		Dispatch.invCount++;
		if (e == null) return true;
		else return ! isEven(e.next);
	}
	
	Boolean noNullKeys(ListElem e) {
		Dispatch.invCount++;
		if (e == null)
			return true;
		if (e.value == null)
			return false;
		return noNullKeys(e.next.next); 
	}
	
	void invariants() {
		if (doInvariants) {
			if (! isEven(head)) {
				System.out.println("List has odd number of elements!");
				System.out.println("Invariant count is " + Dispatch.invCount);
				System.exit(1);			
			}
			if (! noNullKeys(head)) {
				System.out.println("List has null key!");
				System.out.println("Invariant count is " + Dispatch.invCount);
				System.exit(1);			
			}

		}
	}
	
	ListElem findValue(Object a) {
		ListElem g = head;
		while (g != null) {
			if (g.value == a)
				return g.next;
			g = g.next.next;
		}
		return null;
	}
	
	void insert(int a, int b) {
		invariants();
		//ListElem value = findValue(a);
		//if (value == null) {
			ListElem key = new ListElem(a);
			ListElem value = new ListElem(b);
			key.next = value;
			value.next = head;
			head = key;
//		} else {
//			value.value = b;
//		}
			size++;
		invariants();
	}
	
	void barf() {
		invariants();
		if (head != null)
			head.next = head.next.next;
		invariants();
	}
	
	void remove(Object a) {
		invariants();
		ListElem g = head;
		if (head.value == a) {
			head = head.next.next;
			size--;
		} else {
			while (g != null && g.next != null) {
				if (g.next.next != null && g.next.next.value == a) {
					g.next.next = g.next.next.next.next;
					size--;
					break;
				}
				g = g.next.next;
			}
		}
		invariants();
	}
}
