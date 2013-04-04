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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;

import com.hp.android.printplugin.support.PrintServiceStrings;

public abstract class PrintPluginActivity extends Activity implements PrintPlugin.PluginServiceConnection {
	
    private static final String PACKAGE_SCHEME = "package";
    private PackageReceiver mPackageReceiver = new PackageReceiver();
    private WifiUtils mWifiUtils;
	
	public interface PluginUpdateCallback {
		public void pluginAvailable(PrintPlugin plugin, boolean available);
	}
	
	public interface PrintServiceNotification {
		void updatePrintServiceMessenger(Messenger printServiceMessenger);
	};

	protected ArrayList<PrintPlugin> mPrintPlugins = null;
	protected ArrayList<PrintPlugin> mAvailablePrintPlugins = new ArrayList<PrintPlugin>();
	
	private ArrayList<PluginUpdateCallback> mPluginUpdateCallbacks = new ArrayList<PluginUpdateCallback>();
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mWifiUtils = new WifiUtils(this);
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction().add(new FragmentDataHolder(), getResources().getResourceName(R.id.fragment_id__data_holder)).commit();
		} else {
			FragmentDataHolder dataHolder = (FragmentDataHolder)getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.fragment_id__data_holder));
			if (dataHolder != null) {
				mPrintPlugins = dataHolder.mPlugins;
				mAvailablePrintPlugins = dataHolder.mAvailablePrintPlugins;
			}
		}
	}
	
	@Override
	protected void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		FragmentDataHolder dataHolder = (FragmentDataHolder)getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.fragment_id__data_holder));
		if (dataHolder != null) {
			dataHolder.mPlugins = mPrintPlugins;
			dataHolder.mAvailablePrintPlugins = mAvailablePrintPlugins;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (isFinishing()) {
			if (mPrintPlugins != null) {
				for(PrintPlugin plugin : mPrintPlugins) {
					plugin.unbind();
				}
			}
		}
		unregisterReceiver(mPackageReceiver);
	}

	protected boolean processPlugins() {
		boolean result = false;
		if (mPrintPlugins == null) {
			mPrintPlugins = getPrintPlugins();
			for(PrintPlugin plugin : mPrintPlugins) {
				result |= plugin.bind(this);
			}
		} else {
			result = true;
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
		filter.addDataScheme(PACKAGE_SCHEME);
		registerReceiver(mPackageReceiver, filter);
		
		return result;
	}

	protected void addPluginUpdateCallback(PluginUpdateCallback callback) {
		if (callback != null && !mPluginUpdateCallbacks.contains(callback)) {
			mPluginUpdateCallbacks.add(callback);
			for(PrintPlugin plugin : mAvailablePrintPlugins) {
				callback.pluginAvailable(plugin, true);
			}
		}
	}

	protected void removePluginUpdateCallback(PluginUpdateCallback callback) {
		mPluginUpdateCallbacks.remove(callback);
	}
	
	public ArrayList<PrintPlugin> getPrintPlugins() {
		ArrayList<PrintPlugin> plugins = new ArrayList<PrintPlugin>();
		PackageManager pm = getPackageManager();
		List<ResolveInfo> pluginPackageList = pm.queryIntentServices(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE), PackageManager.GET_META_DATA);
		for(ResolveInfo entry : pluginPackageList) {
			plugins.add(new PrintPlugin(this, entry.serviceInfo));
		}
		
		return plugins;
	}
	
	public PrintPlugin findPlugin(String packageName) {
		if (!TextUtils.isEmpty(packageName)) {
			for(PrintPlugin plugin : mAvailablePrintPlugins) {
				if (!TextUtils.isEmpty(plugin.mPackageName) && packageName.equals(plugin.mPackageName))
					return plugin;
			}
		}
		return null;
	}
	
	public ArrayList<PrintPlugin> getAvailablePlugins() {
		return new ArrayList<PrintPlugin>(mAvailablePrintPlugins);
	}
	
	public WifiUtils getWifiUtils() {
		return mWifiUtils;
	}
	
	public void sendRequestToPlugins(Intent request, Messenger replyTo) {
		for(PrintPlugin plugin : mAvailablePrintPlugins) {
			Message msg = Message.obtain(null, 0, request);
			msg.replyTo = replyTo;
			try {
				if (plugin.mMessenger != null) {
					plugin.mMessenger.send(msg);
				}
			} catch (RemoteException e) {
			} catch (Exception e) {
			}
		}
	}
	
	public void sendRequestToPlugin(PrintPlugin plugin, Intent request, Messenger replyTo) {
		Message msg = Message.obtain(null, 0, request);
		msg.replyTo = replyTo;
		try {
			if (plugin.mMessenger != null) {
				plugin.mMessenger.send(msg);
			}
		} catch (RemoteException e) {
		} catch (Exception e) {
		}
	}

	@Override
	public void pluginConnected(PrintPlugin plugin) {
		if (!mAvailablePrintPlugins.contains(plugin)) {
			mAvailablePrintPlugins.add(plugin);
			for(PluginUpdateCallback callback : mPluginUpdateCallbacks) {
				callback.pluginAvailable(plugin, true);
			}
		}
	}

	@Override
	public void pluginDisconnected(PrintPlugin plugin) {
		boolean removed = mAvailablePrintPlugins.remove(plugin);
		if (removed) {
			for(PluginUpdateCallback callback : mPluginUpdateCallbacks) {
				callback.pluginAvailable(plugin, false);
			}
		}
	}
	
	@Override
	public void pluginConnectionFailed(PrintPlugin plugin) {		
	}
	
    private class PackageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	String packageName = intent.getData().getSchemeSpecificPart();
        	Intent serviceIntent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE).setPackage(packageName);
    		ResolveInfo resolvedService = getPackageManager().resolveService(serviceIntent, PackageManager.GET_META_DATA);
    		if (resolvedService != null) {
    			PrintPlugin plugin = new PrintPlugin(PrintPluginActivity.this, resolvedService.serviceInfo);
    			mPrintPlugins.add(plugin);
    			plugin.bind(PrintPluginActivity.this);
    		}
        }
    }
}