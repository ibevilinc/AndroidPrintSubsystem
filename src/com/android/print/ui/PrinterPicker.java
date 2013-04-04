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

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.MenuItem;

public class PrinterPicker extends PrintPluginActivity {
	
	private WifiManager mWifiManager;
	private ConnectivityManager mConnectivityManager;
    private int mWifiTabIndex = -1;
    private int mWirelessDirectTabIndex = -1;
    private int mSavedTabIndex = -1;
    
    // NFC related items
    private NFCUtils mNFCUtils;
    
    private int mLastTab = 0;
    
    private boolean mHasDemoPlugin = false;
    
    public interface NFCPrinterSelection {
    	public void NFCPrinterSelected(NFCUtils.NFCPrinterSelection printer);
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED);

		final ActionBar bar = getActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		bar.setDisplayHomeAsUpEnabled(true);
		
		mWifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		
		mNFCUtils = new NFCUtils(this);
		
		if (savedInstanceState != null) {
			mSavedTabIndex = savedInstanceState.getInt(getResources().getResourceName(R.id.bundle_key__printer_picker_tab), mWifiTabIndex);
		}

		addPluginUpdateCallback(new PrintPluginActivity.PluginUpdateCallback() {
			
			@Override
			public void pluginAvailable(PrintPlugin plugin, boolean available) {
				if (available) {
					mHasDemoPlugin |= plugin.mIsDemoPlugin;
				} else {
					if (plugin.mIsDemoPlugin)
						mHasDemoPlugin = false;
				}
				if (available && (mWifiTabIndex == -1)) {
					final Resources resources = getResources();
					final ActionBar bar = getActionBar();
					Bundle args = getIntent().getExtras();

					mWifiTabIndex = bar.getTabCount();
					bar.addTab(bar.newTab()
								  .setText(R.string.navigation_tab_label__wifi_printers)
								  .setTabListener(new TabListener<FragmentWifi>(PrinterPicker.this, resources.getResourceName(R.id.printer_picker_fragment_wifi), FragmentWifi.class, R.string.activity_label__printer_selection, args)));
					mWirelessDirectTabIndex = bar.getTabCount();
					bar.addTab(bar.newTab()
								  .setText(R.string.navigation_tab_label__wireless_direct_printers)
								  .setTabListener(new TabListener<FragmentWirelessDirect>(PrinterPicker.this, resources.getResourceName(R.id.printer_picker_fragment_wireless_direct), FragmentWirelessDirect.class, R.string.activity_label__printer_selection, args)));

					int tabIndex = mWifiTabIndex;
					if (mSavedTabIndex >= 0) {
						tabIndex = mSavedTabIndex;
					} else {
						tabIndex = computeTabIndex();
					}
					bar.setSelectedNavigationItem(tabIndex);
				}
				
			}
		});
		processPlugins();
		checkIfPluginsInstalled();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		mNFCUtils.disable();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		mNFCUtils.enable();
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		NFCUtils.NFCPrinterSelection printerSelection = mNFCUtils.processIntent(intent);
		if (printerSelection != null) {
			int currentIndex = getActionBar().getSelectedNavigationIndex();
			if (currentIndex == mWifiTabIndex) {
				FragmentWifi fragment = (FragmentWifi)getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_fragment_wifi));
				fragment.NFCPrinterSelected(printerSelection);
			} else if (currentIndex == mWirelessDirectTabIndex) {
				FragmentWirelessDirect fragment = (FragmentWirelessDirect)getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_fragment_wireless_direct));
				fragment.NFCPrinterSelected(printerSelection);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(getResources().getResourceName(R.id.bundle_key__printer_picker_tab), getActionBar().getSelectedNavigationIndex());
	}

	public static class TabListener<T extends Fragment> implements
			ActionBar.TabListener {
		private final PrinterPicker mActivity;
		private final String mTag;
		private final Class<T> mClass;
		private final Bundle mArgs;
		private final int mTitle;
		private Fragment mFragment;

		public TabListener(PrinterPicker activity, String tag, Class<T> clz, int title,
				Bundle args) {
			mActivity = activity;
			mTag = tag;
			mClass = clz;
			mArgs = args;
			mTitle = title;

			// Check to see if we already have a fragment for this tab, probably
			// from a previously saved state. If so, deactivate it, because our
			// initial state is that a tab isn't shown.
			mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
			if (mFragment != null && !mFragment.isDetached()) {
				FragmentTransaction ft = mActivity.getFragmentManager()
						.beginTransaction();
				ft.detach(mFragment);
				ft.commit();
			}
		}

		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			if (mFragment == null) {
				mFragment = Fragment.instantiate(mActivity, mClass.getName(),
						mArgs);
				ft.add(android.R.id.content, mFragment, mTag);
			} else {
				ft.attach(mFragment);
			}
			mActivity.getActionBar().setTitle(mTitle);
		}

		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			mActivity.mLastTab = tab.getPosition();
			if (mFragment != null) {
				ft.detach(mFragment);
			}
		}

		public void onTabReselected(Tab tab, FragmentTransaction ft) {
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home: {
			onBackPressed();
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void removeDialogs() {
		Fragment fragment;
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		fragment = getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_wifi_not_configured));
		if (fragment != null) {
			ft.remove(fragment);
		}
		fragment = getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_no_wifi));
		if (fragment != null) {
			ft.remove(fragment);
		}
		if (!ft.isEmpty()) {
			ft.commit();
		}
	}
	
	private void displayWifiDisabledDialog() {
		Fragment fragment;
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		fragment = getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_wifi_not_configured));
		if (fragment != null) {
			ft.remove(fragment);
		}
		if (getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_no_wifi)) == null) {
			ft.add(PrinterSetupDialog.newInstance(getResources(), R.id.printer_picker_dialog_no_wifi, null), getResources().getResourceName(R.id.printer_picker_dialog_no_wifi));
		}
		if (!ft.isEmpty()) {
			ft.commit();
		}
	}
	
	private void displayWifiNotConfiguredDialog() {
		Fragment fragment;
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		fragment = getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_no_wifi));
		if (fragment != null) {
			ft.remove(fragment);
		}
		if (getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_wifi_not_configured)) == null) {
			ft.add(PrinterSetupDialog.newInstance(getResources(), R.id.printer_picker_dialog_wifi_not_configured, null), getResources().getResourceName(R.id.printer_picker_dialog_wifi_not_configured));
		}
		if (!ft.isEmpty()) {
			ft.commit();
		}
	}
	
	boolean wifiCheck(int id) {
		boolean result = false;

		int currentTabIndex = getActionBar().getSelectedNavigationIndex();		
		if (currentTabIndex == mWifiTabIndex) {
			if (mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting() || mHasDemoPlugin) {
				removeDialogs();
				result = true;
			} else if (mWifiManager.isWifiEnabled()) {
				displayWifiNotConfiguredDialog();
			} else {
				displayWifiDisabledDialog();
			}
		} else if (currentTabIndex == mWirelessDirectTabIndex) {
			if (mWifiManager.isWifiEnabled()) {
				removeDialogs();
				result = true;
			} else {
				displayWifiDisabledDialog();
			}
		}

    	return result;
	}
	
	void keepCloudDisabled() {
		getActionBar().setSelectedNavigationItem(mLastTab);
	}
	
	void keepWifiUnconfigued() {
		getActionBar().setSelectedNavigationItem(mWirelessDirectTabIndex);
	}
	
	void keepWifiOff() {
		onBackPressed();
	}
	
	private int computeTabIndex() {
		int tabIndex = mWifiTabIndex;
		
		NetworkInfo netInfo;
		ConnectivityManager connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		if (mWifiManager.isWifiEnabled()) {
			netInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (netInfo.isConnectedOrConnecting()) {
				if (getWifiUtils().onWirelessDirect()) {
					tabIndex = mWirelessDirectTabIndex;
				}
			} else {
				tabIndex = mWirelessDirectTabIndex;
			}
		} else {

		}
		return tabIndex;
	}
	
	@Override
	public void onBackPressed () {
		boolean allowFinish = true;
		if (allowFinish) {
			super.onBackPressed();
		}
	}
	
	@Override
	public void pluginDisconnected(PrintPlugin plugin) {
		super.pluginDisconnected(plugin);
		checkIfPluginsInstalled();
	}
	
	private void checkIfPluginsInstalled() {
		if (getPrintPlugins().isEmpty()) {
			FragmentTransaction ft = getFragmentManager().beginTransaction();
			DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__no_plugins, null);
			ft.add(dialog,getResources().getResourceName(R.id.dialog_id__no_plugins));
			ft.commit();
		}
	}
}
