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
 * Primary runtime class for Ditto. Includes functions for fetching
 * memoized nodes frmo the graph, storing heap object->node usages, 
 * and rerunning invariants incrementally, in the main doIncremental function.
 */

package runtime;
import org.apache.commons.collections.buffer.BoundedFifoBuffer;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author aj
 *
 */
public class Dispatch {
	public static final int ignoreArgs = 2;
	public static final boolean DEBUG = false;
	static int maxInvariant;
	static int maxFunction;
	public static int count;
	public static int invCount;
  public static IncObject[] written = new IncObject[1000];
  static ArrayList invariants = new ArrayList();
  static ArrayList functions = new ArrayList();
  //static THashSet remove_nodes = new THashSet(new ObjStrategy());
  static ArrayList remove_nodes = new ArrayList();
  static THashSet rerun_nodes = new THashSet(new ObjStrategy());
  static ArrayList differing_nodes = new ArrayList();
  static BoundedFifoBuffer worklist_nodes = new BoundedFifoBuffer();
  static ArrayList affected_nodes = new ArrayList();
  static ComputationNode dummy = new ComputationNode(null, null);
  
  // when an object is created, it registers its invariants
  public static FunctionData registerFunction(FunctionData d) {
  	if (DEBUG)
  		System.out.println("Function registered!");
  	functions.add(d);
  	if (maxFunction >= Integer.SIZE) {
  		System.out.println("Too many invariants have been registered. " +
  				"Either change the type of IncObject.used to a long or find another solution.");
  		System.exit(1);
  	}

  	d.id = maxFunction++;
  	return d;
  }
  
  public static InvariantData registerInvariant(InvariantData d) {
  	if (DEBUG)
  		System.out.println("Invariant registered!");
  	invariants.add(d);
  	d.id = maxInvariant++;
  	return d;
  }
  
  // when an invariant is to be run, the write barrier list 
  // (written) is cleared out, and its locations forwarded
  // to all interested invariants
	public static void distribute() {
		for (int i = 0 ; i < count ; i++) {
			IncObject w = written[i];
			long ch = w.used;
			long id = 1;
			for (int j = 0 ; j < maxInvariant ; j++) {
				if ((ch & id) > 0) {
					((InvariantData) invariants.get(j)).waiting.add(w);
		    }
				id <<= 1;
			}
			w.used = 0;
		}
		count = 0;
	}
	
	public static ComputationNode getMemoized(FunctionData d, Object[] args, 
			ComputationNode parent, int slot) {
		if (DEBUG)
			System.out.println("Got to getMemoized with parent "+ (parent == null ? "null" : parent.ptrString()) + " and args " + args[0]);
		ComputationNode res = null;
		// leaf-node optimization
		 
//		for (int i = args.length - ignoreArgs ; i-- > 0; ) {
//			if (args[i] != null) {
//				end_early = false;
//				break;
//			}
//		}
/* 		if (args[0] == null && args[1] == null) {
			if (d.nullNode == null) {
				if (DEBUG)
					System.out.println("Computing nullNode");
				d.nullNode = new ComputationNode(args);
			}
			if (parent != null)
				ComputationNode.addEdge(parent, d.nullNode, true);
			return d.nullNode;
		}
*/ 
		res = (ComputationNode) d.memo.get(args);
		if (res == null) {
			res = new ComputationNode(args, d);
			if (DEBUG) {
				System.out.println("Couldn't find; adding node " + res + " with args " + args + " and hash value " + 
						ObjArrayStrategy.hash(args) + " first arg hash is " + (args[0] == null ? "null " : args[0].hashCode()));
				res.printArguments();
			}
			d.memo.put(args, res);
		} else if (res.dirty) {
			if (DEBUG)
				System.out.println("Reusing dirty node " + res.ptrString());
			res.removeUselocs(d.invariantData);
			res.result = null;
			res.dirty = false;

			for (int l = 0 ; l < res.children.length ; l++) {
				ComputationNode child = res.children[l];
				if (child == null) continue;
				if (DEBUG)
					System.out.println("Removing edge " + res.ptrString() + " to " + child.ptrString());
				ComputationNode.removeEdge(res, child, l);
				if (child.num_parents == 0)
					remove_nodes.add(child);
			}
			res.depth = Integer.MAX_VALUE;
		} else {
			if (DEBUG)
				System.out.println("Found existing memoized version " + res.ptrString());
			//res.arguments = args;
		}

		if (parent == null) {
			if (DEBUG)
				System.out.println("Setting computation tree");
			// explicitly remove parents
			for (int i = 0 ; i < res.parents_last ; i++) {
				ComputationNode n = res.parents[i];
				if (n == null) continue;
				if (DEBUG)
					System.out.println("Explicitly removing edge " + n.ptrString() + " to " + res);
				ComputationNode.removeEdge(n, res, -1);
				//if (n.num_children == 0 && n.num_parents == 0)
					//remove_nodes.add(n);
			}
			if (d.invariantData.graphRoot == null)
				res.depth = 0;
			d.invariantData.graphRoot = res;
		} else {
			if (parent != dummy) {
				if (DEBUG)
					System.out.println("Adding edge from " + parent.ptrString() + " to " + res.ptrString()); 
				ComputationNode.addEdge(parent, res, slot);

			}
		}
		return res;
	}
	
