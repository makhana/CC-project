package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;

import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.symboltable.*;

import rs.etf.pp1.symboltable.concepts.*;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

import java.util.*;

public class SemanticAnalyzer extends VisitorAdaptor {
	Logger log = Logger.getLogger(getClass());

	// GLOBAL help
	boolean errorDetected = false;
	Obj declType = null;

	String namespace = "";
	int forLoopActive = 0;

	// MAIN help
	boolean mainExists = false;
	boolean mainTypeVoid = false;
	int mainParamsNumber = 0;

	// FUNCTION help
	Obj currentMethod = null;
	boolean returnFound = false;

	HashMap<String, HashMap<Integer, Struct>> globalFunctions = new HashMap<>(); // ime funkcije, (indeks parametra,
																					// parametar)
	int functionParamsCnt = 0;

	HashMap<Integer, Struct> functionCallParams;
	int functionArgsCnt = 0;

	// CLASS help
	Struct extendsType = null;
	Obj currentClass = null;
	Struct classType = null;
	String className;
	int classFieldNumber = 0;
	boolean staticVarStart = false;
	boolean staticInitializerStart = false;

//	List<String> namespaceList = new ArrayList<String>();
	
	int globalVarsAdrCnt = 0;

	int nVars;

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.info(msg.toString());
	}

	private final HashMap<Class<?>, Integer> counters = new HashMap<>();

	private void count(Class<?> klass) {
		int value = counters.getOrDefault(klass, 0);
		counters.put(klass, value + 1);
	}

	public int getCounter(Class<?> klass) {
		return counters.getOrDefault(klass, 0);
	}

	/*
	 * 
	 * PROGRAM and NAMESPACE
	 * 
	 */

	public void visit(ProgName progName) {
		progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
		Tab.openScope();
	}

	public void visit(Program program) {
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(program.getProgName().obj);
		Tab.closeScope();

		if (!mainExists) {
			report_error("Greska: Main funkcija ne postoji u programu ", null);
		} else if (!mainTypeVoid) {
			report_error("Greska: Povratni tip main funkcije nije void ", null);
		} else if (mainParamsNumber != 0) {
			report_error("Greska: Main funkcija nema 0 parametara ", null);
		}
	}

	public void visit(Namespace n) {
		report_info("Zatvaram namespace " + namespace, n);
		namespace = "";
	}

	public void visit(NamespaceName namespaceName) {
		namespace = namespaceName.getNamespaceName();
		report_info("Ovaram namespace " + namespace, namespaceName);
//		namespaceList.add(namespace);
	}
	/*
	 * 
	 * CLASS
	 * 
	 */

	public void visit(ClassName classNameIdent) {
		String name = classNameIdent.getClassName();

		if (!namespace.equalsIgnoreCase("")) {
			name = namespace + "::" + name;
		}
		
		className = name;


	}

	public void visit(ClassDeclNoError classDecl) {
//		if(extendsType != null) {
//			report_info("MOJ INFO", null);
//			classType.setElementType(extendsType);
//		}
		Tab.chainLocalSymbols(classType);
		Tab.closeScope();
		currentClass = null;
		classType = null;
		classFieldNumber = 0;
		extendsType = null;

	}

	public void visit(StaticStart staticStart) {
		staticVarStart = true;
	}

	public void visit(StaticVarDecl staticVarDecl) {
		staticVarStart = false;
	}

	public void visit(StaticInitializerStart staticInitializer) {
		staticInitializerStart = true;
	}

	public void visit(StaticInitializer staticInitializer) {
		staticInitializerStart = false;
	}
	
	public void visit(YesStaticInitializerList yesStaticINitializer) {
		Obj classObj = Tab.find(className);
		if (classObj != Tab.noObj) {
			report_error("Klasa " + className + " je vec definisana", null);
			classType = Tab.noType;
			currentClass = Tab.insert(Obj.Type, className, classType);
		} else {
			classType = new Struct(Struct.Class);

			currentClass = Tab.insert(Obj.Type, className, classType);
		}

		Tab.openScope();
		Tab.insert(Obj.Fld, "TVF", Tab.noType);
		report_info("Obradjuje se klasa " + className, null);
	}
	
	public void visit(NoStaticInitializerList noStaticINitializer) {
		Obj classObj = Tab.find(className);
		if (classObj != Tab.noObj) {
			report_error("Klasa " + className + " je vec definisana", null);
			classType = Tab.noType;
			currentClass = Tab.insert(Obj.Type, className, classType);
		} else {
			classType = new Struct(Struct.Class);

			currentClass = Tab.insert(Obj.Type, className, classType);
		}

		Tab.openScope();
		Tab.insert(Obj.Fld, "TVF", Tab.noType);
		report_info("Obradjuje se klasa " + className, null);
	}
	
	

	// TO DO
	public void visit(YesExtendsType yesExtendsType) {
		if (extendsType != null) {
			classType.setElementType(extendsType);
			for (Obj obj : extendsType.getMembers()) {
				if (obj.getKind() == Obj.Fld) {
					Tab.insert(obj.getKind(), obj.getName(), obj.getType());
				}
			}
		}
	}

	// TO DO
	public void visit(InsertMethodsExtends methodDecl) {
		if (extendsType != null) {
			for (Obj obj : extendsType.getMembers()) {
				if (obj.getKind() == Obj.Meth) {
					Tab.insert(obj.getKind(), obj.getName(), obj.getType());
				}
			}
		}
	}

	// TO DO
	public void visit(NoneMethodDeclList methodDecl) {
		if (extendsType != null) {
			for (Obj obj : extendsType.getMembers()) {
				if (obj.getKind() == Obj.Meth) {
					Tab.insert(obj.getKind(), obj.getName(), obj.getType());
				}
			}
		}
	}

	/*
	 * 
	 * VAR, CONST decl
	 * 
	 */

	public boolean checkIfNameAlreadyDefined(String name) {
		Obj symbol = Tab.currentScope.findSymbol(name);
		if (symbol != null) {
			return true;
		} else {
			return false;
		}
	}

	public void visit(SingleVarDecl singleVarDecl) {

		VarDeclType varDeclType = singleVarDecl.getVarDeclType();
		Obj varNode;
		String varName = "";

		boolean error = false;

		if ((varDeclType instanceof Variable)) {
			varName = ((Variable) varDeclType).getVarName();

			if (staticVarStart == true) {
				varName = className + "::" + varName;
			}

			if (!namespace.equalsIgnoreCase("") && currentMethod == null && currentClass == null) {
				varName = namespace + "::" + varName;
			}

			if (checkIfNameAlreadyDefined(varName)) {
				report_error("Greska na liniji " + singleVarDecl.getLine() + " : promenljiva sa imenom : " + varName
						+ " je vec deklarisana u ovom opsegu ", null);
				error = true;
			}

			if (currentClass != null) {
				varNode = Tab.insert(Obj.Fld, varName, declType.getType());

				
				// allClassFields.get(currentClass.getName()).put(varName, varNode);

			} else {
				varNode = Tab.insert(Obj.Var, varName, declType.getType());
			}
			
			

		} else if ((varDeclType instanceof Array)) {
			varName = ((Array) varDeclType).getVarName();

			if (staticVarStart == true) {
				varName = className + "::" + varName;
			}

			if (!namespace.equalsIgnoreCase("") && currentMethod == null && currentClass == null) {
				varName = namespace + "::" + varName;
			}

			if (checkIfNameAlreadyDefined(varName)) {
				report_error("Greska na liniji " + singleVarDecl.getLine() + " : promenljiva sa imenom : " + varName
						+ " je vec deklarisana u ovom opsegu ", null);
				error = true;
			}

			Struct typeForArray = new Struct(Struct.Array, declType.getType());

			if (currentClass != null) {
				varNode = Tab.insert(Obj.Fld, varName, typeForArray);
				
				// allClassFields.get(currentClass.getName()).put(varName, varNode);
			} else {
				varNode = Tab.insert(Obj.Var, varName, typeForArray);
			}
			
			

		}
		if (!error) {
			report_info("Deklarisana promenljiva " + varName, singleVarDecl);
		}

	}

	public void visit(MultipleVarDecl multipleVarDecl) {

		VarDeclType varDeclType = multipleVarDecl.getVarDeclType();
		Obj varNode;
		String varName = "";
		boolean error = false;

		if ((varDeclType instanceof Variable)) {
			varName = ((Variable) varDeclType).getVarName();

			if (staticVarStart == true) {
				varName = className + "::" + varName;
			}

			if (!namespace.equalsIgnoreCase("") && currentMethod == null && currentClass == null) {
				varName = namespace + "::" + varName;
			}

			if (checkIfNameAlreadyDefined(varName)) {
				report_error("Greska na liniji " + multipleVarDecl.getLine() + " : promenljiva sa imenom : " + varName
						+ " je vec deklarisana u ovom opsegu ", null);
				error = true;
			}

			if (currentClass != null) {
				varNode = Tab.insert(Obj.Fld, varName, declType.getType());
				
				
				// allClassFields.get(currentClass.getName()).put(varName, varNode);
			} else {
				varNode = Tab.insert(Obj.Var, varName, declType.getType());
			}
			
			

		} else if ((varDeclType instanceof Array)) {
			varName = ((Array) varDeclType).getVarName();

			if (staticVarStart == true) {
				varName = className + "::" + varName;
			}

			if (!namespace.equalsIgnoreCase("") && currentMethod == null && currentClass == null) {
				varName = namespace + "::" + varName;
			}

			if (checkIfNameAlreadyDefined(varName)) {
				report_error("Greska na liniji " + multipleVarDecl.getLine() + " : promenljiva sa imenom : " + varName
						+ " je vec deklarisana u ovom opsegu ", null);
				error = true;
			}

			Struct typeForArray = new Struct(Struct.Array, declType.getType());

			if (currentClass != null) {
				varNode = Tab.insert(Obj.Fld, varName, typeForArray);
				// allClassFields.get(currentClass.getName()).put(varName, varNode);
			} else {
				varNode = Tab.insert(Obj.Var, varName, typeForArray);
			}
			
			

		}

		if (!error) {
			report_info("Deklarisana promenljiva " + varName, multipleVarDecl);
		}
	}

	public void visit(SingleConstDecl singleConstDecl) {
		NumCharBoolConst constants = singleConstDecl.getNumCharBoolConst();
		String constName = singleConstDecl.getConstName();
		Obj varNode;

		if (!namespace.equalsIgnoreCase("") && currentMethod == null) {
			constName = namespace + "::" + constName;
		}

		if ((constants instanceof NumConst) && declType.getType().getKind() == Struct.Int) {
			if (checkIfNameAlreadyDefined(constName)) {
				report_error("Greska na liniji " + singleConstDecl.getLine() + " : simbol sa imenom : " + constName
						+ " je vec definisan u ovom opsegu ", null);
			}

			varNode = Tab.insert(Obj.Con, constName, Tab.intType);
			varNode.setAdr(((NumConst) constants).getNumber());
			report_info("Definisana konstanta " + constName + " sa vrednoscu " + varNode.getAdr(), singleConstDecl);

		} else if ((constants instanceof CharConst) && declType.getType().getKind() == Struct.Char) {
			if (checkIfNameAlreadyDefined(constName)) {
				report_error("Greska na liniji " + singleConstDecl.getLine() + " : simbol sa imenom : " + constName
						+ " je vec definisan u ovom opsegu ", null);
			}

			varNode = Tab.insert(Obj.Con, constName, Tab.charType);
			varNode.setAdr(((CharConst) constants).getCharacter());
			report_info("Definisana konstanta " + constName + " sa vrednoscu " + varNode.getAdr(), singleConstDecl);

		} else if ((constants instanceof BoolConst) && declType.getType().getKind() == Struct.Bool) {

			if (checkIfNameAlreadyDefined(constName)) {
				report_error("Greska na liniji " + singleConstDecl.getLine() + " : simbol sa imenom : " + constName
						+ " je vec definisan u ovom opsegu ", null);
			}

			varNode = Tab.insert(Obj.Con, constName, Compiler.boolType);
			varNode.setAdr(((BoolConst) constants).getBoolean());
			report_info("Definisana konstanta " + constName + " sa vrednoscu" + varNode.getAdr(), singleConstDecl);
		} else {
			// ERROR
			report_error("Greska na liniji " + singleConstDecl.getLine()
					+ " : dodeljeni izraz konstanti nije istog tipa kao konstanta ", singleConstDecl);

		}
	}

	public void visit(MultipleConstDecl multipleConstDecl) {

		NumCharBoolConst constants = multipleConstDecl.getNumCharBoolConst();
		String constName = multipleConstDecl.getConstName();
		Obj varNode;

		if (!namespace.equalsIgnoreCase("") && currentMethod == null) {
			constName = namespace + "::" + constName;
		}

		if ((constants instanceof NumConst) && declType.getType().getKind() == Struct.Int) {
			if (checkIfNameAlreadyDefined(constName)) {
				report_error("Greska na liniji " + multipleConstDecl.getLine() + " : simbol sa imenom : " + constName
						+ " je vec definisan u ovom opsegu ", null);
			}

			varNode = Tab.insert(Obj.Con, constName, Tab.intType);
			varNode.setAdr(((NumConst) constants).getNumber());
			report_info("Definisana konstanta " + constName + " sa vrednoscu " + varNode.getAdr(), multipleConstDecl);

		} else if ((constants instanceof CharConst) && declType.getType().getKind() == Struct.Char) {
			if (checkIfNameAlreadyDefined(constName)) {
				report_error("Greska na liniji " + multipleConstDecl.getLine() + " : simbol sa imenom : " + constName
						+ " je vec definisan u ovom opsegu ", null);
			}

			varNode = Tab.insert(Obj.Con, constName, Tab.charType);
			varNode.setAdr(((CharConst) constants).getCharacter());
			report_info("Definisana konstanta " + constName + " sa vrednoscu " + varNode.getAdr(), multipleConstDecl);

		} else if ((constants instanceof BoolConst) && declType.getType().getKind() == Struct.Bool) {

			if (checkIfNameAlreadyDefined(constName)) {
				report_error("Greska na liniji " + multipleConstDecl.getLine() + " : simbol sa imenom : " + constName
						+ " je vec definisan u ovom opsegu ", null);
			}

			varNode = Tab.insert(Obj.Con, constName, Compiler.boolType);
			varNode.setAdr(((BoolConst) constants).getBoolean());
			report_info("Definisana konstanta " + constName + " sa vrednoscu" + varNode.getAdr(), multipleConstDecl);
		} else {
			// ERROR
			report_error("Greska na liniji " + multipleConstDecl.getLine()
					+ " : dodeljeni izraz konstanti nije istog tipa kao konstanta ", multipleConstDecl);

		}
	}

	/*
	 * 
	 * TYPE
	 * 
	 */

	public void visit(SingleType singleType) {

		SyntaxNode parent = singleType.getParent();

		if (YesExtendsType.class == parent.getClass()) {
			// type je potekao iz extends klase

			Obj typeNode = Tab.find(singleType.getTypeName());
			if (typeNode == Tab.noObj) {
				if (namespace != "") {

					typeNode = Tab.find(namespace + "::" + singleType.getTypeName());
					if (typeNode == Tab.noObj) {
						report_error("Nije pronadjen tip " + singleType.getTypeName() + " u tabeli simbola", null);
						singleType.struct = Tab.noType;
					} else {

						declType = typeNode;
						singleType.struct = typeNode.getType();
						extendsType = typeNode.getType();

					}
				}
			} else {
				if (Obj.Type == typeNode.getKind() && typeNode.getType().getKind() == Struct.Class) {
					declType = typeNode;
					singleType.struct = typeNode.getType();
					extendsType = typeNode.getType();
				} else {
					report_error("Greska: Ime " + singleType.getTypeName() + " ne predstavlja klasni tip", singleType);
					singleType.struct = Tab.noType;
				}
			}
		} else {
			Obj typeNode = Tab.find(singleType.getTypeName());
			if (typeNode == Tab.noObj) {
				report_error("Nije pronadjen tip " + singleType.getTypeName() + " u tabeli simbola", null);
				singleType.struct = Tab.noType;
			} else {
				if (Obj.Type == typeNode.getKind()) {
					declType = typeNode;
					singleType.struct = typeNode.getType();
				} else {
					report_error("Greska: Ime " + singleType.getTypeName() + " ne predstavlja tip", singleType);
					singleType.struct = Tab.noType;
				}
			}
		}

	}

	public void visit(NamespaceType type) {
		String typeName = type.getTypeName();
		String namespaceName = type.getNamespaceName();

		String fullTypeName = namespaceName + "::" + typeName;

		Obj typeNode = Tab.find(fullTypeName);

		if (Obj.Type == typeNode.getKind()) {
			declType = typeNode;
			type.struct = typeNode.getType();
		} else {
			report_error(
					"Greska: Ime " + fullTypeName + " ne predstavlja tip definisan u okviru navedenog prostora imena",
					type);
			type.struct = Tab.noType;
		}
	}

	/*
	 * 
	 * METHOD
	 * 
	 */

	public void visit(MethodTypeName methodTypeName) {
		ReturnType returnType = methodTypeName.getReturnType();
		String methodName = methodTypeName.getMethName();

		if (!namespace.equalsIgnoreCase("") && currentClass == null) {
			methodName = namespace + "::" + methodName;
		}

		// mozda ne Tab.find nego checkIfnameExists

		// TO DO videti za ovo

//		Obj meth = Tab.find(methodName);
//		if (meth != Tab.noObj) {
//			report_error("Funkcija " + methodName + " je vec definisana", null);
//			methodTypeName.obj = Tab.noObj;
//		}
		if (checkIfNameAlreadyDefined(methodName)) {
			report_error("Ime " + methodName + " je vec definisano", null);
			methodTypeName.obj = Tab.noObj;
		}

		if (methodName.equalsIgnoreCase("main")) {
			mainExists = true;
			if (returnType instanceof ReturnTypeVoid) {
				mainTypeVoid = true;
			}
		}
		if (returnType instanceof ReturnTypeType) {
			Struct structForReturnedType = ((ReturnTypeType) returnType).getType().struct;
			currentMethod = Tab.insert(Obj.Meth, methodName, structForReturnedType);

		} else if (returnType instanceof ReturnTypeVoid) {
			currentMethod = Tab.insert(Obj.Meth, methodName, Tab.noType);

		}

		methodTypeName.obj = currentMethod;
		functionParamsCnt = 0;

//		if (currentClass == null) {
//			globalFunctions.put(methodName, new HashMap<>());
//		} else {
//			// za metode klase
//			allClassFunctions.get(currentClass.getName()).put(methodName, new HashMap<>());
//			allClassFields.get(currentClass.getName()).put(methodName, currentMethod);
//		}
		Tab.openScope();
		report_info("Obradjuje se funkcija " + methodName, methodTypeName);

		if (currentClass != null) {
			// metod klase je u pitanju -> dodaj implicitan this parametar
			Tab.insert(Obj.Var, "this", classType);
		}

	}

	public void visit(MethodDeclNoError methodDecl) {
		if (!returnFound && currentMethod.getType() != Tab.noType) {
			report_error("Semanticka greska na liniji " + methodDecl.getLine() + ": funkcija " + currentMethod.getName()
					+ " nema return iskaz!", null);
		}

		Tab.chainLocalSymbols(currentMethod);
		currentMethod.setLevel(functionParamsCnt);
		Tab.closeScope();

		returnFound = false;

		currentMethod = null;

	}

	public void visit(SingleFormParams singleParam) {
		FormParamType paramType = singleParam.getFormParamType();
		String paramName = "";
		Obj paramNode = null;
		boolean error = false;
		functionParamsCnt++;

		if (paramType instanceof VariableParam) {
			paramName = ((VariableParam) paramType).getParamName();

			if (checkIfNameAlreadyDefined(paramName)) {
				report_error("Greska na liniji " + singleParam.getLine() + " : parametar sa imenom : " + paramName
						+ " je vec naveden ", null);
				declType = Tab.noObj;
				error = true;
			}
			paramNode = Tab.insert(Obj.Var, paramName, declType.getType());

//			if (currentClass == null) {
//				globalFunctions.get(currentMethod.getName()).put(++functionParamsCnt, declType.getType());
//			} else {
//				// TO DO
//				allClassFunctions.get(currentClass.getName()).get(currentMethod.getName()).put(++functionParamsCnt,
//						declType.getType());
//			}

		} else if (paramType instanceof ArrayParam) {

			paramName = ((ArrayParam) paramType).getParamName();

			if (checkIfNameAlreadyDefined(paramName)) {
				report_error("Greska na liniji " + singleParam.getLine() + " : parametar sa imenom : " + paramName
						+ " je vec naveden ", null);
				declType = Tab.noObj;
				error = true;
			}
			Struct paramTypestruct = new Struct(Struct.Array, declType.getType());
			paramNode = Tab.insert(Obj.Var, paramName, paramTypestruct);

//			if (currentClass == null) {
//				globalFunctions.get(currentMethod.getName()).put(++functionParamsCnt, paramTypestruct);
//			} else {
//				// TO DO
//				allClassFunctions.get(currentClass.getName()).get(currentMethod.getName()).put(++functionParamsCnt,
//						paramTypestruct);
//
//			}

		}

//		paramNode.setLevel(1);
		if (!error) {
			report_info("Naveden lokalni parametar funkcije: " + currentMethod.getName() + " sa nazivom: " + paramName,
					singleParam);
		}

	}

	public void visit(MultipleFormParams multipleParam) {
		FormParamType paramType = multipleParam.getFormParamType();
		String paramName = "";
		Obj paramNode = null;
		boolean error = false;
		functionParamsCnt++;

		if (paramType instanceof VariableParam) {
			paramName = ((VariableParam) paramType).getParamName();

			if (checkIfNameAlreadyDefined(paramName)) {
				report_error("Greska na liniji " + multipleParam.getLine() + " : parametar sa imenom : " + paramName
						+ " je vec naveden ", null);
				declType = Tab.noObj;
				error = true;
			}
			paramNode = Tab.insert(Obj.Var, paramName, declType.getType());

//			if (currentClass == null) {
//				globalFunctions.get(currentMethod.getName()).put(++functionParamsCnt, declType.getType());
//			} else {
//				// TO DO
//				allClassFunctions.get(currentClass.getName()).get(currentMethod.getName()).put(++functionParamsCnt,
//						declType.getType());
//			}

		} else if (paramType instanceof ArrayParam) {

			paramName = ((ArrayParam) paramType).getParamName();

			if (checkIfNameAlreadyDefined(paramName)) {
				report_error("Greska na liniji " + multipleParam.getLine() + " : parametar sa imenom : " + paramName
						+ " je vec naveden ", null);
				declType = Tab.noObj;
				error = true;
			}
			Struct paramTypestruct = new Struct(Struct.Array, declType.getType());
			paramNode = Tab.insert(Obj.Var, paramName, paramTypestruct);

//			if (currentClass == null) {
//				globalFunctions.get(currentMethod.getName()).put(++functionParamsCnt, paramTypestruct);
//			} else {
//				// To DO
//				allClassFunctions.get(currentClass.getName()).get(currentMethod.getName()).put(++functionParamsCnt,
//						paramTypestruct);
//			}

		}

//		paramNode.setLevel(1);
		if (!error) {
			report_info("Naveden lokalni parametar funkcije : " + currentMethod.getName() + " sa nazivom: " + paramName,
					multipleParam);
		}

	}

	/*
	 * 
	 * STATEMENT
	 * 
	 */

	public void visit(ReturnStatement returnStatement) {
		returnFound = true;
		Struct currMethType = currentMethod.getType();
		if (!currMethType.compatibleWith(returnStatement.getExpr().struct)) {
			report_error("Greska na liniji " + returnStatement.getLine() + ": "
					+ "tip izraza u return naredbi se ne slaze sa tipom povratne vrednosti funkcije "
					+ currentMethod.getName(), null);
		}
	}

