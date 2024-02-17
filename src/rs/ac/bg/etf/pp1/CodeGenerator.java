package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import java.util.AbstractMap;

import rs.ac.bg.etf.pp1.CounterVisitor.CondTermCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.DesignatorUnpackingCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.*;

import rs.etf.pp1.symboltable.concepts.*;

public class CodeGenerator extends VisitorAdaptor {

	public Stack<List<Integer>> jumpOnAfterThen = new Stack<>(); // kada postoji else grana ovo skace na else
	public Stack<List<Integer>> jumpOnElse = new Stack<>(); // MOZDA

	public List<Integer> jumpOnThenBranch = new ArrayList<>();
	public List<Integer> jumpOnNextCondition = new ArrayList<>(); // kada je || uslov kombinovan sa &&

	public List<Integer> listToFillJumps = new ArrayList<>();

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
	int designatorMulAdr = Code.dataSize++; // static place for designatorMul
	int designatorEqAdr = Code.dataSize++; // static place for designatorEq

	public List<Obj> unpackingObj;
	public List<Integer> indexesToSkip;
	public List<Integer> designatorType;// 0 - VAR ; 1 - ELEM ; 2 - FLD
	boolean unpackingStart = false;

	// class static initializer
	boolean firstStaticInitializerStart = false;
	int firstStaticInitializerAdr;
	int nextStaticInitializerAdr;

	// class TVF

	public HashMap<Obj, List<AbstractMap.SimpleEntry<Obj, Integer>>> classMethodsAdr = new HashMap<>(); // obj klase,
																										// <obj metode,
																										// adresa
	// metode>

	boolean classDeclStart = false;
	Obj currentClass = null;
	Obj thisObj = null;

	public HashMap<Struct, Integer> classTVFAdr = new HashMap<>(); // struct klase, njena TVF adresa
	int startOfTVF = Code.dataSize;

	Obj classExtends = null;
	int staticField = Code.dataSize++;
	boolean foundClassMember = false;

	Obj currentMethod = null;

	public List<Integer> tvfInitialize = new ArrayList<>();
	boolean firstTVF = false;

	// factor new type
	boolean factorNew = false;

	// method void
	boolean methodVoid = false;

	// MODIFIKACIJA
	boolean finalType = false;
	
	boolean startPars = false;
	boolean factorNewTypePars = false;
	
	List<Integer> argumentOfClassConstructor = new ArrayList<>();
	List<Integer> typesOfClassArguments = new ArrayList<>();
	Obj desClass = null;
	int numberOfArgs = 0;
	
	// MODIFIKACIJA

	private int mainPc;

	public int getMainPc() {
		return mainPc;
	}

	public int getDataSize() {
		return startOfTVF;
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
			if (firstStaticInitializerStart == true) {
				Code.fixup(nextStaticInitializerAdr);
			} else {
				mainPc = Code.pc;
			}

		}
		methodTypeName.obj.setAdr(Code.pc);

		// collect arguments and local variables
		SyntaxNode methodNode = methodTypeName.getParent();

		VarCounter varCnt = new VarCounter();
		methodNode.traverseTopDown(varCnt);

		FormParamCounter fpCnt = new FormParamCounter();
		methodNode.traverseTopDown(fpCnt);

		int cnt = 0;

		if (classDeclStart == true) {

			// ako klasa nasledjuje ispravi metode koje je redefinisala
			boolean found = false;
			if (classExtends != null) {
				for (int i = 0; i < classMethodsAdr.get(currentClass).size(); i++) {
					if (classMethodsAdr.get(currentClass).get(i).getKey().getName() == methodTypeName.obj.getName()) {
						classMethodsAdr.get(currentClass).set(i,
								new AbstractMap.SimpleEntry<>(methodTypeName.obj, Code.pc));
						found = true;
					}
				}
			}

			if (!found) {
				classMethodsAdr.get(currentClass).add(new AbstractMap.SimpleEntry<>(methodTypeName.obj, Code.pc));
			}

			cnt = 1;
			Collection<Obj> locals = methodTypeName.obj.getLocalSymbols();
			for (Obj obj : locals) {
				thisObj = obj; // implicitni this parametar tekuce metode
				break;
			}

		}
		if (methodTypeName.obj.getType() == Tab.noType) {
			methodVoid = true;
		} else {
			methodVoid = false;
		}

