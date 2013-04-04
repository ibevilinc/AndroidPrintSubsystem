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
import java.util.HashMap;
import java.util.ListIterator;
import java.util.UUID;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.android.print.ui.NFCUtils.NFCPrinterSelection;
import com.hp.android.printplugin.support.PrintServiceStrings;

public class FragmentWifi extends ListFragment implements OnQueryTextListener, DiscoveryBroadcastReceiver.DiscoveryNotification, PrinterPicker.NFCPrinterSelection, PrintPluginActivity.PluginUpdateCallback {

	static final String TAG = "WifiList";
			
	private static int DISCOVERY_TIMEOUT_DELAY;
	
	private HashMap<String, DiscoveredPrinter> mPrinterHash = new HashMap<String, DiscoveredPrinter>();
	private ArrayList<DiscoveredPrinter> mPrinterList = new ArrayList<DiscoveredPrinter>();
	private ArrayList<DiscoveredPrinter> mFilteredPrinterList = new ArrayList<DiscoveredPrinter>();
	private IntentFilter mIntentFilter = null;
	private Handler mHandler = new Handler();
	private DiscoveredPrinter mCurrentPrinter = null;
	private WifiManager mWifiManager;
	private boolean mConnectedToWirelessDirect;
	private String mLastSSID;
	private Uri mDiscoveryURI;
		
	private class PrinterAdapter extends BaseAdapter {
		
		private LayoutInflater mInflator;
		public PrinterAdapter(Context context) {
			mInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			return mFilteredPrinterList.size();
		}

		@Override
		public Object getItem(int position) {
			return mFilteredPrinterList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return mFilteredPrinterList.get(position).hashCode();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			if (convertView == null) {
				view = mInflator.inflate(R.layout.simple_list_item_2_single_choice, null);
			} else {
				view = convertView;
			}
			TextView nameText    = (TextView)view.findViewById(R.id.text1);
			TextView addressText = (TextView)view.findViewById(R.id.text2);
			
			RadioButton radioButton = (RadioButton)view.findViewById(R.id.radio);
			
			final DiscoveredPrinter printer = mFilteredPrinterList.get(position);
			nameText.setText(printer.getDisplayValue());
			addressText.setText(printer.mAddress);
			radioButton.setChecked(printer.equals(mCurrentPrinter));
			return view;
		}
	}
	
    // *****************************************************
    //
    // Fragment base code
    //
    // *****************************************************
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        setListAdapter(new PrinterAdapter(getActivity()));
        
        final Resources resources = getResources();
        mWifiManager = (WifiManager)getActivity().getSystemService(Context.WIFI_SERVICE);
        DISCOVERY_TIMEOUT_DELAY = resources.getInteger(R.integer.default_wifi_search_timeout);

