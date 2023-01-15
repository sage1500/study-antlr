package com.study.antlr;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.study.antlr.parser.Java9Lexer;
import com.study.antlr.parser.Java9Parser;
import com.study.antlr.parser.Java9Parser.MethodDeclarationContext;
import com.study.antlr.parser.Java9Parser.NormalClassDeclarationContext;
import com.study.antlr.parser.Java9ParserBaseVisitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class DemoApplication implements ApplicationRunner {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("START");

		var cs = CharStreams.fromFileName("src/main/java/com/study/antlr/DemoApplication.java");
		var lexer = new Java9Lexer(cs);

		switch (3) {
		case 1:
			doTestTokenSource(lexer);
			break;
		case 2: {
			var tokens = new CommonTokenStream(lexer);
			var parser = new Java9Parser(tokens);
			var javaTree = parser.compilationUnit();
			doTestParserRuleContext(javaTree);
		}
		default: {
			var tokens = new CommonTokenStream(lexer);
			var parser = new Java9Parser(tokens);
			var javaTree = parser.compilationUnit();
			doTestVisitor(javaTree, tokens);
		}
		}

		log.info("END");
	}

	private void doTestTokenSource(TokenSource ts) {
		//
		// Tokenの中身を確認するテスト
		//
		// 結果:
		// - トークンのファイルの位置（行番号、カラム位置）を識別可能
		// - トークンのファイルの先頭からの位置（文字単位）を識別可能
		// - tokenIndex の値は、この時点では -1 となっている。
		// - コメントもトークンとして保持されている。
		//
		while (true) {
			var token = ts.nextToken();

			printToken(token);

			if (token.getType() == Token.EOF) {
				break;
			}
		}
	}

	private void doTestParserRuleContext(ParserRuleContext ctx) {
		//
		// visitor 使わずに AST を汎用的にトラバースするテスト
		//
		// 結果:
		// - コメントは AST からは除外されている。
		//
		var startToken = ctx.getStart();
		var endToken = ctx.getStop();
		log.info("ctx {} {} index={}-{} line={}-{}", ctx.getClass().getSimpleName(), ctx.getRuleIndex(),
				startToken.getStartIndex(), endToken.getStopIndex(), startToken.getLine(), endToken.getLine());

		for (var c : ctx.children) {
			if (c instanceof ParserRuleContext) {
				doTestParserRuleContext((ParserRuleContext) c);
			} else if (c instanceof TerminalNode) {
				var tn = (TerminalNode) c;
				var token = tn.getSymbol();
				printToken(token);
			} else {
				log.info("! unknown children: {}", c.getClass());
			}
		}
	}

	private void doTestVisitor(ParserRuleContext ctx, TokenStream tokenStream) {
		//
		// visitor で AST をトラバースするテスト
		//
		if (false) {
			new MyAllVisitor().visit(ctx);
			new MyTypeVisitor().visit(ctx);
		}
		new MyMethodVisitor(tokenStream).visit(ctx);
	}

	/**
	 * トークン内容出力.
	 * 
	 * @param token トークン
	 */
	private void printToken(Token token) {
		log.info("token: line={}:{} pos={}-{} i={} type={} text={}", token.getLine(), token.getCharPositionInLine(),
				token.getStartIndex(), token.getStopIndex(), token.getTokenIndex(), token.getType(), token.getText());
	}

	private static String toString(Token token) {
		return "{line=" + token.getLine() + ":" + token.getCharPositionInLine() + " pos=" + token.getStartIndex() + "-"
				+ token.getStopIndex() + " index=" + token.getTokenIndex() + " type=" + token.getType() + " text="
				+ token.getText() + "}";
	}

	public static class MyNode {
		ParserRuleContext ctx;
		List<MyNode> children = new ArrayList<>();
		MyNode nextSibling;
		MyNode lastSibling;

		public void addSibling(MyNode sibling) {
			if (sibling == null || sibling.nextSibling != null) {
				return;
			}

			// set nextSibling
			if (nextSibling == null) {
				nextSibling = sibling;
			} else {
				lastSibling.nextSibling = sibling;
			}

			// set lastSibling
			lastSibling = sibling;
		}
	}

	public static class MyAllVisitor extends Java9ParserBaseVisitor<MyNode> {
		@Override
		public MyNode visit(ParseTree tree) {
			log.info("visit-tree {}", tree.getClass().getSimpleName());
			return super.visit(tree);
		}

		@Override
		public MyNode visitChildren(RuleNode node) {
			log.info("visit-rule-node {}", node.getClass().getSimpleName());
			return super.visitChildren(node);
		}

		@Override
		public MyNode visitTerminal(TerminalNode node) {
			log.info("visit-terminal-node {}: token={}", node.getClass().getSimpleName(),
					DemoApplication.toString(node.getSymbol()));
			return super.visitTerminal(node);
		}

		@Override
		public MyNode visitErrorNode(ErrorNode node) {
			log.info("visit-error-node {}", node.getClass().getSimpleName());
			return super.visitErrorNode(node);
		}

		@Override
		protected MyNode aggregateResult(MyNode aggregate, MyNode nextResult) {
			if (aggregate == null) {
				return nextResult;
			} else if (nextResult != null) {
				aggregate.addSibling(nextResult);
			}
			return aggregate;
		}

	}

	public static class MyTypeVisitor extends Java9ParserBaseVisitor<MyNode> {

		@Override
		public MyNode visitNormalClassDeclaration(NormalClassDeclarationContext ctx) {
			log.info("visit NormalClassDeclarationContext: {}", ctx.identifier().getText());
			return super.visitNormalClassDeclaration(ctx);
		}

	}

	@RequiredArgsConstructor
	public static class MyMethodVisitor extends Java9ParserBaseVisitor<MyNode> {
		private final TokenStream tokenStream;

		@Override
		public MyNode visitMethodDeclaration(MethodDeclarationContext ctx) {
			log.info("visit MethodDeclarationContext: {}",
					ctx.methodHeader().methodDeclarator().identifier().getText());

			var startToken = ctx.getStart();

			var prevToken = tokenStream.get(startToken.getTokenIndex() - 1);
			if (prevToken.getType() == Java9Lexer.COMMENT) {
				var comment = prevToken.getText();
				if (comment.startsWith("/**")) {
					log.info("has Javadoc: {}", comment);
				}
			}

			return super.visitMethodDeclaration(ctx);
		}

	}

}
