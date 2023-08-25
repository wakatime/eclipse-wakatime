/* ==========================================================
File:        CustomCaretListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;

public class CustomMouseListener implements MouseListener {

    @Override
    public void mouseDown(MouseEvent e) {
        // Logger.debug("CustomMouseListener.mouseDown");

        Heartbeat heartbeat = WakaTime.getHeartbeat(null, false);
        WakaTime.processHeartbeat(heartbeat);
    }

    @Override
    public void mouseDoubleClick(MouseEvent e) { }

    @Override
    public void mouseUp(MouseEvent e) { }

}
