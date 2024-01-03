package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.List;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

public class CounterVisitor extends VisitorAdaptor {

	protected int count;
	protected List<Obj> unpackingObj = new ArrayList<>();
	protected List<Integer> indexesToSkip = new ArrayList<>();
	protected List<Integer> designatorType = new ArrayList<>(); // 0 - VAR ; 1 - ELEM ; 2 - FLD
	protected Obj objDesignatorMul;
	protected Obj objDesignatorEq;

	public int getCount() {
		return count;
	}
	
	public List<Obj> getUnpackingObj() {
		return unpackingObj;
	}
	
	public List<Integer> getIndexesToSkip() {
		return indexesToSkip;
	}
	
	public List<Integer> getDesignatorType() {
		return designatorType;
	}
	
	public Obj getObjDesignatorMul() {
		return objDesignatorMul;
	}
	
	public Obj getObjDesignatorEq() {
		return objDesignatorEq;
	}

	public static class FormParamCounter extends CounterVisitor {

		public void visit(SingleFormParams singleFormParams) {
			count++;
		}

		public void visit(MultipleFormParams multipleFormParams) {
			count++;
		}
	}
	
	public static class VarCounter extends CounterVisitor {

		public void visit(SingleVarDecl varDecl) {
			count++;
		}

		public void visit(MultipleVarDecl varDecl) {
			count++;
		}
	}
	
	public static class CondTermCounter extends CounterVisitor {

		public void visit(ConditionTerm condTerm) {
			count++;
		}

		public void visit(ConditionOrTerm condTerm) {
			count++;
		}
	}
	
	public static class DesignatorUnpackingCounter extends CounterVisitor {

		public void visit(DesignatorListStmtDesignator designator) {
			count++;
			unpackingObj.add(designator.getDesignator().obj);
			Designator des = designator.getDesignator();
			if(des instanceof DesignatorArrayElem) {
				designatorType.add(1);
			} else if(des instanceof DesignatorClassMember) {
				designatorType.add(2);
			} else {
				designatorType.add(0);
			}
		}
		
		public void visit(DesignatorListStmtComma noDesignator) {
			indexesToSkip.add(count++);
			unpackingObj.add(Tab.noObj);
			designatorType.add(-1);
		}
		
		public void visit(DesignatorMul designatorMul) {
			Designator des = designatorMul.getDesignator();
			objDesignatorMul = des.obj;
		}

		public void visit(DesignatorEq designatorEq) {
			Designator des = designatorEq.getDesignator();
			objDesignatorEq = des.obj;
		}
	}
}
