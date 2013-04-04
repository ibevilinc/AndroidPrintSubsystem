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
import java.util.ListIterator;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.hp.android.printplugin.support.PrintServiceStrings;
import com.hp.pps.print.sdk.DocumentInfo;
import com.hp.pps.print.sdk.PrintSetupMessages;

public class FragmentPrintSettings extends Fragment implements
		View.OnClickListener, CallbackFinishedListener, PrintPluginActivity.PluginUpdateCallback {

	static final String TAG = "PrintSettings";
	
	private int mMinNumCopies;
	private int mMaxNumCopies;
	private int mNumCopies;
	private boolean mPhotoPrint;
	private WifiManager mWifiManager;
	private WifiInfo mCurrentConfig;
	private Messenger mStatusMessenger;
	private Messenger mPrintServiceMessenger = null;
	
	private int mDuplexState;
	private int mColorState;
	private int mOrientationState;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);
		final Resources resources = getActivity().getResources();
		mMinNumCopies = resources.getInteger(R.integer.min_num_copies);
		mMaxNumCopies = resources.getInteger(R.integer.max_num_copies);
		mNumCopies = mMinNumCopies;
		
		mWifiManager = (WifiManager)getActivity().getSystemService(Context.WIFI_SERVICE);
		mCurrentConfig = mWifiManager.getConnectionInfo();
		
		mStatusMessenger = new Messenger(new PrintServiceCallbackHandler(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_GET_PRINTER_STATUS, this));
	}
	
	@Override
	public void onResume() {
		super.onResume();
		((PrinterSetup)getActivity()).addPluginUpdateCallback(this);
	}
	
	private void stopMonitoring() {
		final Resources resources = getResources();
		Intent intent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_STOP_MONITORING_PRINTER_STATUS);
		intent.putExtra(PrintServiceStrings.PRINTER_ADDRESS_KEY, getArguments().getString(resources.getResourceName(R.id.bundle_key__selected_printer_address), ""));
		Message msg = Message.obtain(null, 0, intent);
		msg.replyTo = mStatusMessenger;
		try {
			if (mPrintServiceMessenger != null) {
				mPrintServiceMessenger.send(msg);
			}
		} catch (RemoteException e) {
		}
	}
	
	@Override
	public void onPause() {
		stopMonitoring();
		super.onPause();
		((PrinterSetup)getActivity()).removePluginUpdateCallback(this);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case R.id.start_activity_id_printer_selection: {
			if (resultCode == Activity.RESULT_OK) {
				Bundle oldData, newData;
				String oldSSID, newSSID;
				String oldAddress, newAddress;
				
				oldData = getArguments();
				newData = data.getExtras();
				final Resources resources = getResources();
				
				oldSSID    = oldData.getString(resources.getResourceName(R.id.bundle_key__selected_printer_ssid), "");
				oldAddress = oldData.getString(resources.getResourceName(R.id.bundle_key__selected_printer_address), "");
				
				newSSID    = newData.getString(resources.getResourceName(R.id.bundle_key__selected_printer_ssid), "");
				newAddress = newData.getString(resources.getResourceName(R.id.bundle_key__selected_printer_address), "");		
				
				if (!newSSID.equals(oldSSID) || !newAddress.equals(oldAddress)) {
					((PrinterSetup) getActivity()).getPrinterCapabilities(data
							.getExtras());
				}
			} else {
				WifiInfo currentConfig = null;
				boolean wifiChanged = false;
				if (!mWifiManager.isWifiEnabled()) {
					wifiChanged = true;
				} else {
					currentConfig = mWifiManager.getConnectionInfo();
					wifiChanged = !mCurrentConfig.getSSID().equals(currentConfig.getSSID());
				}
				
				mCurrentConfig = currentConfig;
				if (wifiChanged) {
					((PrinterSetup)getActivity()).wifiDisabledOrChanged();
				}
			}
			break;
		}
		default:
			break;
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.print_options, null);

		view.findViewById(R.id.selected_printer_layout)
				.setOnClickListener(this);
		view.findViewById(R.id.show_hide_more_settings)
				.setOnClickListener(this);
		view.findViewById(R.id.button_copies_decrement)
				.setOnClickListener(this);
		view.findViewById(R.id.button_copies_increment)
				.setOnClickListener(this);
		view.findViewById(R.id.button_print).setOnClickListener(this);

		return view;
	}
	
	private String getSelectedPrinterName() {
		Bundle args = getArguments();
		String name = null;
		do {
			if (args == null) {
				continue;
			}
			name = args.getString(getResources().getResourceName(R.id.bundle_key__selected_printer_name));
			if (!TextUtils.isEmpty(name)) {
				continue;
			}
			name = args.getString(getResources().getResourceName(R.id.bundle_key__selected_printer_address));
		} while(false);
		
		return name;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		final Resources resources = getActivity().getResources();

		((TextView) view.findViewById(R.id.printer_name_text))
				.setText(getSelectedPrinterName());
		((ImageView) view.findViewById(R.id.printer_status_icon))
				.setVisibility(View.INVISIBLE);
		hideUnsupportedOptions();
		
		boolean showMore = false;
		
		if (savedInstanceState != null) {
			mNumCopies = savedInstanceState.getInt(resources.getResourceName(R.id.bundle_key__print_option_num_copies));
			showMore = savedInstanceState.getBoolean(resources.getResourceName(R.id.bundle_key__print_option_show_more));
			if (savedInstanceState.containsKey(resources.getResourceName(R.id.bundle_key__print_option_two_sided))) {
				((Switch)view.findViewById(R.id.two_sided_switch)).setChecked(savedInstanceState.getBoolean(resources.getResourceName(R.id.bundle_key__print_option_two_sided)));
			}
			if (savedInstanceState.containsKey(resources.getResourceName(R.id.bundle_key__print_option_color_mode))) {
				((Spinner)view.findViewById(R.id.color_mode_spinner)).setSelection(savedInstanceState.getInt(resources.getResourceName(R.id.bundle_key__print_option_color_mode)));
			}
			if (savedInstanceState.containsKey(resources.getResourceName(R.id.bundle_key__print_option_paper_size))) {
				((Spinner)view.findViewById(R.id.paper_size_spinner)).setSelection(savedInstanceState.getInt(resources.getResourceName(R.id.bundle_key__print_option_paper_size)));
			}
			if (savedInstanceState.containsKey(resources.getResourceName(R.id.bundle_key__print_option_orientation))) {
				((Spinner)view.findViewById(R.id.orientation_spinner)).setSelection(savedInstanceState.getInt(resources.getResourceName(R.id.bundle_key__print_option_orientation)));
			}
		}
		showMore |= PreferenceManager.getDefaultSharedPreferences(
				getActivity()).getBoolean(
				resources.getString(R.string.settings_key__show_more),
				resources.getBoolean(R.bool.show_more_default));
		showAdditionalSettings(showMore);
		updateNumCopies(0);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.activity_printer_options, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			startActivity(new Intent(getActivity(),
					PrinterSetupPreferences.class));
			return true;
		case R.id.menu_about:
			startActivity(new Intent(getActivity(),
					PrinterSetupAbout.class));
			return true;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		final Resources resources = getResources();
		outState.putInt(resources.getResourceName(R.id.bundle_key__print_option_num_copies), mNumCopies);
		outState.putBoolean(resources.getResourceName(R.id.bundle_key__print_option_show_more), (getView().findViewById(R.id.additional_print_settings).getVisibility() != View.GONE));
		if (getView().findViewById(R.id.two_sided_layout).getVisibility() != View.GONE) {
			outState.putBoolean(resources.getResourceName(R.id.bundle_key__print_option_two_sided), (((Switch)getView().findViewById(R.id.two_sided_switch)).isChecked()));
		}
		if (getView().findViewById(R.id.color_mode_layout).getVisibility() != View.GONE) {
			outState.putInt(resources.getResourceName(R.id.bundle_key__print_option_color_mode), (((Spinner)getView().findViewById(R.id.color_mode_spinner)).getSelectedItemPosition()));
		}
		if (getView().findViewById(R.id.paper_size_layout).getVisibility() != View.GONE) {
			outState.putInt(resources.getResourceName(R.id.bundle_key__print_option_paper_size), (((Spinner)getView().findViewById(R.id.paper_size_spinner)).getSelectedItemPosition()));
		}
		if (getView().findViewById(R.id.orientation_layout).getVisibility() != View.GONE) {
			outState.putInt(resources.getResourceName(R.id.bundle_key__print_option_orientation), (((Spinner)getView().findViewById(R.id.orientation_spinner)).getSelectedItemPosition()));
		}
	}

	private void hideUnsupportedOptions() {
		View view = getView();
		DocumentInfo.Type printType = null;
		Bundle printRequest = getArguments();
		printRequest.setClassLoader(getActivity().getClassLoader());
		if (printRequest != null) {
			printType = (DocumentInfo.Type) printRequest
					.getSerializable(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_TYPE);
		}
		mPhotoPrint = ((printType != null) && (printType == DocumentInfo.Type.PHOTO));
		view.findViewById(R.id.two_sided_layout).setVisibility(
				(shouldDiplayDuplex() ? View.VISIBLE : View.GONE));
		view.findViewById(R.id.color_mode_layout).setVisibility(
				(shouldDisplayColorMode() ? View.VISIBLE : View.GONE));
		view.findViewById(R.id.paper_size_layout).setVisibility(
				(shouldDisplayPaperSize() ? View.VISIBLE : View.GONE));
		view.findViewById(R.id.orientation_layout).setVisibility(
				(shouldDisplayOrientation() ? View.VISIBLE : View.GONE));
	}

	private boolean shouldDisplayColorMode() {
		// process color capabilities
		final Resources resources = getActivity().getResources();
		boolean canPrintColor = false;
		boolean printInColor;
		int colorPosition = 0;
		
		mColorState = View.GONE;
		ArrayList<WPrintValuePair> colorModesLoc = new ArrayList<WPrintValuePair>();
		ArrayList<String> colorModes = getArguments().getStringArrayList(
				PrintServiceStrings.PRINT_COLOR_MODE);
		if (((colorModes != null) && (colorModes.size() > 1))) {
			if (getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__HIDE_OPTION_COLOR)) {
				mColorState = View.INVISIBLE;
			} else {
				mColorState = View.VISIBLE;
			}
		}
		if ((colorModes != null) && !colorModes.isEmpty()) {
			for (int i = 0; (!canPrintColor && (i < colorModes.size())); i++) {
				if (colorModes.get(i).equals(PrintServiceStrings.COLOR_SPACE_COLOR)) {
					canPrintColor = true;
					break;
				}
			}
		}
		printInColor = getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_IN_COLOR, true);
		colorModesLoc.add(new WPrintValuePair(
				resources.getString(R.string.color_mode__monochrome),
				PrintServiceStrings.COLOR_SPACE_MONOCHROME));
		if (canPrintColor && printInColor) {
			colorModesLoc.add(new WPrintValuePair(
					resources.getString(R.string.color_mode__color),
					PrintServiceStrings.COLOR_SPACE_COLOR));
			colorPosition = (colorModesLoc.size() - 1);
		}

		ArrayAdapter<WPrintValuePair> adapter = new ArrayAdapter<WPrintValuePair>(getActivity(),
				android.R.layout.simple_spinner_item, colorModesLoc);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		((Spinner) getView().findViewById(R.id.color_mode_spinner))
				.setAdapter(adapter);

		((Spinner) getView().findViewById(R.id.color_mode_spinner))
				.setSelection(colorPosition);
		return (mColorState == View.VISIBLE);
	}

	private boolean shouldDiplayDuplex() {		
		mDuplexState = View.GONE;
		do {
			if (mPhotoPrint) {
				continue;
			}
			ArrayList<String> duplexModes = getArguments().getStringArrayList(
					PrintServiceStrings.SIDES);

			if (((duplexModes != null) && (duplexModes.size() > 1))) {
				if (getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__HIDE_OPTION_TWO_SIDED)) {
					mDuplexState = View.INVISIBLE;
				} else {
					mDuplexState = View.VISIBLE;
				}
				((Switch)getView().findViewById(R.id.two_sided_switch)).setChecked(
						getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_TWO_SIDED));
			}

		} while (false);
		return (mDuplexState == View.VISIBLE);
	}
	
	private static class WPrintValuePair {

		private final Pair<String,String> mPair;
		public WPrintValuePair(String text, String value) {
			mPair = Pair.create(text, value);
		}
		
		public String toString() {
			return mPair.first;
		}
		
		public String getValue() {
			return mPair.second;
		}
	}
	
	private String getPaperSizeName(ArrayList<String> supportedSizes, String paperSize) {
		
		int id = 0;
		// is this a region supported paper size?
		if (supportedSizes.contains(paperSize)) {
			// yes it's supported
			
			// convert the name to something resources is happy with, (ie convert '-' & '.' to '_')
			paperSize = paperSize.replace('.', '_');
			paperSize = paperSize.replace('-', '_'); 
			
			// lookup the id
			id = getActivity().getResources().getIdentifier(paperSize, "string", getActivity().getPackageName());
		}
		return ((id > 0) ? getActivity().getResources().getString(id) : null);
	}

	private boolean shouldDisplayPaperSize() {
		boolean display = false;
		int index = -1;
		
		final Resources resources = getActivity().getResources();
		
		String defaultPaperSize = resources.getString(
				(getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__USE_DEFAULT_LARGE_MEDIA, true) ?
						R.string.default_large_media : R.string.default_small_media));
		do {
			ArrayList<String> paperSizes = getArguments().getStringArrayList(
					PrintServiceStrings.MEDIA_SIZE_NAME);
			ArrayList<WPrintValuePair> paperSizesLoc = new ArrayList<WPrintValuePair>();
			ArrayList<String> supportedPaperSizeList = new ArrayList<String>();
			String[] supportedPaperSizes = resources.getStringArray(R.array.supported_paper_sizes);
			for(int i = 0; i < supportedPaperSizes.length; i++) {
				supportedPaperSizeList.add(supportedPaperSizes[i]);
			}
			if (paperSizes != null) {
				ListIterator<String> iter = paperSizes.listIterator();
				while(iter.hasNext()) {
					String value = iter.next();
					String text = getPaperSizeName(supportedPaperSizeList, value);
					if (!TextUtils.isEmpty(text)) {
						if (value.equals(defaultPaperSize)) {
							index = paperSizesLoc.size();
						}
						paperSizesLoc.add(new WPrintValuePair(text, value));
					}
				}
			}
			
			display = !paperSizesLoc.isEmpty();

			ArrayAdapter<WPrintValuePair> adapter = new ArrayAdapter<WPrintValuePair>(
					getActivity(), android.R.layout.simple_spinner_item,
					paperSizesLoc);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			Spinner spinner = (Spinner) getView().findViewById(R.id.paper_size_spinner);
			spinner.setAdapter(adapter);
			if (index >= 0) {
				spinner.setSelection(index);
			}
		} while (false);
		return display;
	}

	private boolean shouldDisplayOrientation() {
		mOrientationState = View.GONE;
		do {
			final Resources resources = getResources();
			ArrayList<WPrintValuePair> orientationLoc = new ArrayList<WPrintValuePair>();
			if (((PrinterSetup)getActivity()).getSelectedDocumentList() != null) {
				orientationLoc.add(new WPrintValuePair(resources.getString(R.string.orientation__auto),
						PrintServiceStrings.ORIENTATION_AUTO));
			}
			orientationLoc.add(new WPrintValuePair(resources.getString(R.string.orientation__portrait),
					PrintServiceStrings.ORIENTATION_PORTRAIT));
			orientationLoc.add(new WPrintValuePair(resources.getString(R.string.orientation__landscape),
					PrintServiceStrings.ORIENTATION_LANDSCAPE));
			ArrayAdapter<WPrintValuePair> adapter = new ArrayAdapter<WPrintValuePair>(
					getActivity(), android.R.layout.simple_spinner_item,
					orientationLoc);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			Spinner spinner = (Spinner) getView().findViewById(R.id.orientation_spinner);
			spinner.setAdapter(adapter);
			
			if (mPhotoPrint) {
				mOrientationState = View.GONE;
			} else {
				if (getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__HIDE_OPTION_ORIENTATION)) {
					mOrientationState = View.INVISIBLE;
				} else {
					mOrientationState = View.VISIBLE;
				}
				spinner.setSelection(getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_LANDSCAPE_ORIENTATION) ? (orientationLoc.size() - 1) : 0);
			}

		} while (false);
		return (mOrientationState == View.VISIBLE);
	}

	private void showAdditionalSettings(boolean displayAdditionalSettings) {
		View additionalSettings = getView().findViewById(
				R.id.additional_print_settings);
		TextView showHide = (TextView) getView().findViewById(
				R.id.show_hide_more_settings);

		// update the text and icon
		showHide.setText((displayAdditionalSettings ? R.string.print_setting_title__show_less_settings
				: R.string.print_setting_title__show_more_settings));
		showHide.setCompoundDrawablesWithIntrinsicBounds(0, 0,
				(displayAdditionalSettings ? R.drawable.ic_navigation_collapse
						: R.drawable.ic_navigation_expand), 0);
		additionalSettings
				.setVisibility((displayAdditionalSettings ? View.VISIBLE
						: View.GONE));
	}

	private void updateNumCopies(int delta) {
		mNumCopies += delta;
		if (mNumCopies < mMinNumCopies)
			mNumCopies = mMinNumCopies;
		else if (mNumCopies > mMaxNumCopies)
			mNumCopies = mMaxNumCopies;
		getView().findViewById(R.id.button_copies_decrement).setEnabled(
				(mNumCopies > mMinNumCopies));
		getView().findViewById(R.id.button_copies_increment).setEnabled(
				(mNumCopies < mMaxNumCopies));
		TextView text = (TextView) getView().findViewById(R.id.num_copies);
		text.setText(String.valueOf(mNumCopies));
	}

	private Bundle buildPrintSettings() {
		Bundle printSettings = new Bundle();
		printSettings.setClassLoader(getActivity().getClassLoader());
		printSettings.putInt("copies", mNumCopies);

		Spinner spinner;
		boolean printLandscape = false;
		final Resources resources = getResources();
		printSettings.putString(
				PrintServiceStrings.PRINTER_ADDRESS_KEY,
				getArguments().getString(
						getResources().getResourceEntryName(R.id.bundle_key__selected_printer_address)));
		
		printSettings.putInt(PrintServiceStrings.COPIES, mNumCopies);
		printSettings.putString(PrintServiceStrings.FULL_BLEED, (mPhotoPrint ? PrintServiceStrings.FULL_BLEED_ON : PrintServiceStrings.FULL_BLEED_OFF));
		printSettings.putString(PrintServiceStrings.PRINT_COLOR_MODE,
				PrintServiceStrings.COLOR_SPACE_COLOR);
		printSettings.putString(
				PrintServiceStrings.MEDIA_SIZE_NAME,
				resources.getString(R.string.wprint_paper_size_letter));
		printSettings.putString(
				PrintServiceStrings.ORIENTATION_REQUESTED,
				PrintServiceStrings.ORIENTATION_AUTO);
		printSettings.putBoolean(
				PrintServiceStrings.FIT_TO_PAGE, !mPhotoPrint);
		printSettings.putBoolean(
				PrintServiceStrings.FILL_PAGE, mPhotoPrint);
		printSettings.putString(PrintServiceStrings.SIDES,
				PrintServiceStrings.SIDES_SIMPLEX);
		printSettings.putString(
				PrintServiceStrings.ORIENTATION_REQUESTED,
				PrintServiceStrings.ORIENTATION_PORTRAIT);
		printSettings.putBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_LANDSCAPE_ORIENTATION, getArguments().getBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_LANDSCAPE_ORIENTATION));
		
		if (mOrientationState != View.GONE) {
			spinner = (Spinner) getView()
					.findViewById(R.id.orientation_spinner);
			WPrintValuePair pair = (WPrintValuePair) spinner.getSelectedItem();
			printLandscape = pair.getValue().equals(PrintServiceStrings.ORIENTATION_LANDSCAPE);
			printSettings.putBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_LANDSCAPE_ORIENTATION, printLandscape);
			if (((PrinterSetup)getActivity()).getSelectedDocumentList() != null) {
				printSettings
				.putString(
						PrintServiceStrings.ORIENTATION_REQUESTED,
						((WPrintValuePair) spinner.getSelectedItem()).getValue());
			}

		}

		if (mDuplexState != View.GONE) {
			Switch switchSetting = (Switch) getView().findViewById(
					R.id.two_sided_switch);
			if (!mPhotoPrint && switchSetting.isChecked()) {
				printSettings
						.putString(
								PrintServiceStrings.SIDES,
								(printLandscape ? PrintServiceStrings.SIDES_DUPLEX_SHORT_EDGE
										: PrintServiceStrings.SIDES_DUPLEX_LONG_EDGE));
			}
		}

		if (mColorState != View.GONE) {
			spinner = (Spinner) getView().findViewById(R.id.color_mode_spinner);
			
			printSettings.putString(
					PrintServiceStrings.PRINT_COLOR_MODE,
					((WPrintValuePair) spinner.getSelectedItem()).getValue());
		}

		if (getView().findViewById(R.id.paper_size_layout).getVisibility() != View.GONE) {
			spinner = (Spinner) getView().findViewById(R.id.paper_size_spinner);
			printSettings.putString(
					PrintServiceStrings.MEDIA_SIZE_NAME,
					((WPrintValuePair) spinner.getSelectedItem()).getValue());
		}

		return printSettings;
	}

	@Override
	public void onClick(View v) {

		switch (v.getId()) {
		case R.id.selected_printer_layout: {
			stopMonitoring();
			Intent intent = new Intent(getActivity(),
					PrinterPicker.class);
			intent.putExtras(((PrinterSetup)getActivity()).getSelectedPrinter());
			startActivityForResult(intent, R.id.start_activity_id_printer_selection);
			break;
		}

		case R.id.show_hide_more_settings: {
			View additionalSettings = getView().findViewById(
					R.id.additional_print_settings);
			showAdditionalSettings((additionalSettings.getVisibility() == View.GONE));
			break;
		}

		case R.id.button_copies_decrement:
		case R.id.button_copies_increment: {
			updateNumCopies((v.getId() == R.id.button_copies_increment) ? 1
					: -1);
			break;
		}

		case R.id.button_print: {
			stopMonitoring();
			((PrinterSetup) getActivity()).submitPrintJob(buildPrintSettings());
			break;
		}

		default:
		}
	}

	@Override
	public void callComplete(Intent intent) {
		final Bundle data = intent.getExtras();
		if ((data != null) && !data.isEmpty() && !isDetached()) {
			Activity activity = getActivity();
			if (activity == null) {
				return;
			}
			activity.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					final Resources resources = getResources();
					String printerStatus = data.getString(PrintServiceStrings.PRINTER_STATUS_KEY);
					if (TextUtils.isEmpty(printerStatus)) {
						return;
					}
					
					String statusText = null;
					boolean hasErrors, hasWarnings;
					hasErrors = hasWarnings = false;
					
					String[] blockedReasons = null;
					if (data.containsKey(PrintServiceStrings.PRINT_JOB_BLOCKED_STATUS_KEY))
						blockedReasons = data.getStringArray(PrintServiceStrings.PRINT_JOB_BLOCKED_STATUS_KEY);
					
					if (blockedReasons != null) {
						for(int i = 0; i < blockedReasons.length; i++) {
							hasWarnings |= !TextUtils.isEmpty(blockedReasons[i]);
						}
						if (!hasWarnings) {
							blockedReasons = null;
						}
					}
					if (printerStatus.equals(PrintServiceStrings.PRINTER_STATE_BLOCKED)) {
						hasErrors = true;
					} else if (printerStatus.equals(PrintServiceStrings.PRINTER_STATE_RUNNING)) {
						statusText = resources.getString(R.string.printer_state__busy);
						hasWarnings = (blockedReasons != null);
					} else if (printerStatus.equals(PrintServiceStrings.PRINTER_STATE_IDLE)) {
						statusText = resources.getString(R.string.printer_state__ready);
					} else if (printerStatus.equals(PrintServiceStrings.PRINTER_STATE_UNKNOWN)) {
						hasWarnings = true;
						statusText = resources.getString(R.string.printer_state__unknown);
					}
					
					if ((hasErrors || hasWarnings) && (blockedReasons != null)) {
						int i, numUnknowns;
						boolean low_toner, low_ink;
						boolean no_toner, no_ink;
						boolean door_open, check_printer;
						boolean no_paper, jammed;
						
						low_toner = low_ink = no_toner = no_ink = door_open = check_printer = no_paper = jammed = false;
						
						for(i = numUnknowns = 0; i < blockedReasons.length; i++) {
							if (TextUtils.isEmpty(blockedReasons[i])) {
							}
							else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__DOOR_OPEN)) {
								door_open = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__JAMMED)) {
								jammed = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__LOW_ON_INK)) {
								low_ink = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__LOW_ON_TONER)) {
								low_toner = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__OUT_OF_INK)) {
								no_ink = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__OUT_OF_TONER)) {
								no_toner = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__REALLY_LOW_ON_INK)) {
								low_ink = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__OUT_OF_PAPER)) {
								no_paper = true;
							} else if (blockedReasons[i].equals(PrintServiceStrings.BLOCKED_REASON__SERVICE_REQUEST)) {
								check_printer = true;
							} else {
								numUnknowns++;
							}
						}
						if (numUnknowns == blockedReasons.length) {
							check_printer = true;
						}
						if (door_open) {
							statusText = resources.getString(R.string.printer_state__door_open);
						} else if (jammed) {
							statusText = resources.getString(R.string.printer_state__jammed);
						} else if (no_paper) {
							statusText = resources.getString(R.string.printer_state__out_of_paper);
						} else if (check_printer) {
							statusText = resources.getString(R.string.printer_state__check_printer);
						} else if (no_ink) {
							statusText = resources.getString(R.string.printer_state__out_of_ink);
						} else if (no_toner) {
							statusText = resources.getString(R.string.printer_state__out_of_toner);
						} else if (low_ink) {
							statusText = resources.getString(R.string.printer_state__low_on_ink);
						} else if (low_toner) {
							statusText = resources.getString(R.string.printer_state__low_on_toner);
						} else {
							statusText = resources.getString(R.string.printer_state__unknown);
						}
					}

					ImageView icon = (ImageView)getView().findViewById(R.id.printer_status_icon);
					if (hasErrors) {
						icon.setImageResource(R.drawable.ic_error);
					} else if (hasWarnings) {
						icon.setImageResource(R.drawable.ic_warning);
					}
					icon.setVisibility(((hasErrors | hasWarnings)) ? View.VISIBLE : View.INVISIBLE);

					((TextView)getView().findViewById(R.id.printer_status_text)).setText(statusText);
				}
				
			});
		}
		
	}
	
	private void registerForStatus() {
		final Resources resources = getResources();
		Intent intent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_START_MONITORING_PRINTER_STATUS);
		intent.putExtra(PrintServiceStrings.PRINTER_ADDRESS_KEY, getArguments().getString(resources.getResourceName(R.id.bundle_key__selected_printer_address), ""));
		Message msg = Message.obtain(null, 0, intent);
		msg.replyTo = mStatusMessenger;
		try {
			mPrintServiceMessenger.send(msg);
		} catch (RemoteException e) {
		}
	}

	@Override
	public void pluginAvailable(PrintPlugin plugin, boolean available) {		
		do {
			if (plugin == null) {
				continue;
			}
			
			if (!plugin.mPackageName.equals(getArguments().getString(getResources().getResourceName(R.id.bundle_key__selected_printer_plugin_package)))) {
				continue;
			}
			
			if (available) {
				mPrintServiceMessenger = plugin.mMessenger;
				registerForStatus();
			} else if (mPrintServiceMessenger != null) {
				mPrintServiceMessenger = null;
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				DialogFragment dialog = PrinterSetupDialog.newInstance(getResources(), R.id.dialog_id__plugin_failure, null);
				dialog.setTargetFragment(FragmentPrintSettings.this, 0);
				ft.add(dialog,getResources().getResourceName(R.id.dialog_id__plugin_failure));
				ft.commit();
			}
			
		} while(false);
	}
}
