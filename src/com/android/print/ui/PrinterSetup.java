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
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.MenuItem;

import com.hp.android.printplugin.support.PrintServiceStrings;
import com.hp.pps.print.sdk.DocumentInfo;
import com.hp.pps.print.sdk.PageInfo;
import com.hp.pps.print.sdk.PrintContext;
import com.hp.pps.print.sdk.PrintSetupMessages;

public class PrinterSetup extends PrintPluginActivity {
	
	static final String TAG = "PrinterSetup";
	
	public interface PrintServiceNotification {
		void updatePrintServiceMessenger(Messenger printServiceMessenger);
	};
	
	private Resources mResources;
	private Handler mHandler = new Handler();
	
	private Messenger mClientMessenger = null;
	private Messenger mPrintServiceMessenger = null;

	private Bundle mSelectedPrinter         = null;
	private Bundle mSelectedPrinterCaps     = null;
	private Bundle mSelectedPrinterSettings = null;
	private Bundle mRequestedPrintSettings  = null;
	private ArrayList<PrintPlugin> mSupportedPlugins = null;
	private ArrayList<DocumentInfo> mSelectedDocumentList = null;
	private PrintPlugin mSelectedPrinterPlugin = null;
	
	private enum StartAction {
		NONE,
		PRESELECTED,
	};
    		
