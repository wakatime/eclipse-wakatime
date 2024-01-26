package com.wakatime.eclipse.plugin;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;

public class CustomDocumentListener implements IDocumentListener {

    @Override
    public void documentAboutToBeChanged(DocumentEvent event) {
        // noop
    }

    @Override
    public void documentChanged(DocumentEvent event) {
        Heartbeat heartbeat = WakaTime.getHeartbeat(null, false);
        WakaTime.processHeartbeat(heartbeat);
    }

}
