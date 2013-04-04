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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.android.print.ui.NFCUtils.NFCPrinterSelection;
import com.hp.android.printplugin.support.PrintServiceStrings;

public class FragmentWirelessDirect extends ListFragment implements OnQueryTextListener, PrinterPicker.NFCPrinterSelection, DiscoveryBroadcastReceiver.DiscoveryNotification {
	
	static final String TAG = "WifiDirect";
	
	private BroadcastReceiver mReceiver;
	private IntentFilter mFilter;
	private WifiManager mWifiManager;
	private Scanner mScanner;
	private AtomicBoolean mConnected = new AtomicBoolean(false);
	private String[] mSSIDPrefixes;

    private DetailedState mLastState;
    private WifiInfo mLastInfo;
    private boolean mWaitingForConnection = false;
    private SharedPreferences mPrefs;
	private DiscoveryBroadcastReceiver mDiscoveryReceiver = null;
	private Handler mHandler = new Handler();
	private ArrayList<DiscoveredPrinter> mFoundPrinters = new ArrayList<DiscoveredPrinter>();
	private DiscoveredPrinter mSelectedPrinter = null;
	private Runnable mDiscoveryTimeoutRunnable = new Runnable() {

		@Override
		public void run() {
			mSelectedPrinter = null;
			stopDiscovery();
			switch(mFoundPrinters.size()) {
			case 0: {
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id_could_not_find_wireless_direct_printer, null);
				dialog.setTargetFragment(FragmentWirelessDirect.this, 0);
				ft.add(dialog,getResources().getResourceName(R.id.dialog_id_could_not_find_wireless_direct_printer));
				ft.commit();
				break;
			}
			case 1: {
				mSelectedPrinter = mFoundPrinters.get(0);
				setSelectionResult();
				break;
			}
			default: {
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__multiple_printers_on_wireless_direct_network, null);
				dialog.setTargetFragment(FragmentWirelessDirect.this, 0);
				ft.add(dialog,getResources().getResourceName(R.id.dialog_id__multiple_printers_on_wireless_direct_network));
				ft.commit();
				break;
			}
			}
		}
	};
	
	public void selectPrinter(int entry) {
		
	}
        
    private static final int[] STATE_SECURED = {
    	R.attr.state_encrypted,
    };
    
    private static final int[] STATE_NONE = {};
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);
        setRetainInstance(true);
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		mFilter = new IntentFilter();
		mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
		mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);

		mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				handleEvent(context, intent);
			}
		};

		mSSIDPrefixes = getActivity().getResources().getStringArray(R.array.wireless_direct_prefixes);
		mWifiManager = (WifiManager)getActivity().getSystemService(Context.WIFI_SERVICE);
		mScanner = new Scanner(this);
		
        setListAdapter(new AccessPointAdapter(getActivity()));
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
	public void onResume() {
		super.onResume();
		mLastState = null;
		mLastInfo = null;
    	this.setEmptyText(null);
		getActivity().registerReceiver(mReceiver, mFilter);
    	if (((PrinterPicker)getActivity()).wifiCheck(1)) {
    		constructAccessPoints();
    	}
    	if (getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.dialog_id__connecting_to_wireless_direct)) != null) {
    		//startDiscovery();
    	}
	}
 
	@Override
	public void onPause() {
		super.onPause();
		stopDiscovery();
		getActivity().unregisterReceiver(mReceiver);
		mScanner.pause();
	}
	
	private AccessPoint mSelectedAccessPoint;
	
	private void selectedAccessPoint(AccessPoint selectedAP) {
		mSelectedAccessPoint = selectedAP;
		mWaitingForConnection = false;

		if ((mSelectedAccessPoint.mState != null) && (mSelectedAccessPoint.mState == NetworkInfo.DetailedState.CONNECTED)) {
			postConnectProcessing();
			return;
		}
		
		Bundle params = new Bundle();
		params.putString("ssid", mSelectedAccessPoint.ssid);
		params.putString("bssid",mSelectedAccessPoint.bssid);
		params.putInt("security", mSelectedAccessPoint.security);
		params.putSerializable("pskType", mSelectedAccessPoint.pskType);
		if (mSelectedAccessPoint.mConfig != null) {
			params.putParcelable("config", mSelectedAccessPoint.mConfig);
		}
		
		DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), 
					mPrefs.getBoolean(getResources().getString(R.string.settings_key__network_change_warning), getResources().getBoolean(R.bool.show_network_change_warning_default)) ?
							R.id.printer_picker_dialog_network_change_warning :
							R.id.printer_picker_dialog_connect_to_printer, params);
		
		dialog.setTargetFragment(FragmentWirelessDirect.this, 0);
		getFragmentManager().beginTransaction().add(dialog, dialog.getArguments().getString(getResources().getResourceName(R.id.bundle_key__dialog_request_name))).commit();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		selectedAccessPoint((AccessPoint)l.getAdapter().getItem(position));
	}
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
            	mAccessPoints.clear();
            	mFilteredAccessPoints.clear();
            	setEmptyText(null);
        		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
            	refreshAccessPoints();
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }

	private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN));
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                updateAccessPoints();
        } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
            SupplicantState state = (SupplicantState) intent.getParcelableExtra(
                    WifiManager.EXTRA_NEW_STATE);
            updateConnectionState(WifiInfo.getDetailedStateOf(state));
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                    WifiManager.EXTRA_NETWORK_INFO);
            mConnected.set(info.isConnected());
            updateAccessPoints();
            updateConnectionState(info.getDetailedState());
        } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
            updateConnectionState(null);
        }
	}
	
	private void updateAccessPoints() {
        // Safeguard from some delayed event handling
        if (getActivity() == null) return;

        final int wifiState = mWifiManager.getWifiState();

        final ArrayList<AccessPoint> accessPoints;
        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                // AccessPoints are automatically sorted with TreeSet.
            	accessPoints = constructAccessPoints();
            	break;
            default: {
            	accessPoints = new ArrayList<AccessPoint>();
            	break;
            }
            	 
        }
        getActivity().runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mAccessPoints = accessPoints;
				rebuildFilteredList();
				setEmptyText(getResources().getString(R.string.list_view_empty__no_wireless_direct_printers_in_range));
			}
        	
        });
	}
	
	private void updateWifiState(int state) {
        switch (state) {
        case WifiManager.WIFI_STATE_ENABLED:
            mScanner.resume();
            return; // not break, to avoid the call to pause() below
        }
        mScanner.pause();
	}

	private void updateConnectionState(DetailedState state) {
        /* sticky broadcasts can call this when wifi is disabled */
        if (!mWifiManager.isWifiEnabled()) {
            mScanner.pause();
            return;
        }

        if (state != null) {
        	if (state == DetailedState.OBTAINING_IPADDR) {
        		mScanner.pause();
        	} else {
        		mScanner.resume();
        	}
        	if (mWaitingForConnection) {
        		switch(state) {
        		case CONNECTED:
            		postConnectProcessing();
        			break;
        		case FAILED:
        			break;
        		default:
        			break;
        		}

        	}
        }
        mLastInfo = mWifiManager.getConnectionInfo();
        if (state != null) {
        	mLastState = state;
        }
        
        ListIterator<AccessPoint> iter = mAccessPoints.listIterator();
        while(iter.hasNext()) {
        	iter.next().update(mLastInfo, mLastState);
        }
		BaseAdapter adapter = (BaseAdapter)getListAdapter();
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}     
	}
	
	private void postConnectProcessing() {
		//TODO
		PrintPlugin matchedPlugin = null;
		boolean classDriverAvail = false;
		List<PrintPlugin> availablePlugins = ((PrintPluginActivity)getActivity()).getAvailablePlugins();
		for(PrintPlugin plugin : availablePlugins) {
			String prefix = plugin.getWirelessDirectPrefix();
			classDriverAvail |= plugin.isClassPlugin();
			if (!TextUtils.isEmpty(prefix) && mSelectedAccessPoint.ssid.startsWith(prefix)) {
				matchedPlugin = plugin;
				break;
			}
		}
		
		boolean runDiscovery = false;
		if (matchedPlugin != null) {
			String address = matchedPlugin.getWirelessDirectAddress();
			if (!TextUtils.isEmpty(address)) {
				mSelectedPrinter = new DiscoveredPrinter(mSelectedAccessPoint.ssid, null, null, null);
				mSelectedPrinter.mAddress = address;
				mSelectedPrinter.addSupportedPlugin(matchedPlugin);
        		setSelectionResult();
			} else {
				runDiscovery = true;
			}
		} else {
			if (classDriverAvail) {
				runDiscovery = true;
			} else {
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__no_plugins, null);
				dialog.setTargetFragment(FragmentWirelessDirect.this, 0);
				ft.add(dialog,getResources().getResourceName(R.id.dialog_id__no_plugins));
				ft.commit();
			}
		}
		if (runDiscovery) {
			// TODO run & track discovery
//			startDiscovery();
		}
	}
	
	 /** A restricted multimap for use in constructAccessPoints */
    private class Multimap<K,V> {
        private HashMap<K,List<V>> store = new HashMap<K,List<V>>();
        /** retrieve a non-null list of values with key K */
        List<V> getAll(K key) {
            List<V> values = store.get(key);
            return values != null ? values : Collections.<V>emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = store.get(key);
            if (curVals == null) {
                curVals = new ArrayList<V>(3);
                store.put(key, curVals);
            }
            curVals.add(val);
        }
    }
    
    private static class Summary {
        static String get(Context context, String ssid, DetailedState state) {
            String[] formats = context.getResources().getStringArray((ssid == null)
                    ? R.array.wifi_status : R.array.wifi_status_with_ssid);
            int index = state.ordinal();

            if (index >= formats.length || formats[index].length() == 0) {
                return null;
            }
            return String.format(formats[index], ssid);
        }

        static String get(Context context, DetailedState state) {
            return get(context, null, state);
        }
    }
	
	private class AccessPointAdapter extends BaseAdapter {
		
		private LayoutInflater mInflator;
		public AccessPointAdapter(Context context) {
			mInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			return mFilteredAccessPoints.size();
		}

		@Override
		public Object getItem(int position) {
			return mFilteredAccessPoints.get(position);
		}

		@Override
		public long getItemId(int position) {
			return mFilteredAccessPoints.get(position).hashCode();
		}
		
	    private String getSecurityString(final AccessPoint ap, boolean concise) {
//	        Context context = getContext();
	        switch(ap.security) {
	            case  R.id.wifi_security_type_eap:
	                return concise ? getActivity().getString(R.string.wifi_security_short_eap) :
	                	getActivity().getString(R.string.wifi_security_eap);
	            case  R.id.wifi_security_type_psk:
	                switch (ap.pskType) {
	                    case WPA:
	                        return concise ? getActivity().getString(R.string.wifi_security_short_wpa) :
	                            getActivity().getString(R.string.wifi_security_wpa);
	                    case WPA2:
	                        return concise ? getActivity().getString(R.string.wifi_security_short_wpa2) :
	                        	getActivity().getString(R.string.wifi_security_wpa2);
	                    case WPA_WPA2:
	                        return concise ? getActivity().getString(R.string.wifi_security_short_wpa_wpa2) :
	                        	getActivity().getString(R.string.wifi_security_wpa_wpa2);
	                    case UNKNOWN:
	                    default:
	                        return concise ? getActivity().getString(R.string.wifi_security_short_psk_generic)
	                                : getActivity().getString(R.string.wifi_security_psk_generic);
	                }
	            case R.id.wifi_security_type_wep:
	                return concise ? getActivity().getString(R.string.wifi_security_short_wep) :
	                	getActivity().getString(R.string.wifi_security_wep);
	            case R.id.wifi_security_type_none:
	            default:
	                return concise ? "" : getActivity().getString(R.string.wifi_security_none);
	        }
	    }
	    
	    private String getSummaryText(final AccessPoint ap) {

	        Context context = getActivity();
	        if (ap.mState != null) { // This is the active connection
	            return Summary.get(context, ap.mState);
	        } else if (ap.mRssi == Integer.MAX_VALUE) { // Wifi out of range
	            return context.getString(R.string.wifi_not_in_range);
	        } else if (ap.mConfig != null && ap.mConfig.status == WifiConfiguration.Status.DISABLED) {
	        	return context.getString(R.string.wifi_disabled_generic);
	        } else { // In range, not disabled.
	            StringBuilder summary = new StringBuilder();
	            if (ap.mConfig != null) { // Is saved network
//	                summary.append(context.getString(R.string.wifi_remembered));
	            }

	            if (ap.security != R.id.wifi_security_type_none) {
	                String securityStrFormat;
	                if (summary.length() == 0) {
	                    securityStrFormat = context.getString(R.string.wifi_secured_first_item);
	                } else {
	                    securityStrFormat = context.getString(R.string.wifi_secured_second_item);
	                }
	                summary.append(String.format(securityStrFormat, getSecurityString(ap, true)));
	            }

	            if (ap.mConfig == null && ap.wpsAvailable) { // Only list WPS available for unsaved networks

	                if (summary.length() == 0) {
	                    summary.append(context.getString(R.string.wifi_wps_available_first_item));
	                } else {
	                    summary.append(context.getString(R.string.wifi_wps_available_second_item));
	                }
	            }
	            return summary.toString();
//	            setSummary(summary.toString());
	        }
	    }

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			if (convertView == null) {
				view = mInflator.inflate(R.layout.wireless_direct_item, null);
			} else {
				view = convertView;
			}
			TextView nameText    = (TextView)view.findViewById(R.id.text1);
			TextView summaryText = (TextView)view.findViewById(R.id.text2);
			ImageView signal = (ImageView)view.findViewById(R.id.signal);

			final AccessPoint ap = (AccessPoint)getItem(position);
			nameText.setText(ap.ssid);

			String summary = getSummaryText(ap);
			summaryText.setText(summary);
			summaryText.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
						
			int level = ap.getLevel();
			if (level < 0) {
				signal.setImageDrawable(null);
			} else {
				signal.setImageLevel(level);
				signal.setImageResource(R.drawable.wifi_signal);
				signal.setImageState((ap.security != R.id.wifi_security_type_none) ? STATE_SECURED : STATE_NONE, true);
			}
			return view;
		}
	}
	
	private ArrayList<AccessPoint> mFilteredAccessPoints = new ArrayList<AccessPoint>();
	private ArrayList<AccessPoint> mAccessPoints = new ArrayList<AccessPoint>();
	
	private boolean isWirelessDirectAP(AccessPoint ap) {
		boolean result = false;
		if (ap == null)
			return result;
		if ((mSSIDPrefixes == null) || (mSSIDPrefixes.length == 0))
			return true;
		for(int i = 0; (!result && (i < mSSIDPrefixes.length)); i++) {
			result = ap.ssid.contains(mSSIDPrefixes[i]);
		}
		return result;
	}
	
	private ArrayList<AccessPoint> constructAccessPoints() {
		ArrayList<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        Multimap<String, AccessPoint> apMap = new Multimap<String, AccessPoint>();

        // TODO
        final List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
            	if (mWaitingForConnection && (config.networkId == mSelectedAccessPoint.networkId)) {
            		if (config.status == WifiConfiguration.Status.DISABLED) {
            			FragmentTransaction ft = getFragmentManager().beginTransaction();
            			Fragment fragment = getFragmentManager().findFragmentByTag(getResources().getResourceName(R.id.dialog_id__connecting_to_wireless_direct));
            			if (fragment != null) {
                			ft.remove(fragment);
            			}
            			fragment = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__failed_to_connect_to_wireless_direct, null);
            			ft.add(fragment, getResources().getResourceName(R.id.dialog_id__failed_to_connect_to_wireless_direct));
            			if (!ft.isEmpty()) {
            				ft.commit();
            			}
            		}
            	}
                AccessPoint accessPoint = new AccessPoint(config);
                accessPoint.update(mLastInfo, mLastState);
                apMap.put(accessPoint.ssid, accessPoint);
            }
        }
        final List<ScanResult> results = mWifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                // Ignore hidden and ad-hoc networks.
                if (result.SSID == null || result.SSID.length() == 0 ||
                        result.capabilities.contains("[IBSS]")) {
                    continue;
                }

                boolean found = false;
                for (AccessPoint accessPoint : apMap.getAll(result.SSID)) {
                    if (accessPoint.update(result)) {
                        found = true;
                        if (isWirelessDirectAP(accessPoint) && !accessPoints.contains(accessPoint)) {
                        	accessPoints.add(accessPoint);
                    	}
                        break;
                    }
                }
                if (!found) {
                	AccessPoint accessPoint = new AccessPoint(result);
                	if (isWirelessDirectAP(accessPoint) && !accessPoints.contains(accessPoint)) {
                		accessPoints.add(accessPoint);
                	}
                	apMap.put(accessPoint.ssid, accessPoint);

                }
            }
        }
        
        Collections.sort(accessPoints);
		return accessPoints;
	}

	private static class Scanner extends Handler {
		private int mRetry = 0;
	    private int WIFI_RESCAN_INTERVAL_MS;
		
		final private WeakReference<FragmentWirelessDirect> mContext;
		public Scanner(FragmentWirelessDirect context) {
			mContext = new WeakReference<FragmentWirelessDirect>(context);
	        WIFI_RESCAN_INTERVAL_MS = context.getResources().getInteger(R.integer.wifi_rescan_interval);
		}

		void resume() {
			if (!hasMessages(0)) {
				sendEmptyMessage(0);
			}
		}
		
		void pause() {
			mRetry = 0;
			removeMessages(0);
		}

		@Override
		public void handleMessage(Message message) {
			FragmentWirelessDirect context = mContext.get();
			if (context == null)
				return;
			if (context.mWifiManager.startScan()) {
				mRetry = 0;
			} else if (++mRetry >= 3) {
				mRetry = 0;
				return;
			}
			sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);
		}
	}

	private void refreshAccessPoints() {
		if (mWifiManager.isWifiEnabled()) {
			mScanner.pause();
			mScanner.resume();
		}
	}
	
    // *****************************************************
    //
    // Printer search code
    //
    // *****************************************************
	
    private SearchView mSearchView = null;
	private String mQueryText = "";

    private boolean accessPointMatchesCurrentQuery(final AccessPoint ap) {
    	return (TextUtils.isEmpty(mQueryText) || ap.ssid.contains(mQueryText));
    }
    
    private void rebuildFilteredList() {
		// build up list of matching printers
		ArrayList<AccessPoint> newList = new ArrayList<AccessPoint>();
		ListIterator<AccessPoint> iter = mAccessPoints.listIterator();
		while(iter.hasNext()) {
			AccessPoint ap = iter.next();
			if (accessPointMatchesCurrentQuery(ap))
				newList.add(ap);
		}
		
		// update the filtered list and notify the adapter
		mFilteredAccessPoints = newList;
		BaseAdapter adapter = (BaseAdapter)getListAdapter();
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
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
		
		rebuildFilteredList();

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
        }
        return true;
	}
	
	public void setWifiConfiguration(WifiConfiguration config, boolean configChanged) {
		if (config == null) {
			return;
		}
		int networkId = config.networkId;
		if  (networkId < 0) {
			mSelectedAccessPoint.networkId = mWifiManager.addNetwork(config);
			config.networkId = mSelectedAccessPoint.networkId;
			mSelectedAccessPoint.mConfig = config;
		} else if (configChanged) {
			mSelectedAccessPoint.mConfig = config;
			mWifiManager.updateNetwork(config);
		}
		if ((networkId < 0) || configChanged) {
			BaseAdapter adapter = (BaseAdapter)getListAdapter();
			if (adapter != null) {
				adapter.notifyDataSetChanged();
			}  
		}
		mWaitingForConnection = true;
		mWifiManager.disconnect();
		mWifiManager.enableNetwork(mSelectedAccessPoint.networkId, true);
		
		connectToPrinter();
	}
	
	private void connectToPrinter() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__connecting_to_wireless_direct, null);
		dialog.setTargetFragment(FragmentWirelessDirect.this, 0);
		ft.add(dialog,getResources().getResourceName(R.id.dialog_id__connecting_to_wireless_direct));
		ft.commit();
	}
	
	@Override
	public void printerFound(final DiscoveredPrinter printer) {
		if (Patterns.IP_ADDRESS.matcher(printer.mAddress).matches()) {
			getActivity().runOnUiThread(new Runnable() {

				@Override
				public void run() {
					boolean addPrinter = true;
					for(DiscoveredPrinter previousEntry : mFoundPrinters) {
						if (previousEntry.mAddress.equals(printer.mAddress)) {
							previousEntry.update(printer);
							addPrinter = false;
						}
					}
					if (addPrinter) {
						mFoundPrinters.add(printer);
					}
				}
			});
		}
	}
	
	private Uri mDiscoveryURI;
	
	@SuppressWarnings("unused")
	private void startDiscovery() {
		if (mDiscoveryReceiver == null) {
			PrinterPicker context = (PrinterPicker)getActivity();
			mFoundPrinters.clear();
			mDiscoveryReceiver = new DiscoveryBroadcastReceiver((PrintPluginActivity)getActivity(), this);
			mDiscoveryURI = new Uri.Builder().scheme(PrintServiceStrings.SCHEME_DISCOVERY).path(UUID.randomUUID().toString()).build();
			IntentFilter intentFilter= new IntentFilter();
			intentFilter.addAction(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_RESOLVED);
			context.registerReceiver(mDiscoveryReceiver, intentFilter);
			context.sendRequestToPlugins(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_START_DISCOVERY, mDiscoveryURI), null);
			mHandler.postDelayed(mDiscoveryTimeoutRunnable,context.getResources().getInteger(R.integer.wireless_direct_search_timeout));
		}
	}
	
	public void stopDiscovery() {
		if (mDiscoveryReceiver != null) {
			mHandler.removeCallbacks(mDiscoveryTimeoutRunnable);
			PrinterPicker context = (PrinterPicker)getActivity();
			context.unregisterReceiver(mDiscoveryReceiver);
			context.sendRequestToPlugins(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_STOP_DISCOVERY, mDiscoveryURI), null);
		}
		mDiscoveryReceiver = null;
	}
	
	private void setSelectionResult() {
		final Resources resources = getResources();
		Bundle selection = mSelectedPrinter.getBundle(getResources(), mSelectedAccessPoint.ssid);
		selection.putBoolean(resources.getResourceName(R.id.bundle_key__printer_setup_bundle), true);
		getActivity().setResult(Activity.RESULT_OK, new Intent().putExtras(selection));
		getActivity().finish();
	}

	@Override
	public void NFCPrinterSelected(final NFCPrinterSelection printer) {
		if (printer != null) {
			getActivity().runOnUiThread(new Runnable() {

				@Override
				public void run() {
					for(AccessPoint entry : mAccessPoints) {
						if (printer.mWirelessDirect.equals(entry.ssid)) {
							selectedAccessPoint(entry);
						}
					}
				}
			});
		}		
	}

	@Override
	public void printerRemoved(String address) {
		// TODO Auto-generated method stub
		
	}
}
