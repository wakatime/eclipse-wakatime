/* ==========================================================
File:        ApiKeyDialog.java
Description: Prompts for the api key without showing it on screen.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

public class ApiKeyDialog extends InputDialog {

    public ApiKeyDialog(Shell parentShell, String apiKey) {
        super(parentShell, "WakaTime API Key", "Enter your api key from http://wakatime.com/api-key", apiKey, null);
    }

    @Override
    protected int getInputTextStyle() {
        return super.getInputTextStyle() | SWT.PASSWORD;
    }

}