	public static String ptr(Object o) {
		return Integer.toHexString(System.identityHashCode(o));		
	}
	
	static Object oldo;
	static ComputationNode oldcn;
	public static void useMap(InvariantData d, Object o, ComputationNode cn) {
		int id = d.id;
		if (DEBUG)
			System.out.println("At usemap with id " + id + " and object " + o + " at " + ptr(o) + " and " + cn);
		if (o == oldo && cn == oldcn) return;
		IncObject op = (IncObject) o;

		ArrayList a = op.lists[id];
		if (a == null) {
			a = new ArrayList();
			op.lists[id] = a;
		}
		//if (! a.contains(cn)) { // turns out it's faster to keep the dupes
			a.add(cn);
			cn.uselocs.add(o);
		//}
		oldo = o ; oldcn = cn;
		op.used = op.used | (1 << id);
	}
	
	public static Object doIncremental(FunctionData fd, Object[] args) {
		if (DEBUG)
			System.out.println("- DoIncremental invoked for funcid " + fd);
		if (count > 0)
			distribute();

		InvariantData d = (InvariantData) fd.invariantData;
		ArrayList waiting = d.waiting;
		if (DEBUG) {
			System.out.println("Number of elements waiting: " + waiting.size());
			for (int q = 0 ; q < waiting.size() ; q++) {
				System.out.println("Element " + q + ": " + waiting.get(q) + " at " + ptr(waiting.get(q)));
			}
		}
		if (d.graphRoot == null) {
			if (DEBUG)
				System.out.println("Never been run; restarting");
			Object o = fd.run(args, null);
			if (DEBUG)
				System.out.println("Restarted value is " + o);
			return o;
		} 
		
		ComputationNode first = d.graphRoot;
		//System.out.println("First node " + first + " result is " + first.result + " and children is " + first.num_children);
		boolean redo_first = ! ObjArrayStrategy.sEquals(first.arguments, args);
		if (DEBUG && redo_first)
			System.out.println("Initial arguments differ");
		
		affected_nodes.clear();

		// first step: figure out what computation nodes are affected 
		// by the written locations
		for (int i = 0 ; i < waiting.size() ; i++) {
			Object o = waiting.get(i);
			if (DEBUG)  
				System.out.println("Waiting object detected: " + o + 
						" with hash value " + o.hashCode());
			IncObject op = (IncObject) o;
			ArrayList this_used = op.lists[d.id];
			if (DEBUG)
				System.out.println("Number of nodes affected is " + this_used.size());
			for (int y = this_used.size() ; y-- > 0 ; ) { 
				ComputationNode cn = (ComputationNode) this_used.get(y);
				if (! cn.dirty) {
					cn.dirty = true;
					if (DEBUG)
						System.out.println("Marking as dirty " + cn.ptrString());
					affected_nodes.add(cn);
				} else {
					if (DEBUG)
						System.out.println("Already seen node!");
				}
			}
			op.lists[d.id].clear();
		}
		
		waiting.clear();
		
		if (DEBUG)
			System.out.println("Number of nodes is " + affected_nodes.size());

		// initial arguments match, no nodes changed: just return old result
		if (affected_nodes.size() == 0 && ! redo_first)
			return d.graphRoot.result;
		
		int s = affected_nodes.size();
		// TODO now, sort affected by distance from root
		for (int m = 0 ; m < s-1 ; m++) {
			int min = ((ComputationNode) affected_nodes.get(m)).depth, min_idx = 0;
			for (int n = m+1 ; n < s ; n++) {
				ComputationNode node = (ComputationNode) affected_nodes.get(n);
				if (node.depth < min) {
					min = node.depth;
					min_idx = n;
				}
			}
			Object foo = affected_nodes.get(m);
			if (min_idx != 0) {
				affected_nodes.set(m, affected_nodes.get(min_idx));
				affected_nodes.set(min_idx, foo);
			}
		}
		
//		rerun_nodes.clear();
		remove_nodes.clear();
		differing_nodes.clear();

		if (redo_first) {
			if (DEBUG)
				System.out.println("Explicitly rerunning first");
			fd.run(args, null);
			// if the first node has been replaced, prune it
			if (first != d.graphRoot) {
				if (first.num_parents == 0)
					remove_nodes.add(first);
					//first.prune(d);
				first = d.graphRoot;
			}
		}

		for (int i = 0 ; i < s ; i++) {
			ComputationNode n = (ComputationNode) affected_nodes.get(i);
			//remove_nodes.clear();
			if (n.num_parents == 0 && n != d.graphRoot) {
				//if (DEBUG)
				//  System.out.println("Found one to prune " + n.ptrString());
				//n.printArguments();
				//n.prune(d);
				remove_nodes.add(n);
			} else if (n.dirty) {
				// if we find a node with dirty parents, skip it;
				// it will be rerun when the parents are rerun.
				if (n != d.graphRoot) {
					boolean bail = true;
					for (int gz = 0 ; gz < n.parents_last ; gz++) {
						ComputationNode p = n.parents[gz];
						if (p != null && !p.dirty)
							bail = false;
					}
					if (bail) continue;
				}
				for (int l = 0 ; l < n.children.length ; l++) {
					ComputationNode child = n.children[l];
					if (child == null) continue;
					if (DEBUG)
						System.out.println("Removing edge " + n.ptrString() + " to " + child.ptrString());
					ComputationNode.removeEdge(n, child, l);
					if (child.num_parents == 0) {
						if (DEBUG)
							System.out.println("Adding child to remove nodes ");
						remove_nodes.add(child);
					}
				}
				if (DEBUG)
					System.out.println("Running on node " + n.ptrString());
				if (n == first && redo_first)
					continue;

				if (! runNode(n)) {
					if (DEBUG)
						System.out.println("Found differing result for " + n.ptrString());
					differing_nodes.add(n);
				}
				n.dirty = false;
				//				for (int l = 0 ; l < remove_nodes.size() ; l++) {
//					ComputationNode child = (ComputationNode) remove_nodes.get(l);
//					if (DEBUG)
//					System.out.println("Checking child " + child.ptrString() + " for pruning");
//					if (child.num_parents == 0)
//						child.prune(d);
//				}
			}
		}
	
		// finally, recompute differing nodes
		if (differing_nodes.size() > 0) {
			worklist_nodes.clear();
			for (int w = 0 ; w < differing_nodes.size() ; w++) {
				ComputationNode foo = (ComputationNode) differing_nodes.get(w);
				// if we don' have any parents for this node, we have to abort and rerun 

				for (int x = 0 ; x < foo.parents_last ; x++) {
					ComputationNode bar = foo.parents[x];
					if (bar == null) continue;
					
					// only mark and add if hasn't already been done
					if (! worklist_nodes.contains(bar)) {
						markPathsToRoot(bar, 1);
						worklist_nodes.add(bar);
					}
				}
			}
			boolean had_to_rerun = recomputeDiffering(worklist_nodes);
			// if a noparent node forced a full recomputation, skip the final step
			if (had_to_rerun)
				return d.graphRoot.result;
		}

		
		if (DEBUG)
			System.out.println("num to prune is " + remove_nodes.size());
		int pruned = 0;
		for (int l = 0 ; l < remove_nodes.size() ; l++) {
			ComputationNode child = (ComputationNode) remove_nodes.get(l);
			if (DEBUG)
				System.out.println("Checking child " + child.ptrString() + " for pruning");

			if (child.num_parents == 0) {
				pruned += child.prune();
			}
		}
		if (DEBUG)
			System.out.println("num pruned is " + pruned);

		//System.out.println("- Done with doIncremental");
		return d.graphRoot.result;
	}

