package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import rs.ac.bg.etf.pp1.CounterVisitor.CondTermCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.DesignatorUnpackingCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;

import rs.etf.pp1.symboltable.concepts.Obj;

import rs.etf.pp1.symboltable.*;

import rs.etf.pp1.symboltable.concepts.*;

public class CodeGenerator extends VisitorAdaptor {

	public Stack<List<Integer>> jumpOnAfterThen = new Stack<>(); // kada postoji else grana ovo skace na else
	public Stack<List<Integer>> jumpOnElse = new Stack<>(); // MOZDA

	public List<Integer> jumpOnThenBranch = new ArrayList<>();
	public List<Integer> jumpOnNextCondition = new ArrayList<>(); // kada je || uslov kombinovan sa &&

	public List<Integer> listToFillJumps = new ArrayList<>();

//	public List<Integer> forLoopTop = new ArrayList<>();
//	public List<Integer> forLoopEnd = new ArrayList<>();
	public Stack<Integer> forLoopTopAddresses = new Stack<>();

	public Stack<Integer> forLoopBodyAddresses = new Stack<>();
	public Stack<Integer> forLoopThirdConditionStart = new Stack<>();

	boolean forLoopActive = false;
	public Stack<List<Integer>> forLoopTop = new Stack<>();
	public Stack<List<Integer>> forLoopEnd = new Stack<>();

	int condTermNumber = 0;

	// unpacking
	int designatorNumber = 0;
	Obj objDesignatorMul;
	Obj objDesignatorEq;
	public List<Obj> unpackingObj;
	public List<Integer> indexesToSkip;
	public List<Integer> designatorType;// 0 - VAR ; 1 - ELEM ; 2 - FLD
	boolean unpackingStart = false;

	private int mainPc;

	public int getMainPc() {
		return mainPc;
	}

	public CodeGenerator() {
		Tab.chrObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);

