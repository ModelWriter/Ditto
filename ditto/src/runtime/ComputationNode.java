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

/*
 * Data structure for nodes in the computation graph.
 * Stores function arguments, results, used heap values, etc.
 * Since there is no "ComputationGraph" data structure, node 
 * ordering is done by some static functions in this class. 
 */

package runtime;

import java.util.ArrayList;

import org.apache.commons.collections.buffer.BoundedFifoBuffer;

// the function is implicit because we can't have function pointers
public class ComputationNode {
	static BoundedFifoBuffer ancestors = new BoundedFifoBuffer();
	static BoundedFifoBuffer distances = new BoundedFifoBuffer();
	static int[] skipInfo = new int[2];
	static final double depthScale = .0001; 
	static final int PARENT_ARRAY_SIZE = 3;

	public Object result;
	public Object[] arguments;
	ComputationNode[] parents;
	ComputationNode[] children;
	FunctionData data;
	int parents_last;
	int num_parents, depth = Integer.MAX_VALUE;
	ArrayList uselocs;
	boolean dirty;
	int recompute;
	boolean noparent = false;
	
	void printArguments() {
		for (int q = 0 ; q < arguments.length ; q++)
			System.out.println("Arg " + q + ": " + arguments[q]);
	}
	
	static void addEdge(ComputationNode _parent, ComputationNode _child, int slot) {
		_parent.addChild(_child, slot);
		_child.addParent(_parent);
	}
	
	static void removeEdge(ComputationNode _parent, ComputationNode _child, int slot) {
		if (slot >= 0)
			_parent.removeChild(slot);
		else
			_parent.removeChild(_child);
		_child.removeParent(_parent);
	}
	
	public void addChild(ComputationNode c, int slot)  {
		try {
			children[slot] = c;
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Too many children in function; possible bug in Transform.");
			System.exit(1);
		}
  }

	// computes depths for this node and parent nodes 
	// that don't have depths yet
	public static void computeDepth(ComputationNode n) {
		if (n == null) 
			return;
		if (Dispatch.DEBUG)
			System.out.println("Computing depth for node " + n.ptrString());
		int num = 0;
		int child_d = Integer.MAX_VALUE;
		ComputationNode child = null;
		boolean mustReorder = false;
		for (int j = 0 ; j < n.children.length ; j++) {
			if (n.children[j] == null) continue;
			if (n.depth != Integer.MAX_VALUE && n.children[j].depth <= n.depth) {
				if (Dispatch.DEBUG)
					System.out.println("Child " + n.children[j].ptrString() + " less than parent " + n.ptrString() + 
							"; forcing reorder");
				child = n.children[j];
				mustReorder = true;
				break;
			}
			if (n.children[j].depth < child_d) {
				child_d = n.children[j].depth;
				child = n.children[j];
			}
		}

		ComputationNode s = n;
		while (s != null && s.depth == Integer.MAX_VALUE) {
			num++;
			s = s.firstParent();
		}
		
		if (! mustReorder) {
			int[] ss = computeSkipStart(child, s, num);
			if (ss[0] != -1) {
				int start = ss[1];
				int skip = ss[0];

				s = n;
				while (num-- > 0) {
					s.depth = start;
					if (Dispatch.DEBUG)
						System.out.println("Setting depth for node " + s.ptrString());
					start -= skip;
					s = s.firstParent();
				}
			} else {
				mustReorder = true;
			} 
		}
		if (mustReorder) {
			// we have to renumber
			// do it here and then re-call this function
			// complicated because we may need to follow many paths up the tree
			// basic idea: increase the range 
			if (Dispatch.DEBUG)
				System.out.println("Reordering!");
			
			searchParentsDepth(n, child.depth, ancestors, distances, 1);
			ComputationNode starta = (ComputationNode) ancestors.remove();
			int startd = (Integer) distances.remove();
			int[] ns = computeSkipStart(child, starta, startd);
			renumberAncestors(n, starta, 
					startd, ancestors, distances, ns[1], ns[0], 0); 
			//assert(ancestors.size() == 0);
			//assert(distances.size() == 0);
		}
		ComputationNode z = n.data.invariantData.graphRoot;
		if (Dispatch.DEBUG && ! z.checkOrdering()) {
			System.out.println("Depth error!");
			z.debugPrint("");
			System.exit(1);
		}

	}
	
	static int[] computeSkipStart(ComputationNode child, ComputationNode ancestor, int num) {
		int skip, start;
		int parent_d = (ancestor == null ? Integer.MIN_VALUE : ancestor.depth);
		int child_d = (child == null ? Integer.MAX_VALUE : child.depth);
		if (parent_d > (child_d - num) - 1) {
			skipInfo[0] = -1;
			return skipInfo;
		}
		// if there is no outer limit, scale by some constant of the difference
		if (parent_d == Integer.MIN_VALUE && child_d == Integer.MAX_VALUE) {
			skip = (int) (Integer.MAX_VALUE * depthScale);
			start = num/2 * skip;
		} else if (parent_d == Integer.MIN_VALUE) {
			skip = Math.abs((int) (depthScale * (parent_d - child_d)));
			start = child_d - skip;
		} else if (child_d == Integer.MAX_VALUE) {
			skip = Math.abs((int)(depthScale * (child_d - parent_d)));
			start = parent_d + num*skip;
		} else {
			skip = (int) ((child_d - parent_d) / (double)(num+1));
			start = child_d - skip;
		}
		if (skip < 1) skip = 1;
		skipInfo[0] = skip;
		skipInfo[1]	= start;
		return skipInfo;
	}
	
