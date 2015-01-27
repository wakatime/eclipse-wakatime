/* ==========================================================
File:        MenuHandler.java
Description: Prompts user for api key if it does not exist.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.jface.dialogs.InputDialog;

public class MenuHandler extends AbstractHandler {
    /**
     * The constructor.
     */
    public MenuHandler() {
    }

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        this.promptForApiKey(window);
        return null;
    }

    public void promptForApiKey(IWorkbenchWindow window) {
        String apiKey = this.getApiKey();
        InputDialog dialog = new InputDialog(window.getShell(),
            "WakaTime API Key", "Please enter your api key from http://wakatime.com/settings#apikey", apiKey, null);
        if (dialog.open() == IStatus.OK) {
          apiKey = dialog.getValue();
          this.setApiKey(apiKey);
        }
    }

    public String getApiKey() {
        String apiKey = "";
        String setting = getSetting("api_key");
        if (setting != null)
        	apiKey = setting;
        if (apiKey.equals("")) {
	        String secondSetting = getSetting("apikey");
	        if (secondSetting != null)
	        	apiKey = secondSetting;
        }
        return apiKey;
    }

    private void setApiKey(String apiKey) {
        File userHome = new File(System.getProperty("user.home"));
        File configFile = new File(userHome, WakaTime.CONFIG);
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        try {
            br = new BufferedReader(new FileReader(configFile.getAbsolutePath()));
        } catch (FileNotFoundException e1) {
        }
        if (br != null) {
            try {
                String line = br.readLine();
                while (line != null) {
                    String[] parts = line.split("=");
                    if (parts.length == 2 && parts[0].trim().equals("api_key")) {
                        found = true;
                        sb.append("api_key = "+apiKey+"\n");
                    } else {
                        sb.append(line+"\n");
                    }
                    line = br.readLine();
                }
            } catch (IOException e) {
                WakaTime.error("Error", e);
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    WakaTime.error("Error", e);
                }
            }
        }
        if (!found) {
            sb = new StringBuilder();
            sb.append("[settings]\n");
            sb.append("api_key = "+apiKey+"\n");
            sb.append("debug = false\n");
        }
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(configFile.getAbsolutePath(), "UTF-8");
        } catch (FileNotFoundException e) {
            WakaTime.error("Error", e);
        } catch (UnsupportedEncodingException e) {
            WakaTime.error("Error", e);
        }
        if (writer != null) {
            writer.print(sb.toString());
            writer.close();
        }
    }

    public boolean getDebug() {
        boolean debug = false;
        String debugSetting = getSetting("debug");
        if (debugSetting != null && debugSetting.equals("true"))
        	debug = true;
        return debug;
    }

    public String getSetting(String name) {
        String value = null;
        File userHome = new File(System.getProperty("user.home"));
        File configFile = new File(userHome, WakaTime.CONFIG);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(configFile.getAbsolutePath()));
        } catch (FileNotFoundException e1) {
        }
        if (br != null) {
            try {
                String line = br.readLine();
                while (line != null) {
                    String[] parts = line.split("=");
                    if (parts.length == 2 && parts[0].trim().equals(name)) {
                        value = parts[1].trim();
                    }
                    line = br.readLine();
                }
            } catch (IOException e) {
                WakaTime.error("Error", e);
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    WakaTime.error("Error", e);
                }
            }
        }
        return value;
    }
}
