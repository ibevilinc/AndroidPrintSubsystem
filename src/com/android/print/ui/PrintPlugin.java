/*
(c) Copyright 2013 Hewlett-Packard Development Company, L.P.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.android.print.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.text.TextUtils;

import com.hp.android.printplugin.support.PrintServiceStrings;

public class PrintPlugin implements ServiceConnection {
	
	public interface PluginServiceConnection {
		public void pluginConnected(PrintPlugin plugin);
		public void pluginConnectionFailed(PrintPlugin plugin);
		public void pluginDisconnected(PrintPlugin plugin);
	}
	
	public enum ConnectionState {
		NOT_CONNECTED,
		CONNECTING,
		CONNECTED,
	};
	
	private ConnectionState mConnectionState = ConnectionState.NOT_CONNECTED;
	public Messenger mMessenger = null;
	public final String mPackageName;
	private final Context mContext;
	private final ServiceInfo mServiceInfo;
	private PluginServiceConnection mPluginServiceConnection = null;
	private Handler mConnectionHandler = new Handler();
	public final boolean mIsDemoPlugin;
	private Runnable mConnectFailureRunnable = new Runnable() {

		@Override
		public void run() {
			mConnectionState = ConnectionState.NOT_CONNECTED;
			if (mPluginServiceConnection != null) {
				mPluginServiceConnection.pluginConnectionFailed(PrintPlugin.this);
			}
		}
		
	};
	
	public ConnectionState getConnectionState() {
		return mConnectionState;
	}

	public PrintPlugin(Context context, ServiceInfo serviceInfo) {
		mContext     = context.getApplicationContext();
		mServiceInfo = serviceInfo;
		mPackageName = serviceInfo.packageName;
		mIsDemoPlugin = ((mContext.getPackageManager().checkSignatures(mContext.getPackageName(), mPackageName) == PackageManager.SIGNATURE_MATCH) && mPackageName.equals("com.example.printplugindemo"));
	}
	
	public boolean bind(PluginServiceConnection pluginServiceConnection) {
		if (mConnectionState == ConnectionState.NOT_CONNECTED) {
			mConnectionState = ConnectionState.CONNECTING;
			mPluginServiceConnection = pluginServiceConnection;
			if (!mContext.bindService(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE).setPackage(mPackageName), this, Context.BIND_AUTO_CREATE)) {
				mConnectionState = ConnectionState.CONNECTED;
				mPluginServiceConnection = null;
			} else {
				mConnectionHandler.postDelayed(mConnectFailureRunnable, mContext.getResources().getInteger(R.integer.service_bind_timeout));
			}
		}
		return (mConnectionState != ConnectionState.NOT_CONNECTED);
	}
	
	public void unbind() {
		if (mConnectionState != ConnectionState.NOT_CONNECTED) {
			mConnectionState = ConnectionState.NOT_CONNECTED;
			mPluginServiceConnection = null;
			mContext.unbindService(this);
		}
	}
	
	public int getApiVersion() {
		int apiVersion = 0;
		if (mServiceInfo.metaData != null) {
			apiVersion = mServiceInfo.metaData.getInt(PrintServiceStrings.META_DATA__PLUGIN_VERSION, 0);
		}
		return apiVersion;
	}
	
	public boolean isVendorPlugin() {
		boolean result = true;
		if (mServiceInfo.metaData != null) {
			result = mServiceInfo.metaData.getBoolean("com.hp.android.printplugin.is_vendor_plugin", true);
		}
		return result;
	}
	
	public boolean isClassPlugin() {
		boolean result = false;
		if (mServiceInfo.metaData != null) {
			result = mServiceInfo.metaData.getBoolean("com.hp.android.printplugin.is_class_plugin", false);
		}
		return result;
	}
	
	public String getVendor() {
		String vendorName = PrintServiceStrings.VENDOR_NAME_UNKNOWN;
		if (mServiceInfo.metaData != null) {
			vendorName = mServiceInfo.metaData.getString(PrintServiceStrings.META_DATA__PLUGIN_VENDOR, PrintServiceStrings.VENDOR_NAME_UNKNOWN);
		}
		return vendorName;
	}
	
	public String getWirelessDirectPrefix() {
		String prefix = null;
		if (mServiceInfo.metaData != null) {
			prefix = mServiceInfo.metaData.getString(PrintServiceStrings.META_DATA__PLUGIN_WIRELESS_DIRECT_PREFIX);
		}
		if ((prefix != null) && TextUtils.isEmpty(prefix)) {
			prefix = null; // use null to imply lack of support
		}
		return prefix;
	}
	
	public String getWirelessDirectAddress() {
		String prefix = null;
		if (mServiceInfo.metaData != null) {
			prefix = mServiceInfo.metaData.getString(PrintServiceStrings.META_DATA__PLUGIN_WIRELESS_DIRECT_ADDRESS);
		}
		if ((prefix != null) && TextUtils.isEmpty(prefix)) {
			prefix = null; // use null to imply lack of support
		}
		return prefix;
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		mConnectionState = ConnectionState.CONNECTED;
		mConnectionHandler.removeCallbacks(mConnectFailureRunnable);
		mMessenger = new Messenger(service);
		if (mPluginServiceConnection != null) {
			mPluginServiceConnection.pluginConnected(this);
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		if (mConnectionState != ConnectionState.NOT_CONNECTED) {
			// prevent service connection leaks
			mContext.unbindService(this);
		}
		mConnectionState = ConnectionState.NOT_CONNECTED;
		mConnectionHandler.removeCallbacks(mConnectFailureRunnable);
		mMessenger = null;
		if (mPluginServiceConnection != null) {
			mPluginServiceConnection.pluginDisconnected(this);
		}
	}
}
