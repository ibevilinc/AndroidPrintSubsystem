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

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import com.hp.android.printplugin.support.PrintServiceStrings;

public abstract class PluginPreferenceFragment extends PreferenceFragment {
	
	private final String mAction;
	private final int mPluginPrefCategory;
	private final int mPluginPrefResource;
	private final int mPluginAvailableSummary;
	private final int mPluginNotAvailableSummary;
	
    private static final String PACKAGE_SCHEME = "package";
    private PackageReceiver mPackageReceiver = new PackageReceiver();

	public PluginPreferenceFragment(String action, int pluginPrefResource, int pluginPrefCategory, int availableSummary, int notAvailableSummary) {
		super();
		mAction = action;
		mPluginPrefResource = pluginPrefResource;
		mPluginPrefCategory = pluginPrefCategory;
		mPluginAvailableSummary = availableSummary;
		mPluginNotAvailableSummary = notAvailableSummary;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(mPluginPrefResource);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		setupPlugins();

		// listen for package changes
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
		filter.addDataScheme(PACKAGE_SCHEME);
		getActivity().registerReceiver(mPackageReceiver, filter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		getActivity().unregisterReceiver(mPackageReceiver);
	}
	
	void setupPlugins() {
		
        PreferenceCategory pluginPrefs = (PreferenceCategory)findPreference(getString(mPluginPrefCategory));
        
        pluginPrefs.removeAll();
        PackageManager pm = getActivity().getPackageManager();

        List<ResolveInfo> pluginPackageList = pm.queryIntentServices(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE), PackageManager.GET_META_DATA);
        
        if (pluginPackageList != null) {
        	for(ResolveInfo pluginInfo : pluginPackageList) {
        		Preference pluginPref = new Preference(getActivity());
        		pluginPref.setIcon(pluginInfo.serviceInfo.loadIcon(pm));
        		pluginPref.setTitle(pluginInfo.serviceInfo.loadLabel(pm));
        		
                Intent intent = new Intent(mAction).addCategory(Intent.CATEGORY_DEFAULT).setPackage(pluginInfo.serviceInfo.packageName);
        		ResolveInfo pluginData = pm.resolveActivity(intent, PackageManager.GET_META_DATA);
        		pluginPref.setIntent(intent);
        		pluginPref.setSummary(((pluginData != null) ? mPluginAvailableSummary : mPluginNotAvailableSummary)); 
        		pluginPref.setSelectable((pluginData != null));
        		pluginPref.setEnabled((pluginData != null));
        		pluginPrefs.addPreference(pluginPref);
        	}
        }

        if (pluginPrefs.getPreferenceCount() == 0) {
    		Preference pluginPref = new Preference(getActivity());
    		pluginPref.setTitle(R.string.preference_title__no_plugins_installed);
    		pluginPref.setSummary(R.string.preference_summary__no_plugins_installed);
    		pluginPref.setEnabled(false);
    		pluginPref.setSelectable(false);
    		pluginPrefs.addPreference(pluginPref);    		
        }
	}
	
    private class PackageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	setupPlugins();
        }
    }
	
}
