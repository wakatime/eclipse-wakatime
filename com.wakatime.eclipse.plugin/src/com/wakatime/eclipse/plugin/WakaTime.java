/* ==========================================================
File:        WakaTime.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
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

    // Listeners
    private static CustomEditorListener editorListener;
    private static IExecutionListener executionListener;

    // Constants
    public static final long FREQUENCY = 2; // frequency of pings in minutes
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
        System.out.println("Initializing WakaTime plugin (https://wakatime.com) v"+VERSION);
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

                // prompt for apiKey if not set
                MenuHandler handler = new MenuHandler();
                String apiKey = handler.getApiKey();
                if (apiKey == "") {
                    handler.promptForApiKey(window);
                }

                Dependencies deps = new Dependencies();
                if (!deps.isPythonInstalled()) {
                    MessageDialog dialog = new MessageDialog(window.getShell(),
                        "Warning!", null,
                        "WakaTime needs Python installed. Please install Python, then restart Eclipse.",
                        MessageDialog.WARNING, new String[]{IDialogConstants.OK_LABEL}, 0);
                    dialog.open();
                }
                if (!deps.areDependenciesInstalled()) {
                    deps.installDependencies();
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
                            WakaTime.logFile(currentFile, false);
                            WakaTime.getDefault().lastFile = currentFile;
                            WakaTime.getDefault().lastTime = currentTime;
                        }
                    }
                }
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

    public static void logFile(String file, boolean isWrite) {
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add("python");
        cmds.add(WakaTime.getWakaTimeCLI());
        cmds.add("--file");
        cmds.add(WakaTime.fixFilePath(file));
        cmds.add("--plugin");
        cmds.add("eclipse/"+ECLIPSE_VERSION+" eclipse-wakatime/"+VERSION);
        if (isWrite)
            cmds.add("--write");
        try {
            Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            e.printStackTrace();
        }
        if (rootURL == null)
            return null;
        File rootDir = new File(rootURL.getPath());
        File script = new File(rootDir, "dependencies"+File.separator+"wakatime-master"+File.separator+"wakatime-cli.py");
        return script.getAbsolutePath();
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
