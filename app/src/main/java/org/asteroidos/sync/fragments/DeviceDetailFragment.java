/*
 * Copyright (C) 2016 - Florent Revest <revestflo@gmail.com>
 *                      Doug Koellmer <dougkoellmer@hotmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.asteroidos.sync.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.asteroidos.sync.R;
import org.asteroidos.sync.ble.WeatherService;
import org.asteroidos.sync.services.SynchronizationService;

public class DeviceDetailFragment extends Fragment {
    private TextView mConnectedText;
    private ImageView mConnectedImage;

    private TextView mBatteryText;
    private ImageView mBatteryImage;

    FloatingActionButton mFab;

    boolean mConnected = false;

    int mStatus = SynchronizationService.STATUS_DISCONNECTED;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_detail, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mFab = (FloatingActionButton) view.findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mConnected)
                    mConnectListener.onDisconnectRequested();
                else
                    mConnectListener.onConnectRequested();
            }
        });

        mConnectedText = (TextView)view.findViewById(R.id.info_connected);
        mConnectedImage = (ImageView)view.findViewById(R.id.info_icon_connected);

        mBatteryText = (TextView)view.findViewById(R.id.info_battery);
        mBatteryImage = (ImageView)view.findViewById(R.id.info_icon_battery);

        CardView weatherCard = (CardView)view.findViewById(R.id.card_view1);
        weatherCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.weather_settings);
                alert.setMessage(R.string.enter_city_name);

                final SharedPreferences settings = getActivity().getSharedPreferences(WeatherService.PREFS_NAME, 0);
                final EditText edittext = new EditText(getActivity());
                int padding = (int)DeviceDetailFragment.this.getResources().getDisplayMetrics().density*15;
                edittext.setPadding(padding, padding, padding, padding);
                edittext.setText(settings.getString(WeatherService.PREFS_CITY_NAME, WeatherService.PREFS_CITY_NAME_DEFAULT));
                alert.setView(edittext);

                alert.setPositiveButton(getString(R.string.generic_ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String cityName = edittext.getText().toString();
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(WeatherService.PREFS_CITY_NAME, cityName);
                        editor.apply();
                    }
                });

                alert.show();
            }
        });

        CardView findCard = (CardView)view.findViewById(R.id.card_view2);
        findCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new  Intent("org.asteroidos.sync.NOTIFICATION_LISTENER");
                i.putExtra("event", "posted");
                i.putExtra("packageName", "org.asteroidos.sync");
                i.putExtra("id", 0xa57e401d);
                i.putExtra("appName", getString(R.string.app_name));
                i.putExtra("appIcon", "");
                i.putExtra("summary", getString(R.string.watch_finder));
                i.putExtra("body", getString(R.string.phone_is_searching));
                getActivity().sendBroadcast(i);
            }
        });

        CardView screenshotCard = (CardView)view.findViewById(R.id.card_view3);
        screenshotCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().sendBroadcast(new  Intent("org.asteroidos.sync.SCREENSHOT_REQUEST_LISTENER"));
            }
        });

        CardView notifSettCard = (CardView) view.findViewById(R.id.card_view4);
        notifSettCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), R.string.not_supported, Toast.LENGTH_SHORT).show();
            }
        });

        CardView timeSyncCard = (CardView) view.findViewById(R.id.card_view5);
        timeSyncCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().sendBroadcast(new  Intent("org.asteroidos.sync.TIME_SYNC_LISTENER"));
            }
        });

        TextView unpairTextView = (TextView) view.findViewById(R.id.unpairTextView);
        unpairTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDeviceListener.onDefaultDeviceUnselected();
            }
        });
    }

    public void setLocalName(String name) {
        getActivity().setTitle(name);
    }

    public void setStatus(int status) {
        mStatus = status;
        switch(status) {
            case SynchronizationService.STATUS_CONNECTED:
                mConnectedText.setText(R.string.connected);
                mConnectedImage.setImageResource(R.mipmap.android_cloud_done);
                mFab.setImageResource(R.mipmap.android_bluetooth_disconnect);
                mConnected = true;
                break;
            case SynchronizationService.STATUS_DISCONNECTED:
                mConnectedText.setText(R.string.disconnected);
                mConnectedImage.setImageResource(R.mipmap.android_cloud);
                mBatteryText.setVisibility(View.INVISIBLE);
                mBatteryImage.setVisibility(View.INVISIBLE);
                mFab.setImageResource(R.mipmap.android_bluetooth_connect);
                mConnected = false;
                break;
            case SynchronizationService.STATUS_CONNECTING:
                mConnectedText.setText(R.string.connecting);
                mConnectedImage.setImageResource(R.mipmap.android_cloud);
                break;
            default:
                break;
        }
    }

    public void setBatteryPercentage(int percentage) {
        mBatteryText.setVisibility(View.VISIBLE);
        mBatteryImage.setVisibility(View.VISIBLE);
        try {
            mBatteryText.setText(getString(R.string.percentage, percentage));
        } catch(IllegalStateException ignore) {}
    }

    public void scanningStarted() {
        if(mStatus == SynchronizationService.STATUS_DISCONNECTED)
            mConnectedText.setText(R.string.scanning);
    }

    public void scanningStopped() {
        if(mStatus == SynchronizationService.STATUS_DISCONNECTED)
            mConnectedText.setText(R.string.disconnected);
    }

    /* Notifies MainActivity when a device unpairing is requested */
    public interface OnDefaultDeviceUnselectedListener {
        void onDefaultDeviceUnselected();
    }
    public interface OnConnectRequestedListener {
        void onConnectRequested();
        void onDisconnectRequested();
    }
    private DeviceDetailFragment.OnDefaultDeviceUnselectedListener mDeviceListener;
    private DeviceDetailFragment.OnConnectRequestedListener mConnectListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof DeviceDetailFragment.OnDefaultDeviceUnselectedListener)
            mDeviceListener = (DeviceDetailFragment.OnDefaultDeviceUnselectedListener) context;
        else
            throw new ClassCastException(context.toString()
                    + " does not implement DeviceDetailFragment.OnDefaultDeviceUnselectedListener");

        if(context instanceof DeviceDetailFragment.OnConnectRequestedListener)
            mConnectListener = (DeviceDetailFragment.OnConnectRequestedListener) context;
        else
            throw new ClassCastException(context.toString()
                    + " does not implement DeviceDetailFragment.OnConnectRequestedListener");
    }
}
