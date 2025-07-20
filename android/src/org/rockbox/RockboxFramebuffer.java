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

package org.rockbox;

import java.nio.ByteBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewConfiguration;
import android.os.Vibrator;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import eu.chainfire.libsuperuser.Shell;

public class RockboxFramebuffer extends SurfaceView 
                                 implements SurfaceHolder.Callback
{
    private final DisplayMetrics metrics;
    private final ViewConfiguration view_config;
    private Bitmap btm;

    private static final int[] duration_mapping = {
        0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50
    };

    private static final int CENTER_KEYCODE = KeyEvent.KEYCODE_ENTER; // 66
    private static final long LONG_PRESS_DURATION_MS = 1000;
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean centerLongPressTriggered = false;
    private Runnable centerLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            centerLongPressTriggered = true;
            Log.d("RockboxButton", "Center long-press detected, sending POWER keyevent as root");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Shell.SU.run("input keyevent POWER");
                        Log.d("RockboxButton", "POWER keyevent sent as root");
                    } catch (Exception e) {
                        Log.e("RockboxButton", "Failed to send POWER keyevent as root: " + e.getMessage());
                    }
                }
            }).start();
        }
    };

    /* first stage init; needs to run from a thread that has a Looper 
     * setup stuff that needs a Context */
    public RockboxFramebuffer(Context c)
    {
        super(c);
        metrics = c.getResources().getDisplayMetrics();
        view_config = ViewConfiguration.get(c);
        getHolder().addCallback(this);
        /* Needed so we can catch KeyEvents */
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        /* don't draw until native is ready (2nd stage) */
        setEnabled(false);
    }

    private void update(ByteBuffer framebuffer)
    {
        SurfaceHolder holder = getHolder();                            
        Canvas c = holder.lockCanvas();
        if (c == null)
			return;

        btm.copyPixelsFromBuffer(framebuffer);
        synchronized (holder)
        { /* draw */
            c.drawBitmap(btm, 0.0f, 0.0f, null);
        }
        holder.unlockCanvasAndPost(c);
    }
    
    private void update(ByteBuffer framebuffer, Rect dirty)
    {
        SurfaceHolder holder = getHolder();         
        Canvas c = holder.lockCanvas(dirty);
        
        if (c == null)
			return;

        /* can't copy a partial buffer, but it doesn't make a noticeable difference anyway */
        btm.copyPixelsFromBuffer(framebuffer);
        synchronized (holder)
        {   /* draw */
            c.drawBitmap(btm, dirty, dirty, null);   
        }
        holder.unlockCanvasAndPost(c);
    }

    public boolean onTouchEvent(MotionEvent me)
    {
        int x = (int) me.getX();
        int y = (int) me.getY();

        switch (me.getAction())
        {
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            touchHandler(false, x, y);
            return true;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_DOWN:
            touchHandler(true, x, y);
            return true;
        }

        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        // Center button long-press detection
        if ((keyCode == CENTER_KEYCODE) && event.getRepeatCount() == 0) {
            centerLongPressTriggered = false;
            longPressHandler.postDelayed(centerLongPressRunnable, LONG_PRESS_DURATION_MS);
        }
        /* Handle repeat events */
        if (event.getRepeatCount() > 0)
        {
            return buttonHandlerRepeat(keyCode);
        }
        else
        {
            return buttonHandler(keyCode, true);
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // Cancel long-press if released before trigger
        if (keyCode == CENTER_KEYCODE) {
            longPressHandler.removeCallbacks(centerLongPressRunnable);
        }
        return buttonHandler(keyCode, false);
    }
 
    private int getDpi()
    {
        return metrics.densityDpi;
    }
    

    private int getScrollThreshold()
    {
        return view_config.getScaledTouchSlop();
    }

    private native void touchHandler(boolean down, int x, int y);
    public native static boolean buttonHandler(int keycode, boolean state);
    public native static boolean buttonHandlerRepeat(int keycode);
    public native static void triggerVibrationNative(int baseDuration, int boostDuration);
    
    public native void surfaceCreated(SurfaceHolder holder);
    public native void surfaceDestroyed(SurfaceHolder holder);
    
    /* Trigger vibration for button feedback */
    public static void triggerVibration(Context context, int baseDuration, int boostDuration) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                int base_ms = duration_mapping[baseDuration];
                int boost_ms = boostDuration;
                int total_ms = base_ms + boost_ms;
                vibrator.vibrate(total_ms);
            } else {
                android.util.Log.e("RockboxFramebuffer", "Vibrator is null");
            }
        } catch (Exception e) {
            android.util.Log.e("RockboxFramebuffer", "Vibration error: " + e.getMessage());
        }
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        btm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        setEnabled(true);
    }
}