	private Runnable mFailedOpRunnable = new Runnable() {

		@Override
		public void run() {
			FragmentTransaction ft = getFragmentManager().beginTransaction();
			Fragment fragment = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_get_caps_progress));
			if (fragment != null)
				ft.remove(fragment);
			DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.printer_setup_dialog_plugin_op_timeout, null);
			ft.add(dialog, mResources.getResourceName(R.id.printer_setup_dialog_plugin_op_timeout));
			ft.commit();
		}
	};
	
	private Runnable mProcessCapsRunnable = new Runnable() {
		@Override
		public void run() {
			if (checkCapabilities()) {
				capabilitiesVerified();
			} else {
				capabilitiesInvalid();
			}
		}
	};
	
	private Runnable mQueuePrintJobRunnable = new Runnable() {
		@Override
		public void run() {
			if (checkPrintSettings()) {
				queuePrintJob();
			} else {
				printSettingsInvalid();
			}
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case R.id.start_activity_id_printer_selection: {
			if (resultCode == Activity.RESULT_OK) {
				// printer selected, proceed to get its capabilities
				getPrinterCapabilities(data.getExtras());
			} else {
				// nothing was selected, put up no printer selected fragment
				mSelectedPrinter = null;
				mSelectedPrinterCaps = null;
				mSelectedPrinterSettings = null;
				Bundle args = new Bundle();
				args.putBoolean(getResources().getResourceName(R.id.bundle_key__launch_printer_selection), false);
				Fragment fragment = new FragmentNoPrinterSelected();
				fragment.setArguments(args);
				getFragmentManager().beginTransaction()
					.replace(R.id.fragment_container, fragment)
				.commit();
			}
			break;
		}
		default:
			break;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.printer_setup);
		mResources = getResources();
		StartAction startingAction = StartAction.NONE;
		
		setResult(RESULT_CANCELED);
		
        Intent intent = getIntent();
        if (intent != null) {
        	if (PrintSetupMessages.PRINT_SETUP_ACTION.equals(intent.getAction())) {
        		Bundle extras = intent.getExtras();
        		if (extras != null) {
        			mRequestedPrintSettings = extras;
        			if (extras.containsKey(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY)) {
             			mSelectedDocumentList = extras.getParcelableArrayList(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY);
             		} else if (extras.containsKey(PrintSetupMessages.BUNDLE_KEY__PRINT_SETUP_CLIENT_MESSENGER)) {
            			mClientMessenger = (Messenger)extras.getParcelable(PrintSetupMessages.BUNDLE_KEY__PRINT_SETUP_CLIENT_MESSENGER);
            			try {
            				mClientMessenger.send(Message.obtain(null, PrintSetupMessages.MSG_ID__CLIENT_ACK));
            			} catch(RemoteException e) {
            				mClientMessenger = null;
            			}
            		}
        			String[] allowedPackages = mResources.getStringArray(R.array.packages_allowed_to_preselect);
        			List<String> allowedPackagesList = Arrays.asList(allowedPackages);
        			String requestingApp = extras.getString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION);
        			if (!TextUtils.isEmpty(requestingApp) && allowedPackagesList.contains(requestingApp)) {
        				String address = extras.getString(getString(R.string.bundle_key__preselected_printer_address));
        				String name = extras.getString(getString(R.string.bundle_key__preselected_printer_name));
        				
        				if (!TextUtils.isEmpty(address)) {
        					mSelectedPrinter = new Bundle();
        					mSelectedPrinter.putString(mResources.getResourceName(R.id.bundle_key__selected_printer_address), address);
        					if (!TextUtils.isEmpty(name)) {
            					mSelectedPrinter.putString(mResources.getResourceName(R.id.bundle_key__selected_printer_name), name);
        					}
        					startingAction = StartAction.PRESELECTED;
        				}
        			}
        		}
        	} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
         		mSelectedDocumentList = new ArrayList<DocumentInfo>();
         		DocumentInfo doc = new DocumentInfo(intent.getType(), (intent.getType().startsWith("image") ? DocumentInfo.Type.PHOTO : DocumentInfo.Type.DOCUMENT));
         		doc.setDocumentDescription(getString(R.string.activity_label__print_file_receiver));
         		doc.addPage(new PageInfo(intent.getData()));
         		mSelectedDocumentList.add(doc);
        	 } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
        		mSelectedDocumentList = new ArrayList<DocumentInfo>();
         		DocumentInfo doc = new DocumentInfo(intent.getType(), (intent.getType().startsWith("image") ? DocumentInfo.Type.PHOTO : DocumentInfo.Type.DOCUMENT));
         		doc.setDocumentDescription(getString(R.string.activity_label__print_file_receiver));
        		doc.addPage(new PageInfo((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM)));
        		mSelectedDocumentList.add(doc);
        	} else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
        		ArrayList<Uri> fileList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        		mSelectedDocumentList = new ArrayList<DocumentInfo>();
          		ListIterator<Uri> iter = fileList.listIterator();
        		String mimeType = intent.getType();
        		
        		if (mimeType.startsWith("image")) {
        			DocumentInfo doc = new DocumentInfo(intent.getType(), DocumentInfo.Type.PHOTO);
        			doc.setDocumentDescription(getString(R.string.activity_label__print_files_receiver));
            		while(iter.hasNext()) {
            			doc.addPage(new PageInfo(iter.next()));
            		}
            		mSelectedDocumentList.add(doc);
        		} else {
            		while(iter.hasNext()) {
            			DocumentInfo doc = new DocumentInfo(intent.getType(), DocumentInfo.Type.DOCUMENT);
            			doc.addPage(new PageInfo(iter.next()));
                		mSelectedDocumentList.add(doc);
            		}
        		}
        	}
        	// generate a fake print settings request
        	if ((mRequestedPrintSettings == null) && (mSelectedDocumentList != null)) {
        		PrintContext printContext = new PrintContext(this, null);
        		DocumentInfo.Type type = null;
        		boolean allTheSame = true;
        		for(DocumentInfo entry : mSelectedDocumentList) {
        			if (type == null) {
        				type = entry.getDocumentType();
        			} else {
        				allTheSame &= (entry.getDocumentType() == type);
        			}
        		}
        		printContext.setDocumentType(allTheSame ? type : DocumentInfo.Type.DOCUMENT);
        		mRequestedPrintSettings = printContext.startFilePrintSession(mSelectedDocumentList).getExtras();
                printContext.destroyPrintContext();
        	}
        }
	
		if (savedInstanceState != null) {
			final Resources resources = getResources();
			String selectedPrinterKey = resources.getResourceName(R.id.bundle_key__current_selected_printer);
			String printerCapsKey    = resources.getResourceName(R.id.bundle_key__current_printer_capabilities);
			String jobSettingsKey    = resources.getResourceName(R.id.bundle_key__current_job_settings);
			if (savedInstanceState.containsKey(selectedPrinterKey)) {
				mSelectedPrinter = (Bundle)savedInstanceState.getParcelable(selectedPrinterKey);
				loadPluginInformation();
				if (savedInstanceState.containsKey(printerCapsKey)) {
					mSelectedPrinterCaps = (Bundle)savedInstanceState.getParcelable(printerCapsKey);
					if (savedInstanceState.containsKey(jobSettingsKey)) {
						mSelectedPrinterSettings = (Bundle)savedInstanceState.getParcelable(jobSettingsKey);
					}
				}
			}
		}
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		
		processPlugins();
		
		if (savedInstanceState == null) {
			Fragment fragment = null;
			Bundle args = new Bundle();
			if (mSelectedPrinter == null) {
				startingAction = StartAction.NONE;
			}
			switch(startingAction) {
			case PRESELECTED: {
				fragment = new FragmentPrinterPreselected();
				args.putAll(mSelectedPrinter);
				break;
			}
			case NONE:
			default: {
				args.putBoolean(getResources().getResourceName(R.id.bundle_key__launch_printer_selection), !getPrintPlugins().isEmpty());
				fragment = new FragmentNoPrinterSelected();
				break;
			}
			
			}
			fragment.setArguments(args);
			getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();

		}
	}
	
	private boolean isWirelessDirectSSID(String ssid) {
		boolean wirelessDirect = false;
		String[] prefixes = getResources().getStringArray(R.array.wireless_direct_prefixes);
		if (!TextUtils.isEmpty(ssid) && (prefixes != null)) {
			for(int i = 0; (!wirelessDirect && (i < prefixes.length)); i++) {
				wirelessDirect = ssid.contains(prefixes[i]);
			}
		}
		return wirelessDirect;
	}
	
