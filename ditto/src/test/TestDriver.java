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
 * The main driver function for running tests on Ditto.
 */
package test;

import java.util.Random;
/**
 * @author aj
 *
 */
public class TestDriver {
	static Random r = new Random(42);
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: TestDriver.main [num elems] [which test] [skip invariants]");
			System.exit(1);
		}
		int n = Integer.parseInt(args[0]);
		int which = Integer.parseInt(args[1]);
		boolean do_invariants = true;
		if (args.length > 2)
			do_invariants = Integer.parseInt(args[2]) != 1;
		
//		int off = 43;
//		for (int q = off ; q < off + 31 ; q++) {
//			System.out.println("Q is " + q);
//			runRBTree(q, 100, do_invariants);
//		}
		
		System.out.println("Do invariants is " + do_invariants);
		System.out.println("n is " + n);
		long start = 0;
		switch (which) {
		case 0:
			start = runList(n, 10000, do_invariants);
			break;
		case 1:
			start = runTree(n, 10000, do_invariants);
			break;
		case 2:
			start = runAssocList(n, 10000, do_invariants);
			break;
		case 3:
			start = runRBTree(n, 10000, do_invariants);
			break;
		case 4:
			start = runHash(n, 10000, do_invariants);
			break;
		}

		long end = System.currentTimeMillis();
		System.out.println("Total time: " + (end - start));
		
		System.out.println("InvCount is " + runtime.Dispatch.invCount);
		
		System.out.println("Done with all operations.");
	}

	private static long runRBTree(int n, int reps, boolean do_invariants) {
		System.out.println("Performing red-black tree tests");
		TreeMap t = new TreeMap();
		t.doInvariants = do_invariants;
		boolean[] can_remove = new boolean[n*10];
		int[] removed = new int[reps];
		int r_head = 0, r_tail = 0, big = n;
		for (int i = 0 ; i < n ; i++) {
			t.invariants();
			//System.out.println("Integer is " + i);
			t.put(i, n);
			can_remove[i] = true;
			t.invariants();
		}
		System.out.println("InvCount before test is " + runtime.Dispatch.invCount);
		int adds = 0;
		long start = System.currentTimeMillis();
		for (int j = 0 ; j < reps ; j++) {
			//if (j%1 == 0) System.out.println("Size is " + t.size() + " with +" + (2*adds - j) + " adds");
			t.invariants();
			int which = rnd(2);
			switch (which) {
			case 0:
				int toremove = rnd(n);
				int it = 0;
				while (! can_remove[toremove]) {
					toremove = rnd(big);
					it++;
				}
				can_remove[toremove] = false;
				//if (it > 5) System.out.println("it is " + it);
				removed[r_tail++] = toremove;
//				System.out.println("---- Removing " + toremove);
				t.remove(toremove);
				break;
			case 1:
				int h;
				if (r_head == r_tail)	{
					h = big++;
				} else {
					h = removed[r_head++];
				}
				can_remove[h] = true;
				adds++;
				//System.out.println("---- Adding " + h);
				t.put(h, n);
				break;
			}
			t.invariants();
		}
		//t.barf();
		//t.invariants();
		System.out.println(adds + " adds of " + reps + " total; size is " + t.size());
		return start;
	}
	
	private static long runTree(int n, int reps, boolean do_invariants) {
		System.out.println("Performing ordered tree tests");
		OrderedIntTree t = new OrderedIntTree();
	  System.out.println("Inserting " + n + " elements into tree...");
		t.doInvariants = do_invariants;
		long start = System.currentTimeMillis();
		for (int i = 0 ; i < n ; i++) {
			t.insert(rnd(n));
			//t.insert((i+(n/2))%n);
		}

		//System.out.println("Barfing...");
		//t.barf();
		return start;
	}
	
	private static long runList(int n, int reps, boolean do_invariants) {
		System.out.println("Performing ordered list tests");
		OrderedIntList l = new OrderedIntList();
	  System.out.println("Inserting " + n + " elements into list...");
		l.doInvariants = do_invariants;
		int[] added = new int[n];
		for (int i = 0 ; i < n ; i++) {
			l.insert(i);
			added[i]++;
		}
		System.out.println("Starting real test");
		int adds = 0;
		long start = System.currentTimeMillis();
		for (int j = 0 ; j < reps ; j++) {
			//if (j%10 == 0) System.out.println("Size is " + l.size + " with +" + (2*adds - j) + " adds");
			int which = rnd(4);
			switch (which) {
			case 0:
				int toremove = rnd(n);
				while (added[toremove] == 0) {
					toremove = rnd(n);
				}
		//		System.out.println("Removing " + toremove);
				added[toremove]--;
				l.remove(toremove);
				break;
			case 1:
	//			System.out.println("Shifting");
				added[l.shift()]--;
				break;
			default:
				adds++;
				int toadd = rnd(n);
				added[toadd]++;
//			System.out.println("Adding " + toadd);
				l.insert(toadd);
				break;
			}
		}
		System.out.println(adds + " adds of " + reps + " total " + l.size());
		//l.barf();

		return start;	
  }
	
	private static long runHash(int n, int reps, boolean do_invariants) {
		System.out.println("Performing hash map tests");
		IntHashSet m = new IntHashSet(n);
	  System.out.println("Inserting " + n + " elements into hash...");
		m.doInvariants = do_invariants;
		int[] added = new int[n];
		for (int i = 0 ; i < n ; i++) {
			//System.out.println("Inserting " + i);
			m.insert(i);
			added[i]++;
		}
		System.out.println("Starting real test");
		int adds = 0;
		long start = System.currentTimeMillis();
		for (int j = 0 ; j < reps ; j++) {
			//if (j%10 == 0) System.out.println("Size is " + m.size + " with +" + (2*adds - j) + " adds");
			int which = rnd(2);
			switch (which) {
			case 0:
				int toremove = rnd(n);
				while (added[toremove] == 0) {
					toremove = rnd(n);
				}
				//System.out.print("Removing " + toremove + " ");
				added[toremove]--;
				m.remove(toremove);
				break;
			case 1:
				adds++;
				int toadd = rnd(n);
				added[toadd]++;
				//System.out.print("Adding " + toadd + "   ");
				m.insert(toadd);
				break;
			}
		}

		//System.out.println("Barfing...");
		//m.barf();
		System.out.println(adds + " adds of " + reps + " total; size is " + m.size);

		return start;	
  }

	private static int rnd(int n) {
		return (r.nextInt(n));
	}
	
	private static long runAssocList(int n, int reps, boolean do_invariants) {
		System.out.println("Performing assoc list tests");
		AssocList a = new AssocList();
		a.doInvariants = do_invariants;
	  System.out.println("Inserting " + n + " elements into list...");
		int[] added = new int[n];
	  for (int i = 0 ; i < n ; i++) {
			a.insert(i, i+1);
			added[i]++;
		}
	  System.out.println("Adding done");
		long start = System.currentTimeMillis();
		int adds = 0;
		for (int j = 0 ; j < reps ; j++) {
			//if (j%1 == 0) System.out.println("Size is " + a.size + " with +" + (2*adds - j) + " adds");
			int which = rnd(2);
			switch (which) {
			case 0:
				if (a.size == 0)
					continue;
				int toremove = rnd(n);
				while (added[toremove] == 0) {
					toremove = rnd(n);
				}
				//System.out.print("Removing " + toremove + " ");
				added[toremove]--;
				a.remove(toremove);
				break;
			case 1:
				adds++;
				int toadd = rnd(n);
				added[toadd]++;
				//System.out.print("Adding " + toadd + "   ");
				a.insert(toadd, n);
				break;
			}
		}
		//a.barf();
		System.out.println(adds + " adds of " + reps + " total; size is " + a.size);
		return start;
	}
}