  private static boolean runNode(ComputationNode newcn) {
	  Object oldres = newcn.result;
	  //System.out.println("* Running on new node");
	  //newcn.printArguments();
	  Object res = newcn.data.run(newcn.arguments, 
	  		newcn == newcn.data.invariantData.graphRoot ? null : dummy);
	  boolean same = res.equals(oldres);
	  if (DEBUG && ! same)
	  	System.out.println("Old result was " + oldres+ " and new result is " + res);
	  return (same);
  }
	
	static void markPathsToRoot(ComputationNode n, int mark) {
		if (n == null)
			return;
		boolean return_early = false;
		if ((n.recompute > 0 && mark == 1) || (n.recompute > 1 && mark == -1)) {
			return_early = true;
		}
		n.recompute += mark;
		if (DEBUG)
			System.out.println("Marking paths to root for " + n.ptrString() + " with result " + n.result + 
					" and recompute " + n.recompute);		

		if (return_early) {
			if (DEBUG)
				System.out.println("Stopping mark here");
			return;
		}
		for (int i = 0 ; i < n.parents_last ; i++) {
			ComputationNode node = n.parents[i];
			if (node == null) continue;
			markPathsToRoot(node, mark);
		}
	}
	
	static boolean recomputeDiffering(BoundedFifoBuffer worklist) {
		if (DEBUG)
			System.out.println("Processing differing nodes " + worklist.size());
		while (worklist.size() > 0) {
			ComputationNode c = (ComputationNode) worklist.remove();
			
			c.recompute--;
			if (c.recompute == 0) {
				if (DEBUG)
					System.out.println("Recomputing difference for " + c.ptrString() + " old result " + c.result);
				// if result differs, parents need to be recomputed too
				c.dirty = true;
				Object oldres = c.result;
				if (DEBUG) {
					for (int i = 0 ; i < c.children.length ; i++) {
						ComputationNode child = c.children[i];
						if (child != null)
							System.out.println("Child (" + i + ") " + child.ptrString() + " result is " + child.result);
					}
				}
				c.result = c.data.runOnce(c.arguments, c.children);
				//System.out.println("Old was " + oldres + " and new is "  + c.result);
				if (! c.result.equals(oldres)) {
					if (DEBUG)
						System.out.println("Different result: " + c.result + "; adding parents again");

					if (c.noparent) {
						if (DEBUG)
							System.out.println("Noparent differs; rerunning entire invariant");
						ComputationNode root = c.data.invariantData.graphRoot;
						Object o = root.data.run(root.arguments, null);
						if (DEBUG)
							System.out.println("Restarted value is " + o);
						return true;
					}
					
					for (int i = 0 ; i < c.parents_last ; i++) { 
						if (c.parents[i] != null) {
							if (DEBUG)
								System.out.println("Added parent " + c.parents[i].ptrString());
							worklist.add(c.parents[i]);
						}
					}
				}	else {
					if (DEBUG)
						System.out.println("Found same result; stopping upchain");
					c.recompute++; // just so c.recompute is set back to 0 by markPaths
					markPathsToRoot(c, -1);
				}
				//System.out.println("Done recomputing difference");
				c.dirty = false;
			}
		}
		//System.out.println("Done with all differing");
		return false;
	}
}