//	private void checkIfOnWirelessDirect() {
//		String ssid = null;
//		boolean wirelessDirect = false;
//		if (mSelectedPrinter == null) {
//			ConnectivityManager connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
//			if (connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
//				WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
//				ssid = wifiManager.getConnectionInfo().getSSID();
//				wirelessDirect = isWirelessDirectSSID(ssid);
//			}
//		}
//		if (wirelessDirect) {
//			Bundle selectedPrinter = new Bundle();
//			selectedPrinter.putBoolean(getResources().getResourceName(R.id.bundle_key__printer_setup_bundle), true);
//			selectedPrinter.putString(getResources().getResourceName(R.id.bundle_key__selected_printer_ssid), ssid);
//			selectedPrinter.putString(getResources().getResourceName(R.id.bundle_key__selected_printer_name), ssid);
//			selectedPrinter.putString(getResources().getResourceName(R.id.bundle_key__selected_printer_address), getResources().getString(R.string.hp_wireless_direct_address));
//			getPrinterCapabilities(selectedPrinter);
//		} else if (getFragmentManager().findFragmentById(R.id.fragment_container) == null) {
//
//		}
//	}

	@Override
	protected void onSaveInstanceState (Bundle outState) {
		final Resources resources = getResources();
		if (mSelectedPrinter != null) {
			outState.putParcelable(resources.getResourceName(R.id.bundle_key__current_selected_printer), mSelectedPrinter);
			if (mSelectedPrinterCaps != null) {
				outState.putParcelable(resources.getResourceName(R.id.bundle_key__current_printer_capabilities), mSelectedPrinterCaps);
				if (mSelectedPrinterSettings != null) {
					outState.putParcelable(resources.getResourceName(R.id.bundle_key__current_job_settings), mSelectedPrinterSettings);
				}
			}
		}
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		// remove any pending runnables just in case
		removeRunnables();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// remove any pending runnables just in case
		removeRunnables();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home: {
			// go back to app that requested print
			onBackPressed();
			return true;
		}
		default: {
			// default handler
			return super.onOptionsItemSelected(item);
		}
		}
	}
	
	ArrayList<DocumentInfo> getSelectedDocumentList() {
		return mSelectedDocumentList;
	}
	
	Bundle getSelectedPrinter() {
		// return the current selected printer
		return mSelectedPrinter;
	}
	
	PrintPlugin selectPrintServiceMessenger(String packageName) {
		mSelectedPrinterPlugin = findPlugin(packageName);
		mSelectedPrinter.putString(getResources().getResourceName(R.id.bundle_key__selected_printer_plugin_package), packageName);
		return mSelectedPrinterPlugin;
	}
	
	PrintPlugin getSelectedPrintPlugin() {
		// return the print service messenger of the selected plugin
		return mSelectedPrinterPlugin;
	}
	
	ArrayList<PrintPlugin> getSupportedPrintPlugins() {
		return new ArrayList<PrintPlugin>(mSupportedPlugins);
	}
	
	Bundle getSelectedPrinterSettings() {
		// return the current printer settings
		return mSelectedPrinterSettings;
	}
	
	Messenger getClientMessenger() {
		// return the client messenger
		return mClientMessenger;
	}
	
	void wifiDisabledOrChanged() {
		mSelectedPrinter = null;
		Bundle args = new Bundle();
		args.putBoolean(getResources().getResourceName(R.id.bundle_key__launch_printer_selection), false);
		Fragment fragment = new FragmentNoPrinterSelected();
		fragment.setArguments(args);
		getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
	}
	
	private void loadPluginInformation() {
		final Resources resources = getResources();
		selectPrintServiceMessenger(mSelectedPrinter.getString(resources.getResourceName(R.id.bundle_key__selected_printer_plugin_package)));
		
		String[] supportedPlugins = mSelectedPrinter.getStringArray(resources.getResourceName(R.id.bundle_key__selected_printer_supported_plugin_packages));
		if (supportedPlugins != null) {
			mSupportedPlugins = new ArrayList<PrintPlugin>();
			for(String entry : supportedPlugins) {
				PrintPlugin plugin = findPlugin(entry);
				if (plugin != null) {
					mSupportedPlugins.add(plugin);
				}
			}
		} else {
			mSupportedPlugins = new ArrayList<PrintPlugin>(mAvailablePrintPlugins);
		}
	}

	void getPrinterCapabilities(final Bundle selectedPrinter) {
		// store the selected printer
		mSelectedPrinter = selectedPrinter;
		
		loadPluginInformation();
		
		// remove any fragment from the screen
		Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
		if (currentFragment != null) {
			getFragmentManager().beginTransaction().remove(currentFragment).commit();
		}
		// request printer capabilities
		requestPrinterCapabilities();
	}

	void requestPrinterCapabilities() {
		// forget about previous capabilities
		mSelectedPrinterCaps = null;
		mSelectedPrinterSettings = null;
		
		// put up a dialog for the user
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.add(R.id.fragment_container, new FragmentGetPrinterCapabilities(), mResources.getResourceName(R.id.printer_setup_dialog_get_caps_progress));
		ft.commit();
	}
	
	public void retry() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		if (mSelectedPrinterCaps == null) {
			requestPrinterCapabilities();

		} else {
			ft.add(R.id.fragment_container, new FragmentPreparePrintJob(),
					mResources.getResourceName(R.id.printer_setup_dialog_preparing_print));
		}
		ft.commit();
	}
	
	private void removeRunnables() {
		// remove any pending runnables
		mHandler.removeCallbacks(mFailedOpRunnable);
		mHandler.removeCallbacks(mProcessCapsRunnable);
		mHandler.removeCallbacks(mQueuePrintJobRunnable);
	}
	
	void capabilitiesReceived(final BackgroundTask.Result result, final long postTime) {		
		// remove any pending runnables
		removeRunnables();
		
		mSelectedPrinterCaps = null;
		if ((result.mData != null) &&
			result.mData.getAction().equals(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_PRINTER_CAPABILITIES)) {
			mSelectedPrinterCaps = result.mData.getExtras();
		}
		
		// save off the capabilities
		mSelectedPrinterSettings = null;

		// determine what to do based on the result code
		switch(result.mCode) {
		case WPRINT_OK:
			mHandler.postAtTime(mProcessCapsRunnable, postTime);
			break;
		case CANCELLED:
			break;
		default:
			mHandler.post(mFailedOpRunnable);
			break;
		}
	}
	
	private boolean checkCapabilities() {		
		// assume failure
		boolean result = false;
		do {
			if (mSelectedPrinterCaps == null) {
				continue;
			}
			
			if (!mSelectedPrinterCaps.getBoolean(PrintServiceStrings.IS_SUPPORTED, true)) {
				continue;
			}
			
			// success!!
			result = true;
		} while(false);
		
		// return the result
		return result;
	}
	
	private void capabilitiesInvalid() {
		Fragment dialog;

		// remove any dialogs we have up 
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		dialog = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_get_caps_progress));
		if (dialog != null) {
			ft.remove(dialog);
		}
		dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__printer_not_supported, null);
		ft.add(dialog,getResources().getResourceName(R.id.dialog_id__printer_not_supported));
		ft.commit();
	}

	private void capabilitiesVerified() {
		Fragment dialog;
		
		// create the fragment arguments for selecting printer options
		Bundle args = new Bundle();
		args.putAll(mSelectedPrinter);
		args.putAll(mSelectedPrinterCaps);
		if (mRequestedPrintSettings != null) {
			args.putAll(mRequestedPrintSettings);
		}
		
//		if (!args.containsKey(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_TYPE) && (m)
		
		// create the fragment
		FragmentPrintSettings settings = new FragmentPrintSettings();
		settings.setArguments(args);

		// remove any dialogs we have up 
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		dialog = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_get_caps_progress));
		if (dialog != null) {
			ft.remove(dialog);
		}
		dialog = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_plugin_op_timeout));
		if (dialog != null) {
			ft.remove(dialog);
		}
		ft.replace(R.id.fragment_container, settings);
		ft.commit();
	}
	

	void submitPrintJob(Bundle printSettings) {
		// store a copy of the settings
		mSelectedPrinterSettings = printSettings;
		
		mSelectedPrinterSettings.putString(getResources().getResourceName(R.id.bundle_key__selected_printer_plugin_package), mSelectedPrinterPlugin.mPackageName);
		
        Intent intent = getIntent();
        if (intent != null) {
        	if (PrintSetupMessages.PRINT_SETUP_ACTION.equals(intent.getAction())) {
        		Bundle extras = intent.getExtras();
        		if (extras != null) {
        			if (extras.containsKey(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION)) {
        				mSelectedPrinterSettings.putString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION, extras.getString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION));
        			}
        			if (extras.containsKey(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION_NAME)) {
        				mSelectedPrinterSettings.putString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION_NAME, extras.getString(PrintSetupMessages.BUNDLE_KEY__CLIENT_APPLICATION_NAME));
        			}
        		}
        	}
        }
		
		// add the printer address just in case
		mSelectedPrinterSettings.putString(
				PrintServiceStrings.PRINTER_ADDRESS_KEY,
				mSelectedPrinter.getString(getResources().getResourceName(R.id.bundle_key__selected_printer_address)));
		mSelectedPrinterSettings.putBundle(getResources().getResourceName(R.id.bundle_key__selected_printer_info), mSelectedPrinter);
		
		// put up a dialog for the user
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.add(R.id.fragment_container, new FragmentPreparePrintJob(),
				mResources.getResourceName(R.id.printer_setup_dialog_preparing_print));
		ft.commit();
	}
	
	void finalPrintSettingsReceived(final BackgroundTask.Result result, final long postTime) {
		// remove any pending runnables
		removeRunnables();

		// determine what to do based on the result code
		switch(result.mCode) {
		case WPRINT_OK:
			// store the final print settings
			if (result.mData.getAction().equals(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_FINAL_PARAMS)) {
				mSelectedPrinterSettings.putAll(result.mData.getExtras());
			} else {
				mSelectedPrinterSettings = null;
			}
			// queue a runnable to process the results
			mHandler.postAtTime(mQueuePrintJobRunnable, postTime);
		case CANCELLED:
			break;
		default:
			mHandler.post(mFailedOpRunnable);
			break;
		}
	}
	
	//TODO
	private boolean checkPrintSettings() {
		boolean result = false;
		
		do {
			if (mSelectedPrinterSettings == null) {
				continue;
			}
			if (!mSelectedPrinterSettings.containsKey(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY)) {
				continue;
			}
			result = true;
		} while(false);
		return result;
	}
	
	private void printSettingsInvalid() {
		Fragment dialog;
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		// remove any dialogs we have up 

		dialog = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_get_caps_progress));
		if (dialog != null) {
			ft.remove(dialog);
		}
		if (mSelectedPrinterSettings == null) {
			dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__plugin_failure, null);
			ft.add(dialog,getResources().getResourceName(R.id.dialog_id__plugin_failure));
		} else {
			dialog = PrinterSetupDialog.newInstance(getResources(), R.id.printer_setup_dialog_nothing_to_print, null);
			ft.add(dialog,getResources().getResourceName(R.id.printer_setup_dialog_nothing_to_print));
		}
		ft.commit();
	}
	
	private void queuePrintJob() {
		Fragment dialog;
		
		final Resources resources = getResources(); 
		UsedPrinterDB db = UsedPrinterDB.getInstance(this);
		String ssid = mSelectedPrinter.getString(resources.getResourceName(R.id.bundle_key__selected_printer_ssid), "");
		if (!mSelectedPrinterSettings.getBoolean(PrintServiceStrings.PRINT_TO_FILE) &&
			!TextUtils.isEmpty(ssid) &&
			!isWirelessDirectSSID(ssid)) {
			db.updateUsedPrinters(
					ssid,
					mSelectedPrinter.getString(resources.getResourceName(R.id.bundle_key__selected_printer_hostname)),
					mSelectedPrinter.getString(resources.getResourceName(R.id.bundle_key__selected_printer_bonjour_domain_name)),
					mSelectedPrinter.getString(resources.getResourceName(R.id.bundle_key__selected_printer_bonjour_name)));
		}
		
		// kick off the print 
		if ((mClientMessenger != null) || (mSelectedDocumentList != null)) {
			startService(new Intent(getResources().getString(R.string.printer_setup_action__submit_job),
					PrintJobService.getJobUri(getResources()), this, PrintJobService.class).putExtras(mSelectedPrinterSettings));
		} else {

		}
		
		// remove any dialogs we have up 
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		dialog = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_preparing_print));
		if (dialog != null) {
			ft.remove(dialog);
		}
		dialog = getFragmentManager().findFragmentByTag(mResources.getResourceName(R.id.printer_setup_dialog_plugin_op_timeout));
		if (dialog != null) {
			ft.remove(dialog);
		}
		ft.commit();

		// finish the activity
		setResult(RESULT_OK);
		finish();
	}
	
	public void sendRequestToSelectedMessenger(Intent request, Messenger replyTo) {
		Message msg = Message.obtain(null, 0, request);
		msg.replyTo = replyTo;
		try {
			if (mPrintServiceMessenger != null) {
				mPrintServiceMessenger.send(msg);
			}
		} catch (RemoteException e) {
		}
	}
	
	public void sendRequestToAllMessengers(Intent request, Messenger replyTo) {
		Message msg = Message.obtain(null, 0, request);
		msg.replyTo = replyTo;
		try {
			if (mPrintServiceMessenger != null) {
				mPrintServiceMessenger.send(msg);
			}
		} catch (RemoteException e) {
		}
	}
}
