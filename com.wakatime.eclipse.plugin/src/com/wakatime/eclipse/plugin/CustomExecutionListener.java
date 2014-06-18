/* ==========================================================
File:        CustomExecutionListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.net.URI;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class CustomExecutionListener implements IExecutionListener {

    @Override
    public void notHandled(String commandId, NotHandledException exception) {
        // TODO Auto-generated method stub

    }

    @Override
    public void postExecuteFailure(String commandId,
            ExecutionException exception) {
        // TODO Auto-generated method stub

    }

    @Override
    public void postExecuteSuccess(String commandId, Object returnValue) {
        if (commandId.equals("org.eclipse.ui.file.save")) {
            IWorkbench workbench = PlatformUI.getWorkbench();
            if (workbench == null) return;
            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
            if (window == null) return;

            if (window.getPartService() == null) return;
            if (window.getPartService().getActivePart() == null) return;
            if (window.getPartService().getActivePart().getSite() == null) return;
            if (window.getPartService().getActivePart().getSite().getPage() == null) return;
            if (window.getPartService().getActivePart().getSite().getPage().getActiveEditor() == null) return;
            if (window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput() == null) return;

            // log file save event
            IEditorInput input = window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput();
            if (input instanceof IURIEditorInput) {
                URI uri = ((IURIEditorInput)input).getURI();
                if (uri != null && uri.getPath() != null) {
                    String currentFile = uri.getPath();
                    long currentTime = System.currentTimeMillis() / 1000;

                    // always log writes
                    WakaTime.logFile(currentFile, WakaTime.getActiveProject(), true);
                    WakaTime.getDefault().lastFile = currentFile;
                    WakaTime.getDefault().lastTime = currentTime;
                }
            }
        }
    }

    @Override
    public void preExecute(String commandId, ExecutionEvent event) {
        // TODO Auto-generated method stub

    }

}
