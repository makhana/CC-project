package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.ac.bg.etf.pp1.util.SymbolTableBoolVisitor;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.io.FileOutputStream;

public class Compiler {
	
	public static final Struct boolType = new Struct(Struct.Bool);
	
	
	
	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	public static void main(String[] args) throws Exception {
		Logger log = Logger.getLogger(Compiler.class);
		
		Reader br = null;
		
		try {
			File sourceCode = new File("test/program.mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			// LEKSER TEST
//			Symbol currToken = null;
//			while((currToken = lexer.next_token()).sym != sym.EOF) {
//				if(currToken != null && currToken.value != null)
//					log.info(currToken.toString() + " " + currToken.value.toString());
//			}
			
			// PARSER TEST
			MJParser p = new MJParser(lexer);
			Symbol s = p.debug_parse(); // pocetak parsiranja
			
			Program prog = (Program)(s.value);
			Tab.init();
			Tab.insert(Obj.Type, "bool", boolType);
		
			// ispis sintaksnog stabla
			log.info(prog.toString(""));
			log.info("=====================================");
			
			// ispis prepoznatih porogramskih konstrukcija
//			RuleVisitor v = new RuleVisitor();
//			prog.traverseBottomUp(v);
			
//			log.info("Namespaces: " + v.getCounter(Namespace.class));
//			log.info("If/Else statements: " + v.getCounter(RuleVisitor.IfElseStmt.class));
		
			// SEMANTIKA TEST
			SemanticAnalyzer sa = new SemanticAnalyzer();
			prog.traverseBottomUp(sa);
			log.info("=====================================");
			
			
			Tab.dump(new SymbolTableBoolVisitor());
			
			if(!p.errorDetected && sa.passed()) {
				
				File objFile = new File("test/program.obj");
				if(objFile.exists()) objFile.delete();
				
				CodeGenerator codeGenerator = new CodeGenerator();
				prog.traverseBottomUp(codeGenerator);
				Code.dataSize = sa.nVars;
				Code.mainPc = codeGenerator.getMainPc();
				Code.write(new FileOutputStream(objFile));
				log.info("Parsiranje uspesno zavrseno!");
			} else {
				log.error("Parsiranje NIJE uspesno zavrseno!");
			}
			
			
		}
		finally {
			if (br != null) try { br.close();} catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}
		
	}
}
