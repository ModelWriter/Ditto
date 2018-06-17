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
public class OrderedIntTree {
	IntTreeElem root;
	boolean doInvariants = true;
	int maxSum;
	
	Boolean isOrderedLocal(IntTreeElem e) {
		Dispatch.invCount++;
		if (e == null)
			return Boolean.TRUE;
		if (e.left != null && e.value <= e.left.value)
			return Boolean.FALSE;
		if (e.right != null && e.value >= e.right.value)
			return Boolean.FALSE;
		return isOrderedLocal(e.left) && isOrderedLocal(e.right);
	}
	
	Boolean isOrdered(IntTreeElem e, int lower, int upper) {
		Dispatch.invCount++;
		if (e == null)
			return true;
		IntTreeElem l = e.left, r = e.right;
		int val = e.value;
		if (val <= lower || val >= upper)
			return false;
		if (l != null && val <= l.value)
			return false;
		if (r != null && val >= r.value)
			return false;
		return (l == null || isOrdered(l, lower, val)) && 
				(r == null || isOrdered(r, val, upper));
	}

	Integer sumNodes(IntTreeElem e) {
		Dispatch.invCount++;
		if (e == null)
			return 0;
		//System.out.println("left is " + e.left + " and right is " + e.right);
		return e.value + sumNodes(e.left) + sumNodes(e.right);
	}
	
	Integer realSumNodes(IntTreeElem e) {
		if (e == null)
			return 0;
		return e.value + realSumNodes(e.left) + realSumNodes(e.right);		
	}
	
	void invariants() {
		//System.out.println("list is now " + this);
		if (doInvariants) {
			boolean good = true;
			if (! isOrdered(root, Integer.MIN_VALUE, Integer.MAX_VALUE)) { 
				System.out.println("Tree is not ordered!");
				good = false;
			}
			int sum = sumNodes(root);
//			System.out.println("Sum is " + sum);
			//System.out.println("Real sum is " + realSumNodes(root));
			if (sum > maxSum) {
				System.out.println("Tree sum exceeds maxSum of " + maxSum);
				good = false;
			}
			if (! good) {
				System.out.println("Invariant count is " + Dispatch.invCount);
				System.exit(1);
			}
		} 
		// 
	}		
	
	void insert(int i) {
		invariants();
		//System.out.println("inserting number " + i);
		if (root == null) {
			root = new IntTreeElem(i);
		} else {
			insertRec(root, i);
		} 
		maxSum += i;
		invariants();
	}
	
	void insertRec(IntTreeElem which, int i) {
		if (which.value == i)
			return;
		if (which.value > i)
			if (which.left != null)
				insertRec(which.left, i);
			else
				which.left = new IntTreeElem(i);
		else 			
			if (which.right != null)
				insertRec(which.right, i);
			else
				which.right = new IntTreeElem(i);
	}
	
	// intentionally breaks invariant
	void barf() {
		invariants();
		if (root != null && root.left != null)
			root.value = root.left.value;
		invariants();
	}
	
	
}