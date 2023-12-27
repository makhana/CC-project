package rs.ac.bg.etf.pp1.util;

import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;

public class SymbolTableBoolVisitor extends DumpSymbolTableVisitor {

	@Override
	public void visitStructNode(Struct structToVisit) {
		if(structToVisit.getKind() == Struct.Bool) {
			output.append("bool");
		} else {
			super.visitStructNode(structToVisit);
		}
	}
}
