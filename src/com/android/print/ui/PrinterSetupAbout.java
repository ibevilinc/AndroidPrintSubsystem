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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;

import com.hp.android.printplugin.support.PrintServiceStrings;

public class PrinterSetupAbout extends PrintPluginPreferenceActivity {
	
	@Override
	protected void setupFragment() {
		getFragmentManager().beginTransaction()
		.replace(android.R.id.content, new PrefsFragment()).commit();
	}
	
	public static class PrefsFragment extends PluginPreferenceFragment {
		
		public PrefsFragment() {
			super(PrintServiceStrings.ACTION_PRINT_PLUGIN_ABOUT,
				  R.xml.about,
				  R.string.about_key__plugin_info,
				  R.string.preference_summary__touch_to_access_plugin_information,
				  R.string.preference_summary__no_plugin_information_available);
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
		
			Preference appVersion = findPreference(getActivity().getString(R.string.about_key__application_version));
			
			PackageManager pm = getActivity().getPackageManager();
			PackageInfo info;
			try {
				info = pm.getPackageInfo(getActivity().getPackageName(), PackageManager.GET_META_DATA);
				appVersion.setSummary(info.versionName);
			} catch (NameNotFoundException e) {
			}
		}
		
	}
}
