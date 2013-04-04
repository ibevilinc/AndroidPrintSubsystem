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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.UUID;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hp.android.printplugin.support.PrintServiceStrings;
import com.hp.pps.print.sdk.DocumentInfo;
import com.hp.pps.print.sdk.PageInfo;
import com.hp.pps.print.sdk.PrintSetupMessages;

public class PrintJob {
	public final PrintPlugin mPrintPlugin;
	public final UUID mJobID;
	public final Intent mJobIntent;
	public final Bundle mPrinterInfo;
	private final Bundle mJobSettings;
	public final DocumentInfo mPrintDoc;
	private Bundle mStatus = null;
	private final Resources mResources;
	private boolean mSentToPlugin = false;
	private PrintStatusItem mStatusItem;
	private final boolean mCleanupTemporaryFiles;

	private static boolean verifySetting(String key, Bundle inputBundle, Bundle outputBundle) {
		String setting = inputBundle.getString(key);
		if (TextUtils.isEmpty(setting)) {
			return false;
		}
		outputBundle.putString(key, setting);
		return true;
	}
	
	public static LinkedList<PrintJob> parseJobRequest(PrintPlugin printPlugin, Context context, Bundle jobSettings) {

		ArrayList<DocumentInfo> printDocs = null;
		Bundle commonJobSettings = new Bundle();
		boolean result = false;
		Bundle printerInfo = null;
		
		final Resources resources = context.getResources();
		
		do {

			if ((resources == null) || (jobSettings == null)) {
				continue;
			}
			
			printerInfo = jobSettings.getBundle(resources.getResourceName(R.id.bundle_key__selected_printer_info));
			if (printerInfo == null) {
				continue;
			}
			
			if (!verifySetting(PrintServiceStrings.PRINTER_ADDRESS_KEY, jobSettings, commonJobSettings)) {
				continue;
			}
			if (!verifySetting(PrintServiceStrings.MEDIA_SIZE_NAME, jobSettings, commonJobSettings)) {
				continue;
			}
			if (!verifySetting(PrintServiceStrings.PRINT_COLOR_MODE, jobSettings, commonJobSettings)) {
				continue;
			}
			if (!verifySetting(PrintServiceStrings.SIDES, jobSettings, commonJobSettings)) {
				continue;
			}
			if (!verifySetting(PrintServiceStrings.ORIENTATION_REQUESTED, jobSettings, commonJobSettings)) {
				continue;
			}
			commonJobSettings.putBoolean(PrintServiceStrings.PRINT_TO_FILE,
					jobSettings.getBoolean(PrintServiceStrings.PRINT_TO_FILE));
			
			commonJobSettings.putInt(PrintServiceStrings.COPIES,
					jobSettings.getInt(PrintServiceStrings.COPIES, 1));
			
			commonJobSettings.putString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION,
					jobSettings.getString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION));
			
