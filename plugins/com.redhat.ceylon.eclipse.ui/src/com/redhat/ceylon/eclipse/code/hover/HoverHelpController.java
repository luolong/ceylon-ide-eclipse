package com.redhat.ceylon.eclipse.code.hover;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.parser.IModelListener;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.utils.AnnotationUtils;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.information.IInformationProviderExtension2;
import org.eclipse.jface.text.source.ISourceViewer;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class HoverHelpController implements ITextHover, ITextHoverExtension, 
        ITextHoverExtension2, IModelListener {
	
    private IParseController controller;
    private HoverHelper hoverHelper;
    private BestMatchHover fHover;

    public HoverHelpController(CeylonEditor editor) {
        hoverHelper= new HoverHelper();
        fHover = new BestMatchHover(editor);
    }

    public AnalysisRequired getAnalysisRequired() {
        return AnalysisRequired.NAME_ANALYSIS;
    }

    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        return new Region(offset, 0);
    }

    public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
        try {
            final int offset= hoverRegion.getOffset();
            String help= null;
            if (controller!=null && hoverHelper!=null)
                help= hoverHelper.getHoverHelpAt(controller, (ISourceViewer) textViewer, offset);
            if (help == null)
                help= AnnotationUtils.formatAnnotationList(AnnotationUtils.getAnnotationsForOffset((ISourceViewer) textViewer, offset));
            return help;
        } 
        catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
    
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		if (fHover instanceof ITextHoverExtension2) {
			Object info = ((ITextHoverExtension2) fHover).getHoverInfo2(
					textViewer, hoverRegion);
			return info == null ? getHoverInfo(textViewer, hoverRegion) : info;
		} else
			return fHover.getHoverInfo(textViewer, hoverRegion);
	}
	
    public void update(IParseController controller, IProgressMonitor monitor) {
        this.controller= controller;
    }
    
	public IInformationControlCreator getHoverControlCreator() {
		if (fHover instanceof ITextHoverExtension)
			return ((ITextHoverExtension)fHover).getHoverControlCreator();

		return null;
	}

	public IInformationControlCreator getInformationPresenterControlCreator() {
		if (fHover instanceof IInformationProviderExtension2) // this is wrong, but left here for backwards compatibility
			return ((IInformationProviderExtension2) fHover).getInformationPresenterControlCreator();

		return null;
	}
}