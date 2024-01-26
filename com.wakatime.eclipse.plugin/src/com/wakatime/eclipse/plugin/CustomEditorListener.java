/* ==========================================================
File:        CustomEditorListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class CustomEditorListener implements IPartListener2 {

    private static IDocument lastDocument = null;
    private static IDocumentListener documentListener = new CustomDocumentListener();

    @Override
    public void partActivated(IWorkbenchPartReference partRef) {
        // Logger.debug("CustomEditorListener.partActivated");

        IEditorPart activeEditor = partRef.getPage().getActiveEditor();
        Heartbeat heartbeat = WakaTime.getHeartbeat(activeEditor, false);
        WakaTime.processHeartbeat(heartbeat);
    }

    @Override
    public void partOpened(IWorkbenchPartReference partRef) {
        // listen for caret movement in newly active editor
        IEditorPart editor = null;
        try {
            editor = ((IEditorReference) partRef).getEditor(false);
        } catch (java.lang.ClassCastException e) {
            // expected to fail sometimes, ignore it
            return;
        } catch (Exception e) {
            Logger.debug(e);
            return;
        }
        if (editor == null) return;

        try {
            ITextEditor textEditor = (ITextEditor) partRef;
            IDocumentProvider provider = textEditor.getDocumentProvider();
            if (provider != null) {
                   IDocument document = provider.getDocument(textEditor);
                if (document != null) {
                    if (lastDocument != null) {
                        lastDocument.removeDocumentListener(documentListener);
                    }
                    document.addDocumentListener(documentListener);
                    lastDocument = document;
                }
            }
        } catch (java.lang.ClassCastException e) {
            // expected to fail sometimes, ignore it
            return;
        } catch (Exception e) {
            Logger.debug(e);
            return;
        }

        Control adapter = editor.getAdapter(Control.class);
        if (adapter == null) return;

        // listen for mouse clicks
        try {
            adapter.addMouseListener(new CustomMouseListener());
        } catch (Exception e) {
            Logger.debug(e);
        }

        // listen for cursor movement and typing
        try {
            ((StyledText) adapter).addCaretListener(new CustomCaretListener());
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    @Override
    public void partHidden(IWorkbenchPartReference partRef) { }

    @Override
    public void partVisible(IWorkbenchPartReference partRef) { }

    @Override
    public void partInputChanged(IWorkbenchPartReference partRef) { }

    @Override
    public void partBroughtToTop(IWorkbenchPartReference partRef) { }

    @Override
    public void partClosed(IWorkbenchPartReference partRef) { }

    @Override
    public void partDeactivated(IWorkbenchPartReference partRef) { }

}