			commonJobSettings.putString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION_NAME,
					jobSettings.getString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION_NAME));
			
			// get the document list
			printDocs = jobSettings.getParcelableArrayList(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY);
			// check that we have documents
			if ((printDocs == null) || printDocs.isEmpty()) {
				continue;
			}
			
			result = true;
		} while(false);
		
		if (!result) {
			return null;
		}
		
		LinkedList<PrintJob> printList = new LinkedList<PrintJob>();
		
		// check that we can print everything
		ListIterator<DocumentInfo> iter = printDocs.listIterator();
		
		while(iter.hasNext()) {
			DocumentInfo doc = iter.next();
			if ((doc != null) && (doc.getNumPages() != 0)) {
				UUID jobID = UUID.randomUUID();
				Bundle printSettings = new Bundle(commonJobSettings);
				
				if (jobSettings.getBoolean(PrintServiceStrings.PRINT_TO_FILE)) {
					File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
					File outputFile = new File(dir,jobID.toString());
					printSettings.putString(PrintServiceStrings.PRINTER_ADDRESS_KEY, outputFile.getAbsolutePath());
				}

				printSettings.putString(PrintServiceStrings.PRINT_JOB_HANDLE_KEY, jobID.toString());
				String[] fileList = new String[doc.getNumPages()];
				for(int i = 0; i < doc.getNumPages(); i++) {
					fileList[i] = doc.getPage(i).mPageUri.getPath();
				}
				printSettings.putString(PrintServiceStrings.PRINT_DOCUMENT_CATEGORY, ((doc.getDocumentType() == DocumentInfo.Type.PHOTO) ? PrintServiceStrings.PRINT_DOCUMENT_CATEGORY__PHOTO : PrintServiceStrings.PRINT_DOCUMENT_CATEGORY__DOCUMENT));
				printSettings.putStringArray(PrintServiceStrings.PRINT_FILE_LIST, fileList);
				printSettings.putString(PrintServiceStrings.ORIENTATION_REQUESTED, PrintServiceStrings.ORIENTATION_AUTO);
				printSettings.putString(PrintServiceStrings.FULL_BLEED, ((doc.getDocumentType() == DocumentInfo.Type.PHOTO) ? PrintServiceStrings.FULL_BLEED_ON : PrintServiceStrings.FULL_BLEED_OFF));
				printSettings.putBoolean(PrintServiceStrings.FILL_PAGE, (doc.getDocumentType() == DocumentInfo.Type.PHOTO));
				printSettings.putBoolean(PrintServiceStrings.FIT_TO_PAGE, (doc.getDocumentType() == DocumentInfo.Type.DOCUMENT));
				if (doc.getDocumentType() == DocumentInfo.Type.PHOTO) {
					printSettings.putString(PrintServiceStrings.SIDES, PrintServiceStrings.SIDES_SIMPLEX);
				}

				printList.add(new PrintJob(printPlugin, context, printerInfo, jobID, printSettings, doc));
			}
		}
		
		if (printList.isEmpty()) {
			printList = null;
		}
		
		return printList;
	}

	private PrintJob(PrintPlugin printPlugin, Context context, Bundle printerInfo, UUID jobID, Bundle jobSettings, DocumentInfo printDoc) {

		boolean deleteFiles = true;
		mPrintPlugin = printPlugin;
		Intent jobIntent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_PRINT);
		jobIntent.setData(new Uri.Builder().scheme(PrintServiceStrings.SCHEME_JOB_ID).path(jobID.toString()).build());
		jobIntent.setType(printDoc.getMimeType());
		jobIntent.putExtras(jobSettings);
		
		mJobSettings = jobSettings;
		mJobID = jobID;
		mPrintDoc = printDoc;
		mJobIntent = jobIntent;
		mPrinterInfo = printerInfo;
		mResources = context.getResources();
		mStatusItem = new PrintStatusItem(mJobID.toString(), getJobDescription(), getPrinterName(), mResources);
		
		if (BuildConfig.DEBUG) {
			deleteFiles = !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(mResources.getString(R.string.settings_key__keep_temporary_files), mResources.getBoolean(R.bool.default_value__key_temporary_files));
		}
		
		mCleanupTemporaryFiles = deleteFiles;
	}
	
	public void jobFinished() {
		if (mCleanupTemporaryFiles && !mJobSettings.getBoolean(PrintServiceStrings.PRINT_TO_FILE)) {
			File currentFile;
			PageInfo currentPage;
			for(int i = 0; i < mPrintDoc.getNumPages(); i++) {
				currentPage = mPrintDoc.getPage(i);
				currentFile = new File(currentPage.mPageUri.getPath());				
				if (currentPage.isTemporary()) {
					File parentDir = currentFile.getParentFile();
					currentFile.delete();
					parentDir.delete();
				}
			}
		}
	} 
	
	public Bundle getFailedBundle() {
		Bundle failedBundle = new Bundle();
		failedBundle.putString(PrintServiceStrings.PRINT_JOB_HANDLE_KEY, mJobID.toString());
		failedBundle.putString(PrintServiceStrings.PRINT_JOB_DONE_RESULT, PrintServiceStrings.JOB_DONE_ERROR);
		return(failedBundle);
	}
	
	public synchronized void updateStatus(Bundle status) {
		mStatus = status;
		mStatusItem.update(mStatus);
	}
	
	public synchronized PrintStatusItem getStatus() {
		return mStatusItem;
	}
	
	public synchronized PrintStatusItem getStatusCopy() {
		return new PrintStatusItem(mStatusItem);
	}
	
	public Bundle getStatusBundle() {
		return mStatus;
	}
	
	public void cancel() {
		mStatusItem.cancel();
	}
	
	public boolean wasUserCancelled() {
		return mStatusItem.wasUserCancelled();
	}
	
	public boolean sentToPlugin() {
		return mSentToPlugin;
	}
	
	public void updateSentToPlugin() {
		mSentToPlugin = true;
	}
	
	private String getPrinterName() {
		String printerName = null;
		do {
			if (mPrinterInfo == null) {
				continue;
			}
			printerName = mPrinterInfo.getString(mResources.getResourceName(R.id.bundle_key__selected_printer_name));
			if (!TextUtils.isEmpty(printerName)) {
				continue;
			}
			printerName = mPrinterInfo.getString(mResources.getResourceName(R.id.bundle_key__selected_printer_model));
			if (!TextUtils.isEmpty(printerName)) {
				continue;
			}
			printerName = mPrinterInfo.getString(mResources.getResourceName(R.id.bundle_key__selected_printer_address));
			if (!TextUtils.isEmpty(printerName)) {
				continue;
			}
		} while(false);
		
		if (TextUtils.isEmpty(printerName)) {
			printerName = mResources.getString(R.string.printer_name_unknown);
		}
		return printerName;
	}
	
	private String getJobDescription() {
		String description;
		do {
			description = mPrintDoc.getDocumentDescription();
			if (!TextUtils.isEmpty(description))
				continue;
			if (mPrintDoc.getNumPages() == 1) {
				File page = new File(mPrintDoc.getPage(0).mPageUri.getPath());
				description = page.getName();
			}
			if (!TextUtils.isEmpty(description))
				continue;
			description = mJobSettings.getString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION_NAME);
			if (!TextUtils.isEmpty(description))
				continue;
			description = mResources.getString(R.string.no_description_available);
		} while(false);
		
		return description;
	}
}