//	public void visit(NoReturnStatement noReturnStatement) {
//		returnVoidFound = true;
//		Struct currMethType = currentMethod.getType();
//		if (currMethType != Tab.noType) {
//			report_error("Greska na liniji " + noReturnStatement.getLine() + ": "
//					+ "return naredba nema tip a povratna vrednost funkcije nije void " + currentMethod.getName(),
//					null);
//		}
//	}

	public void visit(ReadStatement readStatement) {
		Designator designator = readStatement.getDesignator();
		boolean error = false;

		if (designator.obj.getKind() != Obj.Var && designator.obj.getKind() != Obj.Elem
				&& designator.obj.getKind() != Obj.Fld) {
			report_error(
					"Greska na liniji " + readStatement.getLine() + ": "
							+ "designator read statement-a nije promenljiva, element niza ili polje unutar objekta!",
					null);
			error = true;
		}

		if (!designator.obj.getType().equals(Tab.intType) && !designator.obj.getType().equals(Tab.charType)
				&& !designator.obj.getType().equals(Compiler.boolType)) {
			report_error("Greska na liniji " + readStatement.getLine() + ": "
					+ "designator read statement-a nije tipa int, char ili bool!", null);
			error = true;
		}

		if (!error) {
			report_info("Ispravno izvrsen read statement", readStatement);
		}
	}

	public void visit(PrintStatement printStatement) {
		Expr expr = printStatement.getExpr();

		if (expr.struct != Tab.intType && expr.struct != Tab.charType && expr.struct != Compiler.boolType) {
			report_error("Greska na liniji " + printStatement.getLine() + ": " + "izraz nije tipa int, char ili bool!",
					null);
		} else {
			report_info("Ispravno izvrsen print statement", printStatement);
		}
	}

	public void visit(PrintStatementComma printStatement) {
		Expr expr = printStatement.getExpr();

		if (expr.struct != Tab.intType && expr.struct != Tab.charType && expr.struct != Compiler.boolType) {
			report_error("Greska na liniji " + printStatement.getLine() + ": " + "izraz nije tipa int, char ili bool!",
					null);
		} else {
			report_info("Ispravno izvrsen print statement", printStatement);
		}
	}

	public void visit(ForLoopStart forLoopStart) {
		forLoopActive += 1;
	}

	public void visit(ForStatement forStatement) {
		forLoopActive -= 1;
	}

	public void visit(BreakStatement breakStatement) {
		if (forLoopActive == 0) {
			report_error("Break statement nije iskoriscen unutar for loop-a!", null);
		}
	}

	public void visit(ContinueStatement continueStatement) {
		if (forLoopActive == 0) {
			report_error("Continue statement nije iskoriscen unutar for loop-a!", null);
		}
	}

	/*
	 * 
	 * DESIGNATOR
	 * 
	 */

	public void visit(DesignatorIdent designator) {

		String name = designator.getDesignatorName();
		String newName;

		if (staticInitializerStart == true && !name.equals("eol")) {
			newName = className + "::" + name;
			Obj obj = Tab.find(newName);
			if (obj == null) {
				report_error("Greska na liniji " + designator.getLine() + " : ime " + name + " nije u opsegu klase! ",
						null);
				designator.obj = Tab.noObj;
			} else {
				designator.obj = obj;
			}
		} else {
			if (name.equals("this")) {
				if (currentClass != null) {
					designator.obj = new Obj(Obj.Var, "$", currentClass.getType());
				} else {
					report_error(
							"Greska na liniji " + designator.getLine() + " : ime " + name + " nije u opsegu klase! ",
							null);
					designator.obj = Tab.noObj;
				}

			} else {
				Obj obj = Tab.find(name);

				if (obj == Tab.noObj) {

					if (namespace != "") {
						obj = Tab.find(namespace + "::" + name);
						if (obj == Tab.noObj) {
							report_error("Greska na liniji " + designator.getLine() + " : ime " + name
									+ " nije deklarisano! ", null);
						}
					} else {
						report_error(
								"Greska na liniji " + designator.getLine() + " : ime " + name + " nije deklarisano! ",
								null);
					}
				}
				designator.obj = obj;
			}
		}

	}

	public void visit(DesignatorNamespace designator) {

		if (staticInitializerStart == true) {
			report_error("Greska na liniji " + designator.getLine()
					+ " : u statickom inicijalizatoru dozvoljeno je menjanje samo statickih polja okruzujuce klase",
					null);
		}
		String designatorName = designator.getDesignatorName();
		String namespaceName = designator.getNamespaceDesignator();

		String fullName = namespaceName + "::" + designatorName;
		Obj obj = Tab.find(fullName);
		if (obj == Tab.noObj) {
			report_error("Greska na liniji " + designator.getLine() + " : ime " + fullName + " nije deklarisano! ",
					null);
		}
		designator.obj = obj;

	}

	public void visit(DesignatorArrayElem designatorArray) {

		Designator des = designatorArray.getDesignator();
		if (des.obj.getType().getKind() == Struct.Array && designatorArray.getExpr().struct.assignableTo(Tab.intType)) {

			designatorArray.obj = new Obj(Obj.Elem, des.obj.getName() + ":elem", des.obj.getType().getElemType());
		} else {
			report_error("Greska na liniji " + designatorArray.getLine() + " : identifikator nije int ili designator : "
					+ des.obj.getName() + " nije niz! ", null);
			designatorArray.obj = Tab.noObj;
		}
	}

	public void visit(DesignatorClassMember designatorClass) {
		Designator des = designatorClass.getDesignator();
		String fldName = designatorClass.getFieldName();
//		if(des.obj == Tab.noObj) {
//			designatorClass.obj = Tab.noObj;
//			return;
//		}

		if (des.obj.getType().getKind() != Struct.Class) {
			// ERROR

			report_error("Greska na liniji " + designatorClass.getLine() + " : identifikator : " + des.obj.getName()
					+ " nije ni klasa ni tip klase! ", null);
			designatorClass.obj = Tab.noObj;
		} else if (des.obj.getKind() == Obj.Type) {

			// ident je staticko polje
//			report_info("MOJ INFO", null);

			
			// TO DO
			
			String newName = des.obj.getName() + "::" + fldName;
			
			Obj obj = Tab.find(newName);
			

			if (obj == null) {
				report_error("Greska na liniji " + designatorClass.getLine() + " : identifikator : " + fldName
						+ " nije staticko polje klase", null);
				designatorClass.obj = Tab.noObj;
			} else {

				designatorClass.obj = obj;
			}

		} else {
			// ident je Fld ili Meth
			if (currentClass != null) {

				Obj obj = Tab.currentScope.findSymbol(fldName);
				if (obj == null) {
					report_error("Greska na liniji " + designatorClass.getLine() + " : identifikator : " + fldName
							+ " nije ni polje ni metoda klase koja je tip objekta", null);
					designatorClass.obj = Tab.noObj;
				} else {

					designatorClass.obj = obj;
				}
			} else {

				SymbolDataStructure members = des.obj.getType().getMembersTable();

				Obj obj = members.searchKey(fldName);

				if (obj == null) {
					report_error("Greska na liniji " + designatorClass.getLine() + " : identifikator : " + fldName
							+ " nije ni polje ni metoda klase koja je tip objekta", null);
					designatorClass.obj = Tab.noObj;
				} else {

					designatorClass.obj = obj;
				}
			}
		}

	}

	/*
	 * 
	 * DESIGNATOR STATEMENT
	 * 
	 */

	public void visit(AssignopDesignatorStatement assignment) {
		Designator designator = assignment.getDesignator();
		Expr expr = assignment.getExpr();
		boolean errorFound = false;

		if (designator.obj == Tab.noObj) {
			return;
		}

		if (designator.obj.getKind() != Obj.Var && designator.obj.getKind() != Obj.Elem
				&& designator.obj.getKind() != Obj.Fld) {
			report_error("Greska na liniji " + assignment.getLine() + " : designator : " + designator.obj.getName()
					+ " nije promenljiva, element niza ili polje unutar objekta! ", null);
			errorFound = true;
		}

		if (!expr.struct.compatibleWith(designator.obj.getType())) {
			report_error(
					"Greska na liniji " + assignment.getLine() + " : tipovi designator-a i expr nisu kompatibilni!",
					null);
			errorFound = true;
		}

		if (!errorFound) {
			report_info("Izvrsena dodela vrednosti", assignment);
		}
	}

	public void visit(IncDesignatorStatement increment) {
		// TO DO POLJE OBJEKTA UNUTRASNJE KLASE
		Designator designator = increment.getDesignator();
		boolean errorFound = false;

		if (designator.obj.getKind() != Obj.Var && designator.obj.getKind() != Obj.Elem
				&& designator.obj.getKind() != Obj.Fld) {
			report_error("Greska na liniji " + increment.getLine()
					+ " : designator nije promenljiva, element niza ili polje objekta unutrasnje klase!", null);
			errorFound = true;
		}

		if (designator.obj.getType().getKind() != Struct.Int) {
			report_error("Greska na liniji " + increment.getLine() + " : tip designatora nije int!", null);
			errorFound = true;
		}

		if (!errorFound) {
			report_info("Izvrsen increment", increment);
		}
	}

	public void visit(DecDesignatorStatement decrement) {
		// TO DO POLJE OBJEKTA UNUTRASNJE KLASE
		Designator designator = decrement.getDesignator();
		boolean errorFound = false;

		if (designator.obj.getKind() != Obj.Var && designator.obj.getKind() != Obj.Elem
				&& designator.obj.getKind() != Obj.Fld) {
			report_error("Greska na liniji " + decrement.getLine()
					+ " : designator nije promenljiva, element niza ili polje objekta unutrasnje klase!", null);
			errorFound = true;
		}

		if (designator.obj.getType().getKind() != Struct.Int) {
			report_error("Greska na liniji " + decrement.getLine() + " : tip designatora nije int!", null);
			errorFound = true;
		}

		if (!errorFound) {
			report_info("Izvrsen decrement", decrement);
		}
	}

	public void visit(UnpackingDesignatorStatement unpackingDesignator) {
		Designator mulDesignator = unpackingDesignator.getDesignatorMul().getDesignator();
		Designator eqDesignator = unpackingDesignator.getDesignatorEq().getDesignator();
		DesignatorListStmt designatorList = unpackingDesignator.getDesignatorListStmt();
		boolean errorFound = false;

		if (eqDesignator.obj.getType().getKind() != Struct.Array) {
			report_error("Greska na liniji " + unpackingDesignator.getLine()
					+ " : designator sa desne strane znaka jednakosti nije niz!", null);
			errorFound = true;
		}
		if (mulDesignator.obj.getType().getKind() != Struct.Array) {
			report_error("Greska na liniji " + unpackingDesignator.getLine() + " : designator nakon * znaka nije niz!",
					null);
			errorFound = true;
		}

		if (designatorList instanceof DesignatorListStmtDesignator) {
			Designator des = ((DesignatorListStmtDesignator) designatorList).getDesignator();
			if (!eqDesignator.obj.getType().getElemType().compatibleWith(des.obj.getType())) {
				report_error("Greska na liniji " + unpackingDesignator.getLine()
						+ " : designatori nisu kompatibilnih tipova sa tipom elementa niza za dodelu!", null);
				errorFound = true;
			}
		}

		if (!mulDesignator.obj.getType().getElemType().compatibleWith(eqDesignator.obj.getType().getElemType())) {
			report_error(
					"Greska na liniji " + unpackingDesignator.getLine()
							+ " : elementi niza nakon * nisu kompatibilnih tipova sa tipom elementa niza za dodelu!",
					null);
			errorFound = true;
		}

		if (!errorFound) {
			report_info("Raspakivanje niza je proslo semanticku analizu", unpackingDesignator);
		}

	}

	public void visit(DesignatorListStmtDesignator designatorList) {
		Designator designator = designatorList.getDesignator();
		if (designator.obj.getKind() != Obj.Var && designator.obj.getKind() != Obj.Elem
				&& designator.obj.getKind() != Obj.Fld) {
			report_error("Greska na liniji " + designatorList.getLine()
					+ " : designator nije promenljiva, element niza ili polje unutar objekta!", null);

		}
	}

	public void visit(FuncCallDesignatorStatement funcCall) {

		Designator designator = funcCall.getDesignator();
		boolean error = false;

		if (designator.obj.getKind() == Obj.Meth) {

			String name = designator.obj.getName();

			if (name.contentEquals("ord")) {
				report_error("Greska na liniji " + funcCall.getLine()
						+ " : nije prosledjen dovoljan broj argumenata funkciji " + name, null);
				error = true;
			} else if (name.contentEquals("chr")) {
				report_error("Greska na liniji " + funcCall.getLine()
						+ " : nije prosledjen dovoljan broj argumenata funkciji " + name, null);
				error = true;
			} else if (name.contentEquals("len")) {
				report_error("Greska na liniji " + funcCall.getLine()
						+ " : nije prosledjen dovoljan broj argumenata funkciji " + name, null);
				error = true;
			} else {

				if (designator.obj.getLevel() != 0) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : broj parametara nije 0 a prosledjeno je 0 argumenata funkciji!", null);
					error = true;
				}
			}
		} else {
			report_error(
					"Greska na liniji " + funcCall.getLine() + " : vrsi se poziv promenljive koja nije tipa metode!",
					null);
			error = true;
		}

		if (!error) {
			report_info("Poziv funkcije " + designator.obj.getName() + " je uspesno izvrsen", funcCall);
		}

	}

	public void visit(FuncCallDesignatorStatementActPars funcCall) {

		Designator designator = funcCall.getDesignator();
		boolean error = false;

		if (designator.obj.getKind() == Obj.Meth) {

			String name = designator.obj.getName();
			int numberOfParameters = designator.obj.getLevel();

			int numberOfArgs = functionArgsCnt;

			if (name.contentEquals("ord")) {
				if (functionArgsCnt != 1 || !functionCallParams.get(0).assignableTo(Tab.charType)) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : nije prosledjen dovoljan broj argumenata funkciji " + name
							+ "ili tip prosledjenog argumenta nije int", null);
					error = true;
				}

			} else if (name.contentEquals("chr")) {
				if (functionArgsCnt != 1 || !functionCallParams.get(0).assignableTo(Tab.intType)) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : nije prosledjen dovoljan broj argumenata funkciji " + name
							+ "ili tip prosledjenog argumenta nije char", null);
					error = true;
				}
			} else if (name.contentEquals("len")) {
				if (functionArgsCnt != 1 || functionCallParams.get(0).getKind() != Struct.Array) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : nije prosledjen dovoljan broj argumenata funkciji " + name
							+ "ili parametar nije tipa niz", null);
					error = true;
				}
			} else {

				if (numberOfParameters != numberOfArgs) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : broj parametara i argumenata metode nije jednak!", null);
					error = true;
				} else {
					Collection<Obj> locals = designator.obj.getLocalSymbols();
					int i = 0;
					for (Obj obj : locals) {

						if (!obj.getType().assignableTo(functionCallParams.get(i))) {
							report_error("Greska na liniji " + funcCall.getLine()
									+ " : tipovi parametra i prosledjenih argumenata nisu jednaki", null);
							error = true;
						}
						i++;
						if (i == numberOfParameters) {
							break;
						}
					}
				}
			}
		} else {
			report_error(
					"Greska na liniji " + funcCall.getLine() + " : vrsi se poziv promenljive koja nije tipa metode!",
					null);
			error = true;
		}

		if (!error) {
			report_info("Poziv funkcije " + designator.obj.getName() + " je uspesno izvrsen", funcCall);
		}

		functionCallParams = null;
		functionArgsCnt = 0;

	}

	/*
	 * 
	 * FACTOR
	 * 
	 */

	public void visit(FactorNum num) {
		num.struct = Tab.intType;
	}

	public void visit(FactorChar chr) {
		chr.struct = Tab.charType;
	}

	public void visit(FactorBool bl) {
		bl.struct = Compiler.boolType;
	}

	public void visit(FactorDesignator factorDesignator) {
		factorDesignator.struct = factorDesignator.getDesignator().obj.getType();
	}

	public void visit(FactorExpr factor) {
		factor.struct = factor.getExpr().struct;
	}

	public void visit(FactorNewType factorNewType) {
		if (declType.getType().getKind() != Struct.Class) {
			report_error("Greska na liniji " + factorNewType.getLine() + " : tip nije korisnicki definisan!", null);
			factorNewType.struct = Tab.noType;
		} else {
			factorNewType.struct = declType.getType();
		}

		// TO DO PROVERITI
	}

	public void visit(FactorNewExpr factorNewExpr) {
		Expr expr = factorNewExpr.getExpr();
		if (expr.struct.getKind() != Struct.Int) {
			report_error("Greska na liniji " + factorNewExpr.getLine() + " : expression nije tipa int!", null);
		}

		factorNewExpr.struct = new Struct(Struct.Array, factorNewExpr.getType().struct);
	}

	public void visit(FactorFunctionCall funcCall) {
		Designator designator = funcCall.getDesignator();
		boolean error = false;

		if (designator.obj.getKind() == Obj.Meth) {

			String name = designator.obj.getName();

			if (name.contentEquals("ord")) {
				report_error("Greska na liniji " + funcCall.getLine()
						+ " : nije prosledjen dovoljan broj argumenata funkciji " + name, null);
				error = true;
			} else if (name.contentEquals("chr")) {
				report_error("Greska na liniji " + funcCall.getLine()
						+ " : nije prosledjen dovoljan broj argumenata funkciji " + name, null);
				error = true;
			} else if (name.contentEquals("len")) {
				report_error("Greska na liniji " + funcCall.getLine()
						+ " : nije prosledjen dovoljan broj argumenata funkciji " + name, null);
				error = true;
			} else {
				if (designator.obj.getLevel() != 0) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : broj parametara nije 0 a prosledjeno je 0 argumenata funkciji!", null);
					error = true;
				}
			}
		} else {
			report_error(
					"Greska na liniji " + funcCall.getLine() + " : vrsi se poziv promenljive koja nije tipa metode!",
					null);
			error = true;
		}

		if (!error) {
			report_info("Poziv funkcije " + designator.obj.getName() + " je uspesno izvrsen", funcCall);
		}

		funcCall.struct = designator.obj.getType();
	}

	public void visit(FactorFunctionCallParam funcCall) {
		Designator designator = funcCall.getDesignator();
		boolean error = false;

		if (designator.obj.getKind() == Obj.Meth) {

			String name = designator.obj.getName();
			int numberOfParameters = designator.obj.getLevel();

			int numberOfArgs = functionArgsCnt;

			if (name.contentEquals("ord")) {
				if (functionArgsCnt != 1 || !functionCallParams.get(0).assignableTo(Tab.charType)) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : nije prosledjen dovoljan broj argumenata funkciji " + name
							+ "ili tip prosledjenog argumenta nije int", null);
					error = true;
				}

			} else if (name.contentEquals("chr")) {
				if (functionArgsCnt != 1 || !functionCallParams.get(0).assignableTo(Tab.intType)) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : nije prosledjen dovoljan broj argumenata funkciji " + name
							+ "ili tip prosledjenog argumenta nije char", null);
					error = true;
				}
			} else if (name.contentEquals("len")) {
				if (functionArgsCnt != 1 || functionCallParams.get(0).getKind() != Struct.Array) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : nije prosledjen dovoljan broj argumenata funkciji " + name
							+ "ili parametar nije tipa niz", null);
					error = true;
				}
			} else {

				if (numberOfParameters != numberOfArgs) {
					report_error("Greska na liniji " + funcCall.getLine()
							+ " : broj parametara i argumenata metode nije jednak!", null);
					error = true;
				} else {
					Collection<Obj> locals = designator.obj.getLocalSymbols();
					int i = 0;
					for (Obj obj : locals) {

						if (!obj.getType().assignableTo(functionCallParams.get(i))) {
							report_error("Greska na liniji " + funcCall.getLine()
									+ " : tipovi parametra i prosledjenih argumenata nisu jednaki", null);
							error = true;
						}
						i++;
						if (i == numberOfParameters) {
							break;
						}
					}
				}
			}
		} else {
			report_error(
					"Greska na liniji " + funcCall.getLine() + " : vrsi se poziv promenljive koja nije tipa metode!",
					null);
			error = true;
		}

		if (!error) {
			report_info("Poziv funkcije " + designator.obj.getName() + " je uspesno izvrsen", funcCall);
		}

		functionCallParams = null;
		functionArgsCnt = 0;

		funcCall.struct = designator.obj.getType();

	}

	/*
	 * 
	 * Function CAll PARAMETERS
	 * 
	 */

	public void visit(SingleActPars funcParams) {
		if (functionCallParams == null) {
			functionCallParams = new HashMap<>();
		}
		functionCallParams.put(functionArgsCnt++, funcParams.getExpr().struct);
	}

	public void visit(MultipleActPars funcParams) {
		if (functionCallParams == null) {
			functionCallParams = new HashMap<>();
		}
		functionCallParams.put(functionArgsCnt++, funcParams.getExpr().struct);
	}

	// IMA JOS ZA FAKTOR KLASE I NEW tipove

	/*
	 * 
	 * TERM and EXPR
	 * 
	 */

	public void visit(SingleTerm term) {
		term.struct = term.getFactor().struct;
	}

	public void visit(MultipleTerm mulopTerm) {
		Struct factor = mulopTerm.getFactor().struct;
		Struct termT = mulopTerm.getTerm().struct;

		if (factor.getKind() != Struct.Int || termT.getKind() != Struct.Int) {
			report_error("Greska na liniji " + mulopTerm.getLine() + " : promenljive za mnozenje nisu int tipa", null);
			mulopTerm.struct = Tab.noType;
		} else {
			mulopTerm.struct = termT;
			report_info("Izvrsena operacija mnozenja/deljenja/mod", mulopTerm);
		}

	}

	public void visit(ExprMinusTerm exprTerm) {
		if (exprTerm.getTerm().struct.getKind() != Struct.Int) {
			report_error("Greska na liniji " + exprTerm.getLine() + " : promenljiva nije int tipa!", null);
		}
		exprTerm.struct = exprTerm.getTerm().struct;
	}

	public void visit(ExprTerm exprTerm) {
		exprTerm.struct = exprTerm.getTerm().struct;
	}

	public void visit(AddExpr addExpr) {
		Struct expr = addExpr.getExpr().struct;
		Struct term = addExpr.getTerm().struct;
		if (expr.compatibleWith(term) && expr == Tab.intType) {
			addExpr.struct = expr;
			report_info("Izvrsena operacija sabiranja/oduzimanja", addExpr);
		} else {
			report_error("Greska na liniji " + addExpr.getLine() + " : nekompatibilni tipovi u izrazu za sabiranje!",
					null);
			addExpr.struct = Tab.noType;
		}
	}

	/*
	 * 
	 * CONDITION
	 * 
	 */

	public void visit(CondFactExpr condFact) {
		condFact.struct = condFact.getExpr().struct;
	}

	public void visit(CondFactExprRelop condFact) {
		Expr expr1 = condFact.getExpr();
		Expr expr2 = condFact.getExpr1();
		Relop relop = condFact.getRelop();

		if (!expr1.struct.compatibleWith(expr2.struct)) {
			report_error("Tipovi u izrazu nisu kompatibilni", condFact);
			condFact.struct = Tab.noType;
		} else {
			condFact.struct = Compiler.boolType;
		}

		if (expr1.struct.getKind() == Struct.Array || expr1.struct.getKind() == Struct.Class) {
			if (!(relop instanceof IsEqualRelop) && !(relop instanceof NotEqualRelop)) {
				report_error("Operator nije ispravan u slucaju promenljivih tipa klasa ili niz", condFact);
				condFact.struct = Tab.noType;
			} else {
				condFact.struct = Compiler.boolType;
			}
		}

	}

	public void visit(CondTermFact condTerm) {
		condTerm.struct = condTerm.getCondFact().struct;
	}

	public void visit(CondTermAndFact condTerm) {
		condTerm.struct = condTerm.getCondFact().struct;
	}

	public void visit(ConditionTerm condition) {
		if (condition.getCondTerm().struct != Compiler.boolType) {
			report_error("Condition izraz nije bool tipa", condition);
		}
	}

	public void visit(ConditionOrTerm condition) {
		if (condition.getCondTerm().struct != Compiler.boolType) {
			report_error("Condition izraz nije bool tipa", condition);
		}
	}

	/*
	 * 
	 * END
	 * 
	 * 
	 */

	public boolean passed() {
		return !errorDetected;
	}

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
//
//	public void visit(YesElseStatement YesElseStatement) {
//		count(IfElseStmt.class);
//	}
}
