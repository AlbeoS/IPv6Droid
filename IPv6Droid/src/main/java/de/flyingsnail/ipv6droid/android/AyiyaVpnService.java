/*
 * Copyright (c) 2014 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */

package de.flyingsnail.ipv6droid.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.TicConfiguration;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
 * The Android service controlling the VpnThread.
 * Created by pelzi on 15.08.13.
 */
public class AyiyaVpnService extends VpnService {

    private static final String TAG = AyiyaVpnService.class.getName();
    private static final String SESSION_NAME = AyiyaVpnService.class.getSimpleName();

    public static final String EXTRA_CACHED_TUNNEL = AyiyaVpnService.class.getName() + ".CACHED_TUNNEL";

    // the thread doing the work
    private VpnThread thread;

    // broadcast receivers
    private final ConnectivityReceiver connectivityReceiver = new ConnectivityReceiver();
    private final CommandReceiver commandReceiver = new CommandReceiver();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "received start command");
        if (thread == null || !thread.isAlive()) {
            // Build the configuration object from the saved shared preferences.
            SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            TicConfiguration ticConfiguration = loadTicConfiguration(myPreferences);
            RoutingConfiguration routingConfiguration = loadRoutingConfiguration(myPreferences);
            Log.d(TAG, "retrieved configuration");

            // register receivers of broadcasts
            registerLocalCommandReceiver();
            registerGlobalConnectivityReceiver();

            // Start a new session by creating a new thread.
            TicTunnel cachedTunnel = (TicTunnel)intent.getSerializableExtra(EXTRA_CACHED_TUNNEL);
            thread = new VpnThread(this, cachedTunnel, ticConfiguration, routingConfiguration, SESSION_NAME, startId);
            thread.start();
            Log.i(TAG, "VpnThread started");
        } else {
            Log.i(TAG, "VpnThread not started again - already running");
            Toast.makeText(getApplicationContext(),
                    R.string.vpnservice_already_running,
                    Toast.LENGTH_LONG);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        if (thread != null && !thread.isInterrupted()) {
            thread.interrupt();
        }
        unregisterGlobalConnectivityReceiver();
        unregisterLocalCommandReceiver();
    }

    /**
     * Register the instance of connectivityReceiver for global CONNECTIVITY_ACTIONs.
     */
    private void registerGlobalConnectivityReceiver() {
        final IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        getApplicationContext().registerReceiver(connectivityReceiver, intentFilter);
        Log.d(TAG, "registered CommandReceiver for global broadcasts");
    }

    /**
     * Revert registerGlobalConnectivityReceiver().
     */
    private void unregisterGlobalConnectivityReceiver() {
        getApplicationContext().unregisterReceiver(connectivityReceiver);
        Log.d(TAG, "un-registered CommandReceiver for global broadcasts");
    }

    /**
     * Register the instance of commandReceiver for local BC_* actions.
     */
    private void registerLocalCommandReceiver() {
        // setup the intent filter for status broadcasts and register receiver
        final IntentFilter intentFilter = new IntentFilter(MainActivity.BC_STOP);
        intentFilter.addAction(MainActivity.BC_STATUS_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(commandReceiver,
                intentFilter);
        Log.d(TAG, "registered CommandReceiver for local broadcasts");
    }

    /**
     * Revert registerLocalCommandReceiver.
     */
    private void unregisterLocalCommandReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(commandReceiver);
        Log.d(TAG, "un-registered CommandReceiver for local broadcasts");
    }

    /**
     * Create a new instance of AyiyaVpnService.Builder. This method exists solely for VpnThread.
     * @return a new instance.
     */
    protected Builder createBuilder() {
        return new Builder();
    }

    /** Inner class to handle stop requests */
    private class CommandReceiver extends BroadcastReceiver {
        private CommandReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MainActivity.BC_STOP)) {
                Log.i(TAG, "Received explicit stop brodcast, will stop VPN Tread");
                thread.interrupt();
            } else if (action.equals(MainActivity.BC_STATUS_UPDATE)) {
                Log.i(TAG, "Someone requested a status report, will have one send");
                thread.reportStatus();
            }
        }
    }

    /** Inner class to handle connectivity changes */
    public class ConnectivityReceiver extends BroadcastReceiver {
        private ConnectivityReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                if (thread != null)
                    thread.onConnectivityChange (intent);
            }
        }
    }

    private TicConfiguration loadTicConfiguration(SharedPreferences myPreferences) {
        return new TicConfiguration(myPreferences.getString("tic_username", ""),
                myPreferences.getString("tic_password", ""),
                myPreferences.getString("tic_host", ""));
    }

    private RoutingConfiguration loadRoutingConfiguration(SharedPreferences myPreferences) {
        boolean workaround = myPreferences.getBoolean("routes_workaround", false) &&
                checkAndroidVersionForWorkaround();
        return new RoutingConfiguration(
                myPreferences.getBoolean("routes_default", true),
                myPreferences.getString("routes_specific", "::/0"),
                workaround);
    }

    public static boolean checkAndroidVersionForWorkaround() {
        return (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT_WATCH);
    }

}