		Code.put(Code.enter);
		Code.put(fpCnt.getCount() + cnt);
		Code.put(varCnt.getCount() + fpCnt.getCount() + cnt);

	}

	public void visit(MethodDeclNoError methodDecl) {

		if (!methodVoid) {
			Code.put(Code.trap);
			Code.put(1);
		} else {
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
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
			// var
			Code.store(assignmentStatement.getDesignator().obj);
		} else if (designator instanceof DesignatorArrayElem) {
			// array elem
			Designator des = ((DesignatorArrayElem) designator).getDesignator();
			if (des.obj.getType().getElemType().assignableTo(Tab.charType)) {
				Code.put(Code.bastore);
			} else {
				Code.put(Code.astore);
			}
		} else {
			// class field
			Code.store(assignmentStatement.getDesignator().obj);

		}

		if (classTVFAdr.get(assignmentStatement.getExpr().struct) != null) {
			Code.load(assignmentStatement.getDesignator().obj);
			Code.loadConst(classTVFAdr.get(assignmentStatement.getExpr().struct));
			Code.put(Code.putfield);
			Code.put2(0);
		} else if (assignmentStatement.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.pop);
			Code.put(Code.pop);
		}
		
		if(factorNewTypePars == true) {
			factorNewTypePars = false;
			for(int i = 0; i < argumentOfClassConstructor.size(); i++) {
				Code.load(assignmentStatement.getDesignator().obj);
				if(typesOfClassArguments.get(i) == 0) {
					Code.loadConst(argumentOfClassConstructor.get(i));
				} else if(typesOfClassArguments.get(i) == 1) {
					Code.loadConst(argumentOfClassConstructor.get(i) + '0');
				} else {
					Code.loadConst(argumentOfClassConstructor.get(i));
				}
				
				Code.put(Code.putfield);
				Code.put2(i+1);
			}
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
		} else {
			Code.put(Code.dup);
			Code.load(designator.obj);
			Code.loadConst(1);
			Code.put(Code.sub);
			Code.store(designator.obj);
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
			// var
			Code.store(designator.obj);
		} else if (designator instanceof DesignatorArrayElem) {
			// array elem
			Designator des = ((DesignatorArrayElem) designator).getDesignator();
			if (des.obj.getType().getElemType().assignableTo(Tab.charType)) {
				Code.put(Code.bastore);
			} else {
				Code.put(Code.astore);
			}

		} else {
			// class field
			Code.store(readStatement.getDesignator().obj);
		}
	}

	/*
	 * 
	 * FUNCTION CALLS
	 * 
	 */

	public void visit(FuncCallDesignatorStatement designatorFuncionCall) {
		Obj functionObj = designatorFuncionCall.getDesignator().obj;
		Collection<Obj> temp = functionObj.getLocalSymbols();
		String methodName = designatorFuncionCall.getDesignator().obj.getName();
		for (Obj obj : temp) {

			if (obj.getType().getKind() == Struct.Class && obj.getName() == "this") {
				// first parameter is this meaning this is class method

				if (foundClassMember == false) {
					// when we are inside class method we load implicit THIS
					Code.load(thisObj);
					Code.load(thisObj);
				} else {
					// class field
					Code.put(Code.getstatic);
					Code.put2(staticField);
					foundClassMember = false;
				}

				Code.put(Code.getfield);
				Code.put2(0);
				Code.put(Code.invokevirtual);

				for (int i = 0; i < methodName.length(); i++) {
					Code.put4(methodName.charAt(i));
				}
				Code.put4(-1);

			} else {
				// regular method
				int offset = functionObj.getAdr() - Code.pc;
				Code.put(Code.call);
				Code.put2(offset);
			}
			break;
		}

		if (temp.isEmpty()) {
			// regular method
			int offset = functionObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		}

		if (designatorFuncionCall.getDesignator().obj.getType() != Tab.noType) {
			// not a void function
			Code.put(Code.pop);
		}
	}

	public void visit(FuncCallDesignatorStatementActPars designatorFuncionCall) {
		Obj functionObj = designatorFuncionCall.getDesignator().obj;
		Collection<Obj> temp = functionObj.getLocalSymbols();
		String methodName = designatorFuncionCall.getDesignator().obj.getName();
		for (Obj obj : temp) {

			if (obj.getType().getKind() == Struct.Class && obj.getName() == "this") {
				// first parameter is this meaning this is class method

				if (foundClassMember == false) {
					// when we are inside class method we load implicit THIS
					Code.load(thisObj);
					Code.load(thisObj);
				} else {
					// class field
					Code.put(Code.getstatic);
					Code.put2(staticField);
					foundClassMember = false;
				}

				Code.put(Code.getfield);
				Code.put2(0);
				Code.put(Code.invokevirtual);

				for (int i = 0; i < methodName.length(); i++) {
					Code.put4(methodName.charAt(i));
				}
				Code.put4(-1);

			} else {
				// regular method
				int offset = functionObj.getAdr() - Code.pc;
				Code.put(Code.call);
				Code.put2(offset);
			}
			break;
		}

		if (temp.isEmpty()) {
			int offset = functionObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		}

		if (designatorFuncionCall.getDesignator().obj.getType() != Tab.noType) {
			// not a void function
			Code.put(Code.pop);
		}
	}

	public void visit(FactorFunctionCall functionCall) {
		Obj functionObj = functionCall.getDesignator().obj;
		Collection<Obj> temp = functionObj.getLocalSymbols();
		String methodName = functionCall.getDesignator().obj.getName();
		for (Obj obj : temp) {

			if (obj.getType().getKind() == Struct.Class && obj.getName() == "this") {
				// first parameter is this meaning this is class method

				if (foundClassMember == false) {
					// when we are inside class method we load implicit THIS
					Code.load(thisObj);
					Code.load(thisObj);
				} else {
					// class field
					Code.put(Code.getstatic);
					Code.put2(staticField);
					foundClassMember = false;
				}

				Code.put(Code.getfield);
				Code.put2(0);
				Code.put(Code.invokevirtual);

				for (int i = 0; i < methodName.length(); i++) {
					Code.put4(methodName.charAt(i));
				}
				Code.put4(-1);

			} else {
				// regular method
				int offset = functionObj.getAdr() - Code.pc;
				Code.put(Code.call);
				Code.put2(offset);
			}
			break;
		}

		if (temp.isEmpty()) {
			// regular method
			int offset = functionObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		}

	}

	public void visit(FactorFunctionCallParam functionCall) {
		Obj functionObj = functionCall.getDesignator().obj;
		Collection<Obj> temp = functionObj.getLocalSymbols();
		String methodName = functionCall.getDesignator().obj.getName();
		for (Obj obj : temp) {

			if (obj.getType().getKind() == Struct.Class && obj.getName() == "this") {
				// first parameter is this meaning this is class method

				if (foundClassMember == false) {
					// when we are inside class method we load implicit THIS
					Code.load(thisObj);
					Code.load(thisObj);
				} else {
					// class field
					Code.put(Code.getstatic);
					Code.put2(staticField);
					foundClassMember = false;
				}

				Code.put(Code.getfield);
				Code.put2(0);
				Code.put(Code.invokevirtual);

				for (int i = 0; i < methodName.length(); i++) {
					Code.put4(methodName.charAt(i));
				}
				Code.put4(-1);

			} else {
				// regular method
				int offset = functionObj.getAdr() - Code.pc;
				Code.put(Code.call);
				Code.put2(offset);
			}
			break;
		}

		if (temp.isEmpty()) {
			// regular method
			int offset = functionObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		}

	}

	/*
	 * 
	 * DESIGNATOR
	 * 
	 */

	public void visit(DesignatorIdent designator) {
		SyntaxNode parent = designator.getParent();
		
		desClass = designator.obj;

		if (designator.obj.getKind() == Obj.Fld && thisObj != null) {
			Code.load(thisObj);
		}

		if (FactorDesignator.class == parent.getClass() || IncDesignatorStatement.class == parent.getClass()
				|| DecDesignatorStatement.class == parent.getClass() || DesignatorArrayElem.class == parent.getClass()
				|| DesignatorEq.class == parent.getClass() || DesignatorMul.class == parent.getClass()) {
			Code.load(designator.obj);
		} else if (AssignopDesignatorStatement.class == parent.getClass()) {
			if (designator.obj.getFpPos() == 1) {
				finalType = true;
			}
		}

	}

	public void visit(DesignatorNamespace designator) {
		SyntaxNode parent = designator.getParent();
		
		desClass = designator.obj;

		if (FactorDesignator.class == parent.getClass() || IncDesignatorStatement.class == parent.getClass()
				|| DecDesignatorStatement.class == parent.getClass() || DesignatorArrayElem.class == parent.getClass()
				|| DesignatorEq.class == parent.getClass() || DesignatorMul.class == parent.getClass()) {
			Code.load(designator.obj);
		}

	}

	public void visit(DesignatorArrayElem designator) {
		SyntaxNode parent = designator.getParent();
		Designator des = designator.getDesignator();

		if (FactorDesignator.class == parent.getClass()) {
			// stack: adr, expr
			if (designator.getDesignator().obj.getType().getElemType().assignableTo(Tab.charType)) {
				Code.put(Code.baload);
			} else {
				Code.put(Code.aload);
			}

		}

		if (AssignopDesignatorStatement.class == parent.getClass()) {
			// stack: adr, index -> double it
			Code.put(Code.dup2);
		}
	}

	public void visit(DesignatorClassMember designator) {
		SyntaxNode parent = designator.getParent();
		Designator des = designator.getDesignator();

		if (des.obj.getKind() == Obj.Type) {
			Code.load(designator.obj);
		} else {
			if ((FactorDesignator.class == parent.getClass() || DesignatorArrayElem.class == parent.getClass()
					|| DesignatorEq.class == parent.getClass() || DesignatorMul.class == parent.getClass())) {
				// load the whole field
				Code.load(des.obj);
				Code.load(designator.obj);

				
			} else if (FuncCallDesignatorStatementActPars.class == parent.getClass()
					|| FuncCallDesignatorStatement.class == parent.getClass()
					|| FactorFunctionCall.class == parent.getClass() || FactorFunctionCall.class == parent.getClass()) {

				Code.load(des.obj);
				Code.put(Code.dup);
				Code.put(Code.putstatic);
				Code.put2(staticField); // adresa pomocne staticke promenljive
				foundClassMember = true;

			} else {

				// ovo se radi kada je assignment statement, inc, dec itd
				Code.load(des.obj);
				Code.put(Code.dup);
				Code.put(Code.putstatic);
				Code.put2(staticField); // adresa pomocne staticke promenljive

				if (des.obj.getFpPos() == 1) {

					Code.load(des.obj);
					Code.put(Code.getfield);
					Code.put2(des.obj.getType().getNumberOfFields() + designator.obj.getAdr());
					Code.loadConst(1);

					Code.putFalseJump(Code.ne, 0);
					int adrToPatch = Code.pc - 2;

					// setujemo polje na 1
					Code.load(des.obj);
					Code.loadConst(1);
					Code.put(Code.putfield);
					Code.put2(des.obj.getType().getNumberOfFields() + designator.obj.getAdr());

					// preskacemo trap
					Code.putJump(0);
					int secondAdrToPatch = Code.pc - 2;

					// ako je polje vec bilo 1 -> trap
					Code.fixup(adrToPatch);
					Code.put(Code.trap);
					Code.put(2);

					Code.fixup(secondAdrToPatch);

				}
			}
		}
	}

	/*
	 * 
	 * FACTOR
	 * 
	 */

	public void visit(FactorNum factorNum) {
		if(startPars == true) {
			argumentOfClassConstructor.add(factorNum.getN1());
			typesOfClassArguments.add(0);
		}
		Obj con = Tab.insert(Obj.Con, "numConst", factorNum.struct);
		con.setLevel(0);
		con.setAdr(factorNum.getN1());

		Code.load(con); // put on expression stack

	}

	public void visit(FactorChar factorChar) {
		if(startPars == true) {
			argumentOfClassConstructor.add(factorChar.getC1() - '0');
			typesOfClassArguments.add(1);
		}
		Obj con = Tab.insert(Obj.Con, "charConst", factorChar.struct);
		con.setLevel(0);
		con.setAdr(factorChar.getC1());

		Code.load(con); // put on expression stack
	}

	public void visit(FactorBool factorBool) {
		if(startPars == true) {
			argumentOfClassConstructor.add(factorBool.getB1());
			typesOfClassArguments.add(2);
		}
		Obj con = Tab.insert(Obj.Con, "boolConst", factorBool.struct);
		con.setLevel(0);
		con.setAdr(factorBool.getB1());

		Code.load(con); // put on expression stack
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
	
	public void visit(StartActPars start) {
		startPars = true;
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

		factorNew = true;

		SyntaxNode parent = factorNewType.getParent();
		if (parent.getParent().getParent().getClass() == AssignopDesignatorStatement.class) {
			Designator des = ((AssignopDesignatorStatement) parent.getParent().getParent()).getDesignator();

			if (des.obj.getName().contains("elem")) {
				int temp = Code.get(Code.pc - 1);
				Code.put(Code.pop);
				Code.put(Code.dup);
				Code.put(temp);
				Code.put(Code.dup_x1);
			}
		}

		Code.put(Code.new_);
		if (finalType == true) {
			finalType = false;
			Code.put2(size * 2);
		} else {
			Code.put2(size);
		}

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
		} else if(mulopExpr.getMulop() instanceof ModMulop){
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
		// jump on AFTER THEN
		listToFillJumps = jumpOnAfterThen.pop();
		while (listToFillJumps.size() != 0) {
			int adr = listToFillJumps.remove(listToFillJumps.size() - 1);
			Code.fixup(adr);
		}
	}

	public void visit(IfElseStatement ifStatement) {
		// jump on AFTER THEN
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
		forLoopEnd.add(new ArrayList<>());
	}

	public void visit(ForLoopTop forLoop) {
		forLoopTopAddresses.push(Code.pc);
	}

	public void visit(SingleCondFact condFact) {
		// ako nije ispunjen skoci na FOR END

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

	public void visit(NoneCondFact noneCondFact) {
		// ako nema uslova bezuslovno se skace na body
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

	}

	public void visit(UnpackingDesignatorStatement unpackingStatement) {
		int i = designatorNumber;
		int fromEnd = 0;
		int cnt;

		// provera greske
		Code.put(Code.putstatic);
		Code.put2(designatorEqAdr);
		Code.put(Code.putstatic);
		Code.put2(designatorMulAdr);

		// load designatorEq from static
		Code.put(Code.getstatic);
		Code.put2(designatorEqAdr);

		Code.put(Code.arraylength);
		Code.loadConst(designatorNumber);

		// load designatorMul from static
		Code.put(Code.getstatic);
		Code.put2(designatorMulAdr);

		Code.put(Code.arraylength);
		Code.put(Code.add);
		Code.putFalseJump(Code.ne, 0);
		int jumpAdr = Code.pc - 2;
		Code.put(Code.trap);
		Code.put(2);
		Code.fixup(jumpAdr);

		// obrada pre *

		for (cnt = 0; cnt < designatorNumber; cnt++) {
			for (int ind : indexesToSkip) {
				if (cnt == ind) {
					continue;
				}
			}

			if (designatorType.get(cnt) == 1) {
				// array

				Code.put(Code.getstatic);
				Code.put2(designatorEqAdr);

				Code.loadConst(designatorNumber - cnt - 1);

				// provera ako je char onda baload
				if (objDesignatorEq.getType().getElemType() == Tab.charType) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}

				// provera ako je char onda bastore
				if (unpackingObj.get(cnt).getType().getElemType() == Tab.charType) {
					Code.put(Code.bastore);
				} else {
					Code.put(Code.astore);
				}

			} else if (designatorType.get(cnt) == 2) {
				// fld

				Code.put(Code.getstatic);
				Code.put2(designatorEqAdr);

				Code.loadConst(designatorNumber - cnt - 1);
				if (objDesignatorEq.getType().getElemType() == Tab.charType) {
					Code.put(Code.baload);
				} else {
					Code.put(Code.aload);
				}
				Code.store(unpackingObj.get(cnt));
			} else {
				// var

				Code.put(Code.getstatic);
				Code.put2(designatorEqAdr);

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

		Code.put(Code.getstatic);
		Code.put2(designatorEqAdr);

		Code.put(Code.arraylength);
		Code.putFalseJump(Code.ne, 0);
		jumpAdr = Code.pc - 2;

		// stack: cnt
		Code.put(Code.dup);
		Code.put(Code.dup);

		Code.put(Code.getstatic);
		Code.put2(designatorEqAdr);

		Code.put(Code.arraylength);
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		// stack: cnt, cnt, len, cnt
		Code.put(Code.sub);
		// stack: cnt, cnt, len -cnt

		Code.put(Code.getstatic);
		Code.put2(designatorMulAdr);

		Code.put(Code.arraylength);
		// stack: cnt, cnt, len -cnt, len2
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.sub);

		// stack: cnt, cnt, indexLeft

		Code.put(Code.getstatic);
		Code.put2(designatorMulAdr);

		Code.put(Code.dup_x1);
		Code.put(Code.pop);

		// stack: cnt, cnt, arrLeft indexLeft
		Code.put(Code.dup_x2);
		Code.put(Code.pop);
		// stack: cnt, indexLeft, cnt, arrLeft
		Code.put(Code.dup_x2);
		Code.put(Code.pop);
		// stack: cnt, arrLeft, indexLeft, cnt

		Code.put(Code.getstatic);
		Code.put2(designatorEqAdr);

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

	/*
	 * 
	 * STATIC INITIALIZERS
	 * 
	 */

	public void visit(StaticInitializerStart staticInitializer) {
		if (firstStaticInitializerStart == false) {
			firstStaticInitializerStart = true;
			mainPc = Code.pc;
		} else {
			Code.fixup(nextStaticInitializerAdr);
		}
	}

	public void visit(StaticInitializer staticInitializer) {
		Code.putJump(0);
		nextStaticInitializerAdr = Code.pc - 2;
	}

	/*
	 * 
	 * TVF for class methods
	 * 
	 */

	public void visit(YesExtendsType yesExtendsType) {
		classExtends = yesExtendsType.obj;

	}

	public void visit(ClassName className) {
		classDeclStart = true;

	}

	public void visit(YesStaticInitializerList yesStaticInitializer) {
		currentClass = yesStaticInitializer.obj;

		if (classExtends != null) {
			classMethodsAdr.put(yesStaticInitializer.obj, classMethodsAdr.get(classExtends));

		} else {
			classMethodsAdr.put(yesStaticInitializer.obj, new ArrayList<>());
		}
	}

	public void visit(NoStaticInitializerList noStaticInitializer) {
		currentClass = noStaticInitializer.obj;

		if (classExtends != null) {
			classMethodsAdr.put(noStaticInitializer.obj, classMethodsAdr.get(classExtends));

		} else {
			classMethodsAdr.put(noStaticInitializer.obj, new ArrayList<>());
		}
	}

	public void visit(ClassDeclNoError classDecl) {

		// WRITE TVF for current class

		if (classMethodsAdr.get(currentClass).size() != 0) {
			classTVFAdr.put(currentClass.getType(), Code.dataSize);
			tvfInitialize.add(Code.pc);

			List<AbstractMap.SimpleEntry<Obj, Integer>> temp = classMethodsAdr.get(currentClass);

			if (firstStaticInitializerStart == true) {
				Code.fixup(nextStaticInitializerAdr);
			} else {
				firstStaticInitializerStart = true;
				mainPc = Code.pc;
			}

			for (int i = 0; i < temp.size(); i++) {
				String name = temp.get(i).getKey().getName();
				for (int j = 0; j < name.length(); j++) {
					Code.loadConst(name.charAt(j));
					Code.put(Code.putstatic);
					Code.put2(Code.dataSize++);
				}
				Code.loadConst(-1);
				Code.put(Code.putstatic);
				Code.put2(Code.dataSize++);

				Code.loadConst(temp.get(i).getValue());
				Code.put(Code.putstatic);
				Code.put2(Code.dataSize++);

			}

			Code.loadConst(-2);
			Code.put(Code.putstatic);
			Code.put2(Code.dataSize++);
			Code.putJump(0);
			nextStaticInitializerAdr = Code.pc - 2;
		}

		classDeclStart = false;
		currentClass = null;
		classExtends = null;

	}
	
	// MODIFIKACIJE
	
	public void visit(FactorNewActPars factorNewType) {
		int size = 0;
		int numOfFields = 0;
		Struct superclass = factorNewType.getType().struct;

		while (superclass != null) {
			numOfFields += superclass.getNumberOfFields();
			superclass = superclass.getElemType();
		}

		size = numOfFields * 4;

		factorNew = true;

		SyntaxNode parent = factorNewType.getParent();
		if (parent.getParent().getParent().getClass() == AssignopDesignatorStatement.class) {
			Designator des = ((AssignopDesignatorStatement) parent.getParent().getParent()).getDesignator();

			if (des.obj.getName().contains("elem")) {
				int temp = Code.get(Code.pc - 1);
				Code.put(Code.pop);
				Code.put(Code.dup);
				Code.put(temp);
				Code.put(Code.dup_x1);
			}
		}

		Code.put(Code.new_);
		Code.put2(size);
		
		factorNewTypePars = true;
		startPars = false;
	}
	
	public void visit(ExprCmpTerm term) {
		
		Code.put(Code.dup2);
		Code.put(Code.dup2);
		
		Code.putFalseJump(Code.ne, 0);
		int addrEq = Code.pc-2;
		Code.putFalseJump(Code.lt, 0);
		int addrGt = Code.pc-2;
		Code.putFalseJump(Code.gt, 0);
		int addrLt = Code.pc-2;
		
		int adrToJump1, adrToJump2;
		
		Code.fixup(addrEq);
		Code.put(Code.pop);
		Code.put(Code.pop);
		Code.put(Code.pop);
		Code.put(Code.pop);
		Code.loadConst(0);
		Code.putJump(0);
		adrToJump1 = Code.pc-2;
		Code.fixup(addrGt);
		Code.put(Code.pop);
		Code.put(Code.pop);
		Code.loadConst(1);
		Code.putJump(0);
		adrToJump2 = Code.pc-2;
		Code.fixup(addrLt);
		Code.loadConst(-1);
		
		Code.fixup(adrToJump1);
		Code.fixup(adrToJump2);
	}

}
