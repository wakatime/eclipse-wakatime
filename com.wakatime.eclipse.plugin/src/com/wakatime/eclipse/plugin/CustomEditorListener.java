/* ==========================================================
File:        CustomEditorListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;

public class CustomEditorListener implements IPartListener2 {

    @Override
    public void partActivated(IWorkbenchPartReference partRef) {
        // WakaTime.log.debug("CustomEditorListener.partActivated");

        IEditorPart activeEditor = partRef.getPage().getActiveEditor();
        WakaTime.handleActivity(activeEditor, false);
    }

    @Override
    public void partOpened(IWorkbenchPartReference partRef) {
        // listen for caret movement in newly active editor
        IEditorPart editor = null;
        try {
            editor = ((IEditorReference) partRef).getEditor(false);
        } catch (Exception e) {
            WakaTime.log.debug(e);
            return;
        }
        if (editor == null) return;

        Control adapter = editor.getAdapter(Control.class);
        if (adapter == null) return;

        // listen for mouse clicks
        try {
            adapter.addMouseListener(new CustomMouseListener());
        } catch (Exception e) {
            WakaTime.log.error(e);
        }

        // listen for cursor movement and typing
        try {
            ((StyledText) adapter).addCaretListener(new CustomCaretListener());
        } catch (Exception e) {
            WakaTime.log.error(e);
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
