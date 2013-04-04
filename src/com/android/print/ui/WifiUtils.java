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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

public class WifiUtils {

	private final String[] mPrefixes;
	private final ConnectivityManager mConnManager;
	private final WifiManager mWifiManager;
	
	public WifiUtils(Context context) {
		mPrefixes = context.getResources().getStringArray(R.array.wireless_direct_prefixes);
		mConnManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
	}
	
	public boolean isWirelessDirectSSID(String ssid) {
		boolean wirelessDirect = false;
		if (!TextUtils.isEmpty(ssid) && (mPrefixes != null)) {
			for(int i = 0; (!wirelessDirect && (i < mPrefixes.length)); i++) {
				wirelessDirect = ssid.contains(mPrefixes[i]);
			}
		}
		return wirelessDirect;
	}
	
	public boolean onWirelessDirect() {
		String ssid = null;
		boolean wirelessDirect = false;
		if (mConnManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
			ssid = WifiUtils.removeDoubleQuotes(mWifiManager.getConnectionInfo().getSSID());
			wirelessDirect = isWirelessDirectSSID(ssid);
		}
		return wirelessDirect;
	}
	
    static String removeDoubleQuotes(String string) {
    	if (TextUtils.isEmpty(string)) {
    		return "";
    	}
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }
}
