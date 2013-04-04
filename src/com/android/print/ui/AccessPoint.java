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

import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

class AccessPoint implements Comparable<Object> {
	    /** These values are matched in string arrays -- changes must be kept in sync */
	    enum PskType {
	        UNKNOWN,
	        WPA,
	        WPA2,
	        WPA_WPA2
	    }
	    
		String ssid;
		String bssid;
		int security;
		boolean wpsAvailable = false;
		int networkId;
		int mRssi;
//		ScanResult mScanResult;
	    AccessPoint.PskType pskType = PskType.UNKNOWN;
	    private WifiInfo mInfo;
	    WifiConfiguration mConfig;
	    DetailedState mState;
	    boolean mFaked = false;

		public AccessPoint(WifiConfiguration config) {
			loadConfig(config);
		}
		public AccessPoint(ScanResult result) {
			loadResult(result);
		}
		private static int getSecurity(ScanResult result) {
			if (result.capabilities.contains("WEP")) {
				return R.id.wifi_security_type_wep;
			} else if (result.capabilities.contains("PSK")) {
				return R.id.wifi_security_type_psk;
			} else if (result.capabilities.contains("EAP")) {
				return R.id.wifi_security_type_eap;
			}
			return R.id.wifi_security_type_none;
		}
		static int getSecurity(WifiConfiguration config) {
			if (config.allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
				return R.id.wifi_security_type_psk;
			}
			if (config.allowedKeyManagement.get(KeyMgmt.WPA_EAP) ||
					config.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
				return R.id.wifi_security_type_eap;
			}
			return (config.wepKeys[0] != null) ? R.id.wifi_security_type_wep : R.id.wifi_security_type_none;
		}
				
	    private void loadConfig(WifiConfiguration config) {
	        ssid = (config.SSID == null ? "" : WifiUtils.removeDoubleQuotes(config.SSID));
	        bssid = config.BSSID;
	        security = getSecurity(config);
	        networkId = config.networkId;
	        mRssi = Integer.MAX_VALUE;
	        mConfig = config;
	    }
	    
	    private void loadResult(ScanResult result) {
	        ssid = result.SSID;
	        bssid = result.BSSID;
	        security = getSecurity(result);
	        wpsAvailable = security != R.id.wifi_security_type_eap && result.capabilities.contains("WPS");
	        if (security == R.id.wifi_security_type_psk)
	            pskType = getPskType(result);
	        networkId = -1;
	        mRssi = result.level;
//	        mScanResult = result;
	    }
	    
	    private static AccessPoint.PskType getPskType(ScanResult result) {
	        boolean wpa = result.capabilities.contains("WPA-PSK");
	        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
	        if (wpa2 && wpa) {
	            return PskType.WPA_WPA2;
	        } else if (wpa2) {
	            return PskType.WPA2;
	        } else if (wpa) {
	            return PskType.WPA;
	        } else {
	            return PskType.UNKNOWN;
	        }
	    }
	     
		
		@Override
		public int compareTo(Object another) {
	        if (!(another instanceof AccessPoint)) {
	            return 1;
	        }
	        AccessPoint other = (AccessPoint) another;
	        // Active one goes first.
	        if (mInfo != other.mInfo) {
	            return (mInfo != null) ? -1 : 1;
	        }
	        // Reachable one goes before unreachable one.
	        if ((mRssi ^ other.mRssi) < 0) {
	            return (mRssi != Integer.MAX_VALUE) ? -1 : 1;
	        }
	        // Configured one goes before unconfigured one.
	        if ((networkId ^ other.networkId) < 0) {
	            return (networkId != -1) ? -1 : 1;
	        }
	        // Sort by signal strength.
	        int difference = WifiManager.compareSignalLevel(other.mRssi, mRssi);
	        if (difference != 0) {
	            return difference;
	        }
	        // Sort by ssid.
	        return ssid.compareToIgnoreCase(other.ssid);
		}
		
		void update(WifiInfo info, DetailedState state) {
	        if ((info != null) && (networkId >= 0) && (networkId == info.getNetworkId())) {
	            mRssi = info.getRssi();
	            mInfo = info;
	            mState = state;
	        } else if (mInfo != null) {
	            mInfo = null;
	            mState = null;
	        }
	    }
		
	    boolean update(ScanResult result) {
	        if (ssid.equals(result.SSID) && security == getSecurity(result)) {
	            if (WifiManager.compareSignalLevel(result.level, mRssi) > 0) {
	                int oldLevel = getLevel();
	                mRssi = result.level;
	                if (getLevel() != oldLevel) {
	                }
	            }
	            // This flag only comes from scans, is not easily saved in config
	            if (security == R.id.wifi_security_type_psk) {
	                pskType = getPskType(result);
	            }
	            return true;
	        }
	        return false;
	    }
	    
	    int getLevel() {
	        if (mRssi == Integer.MAX_VALUE) {
	            return -1;
	        }
	        return WifiManager.calculateSignalLevel(mRssi, 4);
	    }
	    
	    static String convertToQuotedString(String string) {
	    	return "\"" + string + "\"";
	    }
	}