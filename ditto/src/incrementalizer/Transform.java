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
 * Does the offline bytecode transformation of invariants:
 * adds hooks to the invariants to call the runtime portion of Ditto,
 * inserts write barriers in the rest of the program to track heap changes.
 */
package incrementalizer;
import javassist.*;
import javassist.expr.*;

import java.io.IOException;
import java.util.*;
import java.io.*;

/*
 * Optimizations to do:
 * multiple functions
 * help out with which args are non primitive
 * help out with runOnce node calls
 * special case hashCode, size, etc
 * objs accessed "everywhere" can be encoded more efficiently
 */

public class Transform {
	static boolean verbose = false; 
	static ClassPool pool;
	static ArrayList invFuncs;
	static ArrayList allClasses;
	static HashSet classesToWrite;
	static HashSet barrieredTypes;
	static HashMap methodsToInfo;
	static class InvariantInfo {
		String name;
		boolean hasEntrypoint;
		public InvariantInfo(String n) {
			name = n;
			hasEntrypoint = false;
		}
	}

	static void usage() {
		System.out.println("Usage: Transform [inv_list] [class_list] [--verbose]\n" +
				"where 'inv_list'   containts a list of invariant functions, e.g. Foo.Bar\n" +
				"and   'class_list' containts a list of all classes to be write-barriered.");
		System.exit(1);
	}

	/**
	 * @param args
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 * @throws IOException
	 */
	public static void main(String[] args) throws NotFoundException, IOException, CannotCompileException {
		System.out.println("Ditto: Automatic Invariant Incrementalization");
		System.out.println("Contact: AJ Shankar (aj@cs.berkeley.edu)\n");
		if (args.length < 2)
				usage();
		
		if (args.length == 3 && args[2].equals("--verbose"))
			verbose = true;
		
		// read in list of functions that are invariants
		String inv_list = args[0];
		invFuncs = new ArrayList();
		barrieredTypes = new HashSet();
    readInputStrings(inv_list, invFuncs);	

    String class_list = args[1];
    allClasses = new ArrayList();
    readInputStrings(class_list, allClasses);

		// process them one by one
		ArrayList toIncrementalize = new ArrayList();
    classesToWrite = new HashSet();
		pool = ClassPool.getDefault();
		// to let Javassist find the relevant class
		pool.insertClassPath(".");
		for (int i = 0 ; i < invFuncs.size() ; i++) {
			String s = (String) invFuncs.get(i);
			int off = s.lastIndexOf('.');
			if (off == -1) 
				break;
			String cl = s.substring(0,off);
			String method = s.substring(off+1);
			CtClass cc = null;
			try {
		    cc = pool.get(cl);
		    // we store the name of the class containing the invariant
		    // so that we can write out the class after modifications
		    classesToWrite.add(cc);
	    } catch (NotFoundException e1) {
	    	System.out.println("Cannot find class " + cl + "; aborting.");
	    	System.exit(1);
	    }
	    CtMethod m = null;
	    try {
	    	m = cc.getDeclaredMethod(method);
	    } catch (NotFoundException e){
	    	System.out.println("Cannot find method " + method + " in class " + cl);
	    	System.exit(1);
	    }
	    System.out.println("Incrementalizing " + s);
	    toIncrementalize.add(m);
		}
		
		// we have to do this in stages to break recursion between functions
		for (int j = 0 ; j < toIncrementalize.size() ; j++) {
			CtMethod m = (CtMethod) toIncrementalize.get(j);
			toIncrementalize.set(j, rewriteFunctionDeclaration(m));
		}

		methodsToInfo = new HashMap();
		for (int j = 0 ; j < toIncrementalize.size() ; j++) {
			CtMethod m = (CtMethod) toIncrementalize.get(j);
	    // add hooks so that when the invariant is run, the runtime
	    // library will construct the incremental data structures
	    instrumentInvariant(m);
		}
		
    // once we've incrementalized everything and identified
    // what data types the invariants depend on, we write barrier 
    // those data tyeps throughout the whole program
		insertWriteBarriers(allClasses);
		if (verbose) 
			System.out.println("Num barriered " + numBarriered + " of " + numTotal);

		// write out all the classes at the end
		Iterator i = classesToWrite.iterator();
		while(i.hasNext()) {
			CtClass myc = (CtClass) i.next();
			myc.writeFile();
//			if (verbose)
//				System.out.println("Class " + myc.getName() + " inherits from " + myc.getSuperclass().getName());
		}
		System.out.println("Done.");
	}

