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
   limitations under the License..
 */
package com.android.print.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;

public abstract class PrintPluginPreferenceActivity extends Activity {
	
	protected abstract void setupFragment();
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		setupFragment();
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            	onBackPressed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
