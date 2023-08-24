/* ==========================================================
File:        CustomExecutionListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.util.Arrays;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;

public class CustomExecutionListener implements IExecutionListener {

    @Override
    public void postExecuteSuccess(String commandId, Object returnValue) {
        // WakaTime.log.debug("CustomExecutionListener.postExecuteSuccess: " + commandId);

        final String[] supportedCommands = new String[]{
            "org.eclipse.ui.file.save",
            "org.jkiss.dbeaver.core.object.open",
            "org.eclipse.ui.edit.text.openLocalFile",
            "org.eclipse.ui.edit.copy",
            "org.eclipse.ui.edit.cut",
            "org.eclipse.ui.edit.paste",
            "org.eclipse.ui.edit.undo",
            "org.eclipse.ui.edit.redo",
        };
        if (!Arrays.asList(supportedCommands).contains(commandId)) return;

        boolean isWrite = commandId.equals("org.eclipse.ui.file.save");
        WakaTime.handleActivity(null, isWrite);
    }

    @Override
    public void notHandled(String commandId, NotHandledException exception) { }

    @Override
    public void postExecuteFailure(String commandId, ExecutionException exception) { }

    @Override
    public void preExecute(String commandId, ExecutionEvent event) { }

}
