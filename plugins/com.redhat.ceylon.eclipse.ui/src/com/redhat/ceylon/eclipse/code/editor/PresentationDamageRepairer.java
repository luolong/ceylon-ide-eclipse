package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getEndOffset;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getStartOffset;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getTokenIndexAtCharacter;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getTokenIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.presentation.IPresentationDamager;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;
import com.redhat.ceylon.compiler.typechecker.parser.CeylonParser;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.parse.CeylonTokenColorer;

class PresentationDamageRepairer implements IPresentationDamager, 
        IPresentationRepairer {
	
    private final ISourceViewer sourceViewer;
    private final CeylonTokenColorer tokenColorer;
    private volatile List<CommonToken> tokens;
    
	PresentationDamageRepairer(ISourceViewer sourceViewer) {
		this.sourceViewer = sourceViewer;
		tokenColorer = new CeylonTokenColorer();
	}
	
	public IRegion getDamageRegion(ITypedRegion partition, 
			DocumentEvent event, boolean documentPartitioningChanged) {

		if (tokens==null) {
			//parse and color the whole document the first time!
			return partition;
		}
		
		if (noTextChange(event)) {
			//it was a change to annotations - don't reparse
			return new Region(event.getOffset(), 
					event.getLength());
		}
		
		int i = getTokenIndexAtCharacter(tokens, event.getOffset()-1);
		if (i<0) i=-i;
		CommonToken t = tokens.get(i);
		if (isWithinExistingToken(event, t)) {
			if (isWithinTokenChange(event, t)) {
				//the edit just changes the text inside
				//a token, leaving the rest of the
				//document structure unchanged
				return new Region(event.getOffset(), 
						event.getText().length());
			}
		}
		return partition;
	}

	public boolean isWithinExistingToken(DocumentEvent event, 
			CommonToken t) {
		return t.getStartIndex()<=event.getOffset() && 
				t.getStopIndex()>=event.getOffset()+event.getLength()-1;
	}

	public boolean isWithinTokenChange(DocumentEvent event,
			CommonToken t) {
		switch (t.getType()) {
		case CeylonLexer.WS:
			for (char c: event.getText().toCharArray()) {
				if (!Character.isWhitespace(c)) {
					return false;
				}
			}
			break;
		case CeylonLexer.UIDENTIFIER:
		case CeylonLexer.LIDENTIFIER:
			for (char c: event.getText().toCharArray()) {
				if (!Character.isJavaIdentifierPart(c)) {
					return false;
				}
			}
			break;
		case CeylonLexer.STRING_LITERAL:
        case CeylonLexer.CHAR_LITERAL:
			for (char c: event.getText().toCharArray()) {
				if (c=='"'||c=='\'') {
					return false;
				}
			}
			break;
		case CeylonLexer.MULTI_COMMENT:
			for (char c: event.getText().toCharArray()) {
				if (c=='/'||c=='*') {
					return false;
				}
			}
			break;
		case CeylonLexer.LINE_COMMENT:
			for (char c: event.getText().toCharArray()) {
				if (c=='\n'||c=='\f'||c=='\r') {
					return false;
				}
			}
			break;
		default:
			return false;
		}
		return true;
	}
	
    public boolean isWithinMetaLiteral(CommonToken token, List<Tree.MetaLiteral> metaLiterals) {
        for (Tree.MetaLiteral metaLiteral : metaLiterals) {
            if (metaLiteral.getStartIndex() != null && metaLiteral.getEndToken() != null) {
                int startIndex = metaLiteral.getStartIndex();
                int stopIndex = ((CommonToken) metaLiteral.getEndToken()).getStopIndex();
                if (startIndex <= token.getStartIndex() && stopIndex >= token.getStopIndex()) {
                    return true;
                }
            }
        }
        return false;
    }

	public void createPresentation(TextPresentation presentation, 
			ITypedRegion damage) {
	    
	    List<Tree.MetaLiteral> metaLiterals = new ArrayList<Tree.MetaLiteral>();
		
		//it sounds strange, but it's better to parse
		//and cache here than in getDamageRegion(),
		//because these methods get called in strange
		//orders
		tokens = parse(metaLiterals);
		
		//int prevStartOffset= -1;
		//int prevEndOffset= -1;
		Iterator<CommonToken> iter= getTokenIterator(tokens, damage);
		if (iter!=null) {
			while (iter.hasNext()) {
				CommonToken token= iter.next();
				if (token.getType()==CeylonLexer.EOF) {
					break;
				}
				
				int startOffset= getStartOffset(token);
				int endOffset= getEndOffset(token);
				
				//TODO: I'm not happy with how this works
				//      This approach is potentially very 
				//      slow, since it involves running 
				//      a Visitor over the parse tree and
				//      then we iterate over all the 
				//      metaliterals for every token
                if (isWithinMetaLiteral(token, metaLiterals)) {
                    changeTokenPresentation(presentation, 
                            tokenColorer.getMetaLiteralColoring(), 
                            startOffset, endOffset);
                    continue;
                }
				
                switch (token.getType()) {
                case CeylonParser.STRING_MID:
                    endOffset-=2; startOffset+=2; 
                    break;
                case CeylonParser.STRING_START:
                    endOffset-=2; 
                    break;
                case CeylonParser.STRING_END:
                    startOffset+=2; 
                    break;
                }
				/*if (startOffset <= prevEndOffset && 
						endOffset >= prevStartOffset) {
					//this case occurs when applying a
					//quick fix, and causes an error
					//from SWT if we let it through
					continue;
				}*/
				if (token.getType()==CeylonParser.STRING_MID ||
				    token.getType()==CeylonParser.STRING_END) {
                    changeTokenPresentation(presentation, 
                            tokenColorer.getInterpolationColoring(),
                            startOffset-2,startOffset-1);
				}
				changeTokenPresentation(presentation, 
						tokenColorer.getColoring(token), 
						startOffset, endOffset);
                if (token.getType()==CeylonParser.STRING_MID ||
                        token.getType()==CeylonParser.STRING_START) {
                    changeTokenPresentation(presentation, 
                            tokenColorer.getInterpolationColoring(),
                            endOffset+1,endOffset+2);
                }
				//prevStartOffset= startOffset;
				//prevEndOffset= endOffset;
			}
		}
		// The document might have changed since the presentation was computed, so
		// trim the presentation's "result window" to the current document's extent.
		// This avoids upsetting SWT, but there's still a question as to whether
		// this is really the right thing to do. i.e., this assumes that the
		// presentation will get recomputed later on, when the new document change
		// gets noticed. But will it?
		/*IDocument doc = sourceViewer.getDocument();
		int newDocLength= doc!=null ? doc.getLength() : 0;
		IRegion presExtent= presentation.getExtent();
		if (presExtent.getOffset() + presExtent.getLength() > newDocLength) {
			presentation.setResultWindow(new Region(presExtent.getOffset(), 
					newDocLength - presExtent.getOffset()));
		}*/
		sourceViewer.changeTextPresentation(presentation, true);
	}
	
    private void changeTokenPresentation(TextPresentation presentation, 
    		TextAttribute attribute, int startOffset, int endOffset) {
    	
		StyleRange styleRange= new StyleRange(startOffset, 
        		endOffset-startOffset+1,
                attribute==null ? null : attribute.getForeground(),
                attribute==null ? null : attribute.getBackground(),
                attribute==null ? SWT.NORMAL : attribute.getStyle());

        // Negative (possibly 0) length style ranges will cause an 
        // IllegalArgumentException in changeTextPresentation(..)
        /*if (styleRange.length <= 0 || 
        		styleRange.start+styleRange.length > 
                        sourceViewer.getDocument().getLength()) {
        	//do nothing
        } 
        else {*/
            presentation.addStyleRange(styleRange);
        //}
    }

    private boolean noTextChange(DocumentEvent event) {
		try {
			return sourceViewer.getDocument()
					.get(event.getOffset(),event.getLength())
					.equals(event.getText());
		} 
		catch (BadLocationException e) {
			return false;
		}
	}
	
	private List<CommonToken> parse(final List<Tree.MetaLiteral> metaLiterals) {
		String text = sourceViewer.getDocument().get();
		ANTLRStringStream input = new ANTLRStringStream(text);
        CeylonLexer lexer = new CeylonLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        
        CeylonParser parser = new CeylonParser(tokenStream);
        Tree.CompilationUnit cu;
        try {
            cu = parser.compilationUnit();
        }
        catch (RecognitionException e) {
            throw new RuntimeException(e);
        }
        
        if (cu != null) {
            cu.visit(new Visitor() {
                @Override
                public void visit(Tree.MetaLiteral metaLiteral) {
                    metaLiterals.add(metaLiteral);
                }
            });
        }
        
        return tokenStream.getTokens(); 
	}
	
    public void setDocument(IDocument document) {}
}