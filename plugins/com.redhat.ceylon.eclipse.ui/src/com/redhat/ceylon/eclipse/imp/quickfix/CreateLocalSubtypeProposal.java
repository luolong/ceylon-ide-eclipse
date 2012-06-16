package com.redhat.ceylon.eclipse.imp.quickfix;

import static com.redhat.ceylon.eclipse.imp.outline.CeylonLabelProvider.CORRECTION;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.imp.editor.quickfix.ChangeCorrectionProposal;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Statement;
import com.redhat.ceylon.eclipse.imp.editor.Util;
import com.redhat.ceylon.eclipse.util.FindStatementVisitor;

class CreateLocalSubtypeProposal extends ChangeCorrectionProposal {
    
    final int offset;
    final int length;
    final IFile file;
    
    CreateLocalSubtypeProposal(ProducedType type, int offset, int length, 
            IFile file, TextChange change) {
        super("Create subtype of '" + type.getProducedTypeName() + "'", 
                change, 10, CORRECTION);
        this.offset=offset;
        this.length=length;
        this.file=file;
    }
    
    @Override
    public void apply(IDocument document) {
        super.apply(document);
        Util.gotoLocation(file, offset, length);
    }

    static void addCreateLocalSubtypeProposal(IDocument doc, Tree.CompilationUnit cu,
            Collection<ICompletionProposal> proposals, IFile file,
            Node node) {
        FindStatementVisitor fsv = new FindStatementVisitor(node, true);
        fsv.visit(cu);
        Statement s = fsv.getStatement();
        if (s!=null) {
            ProducedType type = CreateSubtypeProposal.getType(cu, node);
            if (type!=null &&
                    (type.getDeclaration() instanceof ClassOrInterface ||
                     type.getDeclaration() instanceof IntersectionType) &&
                     type.getDeclaration().isExtendable()) {
                TextChange change = new DocumentChange("Create Subtype", doc);
                change.setEdit(new MultiTextEdit());
                Integer offset = s.getStartIndex();
                String name = type.getDeclaration().getName().replace("&", "").replace("<", "").replace(">", "");
                String dec = CreateSubtypeProposal.subtypeDeclaration(type, false)
                        .replace("$className", "My" + name) + "\n\n";
                change.addEdit(new InsertEdit(offset,dec));
                proposals.add(new CreateLocalSubtypeProposal(type, 
                        offset+6, name.length()+2, file, change));
            }
        }
    }
    
}