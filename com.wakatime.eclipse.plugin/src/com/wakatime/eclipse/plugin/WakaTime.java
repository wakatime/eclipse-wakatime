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
import java.math.BigDecimal;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
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
    public String IDE_NAME;
    public String ECLIPSE_VERSION;
    public boolean isBuilding = false;
    public boolean isAutoBuilding = false;
    public static WakaTime plugin;
    public ILog logInstance;
    public boolean DEBUG = false;
    public static Boolean READY = false;

    // Listeners
    private static CustomEditorListener editorListener;
    private static IExecutionListener executionListener;

    // Schedulers
    public Debouncer<Object> debouncer;
    public ScheduledExecutorService buildScheduler;
    public ScheduledExecutorService autoBuildScheduler;

    // Constants
    public static final BigDecimal FREQUENCY = new BigDecimal(2 * 60);
    public static final int BUILD_THRESHOLD = 3;
    public final String VERSION = Platform.getBundle(PLUGIN_ID).getVersion().toString();

    public String lastFile;
    public BigDecimal lastTime = new BigDecimal(0);
    public IProject lastProject;
    public boolean lastIsBuilding = false;

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
        debouncer = new Debouncer<Object>();
    }

    @Override
    public void earlyStartup() {
        final IWorkbench workbench = PlatformUI.getWorkbench();

        workbench.getDisplay().asyncExec(new Runnable() {
            public void run() {
                try {
                    IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                    if (window == null) return;

                    String debug = ConfigFile.get("settings", "debug", false);
                    WakaTime.getDefault().DEBUG = debug != null && debug.trim().equals("true");
                    Logger.debug("Initializing WakaTime plugin (https://wakatime.com) v"+VERSION);

                    // listen for file saved events
                    ICommandService commandService = workbench.getService(ICommandService.class);
                    executionListener = new CustomExecutionListener();
                    commandService.addExecutionListener(executionListener);

                    // prompt for apiKey if not set
                    String apiKey = ConfigFile.get("settings", "api_key", false);
                    if (apiKey == "") promptForApiKey(window);

                    checkCLI();

                    // log file if one is already opened on startup
                    Heartbeat heartbeat = WakaTime.getHeartbeat(null, false);
                    WakaTime.processHeartbeat(heartbeat);

                    // listen for focused file change
                    if (window.getPartService() != null) window.getPartService().addPartListener(editorListener);

                    // listen for auto-builds
                    WakaTime.setupAutoBuildWatcher();

                    Logger.debug("Finished initializing WakaTime plugin (https://wakatime.com) v"+VERSION);
                } catch (Exception e) {
                    Logger.debug(e);
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

        IWorkbench workbench = PlatformUI.getWorkbench();

        if (workbench != null) {
            ICommandService commandService = workbench.getService(ICommandService.class);
            if (commandService != null) commandService.removeExecutionListener(executionListener);

            IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
            if (window != null && window.getPartService() != null) window.getPartService().removePartListener(editorListener);
        }

        debouncer.shutdown();
    }

    public static void promptForApiKey(IWorkbenchWindow window) {
        String apiKey = ConfigFile.get("settings", "api_key", false);
        InputDialog dialog = new InputDialog(window.getShell(),
            "WakaTime API Key", "Enter your api key from http://wakatime.com/api-key", apiKey, null);
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

    private static void setupAutoBuildWatcher() {
        try {
            Job.getJobManager().addJobChangeListener(new JobChangeAdapter() {
                @Override
                public void aboutToRun(IJobChangeEvent event) {
                    if (event.getJob().belongsTo(ResourcesPlugin.FAMILY_AUTO_BUILD)) {
                        // Logger.debug("Auto-build about to run.");
                        final Heartbeat heartbeat = WakaTime.getHeartbeat(null, false, true);
                        WakaTime.getDefault().debouncer.debounce("auto-build", new Runnable() {
                            @Override public void run() {
                                // TODO: set a periodic timer to send heartbeats for long builds when user left for coffee
                                WakaTime.getDefault().isAutoBuilding = true;
                                WakaTime.processHeartbeat(heartbeat);
                                WakaTime.startWatchingAutoBuild();
                            }
                        }, WakaTime.BUILD_THRESHOLD, TimeUnit.SECONDS);
                    }
                }

                @Override
                public void done(IJobChangeEvent event) {
                    if (event.getJob().belongsTo(ResourcesPlugin.FAMILY_AUTO_BUILD)) {
                        // Logger.debug("Auto-build completed.");
                        final Heartbeat heartbeat = WakaTime.getHeartbeat(null, false, false);
                        WakaTime.getDefault().debouncer.debounce("auto-build", new Runnable() {
                            @Override public void run() {
                                WakaTime.getDefault().isAutoBuilding = false;
                                WakaTime.processHeartbeat(heartbeat);
                                WakaTime.stopWatchingAutoBuild();
                            }
                        }, 1, TimeUnit.MILLISECONDS);
                    }
                }
            });
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    public static void processHeartbeat(Heartbeat heartbeat) {
        if (heartbeat == null) return;
        if (!heartbeat.canSend()) return;

        sendHeartbeat(heartbeat);

        WakaTime.getDefault().lastFile = heartbeat.entity;
        WakaTime.getDefault().lastTime = heartbeat.timestamp;
        WakaTime.getDefault().lastIsBuilding = heartbeat.isBuilding;
    }

    private static void sendHeartbeat(Heartbeat heartbeat) {
        if (!WakaTime.READY) return;

        final String[] cmds = heartbeat.toCliCommands();

        Logger.debug(cmds.toString());

        Runnable r = new Runnable() {
            public void run() {
                try {
                     Process proc = Runtime.getRuntime().exec(cmds);
                     if (getDefault().DEBUG) {
                         BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                         BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                         proc.waitFor();
                         String s;
                         while ((s = stdInput.readLine()) != null) {
                             if (!s.trim().equals("")) Logger.debug(s);
                         }
                         while ((s = stdError.readLine()) != null) {
                             if (!s.trim().equals("")) Logger.debug(s);
                         }
                         Logger.debug("Command finished with return value: " + proc.exitValue());
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

    public static Heartbeat getHeartbeat(IEditorPart activeEditor, boolean isWrite) {
        return WakaTime.getHeartbeat(activeEditor, isWrite, false);
    }

    public static Heartbeat getHeartbeat(IEditorPart activeEditor, boolean isWrite, Boolean isBuilding) {
        if (activeEditor == null) activeEditor = getActiveEditor();
        if (activeEditor == null) return null;

        IEditorInput editorInput = activeEditor.getEditorInput();
        if (editorInput == null) return null;

        try {
            if (editorInput instanceof IURIEditorInput) {
                final URI uri = ((IURIEditorInput) editorInput).getURI();
                if (uri != null && uri.getPath() != null && !uri.getPath().trim().equals("")) {
                    return new Heartbeat(uri.getPath(), isWrite, activeEditor, false, isBuilding);
                }
            } else if (editorInput instanceof IFileEditorInput) {
                final URI uri = ((IFileEditorInput) editorInput).getFile().getLocationURI();
                if (uri != null && uri.getPath() != null && !uri.getPath().trim().equals("")) {
                    return new Heartbeat(uri.getPath(), isWrite, activeEditor, false, isBuilding);
                }
            } else if (editorInput instanceof IPathEditorInput) {
                return new Heartbeat(((IPathEditorInput) editorInput).getPath().makeAbsolute().toString(), isWrite, activeEditor, false, isBuilding);
            }

        } catch(Exception e) {
            Logger.error(e);
        }

        try {
            Class C = editorInput.getClass();
            while (C != null) {
              if (C.getName().equals("org.jkiss.dbeaver.ui.editors.DatabaseEditorInput")) {
                  try {
                        return new Heartbeat(C.getMethod("getDatabaseObject").invoke(editorInput).toString(), isWrite, activeEditor, true, isBuilding);
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

    public static BigDecimal getCurrentTimestamp() {
        return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0));
    }

    public static void startWatchingBuild() {
        if (WakaTime.getDefault().buildScheduler != null) {
            try {
                WakaTime.getDefault().buildScheduler.shutdownNow();
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
        WakaTime.getDefault().buildScheduler = Executors.newSingleThreadScheduledExecutor();
        WakaTime.getDefault().buildScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Heartbeat heartbeat = WakaTime.getHeartbeat(null, false);
                WakaTime.processHeartbeat(heartbeat);
                if (!WakaTime.getDefault().isBuilding && WakaTime.getDefault().buildScheduler != null) WakaTime.getDefault().buildScheduler.shutdownNow();
            }
        }, 90, 90, TimeUnit.SECONDS);
    }

    public static void startWatchingAutoBuild() {
        if (WakaTime.getDefault().autoBuildScheduler != null) {
            try {
                WakaTime.getDefault().autoBuildScheduler.shutdownNow();
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
        WakaTime.getDefault().autoBuildScheduler = Executors.newSingleThreadScheduledExecutor();
        WakaTime.getDefault().autoBuildScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Heartbeat heartbeat = WakaTime.getHeartbeat(null, false);
                WakaTime.processHeartbeat(heartbeat);
                if (!WakaTime.getDefault().isAutoBuilding && WakaTime.getDefault().autoBuildScheduler != null) WakaTime.getDefault().autoBuildScheduler.shutdownNow();
            }
        }, 90, 90, TimeUnit.SECONDS);
    }

    public static void stopWatchingBuild() {
        try {
            if (WakaTime.getDefault().buildScheduler != null) WakaTime.getDefault().buildScheduler.shutdownNow();
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    public static void stopWatchingAutoBuild() {
        try {
            if (WakaTime.getDefault().autoBuildScheduler != null) WakaTime.getDefault().autoBuildScheduler.shutdownNow();
        } catch (Exception e) {
            Logger.debug(e);
        }
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
