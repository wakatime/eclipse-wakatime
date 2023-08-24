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

import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class WakaTime extends AbstractUIPlugin implements IStartup {

    // The plug-in ID
    public static final String PLUGIN_ID = "com.wakatime.eclipse.plugin";

    // The shared instance
    public static WakaTime plugin;
    public static ILog logInstance;
    public String IDE_NAME;
    public String ECLIPSE_VERSION;
    public static boolean DEBUG = false;
    public static Boolean READY = false;
    public static final Logger log = new Logger();

    // Listeners
    private static CustomEditorListener editorListener;
    private static IExecutionListener executionListener;

    // Constants
    public static final long FREQUENCY = 2; // frequency of heartbeats in minutes
    public final String VERSION = Platform.getBundle(PLUGIN_ID).getVersion().toString();

    public String lastFile;
    public long lastTime = 0;
    public IProject lastProject;

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

        super.start(context);
        plugin = this;

        // discover app name and version
        try {
            ECLIPSE_VERSION = Platform.getBundle("org.eclipse.platform").getVersion().toString();
            IDE_NAME = "eclipse";
        } catch (Exception e) {
            try {
                ECLIPSE_VERSION = Platform.getBundle("org.jkiss.dbeaver.core").getVersion().toString();
                IDE_NAME = "dbeaver";
            } catch  (Exception e2) {
                try {
                    ECLIPSE_VERSION = Platform.getProduct().getDefiningBundle().getVersion().toString();
                    IDE_NAME = Platform.getProduct().getName();
                } catch  (Exception e3) {
                    ECLIPSE_VERSION = "unknown";
                    IDE_NAME = "eclipse";
                }
            }
        }
        Logger.debug("Detected " + IDE_NAME + " version: " + ECLIPSE_VERSION);

        editorListener = new CustomEditorListener();
    }

    @Override
    public void earlyStartup() {
        final IWorkbench workbench = PlatformUI.getWorkbench();

        workbench.getDisplay().asyncExec(new Runnable() {
            public void run() {
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                if (window == null) return;

                // listen for file saved events
                ICommandService commandService = (ICommandService) workbench.getService(ICommandService.class);
                executionListener = new CustomExecutionListener();
                commandService.addExecutionListener(executionListener);

                String debug = ConfigFile.get("settings", "debug", false);
                DEBUG = debug != null && debug.trim().equals("true");
                Logger.debug("Initializing WakaTime plugin (https://wakatime.com) v"+VERSION);

                // prompt for apiKey if not set
                String apiKey = ConfigFile.get("settings", "api_key", false);
                if (apiKey == "") promptForApiKey(window);

                checkCLI();

                // log file if one is already opened on startup
                WakaTime.handleActivity(null, false);

                // listen for focused file change
                if (window.getPartService() != null) window.getPartService().addPartListener(editorListener);

                Logger.debug("Finished initializing WakaTime plugin (https://wakatime.com) v"+VERSION);
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

    public static void promptForApiKey(IWorkbenchWindow window) {
        String apiKey = ConfigFile.get("settings", "api_key", false);
        InputDialog dialog = new InputDialog(window.getShell(),
            "WakaTime API Key", "Please enter your api key from http://wakatime.com/settings#apikey", apiKey, null);
        if (dialog.open() == IStatus.OK) {
          apiKey = dialog.getValue();
          ConfigFile.set("settings", "api_key", false, apiKey);
        }
    }

    private void checkCLI() {
        if (!Dependencies.isCLIInstalled()) {
            Logger.debug("Downloading and installing wakatime-cli...");
            Dependencies.installCLI();
            WakaTime.READY = true;
            Logger.debug("Finished downloading and installing wakatime-cli.");
        } else if (Dependencies.isCLIOld()) {
            if (System.getenv("WAKATIME_CLI_LOCATION") != null && !System.getenv("WAKATIME_CLI_LOCATION").trim().isEmpty()) {
                File wakatimeCLI = new File(System.getenv("WAKATIME_CLI_LOCATION"));
                if (wakatimeCLI.exists()) {
                    Logger.error("$WAKATIME_CLI_LOCATION is out of date, please update it.");
                }
            } else {
                Logger.debug("Upgrading wakatime-cli ...");
                Dependencies.installCLI();
                WakaTime.READY = true;
                Logger.debug("Finished upgrading wakatime-cli.");
            }
        } else {
            WakaTime.READY = true;
            Logger.debug("wakatime-cli is up to date.");
        }
        Dependencies.createSymlink(Dependencies.combinePaths(Dependencies.getResourcesLocation(), "wakatime-cli"), Dependencies.getCLILocation());
        Logger.debug("wakatime-cli location: " + Dependencies.getCLILocation());
    }

    public static void handleActivity(IEditorPart activeEditor, boolean isWrite) {
        if (activeEditor == null) activeEditor = getActiveEditor();
        if (activeEditor == null) return;

        Heartbeat heartbeat = WakaTime.getHeartbeat(activeEditor, isWrite);
        if (heartbeat == null) return;

        if (heartbeat.canSend()) {
            sendHeartbeat(heartbeat);
            WakaTime.getDefault().lastFile = heartbeat.entity;
            WakaTime.getDefault().lastTime = heartbeat.timestamp;
        }
    }

    private static void sendHeartbeat(Heartbeat heartbeat) {
        if (!WakaTime.READY) return;

        final String[] cmds = heartbeat.toCliCommands();

        Logger.debug(cmds.toString());

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
                             Logger.debug(s);
                         }
                         while ((s = stdError.readLine()) != null) {
                             Logger.debug(s);
                         }
                         Logger.debug("Command finished with return value: "+proc.exitValue());
                     }
                 } catch (Exception e) {
                     Logger.error(e);
                 }
             }
         };
         new Thread(r).start();
    }

    private static IEditorPart getActiveEditor() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null) return null;

        final IPartService partService = window.getPartService();
        if (partService == null) return null;

        final IWorkbenchPart part = partService.getActivePart();
        if (part == null) return null;

        final IWorkbenchPartSite site = part.getSite();
        if (site == null) return null;

        final IWorkbenchPage page = site.getPage();
        if (page == null) return null;

        return page.getActiveEditor();
    }

    private static Heartbeat getHeartbeat(IEditorPart activeEditor, Boolean isWrite) {
        if (activeEditor == null) return null;

        IEditorInput editorInput = activeEditor.getEditorInput();
        if (editorInput == null) return null;

        try {
            if (editorInput instanceof IURIEditorInput) {
                final URI uri = ((IURIEditorInput) editorInput).getURI();
                if (uri != null && uri.getPath() != null && !uri.getPath().trim().equals("")) {
                    return new Heartbeat(uri.getPath(), isWrite, activeEditor, false);
                }
            } else if (editorInput instanceof IFileEditorInput) {
                final URI uri = ((IFileEditorInput) editorInput).getFile().getLocationURI();
                if (uri != null && uri.getPath() != null && !uri.getPath().trim().equals("")) {
                    return new Heartbeat(uri.getPath(), isWrite, activeEditor, false);
                }
            } else if (editorInput instanceof IPathEditorInput) {
                return new Heartbeat(((IPathEditorInput) editorInput).getPath().makeAbsolute().toString(), isWrite, activeEditor, false);
            }

        } catch(Exception e) {
            Logger.error(e);
        }

        try {
            Class C = editorInput.getClass();
            while (C != null) {
              if (C.getName().equals("org.jkiss.dbeaver.ui.editors.DatabaseEditorInput")) {
                  try {
                        return new Heartbeat(C.getMethod("getDatabaseObject").invoke(editorInput).toString(), isWrite, activeEditor, true);
                  } catch (Exception e) {
                      Logger.error(e);
                    }
              }
              C = C.getSuperclass();
            }

        } catch(Exception e) {
            Logger.error(e);
        }

        return null;
    }

    private static boolean isInstanceOf(Object o, String classPath) {
        @SuppressWarnings("rawtypes")
        Class C = o.getClass();
        while (C != null) {
            // Logger.debug(C.getName());
            if (C.getName().equals(classPath)) return true;
            /*for (Method method : C.getDeclaredMethods()) {
                Logger.debug(method.getName());
            }*/
            C = C.getSuperclass();
        }
        return false;
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
