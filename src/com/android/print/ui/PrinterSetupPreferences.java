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

import android.os.Bundle;
import android.preference.PreferenceCategory;

import com.hp.android.printplugin.support.PrintServiceStrings;

public class PrinterSetupPreferences extends PrintPluginPreferenceActivity {

	@Override
	protected void setupFragment() {
		getFragmentManager().beginTransaction()
		.replace(android.R.id.content, new PrefsFragment()).commit();
	}
	
    public static class PrefsFragment extends PluginPreferenceFragment {
    	
    	public PrefsFragment() {
    		super(PrintServiceStrings.ACTION_PRINT_PLUGIN_SETTINGS,
    			  R.xml.preferences,
    			  R.string.settings_key__plugin_settings,
    			  R.string.preference_summary__touch_to_access_plugin_settings,
    			  R.string.preference_summary__no_plugin_settings_available);
    	}
    	
    	
        @Override
        public void onCreate(Bundle savedInstanceState) {
        	super.onCreate(savedInstanceState);
        	
        	if (!BuildConfig.DEBUG) {
                PreferenceCategory category = (PreferenceCategory)findPreference(getString(R.string.settings_key__app_settings));
                category.removePreference(category.findPreference(getString(R.string.settings_key__keep_temporary_files)));
        	}
        }
    }
}
