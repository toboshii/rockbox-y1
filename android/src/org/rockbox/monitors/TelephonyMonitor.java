/***************************************************************************
 *             __________               __   ___.
 *   Open      \______   \ ____   ____ |  | _\_ |__   _______  ___
 *   Source     |       _//  _ \_/ ___\|  |/ /| __ \ /  _ \  \/  /
 *   Jukebox    |    |   (  <_> )  \___|    < | \_\ (  <_> > <  <
 *   Firmware   |____|_  /\____/ \___  >__|_ \|___  /\____/__/\_ \
 *                     \/            \/     \/    \/            \/
 * $Id$
 *
 * Copyright (C) 2010 Thomas Martitz
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY
 * KIND, either express or implied.
 *
 ****************************************************************************/

package org.rockbox.monitors;

import android.content.Context;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class TelephonyMonitor 
{
    public TelephonyMonitor(Context c)
    {
        try {
            final Handler handler = new Handler(c.getMainLooper());        
            final TelephonyManager tm = (TelephonyManager)
                c.getSystemService(Context.TELEPHONY_SERVICE);
            
            // Check if TelephonyManager is available (some devices like tablets don't have it)
            if (tm == null) {
                android.util.Log.d("TelephonyMonitor", "TelephonyManager not available, skipping telephony monitoring");
                return;
            }
            
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {   /* need to instantiate from a thread that has a Looper */
                    try {
                        tm.listen(new RockboxCallStateListener(), PhoneStateListener.LISTEN_CALL_STATE);
                    } catch (SecurityException e) {
                        android.util.Log.d("TelephonyMonitor", "No permission to listen to call state: " + e.getMessage());
                    } catch (Exception e) {
                        android.util.Log.e("TelephonyMonitor", "Error setting up telephony listener: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            android.util.Log.e("TelephonyMonitor", "Error initializing TelephonyMonitor: " + e.getMessage());
        }
    }

    private class RockboxCallStateListener extends PhoneStateListener
    {
        private int last_state;

        public RockboxCallStateListener()
        {
            super();
            /* set artificial initial state, 
             * we will get an initial event shortly after this,
             * so to handle it correctly we need an invalid state set */
            last_state = TelephonyManager.CALL_STATE_IDLE - 10;
        }

        private void handleState(int state)
        {
            if (state == last_state)
                return;
            switch (state) 
            {
                case TelephonyManager.CALL_STATE_IDLE:
                    postCallHungUp();
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    postCallIncoming();
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    /* for incoming calls we handled at RINGING already,
                     * if the previous state was IDLE then 
                     * this is an outgoing call
                     */
                    if (last_state == TelephonyManager.CALL_STATE_IDLE)
                    {   /* currently handled the same as incoming calls */
                        postCallIncoming();
                    }
                    break;
                default:
                    return;
            }
            last_state = state;
            
        }
        
        @Override
        public void onCallStateChanged(int state, String number)
        {
            super.onCallStateChanged(state, number);
            handleState(state);
        }
    }
    private native void postCallIncoming();
    private native void postCallHungUp();
}
