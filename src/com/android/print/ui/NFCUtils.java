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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.tech.NfcA;
import android.os.Parcelable;
import android.text.TextUtils;
//import android.util.Log;

public class NFCUtils {

	private final Activity mContext;
	private final NfcAdapter mAdapter;
	
	public NFCUtils(Activity context) {
		mContext = context;
//		mAdapter = NfcAdapter.getDefaultAdapter(mContext);
		mAdapter = null;
	}
	
	public static class NFCPrinterSelection {
		public final String mAddress;
		public final String mHostname;
		public final String mBonjourDomainName;
		public final String mWirelessDirect;
		
		private NFCPrinterSelection(String address, String hostname, String bonjourDomainName, String wirelessDirect) {
			mAddress           = address;
			mHostname          = hostname;
			mBonjourDomainName = bonjourDomainName;
			mWirelessDirect    = wirelessDirect;
		}
		
		public static NFCPrinterSelection parse(String data) {
			NFCPrinterSelection selection = null;
			
			if (!TextUtils.isEmpty(data)) {
				int i;
				String[] elems = data.split("\\|");
				for(i = 0; i < elems.length; i++) {
					if (elems[i] == null)
						elems[i] = "";
				}
				if (i == 4) {
					selection = new NFCPrinterSelection(elems[0], elems[1], elems[2], elems[3]);
				}
				else {
				}

			}
			
			return selection;
		}
	};
	
	public void enable() {

		if (mAdapter != null) {
			PendingIntent nfcPendingIntent = PendingIntent.getActivity(
					mContext, 0, new Intent(mContext, mContext.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

			IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
			try {
				ndef.addDataType("*/*");    /* Handles all MIME based dispatches.
			                                       You should specify only the ones that you need. */
			}
			catch (MalformedMimeTypeException e) {
			}
			IntentFilter[] intentFilters = new IntentFilter[] {ndef, };

			String[][] techList = new String[][] { new String[] { NfcA.class.getName() } };

			mAdapter.enableForegroundDispatch(mContext, nfcPendingIntent, intentFilters, techList);
		}

	}
	
	public void disable() {

		if (mAdapter != null) {
			mAdapter.disableForegroundDispatch(mContext);
		}
	}
	
	public NFCPrinterSelection processIntent(Intent intent) {
		NFCPrinterSelection printerSelection = null;
		do {
			if (intent == null) {
				continue;
			}
			
			String action = intent.getAction();
			if (action == null) {
				continue;
			}

			if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
				continue;
			}

			Parcelable[] nfcMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if ((nfcMessages != null) && (nfcMessages.length > 0)) {
				NdefMessage msg = (NdefMessage)nfcMessages[0];

				NdefRecord[] records = msg.getRecords();
				for(NdefRecord rec : records) {
					byte[] payload = rec.getPayload();
					if ((rec.getTnf() == NdefRecord.TNF_WELL_KNOWN) &&
							Arrays.equals(rec.getType(), NdefRecord.RTD_TEXT) &&
							(payload.length > 0)) {
						String textEncoding = ((payload[0] & 0200) == 0) ? "UTF-8" : "UTF-16";
						int languageCodeLength = payload[0] & 0077;
						if ((payload.length - languageCodeLength - 1) >= 0) {

							try {
								String text = new String(payload,
										languageCodeLength + 1,
										payload.length - languageCodeLength - 1,
										textEncoding);
								
								printerSelection = NFCPrinterSelection.parse(text);
							} catch (UnsupportedEncodingException e) {
							}

						}
					}
					if (printerSelection != null) {
						break;
					}
				}
			}
		
		} while(false);
		
		return printerSelection;
	}
}
