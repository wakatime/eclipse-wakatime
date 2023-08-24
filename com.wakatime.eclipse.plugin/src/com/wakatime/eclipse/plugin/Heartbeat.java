package com.wakatime.eclipse.plugin;

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
    public String project;
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
        this.project = this.getProject(activeEditor);
        WakaTime.getDefault().lastProject = this.project;
        this.lineCount = null;
        this.lineNumber = null;
        this.cursorPosition = null;
        this.alternateLanguage = null;
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
        if (this.project != null) {
            cmds.add("--project");
            cmds.add(this.project);
        }
        if (this.isWrite)
            cmds.add("--write");
        if (this.isUnsavedFile)
            cmds.add("--is-unsaved-entity");
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

    private String getProject(IEditorPart activeEditor) {

        if (activeEditor == null) return getDefaultProject();

        IEditorInput input = activeEditor.getEditorInput();
        if (input == null) return getDefaultProject();

        if (input instanceof FileEditorInput) {
            IProject project = ((FileEditorInput)input).getFile().getProject();
            if (project != null && project.getName() != null) return project.getName();
        }

        IProject project = input.getAdapter(IProject.class);
        if (project != null && project.getName() != null) return project.getName();

        IResource resource = input.getAdapter(IResource.class);
        if (resource == null) return getDefaultProject();

        IProject resourceProject = resource.getProject();
        if (resourceProject == null || resourceProject.getName() == null) return getDefaultProject();

        return resourceProject.getName();
    }

    private String getDefaultProject() {
        String lastProject = WakaTime.getDefault().lastProject;
        if (lastProject != null) return lastProject;

        try {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IWorkspaceRoot root = workspace.getRoot();
            IProject[] projects = root.getProjects();
            for (IProject project : projects) {
                if (project != null && project.isOpen()) {
                    return project.getName();
                }
            }
        } catch (Exception e) {
            WakaTime.log.debug(e);
        }

        return null;
    }

    private void fixFilePath() {
        this.entity = this.entity.replaceFirst("^[\\\\/]([A-Z]:[\\\\/])", "$1");
    }
}
