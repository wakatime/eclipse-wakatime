/* ==========================================================
File:        CustomCaretListener.java
Description: Automatic time tracking for Eclipse.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/


package com.wakatime.eclipse.plugin;

import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;

public class CustomCaretListener implements CaretListener {

    @Override
    public void caretMoved(CaretEvent event) {
        // Logger.debug("CustomCaretListener.caretMoved");

        Heartbeat heartbeat = WakaTime.getHeartbeat(null, false);
        WakaTime.processHeartbeat(heartbeat);
    }

}