	static boolean isInvariant(CtMethod m) {
		return (m.getName().indexOf("runOnce") != -1 ||
			invFuncs.indexOf(m.getDeclaringClass().getName() + "." +	m.getName()) != -1);
	}
	static boolean isInvariant(MethodCall m) {
		return (m.getMethodName().indexOf("runOnce") != -1 ||
			invFuncs.indexOf(m.getClassName() + "." +	m.getMethodName()) != -1);
	}
	
	private static void readInputStrings(String filename, ArrayList l) {
	  try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String str;
      while ((str = in.readLine()) != null) {
      	if (! str.startsWith("//"))
          l.add(str);
      }
      in.close();
    } catch (IOException e) {
    	System.out.println("Error reading input file " + filename);
    	System.exit(1);
    }
  }
	
	static int numBarriered;
	static int numTotal;
	
	static void insertWriteBarriers(ArrayList cls) throws NotFoundException, CannotCompileException {
		CtClass obj = pool.get("java.lang.Object");
		CtClass inc_obj = pool.get("runtime.IncObject");
		// first, superclass all the classes that need used bits
		Iterator i = barrieredTypes.iterator();
		if (verbose)
			System.out.println("Number of field-types to barrier is " + barrieredTypes.size());
		while (i.hasNext()) {
			CtField f = (CtField)	i.next();
//			if (verbose)
//				System.out.println("Current field is " + f.getName());
			CtClass c = f.getDeclaringClass();
			if (c == obj) continue;
			// move up until right before java.lang.object, and then insert
			while (c.getSuperclass() != obj) {
//				if (verbose)
//					System.out.println("c is now " + c.getName());
				c = c.getSuperclass();
			}
			if (c != inc_obj) {
				if (verbose)
					System.out.println("Setting superclass of " + c.getName() + " to IncObject");
				c.setSuperclass(inc_obj);
				classesToWrite.add(c);
			}
//			if (verbose)
//				System.out.println("class " + c.getName() + " inherits from " + c.getSuperclass().getName());
		}
		
		// now, iterate through all code, inserting barriers where necessary
		// For each class, for each method, find all field writes of appropriate type 
		// and enclosing object.
		Iterator j = cls.iterator();
		while (j.hasNext()) {
			String cn = (String) j.next();
			CtClass c = pool.get(cn);
			CtMethod[] ms = c.getDeclaredMethods();
			for (int k = 0 ; k < ms.length ; k++) {
				CtMethod m = ms[k];
//				if (verbose)
//					System.out.println("Inserting barriers for method " + m.getLongName());
				final String replacer = "{ if (((runtime.IncObject) $0).used != 0) "
					+ "runtime.Dispatch.written[runtime.Dispatch.count++] = $0; $proceed($1); }";
				m.instrument(
					new ExprEditor() {
				  	public void edit(FieldAccess f) throws CannotCompileException {
			    		try {
			    			CtField gf = f.getField();
			    			if (f.isWriter())
			    				numTotal++;
					  		if (f.isWriter() && ! f.isStatic() && barrieredTypes.contains(gf)) {
//					  			if (verbose)
//					  				System.out.println("Found field candidate " + f.getFieldName());
					  			numBarriered++;
				    			f.replace(replacer);
				    			barrieredTypes.add(gf);
					  		}
			    		} catch (Exception e) {
			    			System.out.println("Can't get field. " + e);
			    			e.printStackTrace();
				    	}
				  	}
					});				
			}
		}
	}
	
	// Code to generate a unique ID at runtime for a particular
	// instantiation of an invariant. Run in the containing class's
	// constructor.
	static void addSetIDToConstructors(CtConstructor[] c, String id_name, 
			String data_name, InvariantInfo ii, boolean init_ii) throws CannotCompileException {
		String bool = id_name + "Done";
		for (int i = 0 ; i < c.length ; i++) {
			if (! c[i].isConstructor())
				continue;
			if (init_ii) {
				String s2 = "{  if (!"+bool+") { " + ii.name + 
					" = runtime.Dispatch.registerInvariant(new runtime.InvariantData());}}";
				if (verbose)
					System.out.println("Adding invariant register to constructor " + c[i].getName());
				c[i].insertBeforeBody(s2);
			}
			String s = "{ if (!"+bool+") { " + id_name + " = runtime.Dispatch.registerFunction(new "
				+ data_name + "((Object) this, " + ii.name + ")); " + bool + " = true; }}";
			if (verbose)
				System.out.println("Adding function register to constructor " + c[i].getName());
			c[i].insertAfter(s);

		}
	}
	
	static String getConv(String cast, String expr, String conv) {
		return "((" + cast + ") " + expr + ")."+conv+"()";
	}
	
	static String getParmCast(CtClass parm, String expr) {
		if (parm == CtClass.intType)
			return getConv("Integer", expr, "intValue");
		else if (parm == CtClass.booleanType)
			return getConv("Boolean", expr, "booleanValue");
		else if (parm == CtClass.floatType)
			return getConv("Float", expr, "floatValue");
		else if (parm == CtClass.doubleType)
			return getConv("Double", expr, "doubleValue");
		else if (parm == CtClass.charType)
			return getConv("Character", expr, "charValue");
		else
			return "("+parm.getName()+") " + expr;
	}
	
	// the call to invoke the invariant used by the run function below
	static String createFunctionCall(CtMethod m, String name, String extra_args) 
		throws NotFoundException {
		String cn = m.getDeclaringClass().getName();
		StringBuffer call = new StringBuffer(cn + " foo = ("+cn+") dataStructure;\n" +
				"return foo." + name + "(");
		CtClass[] parm_types = m.getParameterTypes();
		int args = parm_types.length - runtime.Dispatch.ignoreArgs;
		for (int i = 0 ; i < args ; i++ ) {
			call.append(getParmCast(parm_types[i], "args["+i+"]") + ", ");
		}
		call.append(extra_args + ");");
		return call.toString();
	}
	
	// this is a driver function that the runtime dispatch invokes
	// it calls the actual invariant with the correct arguments
	static void createInvariantDataRun(CtMethod m, CtClass c, int numCalls) 
		throws NotFoundException, CannotCompileException {
		String call = createFunctionCall(m, m.getName(), "parent, -1");
		if (verbose)
			System.out.println("Driver function run call is " + call);
		String debug = "System.out.println(\"About to run invariant\");";
		CtMethod newm = CtNewMethod.make("public Object run(Object[] args, " + 
				"runtime.ComputationNode parent) { /*" + debug + "*/ " 
				+ call + " } ", c);
		c.addMethod(newm);
		
		String once_call = createFunctionCall(m, m.getName() + "_runOnce", "children");
		if (verbose)
			System.out.println("Run once call is " + once_call);
		CtMethod oncem = CtNewMethod.make("public Object runOnce(Object[] args, " + 
				"Object[] children) { " + once_call + " }", c);
		c.addMethod(oncem);
		
		CtMethod ncm = CtNewMethod.make("public int numCalls() { return " + numCalls + "; }", c);
		c.addMethod(ncm);
	}
	
	static boolean didSomething;
	
	static boolean rerouteInvariantCalls(final CtMethod m, final CtClass c, 
						final String inv_id) 
			throws CannotCompileException, NotFoundException {
		final CtClass ret_type = m.getReturnType();
		CtMethod[] ms = c.getDeclaredMethods();
		didSomething = false;
		for (int i = 0 ; i < ms.length ; i++) {
			CtMethod newm = ms[i];
			// do not want to reroute calls among invariants
			if (isInvariant(newm))
				continue;
//			if (verbose)
				//System.out.println("Rerouting calls in method " + newm.getLongName());
			newm.instrument(
			    new ExprEditor() {
			        public void edit(MethodCall callm)
			                      throws CannotCompileException {
			        	if (callm.getClassName().equals(m.getDeclaringClass().getName())
			                && callm.getMethodName().equals(m.getName())) {
			        		if (verbose)
			        			System.out.println("Rerouting call " + c.getName() + "." + m.getName());
			            String replacer = "{ /* System.out.println(\"about to call " + m.getName() + 
			            	"\"); */ $_ = ("+ret_type.getName()+ ") runtime.Dispatch.doIncremental((("+
			            	m.getDeclaringClass().getName()+")$0)." + inv_id + ", $args); }";
			            if (verbose)
			            	System.out.println("Rerouting to: " + replacer);
                   callm.replace(replacer);
                   didSomething = true;
          				classesToWrite.add(c);
			           }
			        }
			    });
		}
		return didSomething;
	}
	
	static int numCallsTmp;
	// This version of the invariant is used when propagating
	// results up the computation graph.
	// It uses previously computed incremental results rather 
	// than looking them up in the hash table. 
	static int createRunOnceInvariant(CtMethod oldm, final CtClass c) 
	throws NotFoundException, CannotCompileException {
		numCallsTmp = 0;
		if (verbose)
			System.out.println("Creating runOnce invariant");

		CtClass[] oldparms = oldm.getParameterTypes();
		// first find all the subcalls and replace them with incremental results
		CtClass[] newparms = new CtClass[oldparms.length + 1];
		for (int l = 0 ; l < oldparms.length ; l++)
			newparms[l] = oldparms[l];
		newparms[oldparms.length] = pool.get("java.lang.Object[]");

		final CtMethod m = CtNewMethod.abstractMethod(oldm.getReturnType(), 
				oldm.getName() + "_runOnce", newparms, oldm.getExceptionTypes(), c);
		m.setBody(oldm, null);

		m.addLocalVariable("cachedValues", pool.get("java.lang.Object[]"));
		m.insertBefore("{ cachedValues = $"+(oldparms.length+1)+"; }");
		//m.insertAfter("{ System.out.println(\"Returning object \" + $_); }");
		m.instrument(
				new ExprEditor() {

					public void edit(MethodCall m) throws CannotCompileException {
						if (m.getClassName().equals(c.getName()) && isInvariant(m)) {
							if (verbose)
								System.out.println("Replacing recursive invariant function call " + 
										m.getMethodName() + " with cached value");
							String replace_with = "{ $_ = ($r) ((runtime.ComputationNode) cachedValues["+(numCallsTmp++)+"]).result; }";
							if (verbose)
									System.out.println(replace_with);
							m.replace(replace_with);
							//m.replace("{ $_ = null; }");
							
						}
					}
		    });
		c.addMethod(m);
		c.setModifiers(c.getModifiers() & ~Modifier.ABSTRACT);
		return numCallsTmp;
	}

	static CtMethod rewriteFunctionDeclaration(CtMethod m) throws CannotCompileException, RuntimeException, NotFoundException {
		final CtClass c = m.getDeclaringClass();
		// add memoizer fields
		final String func_id = funcId(m.getName());
		try {
			CtField f = new CtField(pool.get("runtime.FunctionData"), func_id, c);
			f.setModifiers(Modifier.PUBLIC);
			c.addField(f);
			
			c.addField(new CtField(CtClass.booleanType, func_id + "Done", c));
		} catch (javassist.bytecode.DuplicateMemberException e) {
			System.out.println("Exception: " + e);
			System.out.println("Have you already run Ditto on this invariant before?");
			System.out.println("If so, you need to rebuild it before running Ditto on it again.");
			System.exit(1);
		}
		
		// make an auxiliary data class for this function
		CtClass data_c = pool.makeClass(dataName(c, m), pool.get("runtime.FunctionData"));
		if (verbose)
			System.out.println("Created data class " + data_c.getName());
		CtClass data_const_parms[] = { pool.get("java.lang.Object"), pool.get("runtime.InvariantData") };
		CtConstructor data_const = new CtConstructor(data_const_parms, data_c);
		data_const.setBody("{super($1, $2);}");
		data_c.addConstructor(data_const);
		// add to classes list so that this new class is written to disk
		classesToWrite.add(data_c);

		// first step: duplicate method. but we need to add the parent parameter

		// construct the new list of args, with the additional ones
		// used by the runtime system
		CtClass[] oldparms = m.getParameterTypes();
		int num_parms = oldparms.length + runtime.Dispatch.ignoreArgs;
		CtClass[] newparms = new CtClass[num_parms];
		for (int l = 0 ; l < oldparms.length ; l++)
			newparms[l] = oldparms[l];
		newparms[num_parms - 2] = pool.get("runtime.ComputationNode");
		newparms[num_parms - 1] = CtClass.intType;
		c.removeMethod(m); 
		
		// create a new duplicate method with the new args
		final CtMethod newm = CtNewMethod.abstractMethod(m.getReturnType(), 
					m.getName(), newparms, m.getExceptionTypes(), c);
		newm.setBody(m, null);
		c.addMethod(newm);
		c.setModifiers(c.getModifiers() & ~Modifier.ABSTRACT);		

		// create a version of the method that gets run on 
		// result propagation up the computation graph
		int numCalls = createRunOnceInvariant(m, c);
		
		// then, add checks and calls
		newm.addLocalVariable("cn", pool.get("runtime.ComputationNode"));
		newm.addLocalVariable("cachedValue", pool.get("runtime.ComputationNode"));
		String debug = "/*System.out.println(\"Parent computation node is \" + cn);*/";
		String s = "{ cn = $"+(num_parms-1)+"; "+debug + " cachedValue = runtime.Dispatch.getMemoized(" + func_id + 
		", $args, cn, $"+(num_parms)+"); if (cachedValue != null && cachedValue.result != null) { " +  
		" " + debug + "runtime.ComputationNode.computeDepth(cn); return ("+
			newm.getReturnType().getName() + ") cachedValue.result; }}";
		if (verbose)
			System.out.println("Added function header: " + s);
		newm.insertBefore(s);

		// create a class so that the Dispatch can invoke this invariant
		createInvariantDataRun(newm, data_c, numCalls);
		
		return newm;
	}
	
	static String funcId(String n) {
		return n + "Id";
	}
	
	static String dataName(CtClass c, CtMethod m) {
		return c.getName() + "_" + m.getName() + "Data";
	}
	
	// the main method that incrementalizes the invariant
	static CtMethod instrumentInvariant(final CtMethod newm) throws 
			CannotCompileException, NotFoundException {
		final CtClass c = newm.getDeclaringClass();		
		final String func_id = funcId(newm.getName());
		final String data_name = dataName(c, newm);
		if (verbose)
			System.out.println("Incrementalizing " + newm.getLongName() + " step 2");
		
		// see if there is already info associated with this function
		final InvariantInfo i = (InvariantInfo) methodsToInfo.get(newm);
		// find all function calls to other invariants, and add the boolean parameter
		newm.instrument(
				new ExprEditor() {
					int res = 0;
					public void edit(MethodCall m) throws CannotCompileException {
						if (isInvariant(m)) {
							if (verbose)
								System.out.println("Rewriting recursive invariant function call " + m.getMethodName());
							CtMethod tocall = null;
							try {
								// can't just get the method directly because it no longer 
								// exists with this signature (we replaced it in the previous step)
								String mn = m.getMethodName();
								tocall = pool.getMethod(c.getName(), mn);
							} catch (Exception e) {
								throw new CannotCompileException(e);
							}
							m.replace("{ $_ = $proceed($$, cachedValue, " + res++ + "); }");
							
							InvariantInfo i2 = (InvariantInfo) methodsToInfo.get(tocall);
							// if this has no info and other does, this gets that one
							if (i == null && i2 != null)
								methodsToInfo.put(newm, i2);
							// and vice versa
							else if (i != null && i2 == null)
								methodsToInfo.put(tocall, i);
							// if neither has, we create a new one
							else if (i == null && i2 == null) {
								InvariantInfo newi = new Transform.InvariantInfo(newm.getName() + "InvId");
								methodsToInfo.put(newm, newi);
								methodsToInfo.put(tocall, newi);
							// if they both have different ones, bug
							} else if (i != i2) {
								throw new CannotCompileException("Methods " + newm + " and " + tocall + 
										" are recursive but are also in different invariants!");
							}
							
						}
					}
		    });

		// at the end check to see if the return value is the same as the memoized value
		newm.insertAfter("{ if (cachedValue != null) { cachedValue.result = $_; "+ 
				"boolean _cd = true; for (int i = 0;i<cachedValue.children.length; i++) { "+
				"if (cachedValue.children[i] != null) _cd = false; } if (_cd) runtime.ComputationNode.computeDepth(cachedValue); }}");
		boolean rerouted = false;
		// reroute all calls to this invariant to the runtime dispatch function
		for (int q = 0 ; q < allClasses.size() ; q++) {
			CtClass cl = pool.get((String)allClasses.get(q));
			boolean thisReroute = rerouteInvariantCalls(newm, cl, func_id);
			rerouted = rerouted || thisReroute;
		}
		
		// since we cannot determine whether a new InvariantInfo was created 
		// in the above ExprEditor without the use of a static variable,
		// we refetch the mapped info to see whether it's been set
		InvariantInfo ii = (InvariantInfo) methodsToInfo.get(newm);
		boolean initializeInvData = false;
		if (rerouted) {
			if (i == null && ii.hasEntrypoint == true) {
				throw new CannotCompileException("Method " + newm + " is called both externally and " + 
						"internally by another invariant function that is also called externally and internally." +
						" Each invariant can only have one entrypoint function.");
			} else { 
				ii.hasEntrypoint = true;
				initializeInvData = true;
			}
		}
		
		try { 
			c.getField(ii.name);
		} catch (NotFoundException e) {
			if (verbose)
				System.out.println("Creating field name " + ii.name);
			CtField f = new CtField(pool.get("runtime.InvariantData"), ii.name, c);
			f.setModifiers(Modifier.PUBLIC);
			c.addField(f);				
		}

		// modify the class constructor to get an id from dispatch
		addSetIDToConstructors(c.getConstructors(), func_id, data_name, ii,
				initializeInvData);
		System.out.println("Incrementalizing " + newm.getLongName() + " step 2");
		// find all field reads and set the used bit
		// TODO replace with inv_id!
		final String replacer = "{ runtime.Dispatch.useMap("+ii.name+", $0, cachedValue); " +
			" $_ = $proceed(); }";
		
		newm.instrument(
				new ExprEditor() {
					public void edit(FieldAccess f) throws CannotCompileException {
						if (f.isReader() && ! f.isStatic()) {
							try {
								CtField gf = f.getField();
								String pname = gf.getDeclaringClass().getPackageName(); 
								// don't need to set used for stuff we've just added
								if (gf.getName().equals(func_id) || 
										(pname != null && pname.equals("runtime")))
									return;
								f.replace(replacer);
								if (verbose)
									System.out.println("Need to barrier object containing this field: " + gf.getName());
								barrieredTypes.add(gf);
							} catch (Exception e) {
								System.out.println("Can't get field. " + e);
								e.printStackTrace();
							}
						} 
					}
				});		
		
		return newm;
	}
	
}