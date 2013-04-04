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

import android.app.Fragment;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;

public class FragmentPrinterPreselected extends Fragment {

	private Handler mHandler = new Handler();
	private boolean mHandlerDone = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (mHandlerDone) {
			requestCapabilities();
		} else {
			mHandler.postDelayed(new Runnable() {

				@Override
				public void run() {
					mHandlerDone = true;
					if (!isDetached()) {
						requestCapabilities();
					}
				}
			}, 500);
		}
	}
	
	private void requestCapabilities() {
		PrinterSetup context = (PrinterSetup)getActivity();
		DiscoveredPrinter printer = new DiscoveredPrinter(null, getArguments().getString(getResources().getResourceName(R.id.bundle_key__selected_printer_address)));
		printer.mName = getArguments().getString(getResources().getResourceName(R.id.bundle_key__selected_printer_name));
		ArrayList<PrintPlugin> availablePlugins = context.getAvailablePlugins();
		for(PrintPlugin plugin : availablePlugins) {
			printer.addSupportedPlugin(plugin);	
		}
        WifiManager wifiManager = (WifiManager)getActivity().getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		String currentSSID = WifiUtils.removeDoubleQuotes(wifiInfo.getSSID());
		context.getPrinterCapabilities(printer.getBundle(getResources(), currentSSID));
	}
}
