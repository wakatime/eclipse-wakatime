/* ==========================================================
File:        CustomEditorListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.net.URI;


import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.IURIEditorInput;

public class CustomEditorListener implements IPartListener2 {

    @Override
    public void partActivated(IWorkbenchPartReference partRef) {
        IEditorPart part = partRef.getPage().getActiveEditor();
        if (!(part instanceof AbstractTextEditor))
            return;

        // log new active file
        IEditorInput input = part.getEditorInput();
        if (input instanceof IURIEditorInput) {
            URI uri = ((IURIEditorInput)input).getURI();
            if (uri != null && uri.getPath() != null) {
                String currentFile = uri.getPath();
                long currentTime = System.currentTimeMillis() / 1000;
                if (!currentFile.equals(WakaTime.getDefault().lastFile) || WakaTime.getDefault().lastTime + WakaTime.FREQUENCY * 60 < currentTime) {
                    WakaTime.logFile(currentFile, WakaTime.getActiveProject(), false);
                    WakaTime.getDefault().lastFile = currentFile;
                    WakaTime.getDefault().lastTime = currentTime;
                }
            }
        }
    }

    @Override
    public void partBroughtToTop(IWorkbenchPartReference partRef) {
        // TODO Auto-generated method stub

    }

    @Override
    public void partClosed(IWorkbenchPartReference partRef) {
        // TODO Auto-generated method stub

    }

    @Override
    public void partDeactivated(IWorkbenchPartReference partRef) {
        // TODO Auto-generated method stub

    }

    @Override
    public void partOpened(IWorkbenchPartReference partRef) {

        // listen for caret movement
        try {
            AbstractTextEditor e = (AbstractTextEditor)((IEditorReference) partRef).getEditor(false);
            ((StyledText)e.getAdapter(Control.class)).addCaretListener(new CustomCaretListener());
        } catch (Exception e) {
        }
    }

    @Override
    public void partHidden(IWorkbenchPartReference partRef) {
        // TODO Auto-generated method stub

    }

    @Override
    public void partVisible(IWorkbenchPartReference partRef) {
        // TODO Auto-generated method stub

    }

    @Override
    public void partInputChanged(IWorkbenchPartReference partRef) {
        // TODO Auto-generated method stub

    }

}
