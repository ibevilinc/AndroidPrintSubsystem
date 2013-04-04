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
import java.util.UUID;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.hp.android.printplugin.support.PrintServiceStrings;

public class FragmentNoPrinterSelected extends Fragment implements View.OnClickListener, DiscoveryBroadcastReceiver.DiscoveryNotification, PrintPluginActivity.PluginUpdateCallback {
	
	static final String TAG = "FragmentNoPrinterSelected";
	
	private Uri mDiscoveryURI = null;
	
	private ArrayList<UsedPrinter> mLastUsedPrinters = null;
	private String mSearchSSID = null;
	
	private static class BackgroundSearch extends AsyncTask<Void, Integer, Void> {
		
		private FragmentNoPrinterSelected mContext = null;
		private boolean mDone = false;
		private final int mTimeout;
		
		public BackgroundSearch(FragmentNoPrinterSelected context) {
			mTimeout = context.getResources().getInteger(R.integer.used_printer_search_timeout);
			mContext = context;
		}

		@Override
		protected Void doInBackground(Void... params) {
			try {
				Thread.sleep(mTimeout);
			} catch (InterruptedException e) {
			}
			
			return null;
		}
		
		protected synchronized void onPostExecute(Void result) {
			mDone = true;
			if (mContext != null) {
				mContext.searchComplete(true);
			}
		}
		
		public synchronized void detach() {
			mContext = null;
		}
		
		public synchronized void attach(FragmentNoPrinterSelected context) {
			mContext = context;
			if ((mContext != null) && mDone) {
				mContext.searchComplete(true);
			}
		}
	}
	
	private BackgroundSearch mLastUsedSearch = null;
	private int mBestFoundIndex = -1;
	
