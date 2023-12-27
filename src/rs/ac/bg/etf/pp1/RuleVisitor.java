package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;

import java.util.HashMap;

public class RuleVisitor extends VisitorAdaptor {


	Logger log = Logger.getLogger(getClass());

	private final HashMap<Class<?>, Integer> counters = new HashMap<>();

	private void count(Class<?> klass) {
		int value = counters.getOrDefault(klass, 0);
		counters.put(klass, value + 1);
	}

	public int getCounter(Class<?> klass) {
		return counters.getOrDefault(klass, 0);
	}

//	public void  visit(VarDecl vardecl) {
//		varDeclCount++;
//	}

//	public void visit(PrintStatement PrintStatement) { 
//		printCallCount++;
//	}
//	public void visit(Namespace Namespace) {
//		count(Namespace.class);
//	}
//	
//	public static class IfElseStmt {
//	}
//	
//	public void visit(IfStmt IfStmt) {
//		count(IfElseStmt.class);
//	}
//	public void visit(YesElseStatement YesElseStatement) {
//		count(IfElseStmt.class);
//	}
//	public void visit(NoElseStatement NoElseStatement) {
//		count(IfElseStmt.class);
//	}
}
