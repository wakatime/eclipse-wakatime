/* ==========================================================
File:        CustomCaretListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.net.URI;

import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class CustomCaretListener implements CaretListener {

    @Override
    public void caretMoved(CaretEvent event) {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null) return;
        if (window.getPartService() == null) return;
        if (window.getPartService().getActivePart() == null) return;
        if (window.getPartService().getActivePart().getSite() == null) return;
        if (window.getPartService().getActivePart().getSite().getPage() == null) return;
        if (window.getPartService().getActivePart().getSite().getPage().getActiveEditor() == null) return;
        if (window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput() == null) return;

        // log file if one is opened by default
        IEditorInput input = window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput();
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

}
