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
        // Logger.debug("CustomExecutionListener.preExecute: " + commandId);
        if (!Arrays.asList(buildCommands).contains(commandId)) return;

        final Heartbeat heartbeat = WakaTime.getHeartbeat(null, false, true);
        WakaTime.getDefault().debouncer.debounce("manual-build", new Runnable() {
            @Override public void run() {
                // TODO: set a periodic timer to send heartbeats for long builds when user left for coffee
                WakaTime.getDefault().isBuilding = true;
                WakaTime.processHeartbeat(heartbeat);
                WakaTime.startWatchingBuild();
            }
        }, WakaTime.BUILD_THRESHOLD, TimeUnit.SECONDS);
    }

    @Override
    public void postExecuteSuccess(String commandId, Object returnValue) {
        // Logger.debug("CustomExecutionListener.postExecuteSuccess: " + commandId);
        if (!Arrays.asList(postExecCommands).contains(commandId)) return;

        final boolean isWrite = commandId.equals(IWorkbenchCommandConstants.FILE_SAVE);

        if (Arrays.asList(buildCommands).contains(commandId)) {
            final Heartbeat heartbeat = WakaTime.getHeartbeat(null, isWrite, false);
            WakaTime.getDefault().debouncer.debounce("manual-build", new Runnable() {
                @Override public void run() {
                    WakaTime.getDefault().isBuilding = false;
                    WakaTime.processHeartbeat(heartbeat);
                    WakaTime.stopWatchingBuild();
                }
            }, 1, TimeUnit.MILLISECONDS);
            return;
        }

        Heartbeat heartbeat = WakaTime.getHeartbeat(null, isWrite);
        WakaTime.processHeartbeat(heartbeat);
    }

    @Override
    public void notHandled(String commandId, NotHandledException exception) { }

    @Override
    public void postExecuteFailure(String commandId, ExecutionException exception) {
        // Logger.debug("CustomExecutionListener.postExecuteFailure: " + commandId);
        if (!Arrays.asList(buildCommands).contains(commandId)) return;

        final Heartbeat heartbeat = WakaTime.getHeartbeat(null, false, false);
        WakaTime.getDefault().debouncer.debounce("manual-build", new Runnable() {
            @Override public void run() {
                WakaTime.getDefault().isBuilding = false;
                WakaTime.processHeartbeat(heartbeat);
                WakaTime.stopWatchingBuild();
            }
        }, 1, TimeUnit.MILLISECONDS);
    }
}