		Tab.ordObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);

		Tab.lenObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.arraylength);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	/*
	 * 
	 * METHOD
	 * 
	 */

	public void visit(MethodTypeName methodTypeName) {
		if (methodTypeName.getMethName().equalsIgnoreCase("main")) {
			mainPc = Code.pc;
		}
		methodTypeName.obj.setAdr(Code.pc);

		// collect arguments and local variables
		SyntaxNode methodNode = methodTypeName.getParent();

		VarCounter varCnt = new VarCounter();
		methodNode.traverseTopDown(varCnt);

		FormParamCounter fpCnt = new FormParamCounter();
		methodNode.traverseTopDown(fpCnt);

		// generate entry
		Code.put(Code.enter);
		Code.put(fpCnt.getCount());
		Code.put(varCnt.getCount() + fpCnt.getCount());

	}

	public void visit(MethodDeclNoError methodDecl) {

		Code.put(Code.exit);
		Code.put(Code.return_);

	}

	public void visit(ReturnStatement returnStatement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(NoReturnStatement noReturnStatement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	/*
	 * 
	 * STATEMENT
	 * 
	 */

	public void visit(PrintStatement printStatement) {

		if (printStatement.getExpr().struct == Tab.intType) {
			Code.loadConst(5);
			Code.put(Code.print);
		} else if (printStatement.getExpr().struct == Tab.charType) {
			Code.loadConst(1);
			Code.put(Code.bprint);
		} else {
			// bool
			Code.loadConst(5);
			Code.put(Code.print);
		}
	}

	public void visit(PrintStatementComma printStatement) {
		Code.loadConst(printStatement.getPrintWidth());
		if (printStatement.getExpr().struct == Tab.intType) {
			Code.put(Code.print);
		} else if (printStatement.getExpr().struct == Tab.charType) {
			Code.put(Code.bprint);
		} else {
			// bool
			Code.put(Code.print);
		}
	}

	/*
	 * 
	 * DESIGNATOR STATEMENT
	 * 
	 */

	public void visit(AssignopDesignatorStatement assignmentStatement) {
		Designator designator = assignmentStatement.getDesignator();

		if (designator instanceof DesignatorIdent || designator instanceof DesignatorNamespace) {
			Code.store(assignmentStatement.getDesignator().obj);
		} else if (designator instanceof DesignatorArrayElem) {
			Designator des = ((DesignatorArrayElem) designator).getDesignator();
			if (des.obj.getType().getElemType().assignableTo(Tab.charType)) {
				Code.put(Code.bastore);
			} else {
				Code.put(Code.astore);
			}
		} else {
//			Code.load(assignmentStatement.getDesignator().obj);
//			Code.put(Code.dup_x1);
//			Code.put(Code.pop);
			Code.store(assignmentStatement.getDesignator().obj);

			// moram da dovrsim za element KLASE
		}

	}

	public void visit(IncDesignatorStatement incDesignatorStatement) {
		Designator designator = incDesignatorStatement.getDesignator();

		if (designator instanceof DesignatorIdent || designator instanceof DesignatorNamespace) {
			Code.loadConst(1);
			Code.put(Code.add);
			Code.store(designator.obj);
		} else if (designator instanceof DesignatorArrayElem) {
			Code.put(Code.dup2);
			Code.put(Code.aload);
			Code.loadConst(1);
			Code.put(Code.add);
			Code.put(Code.astore);
		} else {
			Code.put(Code.dup);
			Code.load(designator.obj);
			Code.loadConst(1);
			Code.put(Code.add);
			Code.store(designator.obj);
		}
	}

	public void visit(DecDesignatorStatement decDesignatorStatement) {
		Designator designator = decDesignatorStatement.getDesignator();

		if (designator instanceof DesignatorIdent || designator instanceof DesignatorNamespace) {
			Code.loadConst(1);
			Code.put(Code.sub);
			Code.store(designator.obj);
		} else if (designator instanceof DesignatorArrayElem) {
			Code.put(Code.dup2);
			Code.put(Code.aload);
			Code.loadConst(1);
			Code.put(Code.sub);
			Code.put(Code.astore);

			// PROVERI
		} else {
			Code.put(Code.dup);
			Code.load(designator.obj);
			Code.loadConst(1);
			Code.put(Code.sub);
			Code.store(designator.obj);
			// TO DO
		}
	}

	public void visit(ReadStatement readStatement) {
		Designator designator = readStatement.getDesignator();

		if (designator.obj.getType().assignableTo(Tab.charType)) {
			Code.put(Code.bread);
		} else {
			Code.put(Code.read);
		}

		if (designator instanceof DesignatorIdent || designator instanceof DesignatorNamespace) {
			Code.store(designator.obj);
		} else if (designator instanceof DesignatorArrayElem) {
			Designator des = ((DesignatorArrayElem) designator).getDesignator();
			if (des.obj.getType().getElemType().assignableTo(Tab.charType)) {
				Code.put(Code.bastore);
			} else {
				Code.put(Code.astore);
			}

		} else {
			// ZA KLASE
		}
	}

	public void visit(FuncCallDesignatorStatement designatorFuncionCall) {
		Obj functionObj = designatorFuncionCall.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);

		if (designatorFuncionCall.getDesignator().obj.getType() != Tab.noType) {
			// nije void funkcija
			Code.put(Code.pop);
		}
	}

	public void visit(FuncCallDesignatorStatementActPars designatorFuncionCall) {
		Obj functionObj = designatorFuncionCall.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);

		if (designatorFuncionCall.getDesignator().obj.getType() != Tab.noType) {
			// nije void funkcija
			Code.put(Code.pop);
		}
	}

	/*
	 * 
	 * DESIGNATOR
	 * 
	 */

	public void visit(DesignatorIdent designator) {
		SyntaxNode parent = designator.getParent();

		if (FactorDesignator.class == parent.getClass() || IncDesignatorStatement.class == parent.getClass()
				|| DecDesignatorStatement.class == parent.getClass()) {
			Code.load(designator.obj);
		}

	}

	public void visit(DesignatorNamespace designator) {
		SyntaxNode parent = designator.getParent();

		if (FactorDesignator.class == parent.getClass() || IncDesignatorStatement.class == parent.getClass()
				|| DecDesignatorStatement.class == parent.getClass()) {
			Code.load(designator.obj);
		}

	}

	public void visit(DesignatorArrayElem designator) {
		SyntaxNode parent = designator.getParent();
		Designator des = designator.getDesignator();

		if (FactorDesignator.class == parent.getClass()) {
			// expr je na steku
			
			if(des.obj.getKind() == Obj.Fld) {
				int temp = Code.get(Code.pc-1);
				Code.put(Code.pop);
				Code.load(des.obj);
				Code.put(temp);
				if (designator.getDesignator().obj.getType().getElemType().assignableTo(Tab.charType)) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
			} else {
				Code.load(des.obj);
				Code.put(Code.dup_x1);
				Code.put(Code.pop);
				if (designator.getDesignator().obj.getType().getElemType().assignableTo(Tab.charType)) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
			}
			
			

			// PROVERI OVO
		} else if (AssignopDesignatorStatement.class == parent.getClass()
				|| IncDesignatorStatement.class == parent.getClass()
				|| DecDesignatorStatement.class == parent.getClass() || ReadStatement.class == parent.getClass()) {
			// ovde imam problem za klasu
			if(des.obj.getKind() == Obj.Fld) {
				int temp = Code.get(Code.pc-1);
				Code.put(Code.pop);
				Code.load(des.obj);
				Code.put(temp);
				
			} else {
				Code.load(des.obj);
				Code.put(Code.dup_x1);
				Code.put(Code.pop);
			}
			
		}
		
		if(DesignatorClassMember.class == parent.getClass()) {
			Code.load(des.obj);
			Code.put(Code.dup_x1);
			Code.put(Code.pop);
		}

		// unpacking
		if (DesignatorListStmtDesignator.class == parent.getClass()) {
			// expr je na steku
			if(des.obj.getKind() == Obj.Fld) {
				int temp = Code.get(Code.pc-1);
				Code.put(Code.pop);
				Code.load(des.obj);
				Code.put(temp);
				
			} else {
				Code.load(des.obj);
				Code.put(Code.dup_x1);
				Code.put(Code.pop);
			}
		}
	}

	public void visit(DesignatorClassMember designator) {
		SyntaxNode parent = designator.getParent();
		Designator des = designator.getDesignator();
		
		
		Code.load(des.obj);
		
		
//		if (FactorDesignator.class == parent.getClass()) {
//
//			Code.load(des.obj);
//			// TO DO
//
//		} else if (AssignopDesignatorStatement.class == parent.getClass()
//				|| IncDesignatorStatement.class == parent.getClass()
//				|| DecDesignatorStatement.class == parent.getClass() || ReadStatement.class == parent.getClass()) {
//			
//			
//			Code.load(des.obj);
//
//		}
//
//		// unpacking
//		if (DesignatorListStmtDesignator.class == parent.getClass()) {
//			// expr je na steku
//			Code.load(des.obj);
//		}
//		
//		if(DesignatorArrayElem.class == parent.getClass()) {
//			Code.load(des.obj);
//			//Code.load(((DesignatorArrayElem)parent).getDesignator().obj);
//		}
	}

	/*
	 * 
	 * FACTOR
	 * 
	 */

	public void visit(FactorDesignator factorDesignator) {
		Designator designator = factorDesignator.getDesignator();
		if (designator.obj.getKind() == Obj.Fld) {
			Code.load(designator.obj);
		}
	}

	public void visit(FactorNum factorNum) {
		Obj con = Tab.insert(Obj.Con, "numConst", factorNum.struct);
		con.setLevel(0);
		con.setAdr(factorNum.getN1());

		Code.load(con); // put on expression stack

	}

	public void visit(FactorChar factorChar) {
		Obj con = Tab.insert(Obj.Con, "charConst", factorChar.struct);
		con.setLevel(0);
		con.setAdr(factorChar.getC1());

		Code.load(con); // put on expression stack
	}

	public void visit(FactorBool factorBool) {
		Obj con = Tab.insert(Obj.Con, "boolConst", factorBool.struct);
		con.setLevel(0);
		con.setAdr(factorBool.getB1());

		Code.load(con); // put on expression stack
	}

	public void visit(FactorFunctionCall functionCall) {
		Obj functionObj = functionCall.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	public void visit(FactorFunctionCallParam functionCall) {
		Obj functionObj = functionCall.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	public void visit(FactorNewExpr factorNewExpr) {
		if (factorNewExpr.getType().struct.assignableTo(Tab.charType)) {
			Code.put(Code.newarray);
			Code.loadConst(0); // b
		} else {
			Code.put(Code.newarray);
			Code.loadConst(1); // b
		}
	}

	public void visit(FactorNewType factorNewType) {
		int size = 0;
		int numOfFields = 0;
		Struct superclass = factorNewType.getType().struct;

		while (superclass != null) {
			numOfFields += superclass.getNumberOfFields();
			superclass = superclass.getElemType();
		}

		size = numOfFields * 4;
		Code.put(Code.new_);
		Code.put2(size);

	}

	/*
	 * 
	 * TERM, EXPR
	 * 
	 */

	public void visit(ExprMinusTerm expr) {
		Code.put(Code.neg);
	}

	public void visit(AddExpr addExpr) {
		if (addExpr.getAddop() instanceof PlusAddop) {
			Code.put(Code.add);
		} else {
			Code.put(Code.sub);
		}
	}

	public void visit(MultipleTerm mulopExpr) {
		if (mulopExpr.getMulop() instanceof MulMulop) {
			Code.put(Code.mul);
		} else if (mulopExpr.getMulop() instanceof DivMulop) {
			Code.put(Code.div);
		} else {
			Code.put(Code.rem);
		}
	}

	/*
	 * 
	 * IF, ELSE, CONDITION
	 * 
	 */

	public int checkRelopOp(CondFact fact) {
		Relop op = ((CondFactExprRelop) fact).getRelop();

		if (op instanceof IsEqualRelop) {
			return Code.eq;
		} else if (op instanceof NotEqualRelop) {
			return Code.ne;
		} else if (op instanceof GreaterRelop) {
			return Code.gt;
		} else if (op instanceof GreaterOrEqualRelop) {
			return Code.ge;
		} else if (op instanceof LessRelop) {
			return Code.lt;
		} else {
			return Code.le;
		}
	}

	public void visit(IfStatement ifStatement) {
		// ovde se skace kada je na AFTER THEN
		listToFillJumps = jumpOnAfterThen.pop();
		while (listToFillJumps.size() != 0) {
			int adr = listToFillJumps.remove(listToFillJumps.size() - 1);
			Code.fixup(adr);
		}
	}

	public void visit(IfElseStatement ifStatement) {
		// ovde se skace kada je na AFTER THEN
		listToFillJumps = jumpOnAfterThen.pop();
		while (listToFillJumps.size() != 0) {
			int adr = listToFillJumps.remove(listToFillJumps.size() - 1);
			Code.fixup(adr);
		}
	}

	public void visit(ElseStart elseStart) {
		// ovde se skace kada je na AFTER THEN plus novi skok na AFTER THEN (tj after
		// else)
		Code.putJump(0);

		listToFillJumps = jumpOnAfterThen.pop();
		while (listToFillJumps.size() != 0) {
			int adr = listToFillJumps.remove(listToFillJumps.size() - 1);
			Code.fixup(adr);
		}
		listToFillJumps = new ArrayList<>();
		listToFillJumps.add(Code.pc - 2);
		jumpOnAfterThen.add(listToFillJumps);

	}

	public void visit(IfStart ifStart) {
		jumpOnAfterThen.add(new ArrayList<>());

		SyntaxNode parent = ifStart.getParent();

		Condition condition;
		if (parent instanceof IfStatement) {
			condition = ((IfStatement) parent).getCondition();
		} else {
			condition = ((IfElseStatement) parent).getCondition();
		}
		CondTermCounter condTermCnt = new CondTermCounter();
		condition.traverseTopDown(condTermCnt);

		condTermNumber = condTermCnt.getCount();

	}

	public void visit(CondTermFact cond) {
		SyntaxNode parent = cond.getParent();
		CondFact fact = cond.getCondFact();

		listToFillJumps = jumpOnAfterThen.peek();

		if (condTermNumber == 1) {
			// poslednji condTerm u Condition izrazu -> ako je netacan skacem na AFTER THEN
			if (fact instanceof CondFactExpr) {
				// expr
				Code.loadConst(1);
				Code.putFalseJump(Code.eq, 0);
				listToFillJumps.add(Code.pc - 2);
			} else {
				// expr relop expr
				Code.putFalseJump(checkRelopOp(fact), 0);
				listToFillJumps.add(Code.pc - 2);
			}

		} else {
			if (ConditionTerm.class == parent.getClass() || ConditionOrTerm.class == parent.getClass()) {
				// ako je expr ili expr relop expr tacan skacem odmah na THEN granu
				if (fact instanceof CondFactExpr) {
					// expr
					Code.loadConst(1);
					Code.putFalseJump(Code.ne, 0);
					jumpOnThenBranch.add(Code.pc - 2);
				} else {
					// expr relop expr
					Code.putFalseJump(Code.inverse[checkRelopOp(fact)], 0);
					jumpOnThenBranch.add(Code.pc - 2);
				}
			} else {
				// ako nije tacan skacem na sldeci Condition uslov, ako je tacan samo nastavljam
				// dalje da proveravam
				if (fact instanceof CondFactExpr) {
					// expr
					Code.loadConst(1);
					Code.putFalseJump(Code.eq, 0);
					jumpOnNextCondition.add(Code.pc - 2);
				} else {
					// expr relop expr
					Code.putFalseJump(checkRelopOp(fact), 0);
					jumpOnNextCondition.add(Code.pc - 2);
				}
			}
		}
	}

	public void visit(CondTermAndFact cond) {
		SyntaxNode parent = cond.getParent();
		CondFact fact = cond.getCondFact();

		listToFillJumps = jumpOnAfterThen.peek();

		if (condTermNumber == 1) {
			// poslednji condTerm u Condition izrazu -> ako je netacan skacem na AFTER THEN
			if (fact instanceof CondFactExpr) {
				// expr
				Code.loadConst(1);
				Code.putFalseJump(Code.eq, 0);
				listToFillJumps.add(Code.pc - 2);
			} else {
				// expr relop expr
				Code.putFalseJump(checkRelopOp(fact), 0);
				listToFillJumps.add(Code.pc - 2);
			}

		} else {
			if (ConditionTerm.class == parent.getClass() || ConditionOrTerm.class == parent.getClass()) {
				// ako je expr ili expr relop expr tacan skacem odmah na THEN granu
				if (fact instanceof CondFactExpr) {
					// expr
					Code.loadConst(1);
					Code.putFalseJump(Code.ne, 0);
					jumpOnThenBranch.add(Code.pc - 2);
				} else {
					// expr relop expr
					Code.putFalseJump(Code.inverse[checkRelopOp(fact)], 0);
					jumpOnThenBranch.add(Code.pc - 2);
				}
			} else {
				// ako nije tacan skacem na sldeci Condition uslov, ako je tacan samo nastavljam
				// dalje da proveravam
				if (fact instanceof CondFactExpr) {
					// expr
					Code.loadConst(1);
					Code.putFalseJump(Code.eq, 0);
					jumpOnNextCondition.add(Code.pc - 2);
				} else {
					// expr relop expr
					Code.putFalseJump(checkRelopOp(fact), 0);
					jumpOnNextCondition.add(Code.pc - 2);
				}
			}
		}
	}

	public void visit(ConditionTerm condition) {
		condTermNumber--;
		// ovde se skace kada je na NEXT CONDITION
		while (jumpOnNextCondition.size() != 0) {
			int adr = jumpOnNextCondition.remove(jumpOnNextCondition.size() - 1);
			Code.fixup(adr);
		}
	}

	public void visit(ConditionOrTerm condition) {
		condTermNumber--;
		// ovde se skace kada je na NEXT CONDITION
		while (jumpOnNextCondition.size() != 0) {
			int adr = jumpOnNextCondition.remove(jumpOnNextCondition.size() - 1);
			Code.fixup(adr);
		}
	}

	public void visit(ThenBranch thenBranch) {
		// ovde se skace kada je na THEN
		while (jumpOnThenBranch.size() != 0) {
			int adr = jumpOnThenBranch.remove(jumpOnThenBranch.size() - 1);
			Code.fixup(adr);
		}
	}

	/*
	 * 
	 * FOR loop
	 * 
	 */

	public void visit(ForLoopStart forLoopStart) {
		// ovo je moj top ali bez designatorStatement
//		forLoopTop.add(new ArrayList<>());
		forLoopEnd.add(new ArrayList<>());
	}

	public void visit(ForLoopTop forLoop) {
		forLoopTopAddresses.push(Code.pc);
//		listToFillJumps = forLoopTop.pop();
//		while (listToFillJumps.size() != 0) {
//			int adr = listToFillJumps.remove(listToFillJumps.size() - 1);
//			Code.fixup(adr);
//		}
	}

	public void visit(SingleCondFact condFact) {
		// ako nije ispunjen skoci na FOR END
//		listToFillJumps = forLoopTop.pop();

		// ovde moram da pushujem condition

		listToFillJumps = forLoopEnd.peek();

		CondFact cond = condFact.getCondFact();
		if (cond instanceof CondFactExpr) {
			// expr
			Code.loadConst(1);
			Code.putFalseJump(Code.eq, 0);
			listToFillJumps.add(Code.pc - 2);
		} else {
			// expr relop expr
			Code.putFalseJump(checkRelopOp(cond), 0);
			listToFillJumps.add(Code.pc - 2);
		}
		Code.putJump(0);
		forLoopBodyAddresses.push(Code.pc - 2);
	}

	public void visit(ForStatement forStatementEnd) {
		forLoopTopAddresses.pop();
		listToFillJumps = forLoopEnd.pop();
		while (listToFillJumps.size() != 0) {
			int adr = listToFillJumps.remove(listToFillJumps.size() - 1);
			Code.fixup(adr);
		}
	}

	public void visit(ForLoopEnd forLoopEnd) {
		Code.putJump(forLoopThirdConditionStart.pop());
	}

	public void visit(ForLoopJumpToTop forLoop) {
		Code.putJump(forLoopTopAddresses.peek());
	}

	public void visit(ForLoopBody forLoopBody) {
		Code.fixup(forLoopBodyAddresses.pop());
	}

	public void visit(ForLoopThirdCondition forLoop) {
		forLoopThirdConditionStart.push(Code.pc);
	}

	public void visit(BreakStatement breakStatement) {
		// jump na END
		Code.putJump(0);
		listToFillJumps = forLoopEnd.peek();
		listToFillJumps.add(Code.pc - 2);
	}

	public void visit(ContinueStatement continueStatement) {
		Code.putJump(forLoopThirdConditionStart.peek());
	}

	/*
	 * 
	 * UNPACKING
	 * 
	 */

	public void visit(UnpackingStart unpackingStartStmt) {
		unpackingStart = true;
		SyntaxNode parent = unpackingStartStmt.getParent();

		DesignatorUnpackingCounter desCnt = new DesignatorUnpackingCounter();

		parent.traverseTopDown(desCnt);

		designatorNumber = desCnt.getCount();
		unpackingObj = desCnt.getUnpackingObj();
		indexesToSkip = desCnt.getIndexesToSkip();
		designatorType = desCnt.getDesignatorType();

		objDesignatorMul = desCnt.getObjDesignatorMul();
		objDesignatorEq = desCnt.getObjDesignatorEq();

		Code.load(objDesignatorEq);
		Code.put(Code.arraylength);
		Code.loadConst(designatorNumber);
		Code.load(objDesignatorMul);
		Code.put(Code.arraylength);
		Code.put(Code.add);
		Code.putFalseJump(Code.ne, 0);
		int jumpAdr = Code.pc - 2;
		Code.put(Code.trap);
		Code.put(2);
		Code.fixup(jumpAdr);

	}

	public void visit(UnpackingDesignatorStatement unpackingStatement) {
		int i = designatorNumber;
		int fromEnd = 0;
		int cnt;

		// obrada pre *

		for (cnt = 0; cnt < designatorNumber; cnt++) {
			for (int ind : indexesToSkip) {
				if (cnt == ind) {
					continue;
				}
			}

			if (designatorType.get(cnt) == 1) {
				// array
				Code.load(objDesignatorEq);
				Code.loadConst(designatorNumber - cnt - 1);
				if (objDesignatorEq.getType().getElemType() == Tab.charType) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
				// provera ako je char onda bastore
				if(unpackingObj.get(cnt).getType().getElemType() == Tab.charType) {
					Code.put(Code.bastore);
				} else {
					Code.put(Code.astore);
				}
				
			} else if (designatorType.get(cnt) == 2) {
				// fld
				Code.load(objDesignatorEq);
				Code.loadConst(designatorNumber - cnt - 1);
				if (objDesignatorEq.getType().getElemType() == Tab.charType) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
				Code.store(unpackingObj.get(cnt));
			} else {
				// var
				Code.load(objDesignatorEq);
				Code.loadConst(designatorNumber - cnt - 1);
				if (objDesignatorEq.getType().getElemType() == Tab.charType) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
				Code.store(unpackingObj.get(cnt));
			}
		}

		// obrada nakon *

		// cnt = designatorNumber
		Code.loadConst(cnt);
		int startAdr = Code.pc;
		Code.put(Code.dup);
		Code.load(objDesignatorEq);
		Code.put(Code.arraylength);
		Code.putFalseJump(Code.ne, 0);
		int jumpAdr = Code.pc - 2;

		// stack: cnt
		Code.put(Code.dup);
		Code.put(Code.dup);
		Code.load(objDesignatorEq);
		Code.put(Code.arraylength);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		// stack: cnt, cnt, len, cnt
		Code.put(Code.sub);
		// stack: cnt, cnt, len -cnt
		Code.load(objDesignatorMul);
		Code.put(Code.arraylength);
		// stack: cnt, cnt, len -cnt, len2
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.sub);

		// stack: cnt, cnt, indexLeft
		Code.load(objDesignatorMul);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);

		// stack: cnt, cnt, arrLeft indexLeft
		Code.put(Code.dup_x2);
		Code.put(Code.pop);
		// stack: cnt, indexLeft, cnt, arrLeft
		Code.put(Code.dup_x2);
		Code.put(Code.pop);
		// stack: cnt, arrLeft, indexLeft, cnt
		Code.load(objDesignatorEq);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		// stack: cnt, arrLeft, indexLeft, arrRight, cnt
		if (objDesignatorEq.getType().getElemType() == Tab.charType) {
			Code.put(Code.baload);
		} else {
			Code.put(Code.aload);
		}
		// stack: cnt, arrLeft, indexLeft, arrRight[cnt]
		if (objDesignatorMul.getType().getElemType() == Tab.charType) {
			Code.put(Code.bastore);
		} else {
			Code.put(Code.astore);
		}

		// stack: cnt
		Code.loadConst(1);
		Code.put(Code.add);
		Code.putJump(startAdr);

		Code.fixup(jumpAdr);
		Code.put(Code.pop); // remove cnt
	}

}
