package com.wakatime.eclipse.plugin;

import java.net.URI;
import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class Heartbeat {
    public String entity;
    public String entityType;
    public long timestamp;
    public Boolean isWrite;
    public IProject project;
    public Integer lineCount;
    public Integer lineNumber;
    public Integer cursorPosition;
    public String alternateLanguage;
    public Boolean isBuilding;
    public Boolean isUnsavedFile;

    public Heartbeat(String entity, Boolean isWrite, IEditorPart activeEditor, Boolean isDatabase) {
        super();

        this.entity = entity;
        this.entityType = "file";
        this.timestamp = System.currentTimeMillis() / 1000;
        this.isWrite = isWrite;
        this.setProject(activeEditor);
        this.isBuilding = false;
        this.isUnsavedFile = isDatabase;
        if (!isDatabase) {
            this.fixFilePath();
            this.setFileMetadata(activeEditor);
        }
    }

    public void setFileMetadata(IEditorPart activeEditor) {
        ITextEditor editor = (ITextEditor) activeEditor.getAdapter(ITextEditor.class);
        if (editor == null) return;

        IDocumentProvider docProvider = editor.getDocumentProvider();
        if (docProvider == null) return;

        IEditorInput input = activeEditor.getEditorInput();
        if (input == null) return;

        IDocument document = docProvider.getDocument(input);
        if (document == null) return;

        this.lineCount = document.getNumberOfLines();

        IWorkbenchPartSite site = activeEditor.getSite();
        if (site == null) return;

        ISelectionProvider selectionProvider = site.getSelectionProvider();
        if (selectionProvider == null) return;

        ITextSelection selection = (ITextSelection) selectionProvider.getSelection();
        if (selection == null) return;

        int line = selection.getStartLine();
        if (line < 0) return;

        int offset = selection.getOffset();
        if (offset < 0) return;

        try {
            int cursor = offset - document.getLineOffset(line);
            if (cursor < 0) return;

            this.cursorPosition = cursor + 1;
            this.lineNumber = line + 1;

        } catch (BadLocationException e) {
            WakaTime.log.debug(e);
        }
    }

    public boolean canSend() {
        if (this.isWrite) return true;

        if (WakaTime.getDefault().lastTime + WakaTime.FREQUENCY * 60 < this.timestamp) return true;

        return !this.entity.equals(WakaTime.getDefault().lastFile);
    }

    public String[] toCliCommands() {
        ArrayList<String> cmds = new ArrayList<String>();
        cmds.add(Dependencies.getCLILocation());
        cmds.add("--entity");
        cmds.add(this.entity);
        cmds.add("--plugin");
        cmds.add(WakaTime.getDefault().IDE_NAME + "/" + WakaTime.getDefault().ECLIPSE_VERSION + " eclipse-wakatime/" + WakaTime.getDefault().VERSION);
        if (this.project != null && this.project.getName() != null) {
            cmds.add("--project");
            cmds.add(this.project.getName());
        }
        if (this.isWrite)
            cmds.add("--write");
        if (this.isUnsavedFile) {
            cmds.add("--is-unsaved-entity");
        }
        String projectFolder = this.getProjectFolder();
        if (projectFolder != null) {
            cmds.add("--project-folder");
            cmds.add(projectFolder);
        }
        if (this.isBuilding) {
            cmds.add("--category");
            cmds.add("building");
        }
        if (this.alternateLanguage != null) {
            cmds.add("--alternate-language");
            cmds.add(this.alternateLanguage);
        }
        if (this.cursorPosition != null) {
            cmds.add("--cursorpos");
            cmds.add(Integer.toString(this.cursorPosition));
        }
        if (this.lineNumber != null) {
            cmds.add("--lineno");
            cmds.add(Integer.toString(this.lineNumber));
        }
        if (this.lineCount != null) {
            cmds.add("--lines-in-file");
            cmds.add(Integer.toString(this.lineCount));
        }
        if (WakaTime.getDefault().DEBUG) {
            Logger.debug(cmds.toString());
            cmds.add("--verbose");
        }
        return cmds.toArray(new String[cmds.size()]);
    }

    private void setProject(IEditorPart activeEditor) {
        if (activeEditor == null) {
            setDefaultProject();
            return;
        }

        IEditorInput input = activeEditor.getEditorInput();
        if (input == null) {
            setDefaultProject();
            return;
        }

        if (input instanceof FileEditorInput) {
            IProject project = ((FileEditorInput)input).getFile().getProject();
            if (project != null && project.getName() != null) {
                this.project = project;
                WakaTime.getDefault().lastProject = this.project;
                return;
            }
        }

        IProject project = input.getAdapter(IProject.class);
        if (project != null && project.getName() != null) {
            this.project = project;
            WakaTime.getDefault().lastProject = this.project;
            return;
        }

        IResource resource = input.getAdapter(IResource.class);
        if (resource == null) {
            setDefaultProject();
            return;
        }

        IProject resourceProject = resource.getProject();
        if (resourceProject == null || resourceProject.getName() == null) {
            setDefaultProject();
            return;
        }

        this.project = resourceProject;
        WakaTime.getDefault().lastProject = this.project;
    }

    private void setDefaultProject() {
        IProject lastProject = WakaTime.getDefault().lastProject;
        if (lastProject != null) {
            this.project = lastProject;
            return;
        }

        try {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IWorkspaceRoot root = workspace.getRoot();
            IProject[] projects = root.getProjects();
            for (IProject project : projects) {
                if (project != null && project.isOpen()) {
                    this.project = project;
                    return;
                }
            }
        } catch (Exception e) {
            WakaTime.log.debug(e);
        }
    }

    private String getProjectFolder() {
        if (this.project == null) return null;
        
        URI root = this.project.getLocationURI();
        if (root == null) return null;
        
        return root.getPath();
    }

    private void fixFilePath() {
        this.entity = this.entity.replaceFirst("^[\\\\/]([A-Z]:[\\\\/])", "$1");
    }
}
