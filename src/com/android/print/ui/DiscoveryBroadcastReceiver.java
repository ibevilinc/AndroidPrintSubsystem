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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hp.android.printplugin.support.PrintServiceStrings;

class DiscoveryBroadcastReceiver extends BroadcastReceiver {

	public interface DiscoveryNotification {
		public void printerFound(DiscoveredPrinter printer);
		public void printerRemoved(String address);
	}

	private final DiscoveryNotification mCallback;
	private final PrintPluginActivity mContext;
	
	public DiscoveryBroadcastReceiver(PrintPluginActivity context, DiscoveryNotification callback) {
		mContext  = context;
		mCallback = callback;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (mCallback != null) {
			String action = intent.getAction();
			PrintPlugin plugin = mContext.findPlugin(intent.getStringExtra(PrintServiceStrings.PACKAGE_NAME));
			if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_RESOLVED.equals(action) && (plugin != null)) {
				mCallback.printerFound(new DiscoveredPrinter(context.getResources(), intent.getExtras(), plugin));
			} else if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_DEVICE_REMOVED.equals(action)) {
				mCallback.printerRemoved(intent.getStringExtra(PrintServiceStrings.DISCOVERY_DEVICE_ADDRESS));
			}
		}
	}
	
}