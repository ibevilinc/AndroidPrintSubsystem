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
import java.util.Collections;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

public class UsedPrinterDB extends SQLiteOpenHelper {

	private static UsedPrinterDB mDBInstance = null;
	private static final String TABLE_USED_PRINTERS = "used_printers";
	private static final String COLUMN_ID = "_id";
	private static final String COLUMN_HOST_NAME = "hostname";
	private static final String COLUMN_BONJOUR_DOMAIN_NAME = "bonjourdomainname";
	private static final String COLUMN_BONJOUR_NAME = "bonjourname";

	private static final String COLUMN_NETWORK_SSID = "ssid";
	private static final String COLUMN_LAST_USED = "lastused";
	
	private final int MAX_USED_PRINTERS;
	
	public static synchronized UsedPrinterDB getInstance(Context context) {
		if (mDBInstance == null) {
			mDBInstance = new UsedPrinterDB(context);
		}
		return mDBInstance;
	}
	
	private UsedPrinterDB(Context context) {
		super(context.getApplicationContext(),
			  context.getResources().getString(R.string.used_printer_database_name),
			  null,
			  context.getResources().getInteger(R.integer.used_printer_database_version));	
		MAX_USED_PRINTERS = context.getResources().getInteger(R.integer.max_used_printer_entries);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table " + TABLE_USED_PRINTERS +
				"("
					+       COLUMN_ID                  + " INTEGER PRIMARY KEY AUTOINCREMENT"
					+ "," + COLUMN_NETWORK_SSID        + " TEXT"
					+ "," + COLUMN_HOST_NAME           + " TEXT"
					+ "," + COLUMN_BONJOUR_DOMAIN_NAME + " TEXT"
					+ "," + COLUMN_BONJOUR_NAME        + " TEXT"
					+ "," + COLUMN_LAST_USED           + " LONG"
				+ ")");

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_USED_PRINTERS);
		onCreate(db);
	}
	
	public synchronized ArrayList<UsedPrinter> getUsedPrinters(String ssid) {
		
		ArrayList<UsedPrinter> usedPrinters = new ArrayList<UsedPrinter>();
		
		if (TextUtils.isEmpty(ssid)) {
			return usedPrinters;
		}
		
		String selection = COLUMN_NETWORK_SSID + " = ?";
		SQLiteDatabase db = getReadableDatabase();

		Cursor cursor = db.query(TABLE_USED_PRINTERS, null, selection, new String[] { ssid }, null,
				null, null);
		
		int hostnameIndex          = cursor.getColumnIndexOrThrow(COLUMN_HOST_NAME);
		int bonjourDomainNameIndex = cursor.getColumnIndexOrThrow(COLUMN_BONJOUR_DOMAIN_NAME);
		int bonjourNameIndex       = cursor.getColumnIndexOrThrow(COLUMN_BONJOUR_NAME);
		int timestampIndex         = cursor.getColumnIndexOrThrow(COLUMN_LAST_USED);
		
		if (cursor.moveToFirst()) {
			do {
				UsedPrinter printer = new UsedPrinter(null,
						 cursor.getString(hostnameIndex),
						 cursor.getString(bonjourDomainNameIndex),
						 cursor.getString(bonjourNameIndex),
						 cursor.getLong(timestampIndex));
				usedPrinters.add(printer);
			} while(cursor.moveToNext());
		}
		cursor.close();
		db.close();
		
		Collections.sort(usedPrinters);
		return usedPrinters;
	}

	public synchronized void updateUsedPrinters(String ssid, String hostname, String bonjourDomainName, String bonjourName) {
		
		// need to have the ssid and at either the hostname or bonjourDomainName to store the entry
		if (TextUtils.isEmpty(ssid) ||
			(TextUtils.isEmpty(hostname) && TextUtils.isEmpty(bonjourDomainName))) {
			return;
		}
		
		UsedPrinter oldData = null;
		UsedPrinter lastUsed = new UsedPrinter(ssid, hostname, bonjourDomainName, bonjourName, System.currentTimeMillis());
		ArrayList<UsedPrinter> usedPrinters = getUsedPrinters(lastUsed.mSSID);
		int index = usedPrinters.indexOf(lastUsed);
		if (index < 0) {
			usedPrinters.add(0, lastUsed);
		} else {
			// make sure we've got the most data possible
			oldData = usedPrinters.get(index);
			lastUsed.update(oldData);
			
			// update the entry
			usedPrinters.set(index, lastUsed);
		}
		
		ContentValues values = new ContentValues();
		values.put(COLUMN_NETWORK_SSID,        lastUsed.mSSID);
		values.put(COLUMN_HOST_NAME,           lastUsed.getDBHostname());
		values.put(COLUMN_BONJOUR_DOMAIN_NAME, lastUsed.getDBBonjourDomainName());
		values.put(COLUMN_BONJOUR_NAME,        lastUsed.getDBBonjourName());
		values.put(COLUMN_LAST_USED,           lastUsed.mTimestamp);
		
		SQLiteDatabase db = getWritableDatabase();
		// did we add or replace an entry
		if (index < 0) {
			// add new entry
			db.insert(TABLE_USED_PRINTERS, null, values);
		} else {
			// update existing entry
			db.update(TABLE_USED_PRINTERS,
					  values,
					  COLUMN_NETWORK_SSID + " = ?" +
							  " AND " + COLUMN_HOST_NAME + "= ?" +
							  " AND " + COLUMN_BONJOUR_DOMAIN_NAME + "= ?",
					  new String[]{oldData.mSSID, oldData.getDBHostname(), oldData.getDBBonjourDomainName()});
		}

		// drop any excess entries
		for(index = MAX_USED_PRINTERS; index < usedPrinters.size(); index++) {
			oldData = usedPrinters.get(index);
			db.delete(TABLE_USED_PRINTERS,
					  COLUMN_NETWORK_SSID + " = ?" +
							  " AND " + COLUMN_HOST_NAME + "= ?" +
							  " AND " + COLUMN_BONJOUR_DOMAIN_NAME + "= ?",
					  new String[]{oldData.mSSID, oldData.getDBHostname(), oldData.getDBBonjourDomainName()});
		}
		db.close();
	}
}
