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

import android.text.TextUtils;

public class UsedPrinter extends DiscoveredPrinter implements Comparable<UsedPrinter> {
	
	public final Long mTimestamp;
	
	public UsedPrinter(String ssid, String hostname, String bonjourDomainName, String bonjourName, long timestamp) {
		super(ssid,
			  (TextUtils.isEmpty(hostname) ? null : hostname),
			  (TextUtils.isEmpty(bonjourDomainName) ? null : bonjourDomainName),
			  (TextUtils.isEmpty(bonjourName) ? null : bonjourName));
		mTimestamp = Long.valueOf(timestamp);
	}

	@Override
	public int compareTo(UsedPrinter other) {
		// reverse compare
		return other.mTimestamp.compareTo(mTimestamp);
	}

	public String getDBHostname() {
		return (TextUtils.isEmpty(mHostname) ? "" : mHostname);
	}
	
	public String getDBBonjourDomainName() {
		return (TextUtils.isEmpty(mBonjourDomainName) ? "" : mBonjourDomainName);
	}
	
	public String getDBBonjourName() {
		return (TextUtils.isEmpty(mBonjourName) ? "" : mBonjourName);
	}
}