/* ==========================================================
File:        WakaTime.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class WakaTime extends AbstractUIPlugin implements IStartup {

    // The plug-in ID
    public static final String PLUGIN_ID = "com.wakatime.eclipse.plugin";

    // The shared instance
    private static WakaTime plugin;
    private static ILog logInstance;
    private static boolean DEBUG = false;

    // Listeners
    private static CustomEditorListener editorListener;
    private static IExecutionListener executionListener;

    // Constants
    public static final long FREQUENCY = 2; // frequency of heartbeats in minutes
    public static final String CONFIG = ".wakatime.cfg";
    public static final String VERSION = Platform.getBundle(PLUGIN_ID).getVersion().toString();
    public static final String ECLIPSE_VERSION = Platform.getBundle("org.eclipse.platform").getVersion().toString();


    public String lastFile;
    public long lastTime = 0;

    /**
     * The constructor
     */
    public WakaTime() {
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    public void start(BundleContext context) throws Exception {
        logInstance = getLog();
        WakaTime.log("Initializing WakaTime plugin (https://wakatime.com) v"+VERSION);

        super.start(context);
        plugin = this;

        editorListener = new CustomEditorListener();
    }

    @Override
    public void earlyStartup() {
        final IWorkbench workbench = PlatformUI.getWorkbench();

        // listen for save file events
        ICommandService commandService = (ICommandService) workbench.getService(ICommandService.class);
        executionListener = new CustomExecutionListener();
        commandService.addExecutionListener(executionListener);

        workbench.getDisplay().asyncExec(new Runnable() {
            public void run() {
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                if (window == null) return;

                // setup config file parsing
                MenuHandler handler = new MenuHandler();
                DEBUG = handler.getDebug();

                // prompt for apiKey if not set
                String apiKey = handler.getApiKey();
                if (apiKey == "") {
                    handler.promptForApiKey(window);
                }

                Dependencies deps = new Dependencies();

                if (!deps.isPythonInstalled()) {
                    deps.installPython();
                    if (!deps.isPythonInstalled()) {
                        MessageDialog dialog = new MessageDialog(window.getShell(),
                            "Warning!", null,
                            "WakaTime needs Python installed. Please install Python from python.org/downloads, then restart Eclipse.",
                            MessageDialog.WARNING, new String[]{IDialogConstants.OK_LABEL}, 0);
                        dialog.open();
                    }
                }
                if (!deps.isCLIInstalled()) {
                    deps.installCLI();
                }

                if (window.getPartService() == null) return;

                // listen for caret movement
                if (window.getPartService().getActivePartReference() != null &&
                    window.getPartService().getActivePartReference().getPage() != null &&
                    window.getPartService().getActivePartReference().getPage().getActiveEditor() != null
                ) {
                    IEditorPart part = window.getPartService().getActivePartReference().getPage().getActiveEditor();
                    if (!(part instanceof AbstractTextEditor))
                        return;
                    ((StyledText)part.getAdapter(Control.class)).addCaretListener(new CustomCaretListener());
                }

                // listen for change of active file
                window.getPartService().addPartListener(editorListener);

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

                WakaTime.log("Finished initializing WakaTime plugin (https://wakatime.com) v"+VERSION);
            }
        });
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null && window.getPartService() != null)
            window.getPartService().removePartListener(editorListener);
    }

    public static void logFile(String file, String project, boolean isWrite) {
        final String[] cmds = buildCliCommands(file, project, isWrite);

        if (DEBUG)
            WakaTime.log(cmds.toString());

        Runnable r = new Runnable() {
            public void run() {
                try {
                     Process proc = Runtime.getRuntime().exec(cmds);
                     if (DEBUG) {
	                     BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	                     BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
	                     proc.waitFor();
	                     String s;
	                     while ((s = stdInput.readLine()) != null) {
	                         WakaTime.log(s);
	                     }
	                     while ((s = stdError.readLine()) != null) {
	                         WakaTime.log(s);
	                     }
	                     WakaTime.log("Command finished with return value: "+proc.exitValue());
                     }
                 } catch (Exception e) {
                     WakaTime.error("Error", e);
                 }
             }
         };
         new Thread(r).start();
    }

    public static String[] buildCliCommands(String file, String project,  boolean isWrite) {
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getPythonLocation());
        cmds.add(WakaTime.getWakaTimeCLI());
        cmds.add("--file");
        cmds.add(WakaTime.fixFilePath(file));
        cmds.add("--plugin");
        cmds.add("eclipse/"+ECLIPSE_VERSION+" eclipse-wakatime/"+VERSION);
        if (project != null) {
            cmds.add("--project");
            cmds.add(project);
        }
        if (isWrite)
            cmds.add("--write");
        if (DEBUG) {
            WakaTime.log(cmds.toString());
            cmds.add("--verbose");
        }
        return cmds.toArray(new String[cmds.size()]);
    }

    public static String getActiveProject() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null) return null;
        if (window.getPartService() == null) return null;
        if (window.getPartService().getActivePart() == null) return null;
        if (window.getPartService().getActivePart().getSite() == null) return null;
        if (window.getPartService().getActivePart().getSite().getPage() == null) return null;
        if (window.getPartService().getActivePart().getSite().getPage().getActiveEditor() == null) return null;
        if (window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput() == null) return null;

        IEditorInput input = window.getPartService().getActivePart().getSite().getPage().getActiveEditor().getEditorInput();

        IProject project = null;

        if (input instanceof FileEditorInput) {
            project = ((FileEditorInput)input).getFile().getProject();
        }

        if (project == null)
            return null;

        return project.getName();
    }

    public static String fixFilePath(String file) {
        return file.replaceFirst("^[\\\\/]([A-Z]:[\\\\/])", "$1");
    }

    public static String getWakaTimeCLI() {
        Bundle bundle = Platform.getBundle("com.wakatime.eclipse.plugin");
        URL url = bundle.getEntry("/");
        URL rootURL = null;
        try {
            rootURL = FileLocator.toFileURL(url);
        } catch (Exception e) {
            WakaTime.error("Error", e);
        }
        if (rootURL == null)
            return null;
        File script = new File(Dependencies.combinePaths(rootURL.getPath(), "dependencies", "wakatime-master", "wakatime", "cli.py"));
        return script.getAbsolutePath();
    }

    public static void log(String msg) {
        WakaTime.logMessage(msg, Status.INFO, null);
    }

    public static void error(String msg) {
        WakaTime.logMessage(msg, Status.ERROR, null);
    }

    public static void error(String msg, Exception e) {
        WakaTime.logMessage(msg, Status.ERROR, e);
    }

    public static void logMessage(String msg, int level, Exception e) {
        if (logInstance != null)
            logInstance.log(new Status(level, PLUGIN_ID, Status.OK, msg, e));
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static WakaTime getDefault() {
        return plugin;
    }

    /**
     * Returns an image descriptor for the image file at the given
     * plug-in relative path
     *
     * @param path the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }
}
