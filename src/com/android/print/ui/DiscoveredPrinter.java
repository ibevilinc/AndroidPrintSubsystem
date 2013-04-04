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

import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;

import com.hp.android.printplugin.support.PrintServiceStrings;

class DiscoveredPrinter {
	public String mAddress           = null;
	public String mBonjourName       = null;
	public String mBonjourDomainName = null;
	public String mHostname          = null;
	public String mModel             = null;
	public String mSSID              = null;
	public String mName              = null;
	
	private ArrayList<PrintPlugin> mSupportedPlugins = new ArrayList<PrintPlugin>();
	private PrintPlugin mSelectedPlugin = null;
	
	public DiscoveredPrinter(final String model, final String address) {
		mModel   = model;
		mAddress = address;
	}
	
	public DiscoveredPrinter(String ssid, String hostname, String bonjourDomainName, String bonjourName) {
		mSSID = ssid;
		if (!TextUtils.isEmpty(hostname)) {
			mHostname = hostname;
		}
		if (!TextUtils.isEmpty(bonjourDomainName)) {
			mBonjourDomainName = bonjourDomainName;
		}
		if (!TextUtils.isEmpty(bonjourName)) {
			mBonjourName = bonjourName;
		}
	}
	
	public DiscoveredPrinter(Resources resources, Bundle data, PrintPlugin plugin) {
		if (data.containsKey(resources.getResourceName(R.id.bundle_key__printer_setup_bundle))) {
			mAddress = data.getString(resources.getResourceName(R.id.bundle_key__selected_printer_address));
			mBonjourName = data.getString(resources.getResourceName(R.id.bundle_key__selected_printer_bonjour_name));
			mBonjourDomainName = data.getString(resources.getResourceName(R.id.bundle_key__selected_printer_bonjour_domain_name));
			mHostname = data.getString(resources.getResourceName(R.id.bundle_key__selected_printer_hostname));
			mModel = data.getString(resources.getResourceName(R.id.bundle_key__selected_printer_model));
			mName = data.getString(resources.getResourceName(R.id.bundle_key__selected_printer_name));
		} else {
			mAddress = data.getString(PrintServiceStrings.DISCOVERY_DEVICE_ADDRESS);
			mBonjourName = data.getString(PrintServiceStrings.DISCOVERY_DEVICE_BONJOUR_NAME);
			mBonjourDomainName = data.getString(PrintServiceStrings.DISCOVERY_DEVICE_BONJOUR_DOMAIN_NAME);
			mHostname = data.getString(PrintServiceStrings.DISCOVERY_DEVICE_HOSTNAME);
			mModel = data.getString(PrintServiceStrings.DISCOVERY_DEVICE_NAME);
		}
		if (plugin != null) {
			mSupportedPlugins.add(plugin);
			mSelectedPlugin = plugin;
		}
	}
	
	public String getDisplayValue() {
		if (!TextUtils.isEmpty(mSSID)) {
			return mSSID;
		} else if (!TextUtils.isEmpty(mBonjourName)) {
			return mBonjourName;
		} else if (!TextUtils.isEmpty(mModel)) {
			return mModel;
		} else if (!TextUtils.isEmpty(mName)) {
			return mName;
		} else 
		return mAddress;
	}
	
	public void setSSID(String ssid) {
		mSSID = ssid;
	}
	
	public void update(DiscoveredPrinter other) {
		if (!TextUtils.isEmpty(other.mAddress)) {
			mAddress = other.mAddress;
		}
		if (!TextUtils.isEmpty(other.mModel)) {
			mModel = other.mModel;
		}
		if (!TextUtils.isEmpty(other.mName)) {
			mName = other.mName;
		}
		if (!TextUtils.isEmpty(other.mSSID)) {
			mSSID = other.mSSID;
		}
		if (!TextUtils.isEmpty(other.mBonjourName)) {
			mBonjourName = other.mBonjourName;
		}
		if (!TextUtils.isEmpty(other.mBonjourDomainName)) {
			mBonjourDomainName = other.mBonjourDomainName;
		}
		if (!TextUtils.isEmpty(other.mHostname)) {
			mHostname = other.mHostname;
		}
		for(PrintPlugin plugin : other.mSupportedPlugins) {
			if (!mSupportedPlugins.contains(plugin)) {
				mSupportedPlugins.add(plugin);
			}
		}
	}
	