	static void renumberAncestors(ComputationNode curr, ComputationNode target, int distance,
			BoundedFifoBuffer ancestors, BoundedFifoBuffer distances, int start, int skip, int num) {
		if (curr == target || curr == null || num == distance) return;
		int old = curr.depth;
		curr.depth = start;
		if (Dispatch.DEBUG)
			System.out.println("Set node depth from " + old + " for " + curr.ptrString());
		renumberAncestors(curr.firstParent(), target, distance, ancestors, distances, start-skip, skip, num+1);

		if (curr.num_parents > 1) {
			boolean foundit = false;
			for (int i = 0 ; i < curr.parents.length ; i++) {
				if (curr.parents[i] != null) {
					if (! foundit) {	
						foundit = true;
					} else {
						ComputationNode newt = (ComputationNode) ancestors.remove();
						Integer newd = (Integer) distances.remove();
						int[] news = computeSkipStart(curr, newt, newd-num);
						int newskip = news[0];
						int newstart = news[1];
						renumberAncestors(curr.parents[i], newt, newd, ancestors, distances, newstart, newskip, num+1);
					}
				}
			}
		}
	}
	
	static void searchParentsDepth(ComputationNode n, int start,
			BoundedFifoBuffer ancestors, BoundedFifoBuffer distances, int num) {
		if ((start - n.depth)/num > 50) { 
			ancestors.add(n);
			distances.add(num);
			return;
		} else {
			if (n.noparent) {
				System.out.println("Screwed. Need to fix this.");	
				System.exit(1);
			}
			boolean did = false;
			for (int i = 0 ; i < n.parents.length ; i++) {
				if (n.parents[i] != null) {
					searchParentsDepth(n.parents[i], start, ancestors, distances, num+1);
					did = true;
				}
			}
			if (! did) {
				ancestors.add(Dispatch.dummy);
				distances.add(num+1);
			}
		}
	}
	
	public void addParent(ComputationNode p) {
		//System.out.println("Adding parent " + p + " to " + this);
		if (noparent) {
			num_parents++;
			return;
		}
		if (parents_last == parents.length) {
			if (parents_last > num_parents) {
				// compact
				int j = 0, k = parents_last;
				for (int  i = 0 ; i < k ; i++) {
					if (parents[i] == null) {
						parents_last--;
					} else {
						if (i != j) {
							parents[j++] = parents[i];
							parents[i] = null;
						}
					}
				}
			} else {
				noparent = true;
				if (Dispatch.DEBUG)
					System.out.println("Setting noparent flag!");
				num_parents++;
				return;
			}
		} 
		parents[parents_last++] = p;
		num_parents++;
		//System.out.println("Successfully added parent; now with " + num_parents);
	}
		
	public void removeParent(ComputationNode p) {
		boolean found = false;
		if (noparent) {
			num_parents--;
			return;
		}
		for (int i = 0 ; i < parents_last ; i++) {
			if (parents[i] == null) continue;
			if (!found && parents[i] == p) {
				parents[i] = null;
				found = true;
				num_parents--;
				//System.out.println("Parent " + p + " succesfully removed, leaving " + num_parents);
			} else {

			}
		}
		if (num_parents == 0)
			parents_last = 0;
	}

	ComputationNode firstParent() {
		for (int i = 0 ; i < parents.length ; i++) {
			if (parents[i] != null)
				return parents[i];
		}
		return null;
	}

	public void removeChild(int slot) {
		children[slot] = null;
	}

	public void removeChild(ComputationNode p) {
		for (int i = 0 ; i < children.length ; i++)
			if (children[i] == p) {
				children[i] = null;
				return;
			}
	}

	void removeUselocs(InvariantData d) {
		for (int i = uselocs.size() ; i-- > 0; ) {
			IncObject o = (IncObject) uselocs.get(i);
			//ArrayList a = (ArrayList) o.lists.get(d.id);
			ArrayList a = o.lists[d.id];
			a.remove(this);
		}	
		uselocs.clear();
	}
	
	int prune() {
		if (data.memo.get(arguments) == this) {
			data.memo.remove(arguments);
		} else {
			//System.out.println("Different memoized version already stored!");
		}
		
		if (Dispatch.DEBUG)
			System.out.println("pruning " + ptrString());
		depth = Integer.MAX_VALUE;
		removeUselocs(data.invariantData);
		int pruned = 1;
		for (int j = 0 ; j < children.length ; j++) {
			ComputationNode child = children[j];
			//System.out.println("pruning edge to " + child);
			if (child == null) continue;
			ComputationNode.removeEdge(this, child, j);
			if (child.num_parents == 0) 
				pruned += child.prune();
			else 
				if (Dispatch.DEBUG)
					System.out.println("Stopping; num parents is " + child.num_parents);
		}
		return pruned;
	}
	
	String ptrString() {
		if (this == Dispatch.dummy)
			return "Dummy";
		return this + " (" + this.result + ", " + this.arguments[0]	 + ") depth " + depth;
	}
	
	boolean checkOrdering() {
		for (int i = 0 ; i < children.length ; i++) {
			if (children[i] != null) {
				if (children[i].depth <= depth || ! children[i].checkOrdering()) {
					if (children[i].depth <= depth)
						System.out.println("Depth of parent " + this + " is " + depth + 
								" and depth of child " + children[i] + " is " + children[i].depth);
					return false;
				}
			}
		}
		return true;
	}
	
	public void debugPrint(String indent) {
		System.out.println(indent + ptrString());
		for (int i = 0 ; i < children.length ; i++) {
			if (children[i] != null) {
				children[i].debugPrint(indent + " ");
			}
				
		}
	
	}
	
	ComputationNode(Object[] args, FunctionData d) {
		parents = new ComputationNode[PARENT_ARRAY_SIZE];
		if (d != null)
			children = new ComputationNode[d.numCalls()];
		uselocs = new ArrayList();
		arguments = args;
		data = d;
	}
}