	private void searchComplete(final boolean startActivity) {
		final PrinterSetup context = (PrinterSetup)getActivity();
		context.runOnUiThread(new Runnable() {
			public void run() {
				context.removePluginUpdateCallback(FragmentNoPrinterSelected.this);
				mLastUsedSearch = null;
		    	if (mDiscoveryReceiver != null) {
		    		context.unregisterReceiver(mDiscoveryReceiver);
					context.sendRequestToPlugins(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_STOP_DISCOVERY, mDiscoveryURI), null);
		    		mDiscoveryReceiver = null;
		    	}
				if (mBestFoundIndex != -1) {
					UsedPrinter lastUsed = mLastUsedPrinters.get(mBestFoundIndex);
					context.getPrinterCapabilities(lastUsed.getBundle(getResources(), mSearchSSID));
				} else if (startActivity){
					startActivityForResult(new Intent(context, PrinterPicker.class), R.id.start_activity_id_printer_selection);
				}
				View view = getView();
				if (view != null) {
					view.findViewById(R.id.previous_printer_search_layout).setVisibility(View.GONE);
				}
			}
		});
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch(requestCode) {
			case R.id.start_activity_id_printer_selection: {
				((PrinterSetup)getActivity()).getPrinterCapabilities(data.getExtras());
				break;
			}
			default:
				break;
			}
		}
	}
	
	private DiscoveryBroadcastReceiver mDiscoveryReceiver = null;
	
	@Override
	public void onAttach(Activity activity) {
		final PrinterSetup context = (PrinterSetup)activity;
		super.onAttach(activity);
		if (mLastUsedSearch != null) {
			registerReceiver();
		} else if (!getRetainInstance() && getArguments().getBoolean(getResources().getResourceName(R.id.bundle_key__launch_printer_selection))) {
			WifiManager wifiManager = (WifiManager)activity.getSystemService(Context.WIFI_SERVICE);
			mSearchSSID = WifiUtils.removeDoubleQuotes(wifiManager.getConnectionInfo().getSSID());
			mLastUsedPrinters = UsedPrinterDB.getInstance(activity).getUsedPrinters(mSearchSSID);
			if (mLastUsedPrinters.isEmpty()) {
				if (context.getWifiUtils().onWirelessDirect()) {
					context.addPluginUpdateCallback(this);
					mLastUsedSearch = new BackgroundSearch(this);
				} else {
					searchComplete(true);
				}
			} else {
				mDiscoveryURI = new Uri.Builder().scheme(PrintServiceStrings.SCHEME_DISCOVERY).path(UUID.randomUUID().toString()).build();
				mLastUsedSearch = new BackgroundSearch(this);
			}
		}
	}
	
	private void registerReceiver() {
		if (mDiscoveryReceiver == null) {
			mDiscoveryReceiver = new DiscoveryBroadcastReceiver((PrintPluginActivity)getActivity(), this);
			IntentFilter intentFilter= new IntentFilter();
			intentFilter.addAction(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_RESOLVED);
    		getActivity().registerReceiver(mDiscoveryReceiver, intentFilter);
		}
	}
	
	private void unregisterReceiver() {
		if (mDiscoveryReceiver != null) {
    		getActivity().unregisterReceiver(mDiscoveryReceiver);
		}
		mDiscoveryReceiver = null;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		setRetainInstance(true);
	}
	
	@Override 
	public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.no_printer_selected, null);
		view.findViewById(R.id.no_printer_selected).setOnClickListener(this);
		if (mLastUsedSearch != null) {
			view.findViewById(R.id.previous_printer_search_layout).setVisibility(View.VISIBLE);
		}
		return view;
	}
	
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.activity_printer_options, menu);
    }
    
    @Override
    public void onDetach() {
    	super.onDetach();
    	if (mLastUsedSearch != null) {
    		mLastUsedSearch.detach();
    	}
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	if (mLastUsedSearch != null) {
    		mLastUsedSearch.detach();
    	}
    	unregisterReceiver();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	if (mDiscoveryURI != null) {
    		registerReceiver();
			((PrinterSetup)getActivity()).addPluginUpdateCallback(this);
    	}
    	if (mLastUsedSearch != null) {
    		mLastUsedSearch.attach(this);
    		// recheck for null since attaching might cause it to end
    		if ((mLastUsedSearch != null) && (mLastUsedSearch.getStatus() == AsyncTask.Status.PENDING)) {
				mLastUsedSearch.execute();
    		}
    	}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
		case R.id.menu_settings:
		case R.id.menu_about:
			if (mLastUsedSearch != null) {
				mLastUsedSearch.detach();
				mLastUsedSearch.cancel(true);
				mBestFoundIndex = -1;
			}
			searchComplete(false);
		default:
			break;
        }
        switch (item.getItemId()) {
		case R.id.menu_settings: {
			startActivity(new Intent(getActivity(), PrinterSetupPreferences.class));
			return true;
		}
		case R.id.menu_about: {
			startActivity(new Intent(getActivity(), PrinterSetupAbout.class));
			return true;
		}
		default:
			break;
        }
        return super.onOptionsItemSelected(item);
    }

	@Override
	public void onClick(View v) {

		switch(v.getId()) {
		case R.id.no_printer_selected: {
			if (mLastUsedSearch != null) {
				mLastUsedSearch.detach();
				mLastUsedSearch.cancel(true);
				mBestFoundIndex = -1;
			}
			searchComplete(true);
			break;
		}
		default:
		}
	}

	@Override
	public void printerFound(final DiscoveredPrinter printer) {
		getActivity().runOnUiThread(new Runnable() {
			
			private int getIndex() {
				int index = 0;
				for(UsedPrinter used : mLastUsedPrinters) {
					if (printer.equals(used)) {
						return index;
					}
					index++;
				}
				return -1;
			}
			
			public void run() {
				int index = getIndex();				
				if (index >= 0) {
					UsedPrinter usedPrinter = mLastUsedPrinters.get(index);
					usedPrinter.update(printer);
					if (index != mBestFoundIndex) {
						mBestFoundIndex = ((mBestFoundIndex < 0) ? index : Math.min(mBestFoundIndex, index));
					}
				}
			}
		});
	}

	@Override
	public void printerRemoved(String address) {		
	}

	@Override
	public void pluginAvailable(final PrintPlugin plugin, boolean available) {
		if (available) {
			if (mDiscoveryURI != null) {
				((PrinterSetup)getActivity()).sendRequestToPlugin(plugin, new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_START_DISCOVERY, mDiscoveryURI), null);
			} else {
				final String pluginSSID    = plugin.getWirelessDirectPrefix();
				final String pluginAddress = plugin.getWirelessDirectAddress();
				if (!TextUtils.isEmpty(pluginSSID) && pluginSSID.startsWith(pluginSSID)) {
					if (!TextUtils.isEmpty(pluginAddress)) {
						getActivity().runOnUiThread(new Runnable() {

							@Override
							public void run() {
								UsedPrinter selectedPrinter = new UsedPrinter(mSearchSSID, null, null, null, 0);
								selectedPrinter.mAddress = pluginAddress;
								selectedPrinter.addSupportedPlugin(plugin);
								mLastUsedPrinters.add(selectedPrinter);
								mBestFoundIndex = 0;
							}
						});
					}
				}
			}
		}

	}

}