	public Bundle getBundle(Resources resources, String ssid) {
		Bundle data = new Bundle();
		data.putBoolean(resources.getResourceName(R.id.bundle_key__printer_setup_bundle), true);
		data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_ssid), ssid);
		data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_name), getDisplayValue());
		data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_address), mAddress);
		data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_model), mModel);
		if (!TextUtils.isEmpty(mBonjourName)) {
			data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_bonjour_name), mBonjourName);
		}
		if (!TextUtils.isEmpty(mBonjourDomainName)) {
			data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_bonjour_domain_name), mBonjourDomainName);
		}
		if (!TextUtils.isEmpty(mHostname)) {
			data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_hostname), mHostname);
		}
		int index = 0;
		PrintPlugin selectedPlugin = getSelectedPlugin();
		String[] supportedPlugins = new String[mSupportedPlugins.size()];
		if (!mSupportedPlugins.isEmpty() && (selectedPlugin != null)) {
			supportedPlugins[index++] = selectedPlugin.mPackageName;
		}
		for(PrintPlugin plugin : mSupportedPlugins) {
			if (plugin != selectedPlugin) {
				supportedPlugins[index++] = plugin.mPackageName;
			}
		}
		data.putString(resources.getResourceName(R.id.bundle_key__selected_printer_plugin_package), ((selectedPlugin != null) ? selectedPlugin.mPackageName : ""));
		data.putStringArray(resources.getResourceName(R.id.bundle_key__selected_printer_supported_plugin_packages), supportedPlugins);	
		return data;
	}
	
	public boolean equals(DiscoveredPrinter other) {
		// same object
		if (other == this) {
			return true;
		}
		// null check
		if (other == null) {
			return false;
		}
		
		boolean checkPerformed = false;
		
		// check the address
		if (!TextUtils.isEmpty(mAddress) &&
			!TextUtils.isEmpty(other.mAddress)) {
			checkPerformed = true;
			if (!mAddress.equals(other.mAddress)) {
				return false;
			}
		}
		
		// we don't check the name for a few reasons
		// - The bonjour name could be automatically changed to avoid conflicts
		// - Depending on which discovery (mDNS/SNMP) the name could be different
		// - We use a different name when using Wireless Direct
		// check the name next
		if (!TextUtils.isEmpty(mBonjourDomainName) &&
			!TextUtils.isEmpty(other.mBonjourDomainName)) {
			checkPerformed = true;
			if (!mBonjourDomainName.equals(other.mBonjourDomainName)) {
				return false;
			}
		}
		
		if (!TextUtils.isEmpty(mHostname) &&
		    !TextUtils.isEmpty(other.mHostname)) {
			checkPerformed = true;
			if (!mHostname.equals(other.mHostname)) {
				return false;
			}
		}

		// everything checks out, probably the same printer
		return checkPerformed;
	}
	
	public boolean matchesQuery(String query) {
		do {
			if (TextUtils.isEmpty(query)) {
				continue;
			}
			if ((mModel != null) && mModel.contains(query)) {
				continue;
			}
			if ((mBonjourName != null) && mBonjourName.contains(query)) {
				continue;
			}
			if (mAddress.contains(query)) {
				continue;
			}
			if ((mBonjourDomainName != null) && mBonjourDomainName.contains(query)) {
				continue;
			}
			if ((mHostname != null) && mHostname.contains(query)) {
				continue;
			}
			return false;
		} while(false);
		return true;
	}
	
	public PrintPlugin getSelectedPlugin() {
		// no plugin selected
		if (mSelectedPlugin == null) {
			// look for a vendor plugin
			for(PrintPlugin plugin : mSupportedPlugins) {
				if (plugin.isVendorPlugin()) {
					mSelectedPlugin = plugin;
					break;
				}
			}
			// settle for anything
			if ((mSelectedPlugin == null) && !mSupportedPlugins.isEmpty()) {
				mSelectedPlugin = mSupportedPlugins.get(0); 
			}
		}
		// return result
		return mSelectedPlugin;
	}
	
	public void setSelectedPlugin(PrintPlugin selectedPlugin) {
		if (!mSupportedPlugins.contains(selectedPlugin)) {
			mSupportedPlugins.add(selectedPlugin);
		}
		mSelectedPlugin = selectedPlugin;
	}
	
	public List<PrintPlugin> getSupportedPlugins() {
		return new ArrayList<PrintPlugin>(mSupportedPlugins);
	}
	
	public void addSupportedPlugin(PrintPlugin plugin) {
		// sort plugins by category
		int index = 0;
		if (plugin.isClassPlugin()) {
			index = mSupportedPlugins.size();
		} else {
			if (TextUtils.isEmpty(mModel) || !mModel.contains(plugin.getVendor())) {
				for(PrintPlugin entry : mSupportedPlugins) {
					if (entry.isClassPlugin())
						break;
					index++;
				}
			}
		}
		mSupportedPlugins.add(index, plugin);
	}
	
	public void removeSupportedPlugin(PrintPlugin plugin) {
		mSupportedPlugins.remove(plugin);
		if (mSelectedPlugin == plugin) {
			mSelectedPlugin = null;
		}
	}
	
	public boolean isSupported() {
		return !mSupportedPlugins.isEmpty();
	}
}