    	mIntentFilter = new IntentFilter();
    	mIntentFilter.addAction(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_RESOLVED);
    	mIntentFilter.addAction(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_REMOVED);
    }
    
    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	View view = inflater.inflate(R.layout.wifi_printer_list, null);
    	return view;
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	PrinterPicker context = (PrinterPicker)getActivity();
    	context.addPluginUpdateCallback(this);
    	if (((PrinterPicker)getActivity()).wifiCheck(0)) {
    		mConnectedToWirelessDirect = context.getWifiUtils().onWirelessDirect();
    		boolean ssidChanged = checkIfSSIDChanged();
            Bundle currentPrinterData = getArguments();
            if (currentPrinterData != null) {
            	//TODO
            	if (mLastSSID.equals(currentPrinterData.getString(getResources().getResourceName(R.id.bundle_key__selected_printer_ssid)))) {
            		mCurrentPrinter = new DiscoveredPrinter(getResources(), currentPrinterData, null);
            	} else {
            		mCurrentPrinter = null;
            	}
            }
    		if (getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.printer_picker_dialog_no_wifi_printers_found)) == null)
    			startDiscovery(ssidChanged);
    	} else {
    		mLastSSID = null;
    		mPrinterList.clear();
    		mFilteredPrinterList.clear();
    		BaseAdapter adapter = (BaseAdapter) getListAdapter();
    		if (adapter != null) {
    			adapter.notifyDataSetChanged();
    		}
    	}
    		
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	((PrintPluginActivity)getActivity()).removePluginUpdateCallback(this);
    	stopDiscovery();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	
    }
    
    private void printerSelected(DiscoveredPrinter printer) {
		mCurrentPrinter = printer;
		Intent intent = new Intent();
		intent.putExtras(mCurrentPrinter.getBundle(getResources(), mLastSSID));
		getActivity().setResult(Activity.RESULT_OK, intent);
		getActivity().finish();
    }
    	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		printerSelected((DiscoveredPrinter)l.getAdapter().getItem(position));
	}

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.activity_printer_selection, menu);
        MenuItem menuItem =  menu.findItem(R.id.menu_search);
        menuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
			
			@Override
			public boolean onMenuItemActionExpand(MenuItem item) {
				return true;
			}
			
			@Override
			public boolean onMenuItemActionCollapse(MenuItem item) {
				onQueryTextChange(null);
				return true;
			}
		});
        mSearchView = (SearchView)menuItem.getActionView();
        mSearchView.setOnQueryTextListener(this);
    }
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
            	stopDiscovery();
            	startDiscovery(true);
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    // *****************************************************
    //
    // Discovery related code
    //
    // *****************************************************
    
	private class DiscoveryTimeoutRunnable implements Runnable {

		@Override
		public void run() {
			// stop discovery
			stopDiscovery();
			// did we find anything?
			if (mPrinterList.isEmpty()) {
				// nope, need to display an dialog
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.printer_picker_dialog_no_wifi_printers_found, null);
				dialog.setTargetFragment(FragmentWifi.this, 0);
				ft.add(dialog,getResources().getResourceName(R.id.printer_picker_dialog_no_wifi_printers_found));
				ft.commit();
			}
		}
	}
	
	private DiscoveryBroadcastReceiver mDiscoveryReceiver = null;
	private DiscoveryTimeoutRunnable mDiscoveryTimeout = new DiscoveryTimeoutRunnable();
	
	private boolean checkIfSSIDChanged() {
		boolean result = true;
		WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
		String currentSSID = WifiUtils.removeDoubleQuotes(wifiInfo.getSSID());
		do {
			if (mLastSSID == null) {
				continue;
			}
			if (currentSSID == null) {
				continue;
			}
			result = !mLastSSID.equals(currentSSID);
		} while(false);
		mLastSSID = currentSSID;		
		return result;
	}
    
    void startDiscovery(boolean refresh) {

    	if (refresh) {
    		mPrinterList.clear();
    		mFilteredPrinterList.clear();
    		mPrinterHash.clear();
        	if (mCurrentPrinter != null) {
        		addPrinter(mCurrentPrinter);
        	}
    	}
    	
    	getView().findViewById(R.id.search_progress).setVisibility(View.VISIBLE);

    	
		BaseAdapter adapter = (BaseAdapter) getListAdapter();
		adapter.notifyDataSetChanged();
    	if (mDiscoveryReceiver != null) {
    		getActivity().unregisterReceiver(mDiscoveryReceiver);
    	}
    	mDiscoveryURI = new Uri.Builder().scheme(PrintServiceStrings.SCHEME_DISCOVERY).path(UUID.randomUUID().toString()).build();
		mDiscoveryReceiver = new DiscoveryBroadcastReceiver((PrintPluginActivity)getActivity(), this);
		getActivity().registerReceiver(mDiscoveryReceiver, mIntentFilter);
		((PrintPluginActivity)getActivity()).sendRequestToPlugins(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_START_DISCOVERY, mDiscoveryURI), null);
    	mHandler.postDelayed(mDiscoveryTimeout, DISCOVERY_TIMEOUT_DELAY);
    }
    
    private void stopDiscovery() {

    	getView().findViewById(R.id.search_progress).setVisibility(View.GONE);
    	mHandler.removeCallbacks(mDiscoveryTimeout);
    	if (mDiscoveryReceiver != null) {
    		getActivity().unregisterReceiver(mDiscoveryReceiver);
    		mDiscoveryReceiver = null;
    		((PrintPluginActivity)getActivity()).sendRequestToPlugins(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_STOP_DISCOVERY, mDiscoveryURI), null);
    		mDiscoveryURI = null;
    	}	
    }
    
    // *****************************************************
    //
    // Discovery related code
    //
    // *****************************************************
    
	void addPrinter(final DiscoveredPrinter printer) {
		if (printer == null)
			return;
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				DiscoveredPrinter previousEntry = mPrinterHash.get(printer.mAddress);
				// check to see if we already know about this printer
				if (previousEntry == null) {
					// new printer, add it to the list
					mPrinterHash.put(printer.mAddress, printer);
					mPrinterList.add(printer);
					// does the printer match the current query
					if (printer.equals(mCurrentPrinter)) {
						// speed up future checks by the adapter
						mCurrentPrinter = printer;
					}
					if (printerMatchesCurrentQuery(printer)) {
						mFilteredPrinterList.add(printer);
					}
				} else {
					previousEntry.update(printer);
				}
				((BaseAdapter) getListAdapter()).notifyDataSetChanged();
			}
		});
	}
	
	void removePrinter(final String address) {
		if (TextUtils.isEmpty(address))
			return;
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// remove the specified printer
				final DiscoveredPrinter printer = mPrinterHash.remove(address);
				// did we find it?
				if (printer != null) {
					// found it, remove from global list
					mPrinterList.remove(printer);
					// remove from filtered list if present
					if (mFilteredPrinterList.remove(printer)) {
						// notify the adapter of the change
						((BaseAdapter) getListAdapter()).notifyDataSetChanged();
					}
				}
			}
		});
	}
	
    // *****************************************************
    //
    // Printer search code
    //
    // *****************************************************
	
    private SearchView mSearchView = null;
	private String mQueryText = "";

    private boolean printerMatchesCurrentQuery(final DiscoveredPrinter printer) {
    	return (TextUtils.isEmpty(mQueryText) || printer.matchesQuery(mQueryText));
    }
    
    private void updateFilteredList() {
		
		// build up list of matching printers
		ArrayList<DiscoveredPrinter> newList = new ArrayList<DiscoveredPrinter>();
		ListIterator<DiscoveredPrinter> iter = mPrinterList.listIterator();
		while(iter.hasNext()) {
			DiscoveredPrinter printer = iter.next();
			if (printerMatchesCurrentQuery(printer))
				newList.add(printer);
		}
		
		// update the filtered list and notify the adapter
		mFilteredPrinterList = newList;
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
    }

	@Override
	public boolean onQueryTextChange(String query) {
		// avoid null string
		if (query == null) query = "";
		// did the query change?
		if (mQueryText.equals(query)) {
			return false;
		}
		// update the query
		mQueryText = query;
		
		updateFilteredList();
		
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
        // When the search is "committed" by the user, then hide the keyboard so the user can
        // more easily browse the list of results.
        if (mSearchView != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();
            if (mFilteredPrinterList.isEmpty()) {
            	if (validNetworkAddress(query)) {
            		DiscoveredPrinter manualPrinter = new DiscoveredPrinter(null, query);
            		ArrayList<PrintPlugin> plugins = ((PrinterPicker)getActivity()).getAvailablePlugins();
            		for(PrintPlugin plugin : plugins) {
            			manualPrinter.addSupportedPlugin(plugin);
            		}
            		
            		printerSelected(manualPrinter);
            	} else {
            	}
            } else {
            }
        }
        return true;
	}
	
	public boolean validNetworkAddress(String query) {
		if (Pattern.matches(Patterns.IP_ADDRESS.pattern(), query)) {
			return true;
		}
		return false;
	}
 
	@Override
	public void printerFound(DiscoveredPrinter newPrinter) {
		if (mConnectedToWirelessDirect) {
			// TODO use wireless direct name
//			WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
//			if (newPrinter.mAddress.equals(getResources().getString(R.string.hp_wireless_direct_address))) {
//				newPrinter.setSSID(wifiInfo.getSSID());
//				if (mCurrentPrinter == null) {
//					mCurrentPrinter = newPrinter;
//				}
//			}
		}
		addPrinter(newPrinter);
	}

	@Override
	public void printerRemoved(String address) {
		removePrinter(address);
	}

	
    // *****************************************************
    //
    // NFC Tap to select
    //
    // *****************************************************
	@Override
	public void NFCPrinterSelected(final NFCPrinterSelection printer) {
		if (printer != null) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					DiscoveredPrinter selectedPrinter = null;
					for(DiscoveredPrinter entry : mPrinterList) {
						if (printer.mAddress.equals(entry.mAddress) &&
							(
							((entry.mBonjourDomainName == null) && (entry.mHostname == null)) ||
							 (!TextUtils.isEmpty(entry.mHostname) && printer.mHostname.equals(entry.mHostname)) ||
							 (!TextUtils.isEmpty(entry.mBonjourDomainName) && printer.mBonjourDomainName.equals(entry.mBonjourDomainName)))) {
							selectedPrinter = entry;
							break;
						}
					}
					if (selectedPrinter == null) {
						selectedPrinter = new DiscoveredPrinter(null, printer.mAddress);
	            		ArrayList<PrintPlugin> plugins = ((PrinterPicker)getActivity()).getAvailablePlugins();
	            		for(PrintPlugin plugin : plugins) {
	            			selectedPrinter.addSupportedPlugin(plugin);
	            		}
					}
					printerSelected(selectedPrinter);
				}
				
			});
		}		
	}

	@Override
	public void pluginAvailable(final PrintPlugin plugin, boolean available) {
		if (available) {
			boolean discoveryRunning = (getView().findViewById(R.id.search_progress).getVisibility() == View.VISIBLE);
			if (discoveryRunning) {
				mHandler.removeCallbacks(mDiscoveryTimeout);
				((PrintPluginActivity)getActivity()).sendRequestToPlugin(plugin, new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_START_DISCOVERY, mDiscoveryURI), null);
		    	mHandler.postDelayed(mDiscoveryTimeout, DISCOVERY_TIMEOUT_DELAY);
			}
		} else {
			getActivity().runOnUiThread(new Runnable() {

				@Override
				public void run() {
					ArrayList<DiscoveredPrinter> toRemove = new ArrayList<DiscoveredPrinter>();
					for(DiscoveredPrinter entry : mPrinterList) {
						entry.removeSupportedPlugin(plugin);
						if (!entry.isSupported()) {
							toRemove.add(entry);
						}
					}
					for(DiscoveredPrinter entry : toRemove) {
						mPrinterList.remove(entry);
						mPrinterHash.remove(entry.mAddress);
					}
					updateFilteredList();
				}
			});
		}
	}
}
