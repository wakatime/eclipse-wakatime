/* ==========================================================
File:        CustomExecutionListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.ui.IWorkbenchCommandConstants;

public class CustomExecutionListener implements IExecutionListener {

    private final String[] postExecCommands = new String[]{
    	IWorkbenchCommandConstants.FILE_SAVE,
        IWorkbenchCommandConstants.EDIT_COPY,
        IWorkbenchCommandConstants.EDIT_CUT,
        IWorkbenchCommandConstants.EDIT_PASTE,
        IWorkbenchCommandConstants.EDIT_UNDO,
        IWorkbenchCommandConstants.EDIT_REDO,
        IWorkbenchCommandConstants.PROJECT_BUILD_ALL,
        IWorkbenchCommandConstants.PROJECT_BUILD_PROJECT,
        "org.jkiss.dbeaver.core.object.open",
        "org.eclipse.ui.edit.text.openLocalFile",
    };

    final String[] buildCommands = new String[]{
        IWorkbenchCommandConstants.PROJECT_BUILD_ALL,
        IWorkbenchCommandConstants.PROJECT_BUILD_PROJECT,
    };

    @Override
    public void preExecute(String commandId, ExecutionEvent event) {
    	// WakaTime.log.debug("CustomExecutionListener.preExecute: " + commandId);
    	if (!Arrays.asList(buildCommands).contains(commandId)) return;

    	WakaTime.getDefault().debouncer.debounce("manual-build", new Runnable() {
    	    @Override public void run() {
                // TODO: set a periodic timer to send heartbeats for long builds when user left for coffee
    	        WakaTime.getDefault().isBuilding = true;
    	        WakaTime.handleActivity(null, false);
    	    }
    	}, 3, TimeUnit.SECONDS);
    }

    @Override
    public void postExecuteSuccess(String commandId, Object returnValue) {
        // WakaTime.log.debug("CustomExecutionListener.postExecuteSuccess: " + commandId);
        if (!Arrays.asList(postExecCommands).contains(commandId)) return;

        final boolean isWrite = commandId.equals(IWorkbenchCommandConstants.FILE_SAVE);
        
        if (Arrays.asList(buildCommands).contains(commandId)) {
        	WakaTime.getDefault().debouncer.debounce("manual-build", new Runnable() {
        	    @Override public void run() {
        	        WakaTime.getDefault().isBuilding = false;
        	        WakaTime.handleActivity(null, isWrite);
        	    }
        	}, 1, TimeUnit.MILLISECONDS);
        	return;
        }

        WakaTime.handleActivity(null, isWrite);
    }

    @Override
    public void notHandled(String commandId, NotHandledException exception) { }

    @Override
    public void postExecuteFailure(String commandId, ExecutionException exception) {
    	// WakaTime.log.debug("CustomExecutionListener.postExecuteFailure: " + commandId);
    	if (!Arrays.asList(buildCommands).contains(commandId)) return;

    	WakaTime.getDefault().debouncer.debounce("manual-build", new Runnable() {
    	    @Override public void run() {
    	        WakaTime.getDefault().isBuilding = false;
    	        WakaTime.handleActivity(null, false);
    	    }
    	}, 1, TimeUnit.MILLISECONDS);
    }
}
