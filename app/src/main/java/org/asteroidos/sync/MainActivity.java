package org.asteroidos.sync;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerState;
import com.idevicesinc.sweetblue.ManagerStateListener;
import com.idevicesinc.sweetblue.utils.BluetoothEnabler;
import com.idevicesinc.sweetblue.utils.Interval;

import org.asteroidos.sync.fragments.DeviceListFragment;
import org.asteroidos.sync.fragments.DeviceDetailFragment;
import org.asteroidos.sync.services.NLService;
import org.asteroidos.sync.services.SynchronizationService;

import static com.idevicesinc.sweetblue.BleManager.get;

public class MainActivity extends AppCompatActivity implements DeviceListFragment.OnDefaultDeviceSelectedListener,
        DeviceListFragment.OnScanRequestedListener, DeviceDetailFragment.OnDefaultDeviceUnselectedListener,
        DeviceDetailFragment.OnConnectRequestedListener, ManagerStateListener, BleManager.DiscoveryListener, BleManager.UhOhListener {
    private BleManager mBleMngr;
    private DeviceListFragment mListFragment;
    private DeviceDetailFragment mDetailFragment;
    Messenger mSyncServiceMessenger;
    Intent mSyncServiceIntent;
    final Messenger mDeviceDetailMessenger = new Messenger(new MainActivity.SynchronizationHandler());
    int mStatus = SynchronizationService.STATUS_DISCONNECTED;

    public static final String PREFS_NAME = "WeatherPreferences";
    public static final String PREFS_DEFAULT_MAC_ADDR = "defaultMacAddress";
    public static final String PREFS_DEFAULT_LOC_NAME = "defaultLocalName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultDevMacAddr = prefs.getString(PREFS_DEFAULT_MAC_ADDR, "");

        /* Start and/or attach to the Synchronization Service */
        mSyncServiceIntent = new Intent(this, SynchronizationService.class);
        startService(mSyncServiceIntent);

        BluetoothEnabler.start(this);
        mBleMngr = get(getApplication());
        mBleMngr.setListener_State(this);
        mBleMngr.setListener_Discovery(this);
        mBleMngr.setListener_UhOh(this);

        if(defaultDevMacAddr.isEmpty() || !mBleMngr.hasDevice(defaultDevMacAddr))
            onScanRequested();

        /* Check that bluetooth is enabled */
        if (!mBleMngr.isBleSupported())
            showBleNotSupported();

        /* Check that notifications are enabled */
        ComponentName cn = new ComponentName(this, NLService.class);
        String flat = Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners");
        if (!(flat != null && flat.contains(cn.flattenToString()))) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.notifications)
                    .setMessage(R.string.notifications_enablement)
                    .setPositiveButton(R.string.generic_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                            startActivity(intent);
                        }
                    })
                    .show();
        }

        if (savedInstanceState == null) {
            Fragment f;
            if(defaultDevMacAddr.isEmpty())
                f = mListFragment = new DeviceListFragment();
            else {
                setTitle(prefs.getString(PREFS_DEFAULT_LOC_NAME, ""));
                f = mDetailFragment = new DeviceDetailFragment();
            }

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.flContainer, f);
            ft.commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mStatus != SynchronizationService.STATUS_CONNECTED)
            stopService(mSyncServiceIntent);
    }

    /* Fragments switching */
    @Override
    public void onDefaultDeviceSelected(String macAddress) {
        mDetailFragment = new DeviceDetailFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContainer, mDetailFragment)
                .commit();

        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_SET_DEVICE);
            msg.obj = macAddress;
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {}

        onConnectRequested();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREFS_DEFAULT_MAC_ADDR, macAddress);
        editor.apply();

        mListFragment = null;
    }

    @Override
    public void onDefaultDeviceUnselected() {
        mListFragment = new DeviceListFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContainer, mListFragment)
                .commit();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREFS_DEFAULT_MAC_ADDR, "");
        editor.putString(PREFS_DEFAULT_LOC_NAME, "");
        editor.apply();

        mDetailFragment = null;
        setTitle(R.string.app_name);
    }

    /* Synchronization service events handling */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            mSyncServiceMessenger = new Messenger(service);

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String defaultDevMacAddr = prefs.getString(PREFS_DEFAULT_MAC_ADDR, "");

            if(mBleMngr.hasDevice(defaultDevMacAddr)) {
                try {
                    Message msg = Message.obtain(null, SynchronizationService.MSG_SET_DEVICE);
                    msg.obj = defaultDevMacAddr;
                    msg.replyTo = mDeviceDetailMessenger;
                    mSyncServiceMessenger.send(msg);
                } catch (RemoteException ignored) {}
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mSyncServiceMessenger = null;
        }
    };

    @Override
    public void onConnectRequested() {
        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_CONNECT);
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {}
    }

    @Override
    public void onDisconnectRequested() {
        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_DISCONNECT);
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {}
    }

    class SynchronizationHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if(mDetailFragment == null) return;

            switch (msg.what) {
                case SynchronizationService.MSG_SET_LOCAL_NAME:
                    String name = (String)msg.obj;
                    mDetailFragment.setLocalName(name);

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(PREFS_DEFAULT_LOC_NAME, name);
                    editor.apply();
                    break;
                case SynchronizationService.MSG_SET_STATUS:
                    mDetailFragment.setStatus(msg.arg1);
                    if(msg.arg1 == SynchronizationService.STATUS_CONNECTED) {
                        try {
                            Message batteryMsg = Message.obtain(null, SynchronizationService.MSG_REQUEST_BATTERY_LIFE);
                            batteryMsg.replyTo = mDeviceDetailMessenger;
                            mSyncServiceMessenger.send(batteryMsg);
                        } catch (RemoteException ignored) {}
                    }
                    mStatus = msg.arg1;
                    break;
                case SynchronizationService.MSG_SET_BATTERY_PERCENTAGE:
                    mDetailFragment.setBatteryPercentage(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /* Bluetooth scanning events handling */
    @Override
    public void onEvent(BleManager.StateListener.StateEvent event) {
        if(event.didExit(BleManagerState.SCANNING)) {
            if(mListFragment != null) mListFragment.scanningStopped();
            else                      mDetailFragment.scanningStopped();
        } else if(event.didEnter(BleManagerState.SCANNING)) {
            if(mListFragment != null) mListFragment.scanningStarted();
            else                      mDetailFragment.scanningStarted();
        }
    }

    @Override
    public void onEvent(BleManager.DiscoveryListener.DiscoveryEvent event) {
        if (event.was(BleManager.DiscoveryListener.LifeCycle.DISCOVERED)) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String defaultDevMacAddr = prefs.getString(PREFS_DEFAULT_MAC_ADDR, "");

            if(defaultDevMacAddr.equals(event.device().getMacAddress())) {
                try {
                    Message msg = Message.obtain(null, SynchronizationService.MSG_SET_DEVICE);
                    msg.obj = defaultDevMacAddr;
                    msg.replyTo = mDeviceDetailMessenger;
                    mSyncServiceMessenger.send(msg);
                } catch (RemoteException ignored) {}

                onConnectRequested();
            }

            if (mListFragment == null) return;
            mListFragment.deviceDiscovered(event.device());
        } else if (event.was(BleManager.DiscoveryListener.LifeCycle.UNDISCOVERED)) {
            if (mListFragment == null) return;
            mListFragment.deviceUndiscovered(event.device());
        }
    }

    @Override
    public void onScanRequested() {
        mBleMngr.turnOn();
        mBleMngr.startScan(Interval.secs(10.0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBleMngr.onResume();
        bindService(mSyncServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBleMngr.onPause();
        unbindService(mConnection);
    }

    /* Bluetooth errors handling */
    @Override public void onEvent(UhOhEvent event) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        final android.app.AlertDialog dialog = builder.create();

        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener()
        {
            @Override public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();

                if( which == DialogInterface.BUTTON_POSITIVE )
                    mBleMngr.reset();
            }
        };

        dialog.setTitle(event.uhOh().name());

        if(event.uhOh().getRemedy() == Remedy.RESET_BLE)
        {
            dialog.setMessage(getString(R.string.uhoh_message_nuke));
            dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                    getString(R.string.uhoh_message_nuke_drop), clickListener);
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.generic_cancel), clickListener);
        }
        else if(event.uhOh().getRemedy() == Remedy.RESTART_PHONE)
        {
            dialog.setMessage(getString(R.string.uhoh_message_phone_restart));
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                    getString(R.string.generic_ok), clickListener);
        }
        else if(event.uhOh().getRemedy() == Remedy.WAIT_AND_SEE)
        {
            dialog.setMessage(getString(R.string.uhoh_message_weirdness));
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                    getString(R.string.generic_ok), clickListener);
        }

        dialog.show();
    }

    public void showBleNotSupported() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        final android.app.AlertDialog dialog = builder.create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        };

        dialog.setMessage(getString(R.string.ble_not_supported));
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                getString(R.string.generic_ok), clickListener);
        dialog.show();
    }
}
