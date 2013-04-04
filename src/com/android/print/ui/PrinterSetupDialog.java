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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import com.android.print.ui.AccessPoint.PskType;

public class PrinterSetupDialog extends DialogFragment {

	static PrinterSetupDialog newInstance(Resources resources, int requestedDialog, Bundle additionalParams) {
		PrinterSetupDialog dialog = new PrinterSetupDialog();
		Bundle args = new Bundle();
		if (additionalParams != null) {
			args.putAll(additionalParams);
		}
		args.putInt(resources.getResourceName(R.id.bundle_key__dialog_request_number), requestedDialog);
		args.putString(resources.getResourceName(R.id.bundle_key__dialog_request_name), resources.getResourceName(requestedDialog));
		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog dialog = null;
		Bundle args = getArguments();
		int requestedDialog = args.getInt(getResources().getResourceName(R.id.bundle_key__dialog_request_number));
		switch (requestedDialog) {
		case R.id.printer_setup_dialog_get_caps_progress: {
			ProgressDialog progressDialog = new ProgressDialog(getActivity());
			progressDialog
					.setMessage(getString(R.string.dialog_body__obtaining_printer_capabilities));
			progressDialog.setButton(
					ProgressDialog.BUTTON_NEGATIVE,
					getActivity().getResources().getString(
							android.R.string.cancel),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (!isDetached()) {
								getActivity().onBackPressed();
							}
						}
					});
			dialog = progressDialog;
			setCancelable(false);
			break;
		}

		case R.id.printer_setup_dialog_plugin_op_timeout: {
			dialog = new AlertDialog.Builder(getActivity())
					.setTitle(R.string.dialog_title__printer_error)
					.setMessage(R.string.dialog_body__printer_error)
					.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									getActivity().finish();
								}
							})
					.setNeutralButton(R.string.dialog_button__choose_another,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									((PrinterSetup) getActivity())
											.startActivityForResult(
													new Intent(getActivity(),
															PrinterPicker.class),
													R.id.start_activity_id_printer_selection);
								}
							})
					.setPositiveButton(R.string.dialog_button__try_again,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									((PrinterSetup) getActivity()).retry();
								}
							})

					.create();
			break;
		}

		case R.id.printer_setup_dialog_preparing_print: {
			ProgressDialog progressDialog = new ProgressDialog(getActivity());
			progressDialog.setMessage(getString(R.string.dialog_body__preparing_print_job));
			progressDialog.setButton(
					ProgressDialog.BUTTON_NEGATIVE,
					getActivity().getResources().getString(
							android.R.string.cancel),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							getActivity().onBackPressed();
						}
					});
			setCancelable(false);
			dialog = progressDialog;
			break;
		}
		
		case R.id.printer_picker_dialog_no_wifi_printers_found: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__no_printers_found)
			.setMessage(R.string.dialog_body__no_printers_found)
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					getActivity().onBackPressed();
				}
			})
			.setPositiveButton(R.string.dialog_button__try_again, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					((FragmentWifi)getTargetFragment()).startDiscovery(false);
				}
			})
			.create();
			break;
		}
		
		case R.id.printer_picker_dialog_connect_to_printer: {
			View view = getActivity().getLayoutInflater().inflate(R.layout.wifi_dialog, null);
			final EditText passwordField = (EditText)view.findViewById(R.id.wifi_password);
			TextView securityText = (TextView)view.findViewById(R.id.wifi_security);
			final WifiConfiguration wifiConfig = args.getParcelable("config");
			final int wifiSecurity = args.getInt("security");
			switch(wifiSecurity) {
			case R.id.wifi_security_type_none:
				securityText.setText(R.string.wifi_security_none);
				view.findViewById(R.id.wifi_password_layout).setVisibility(View.GONE);
				break;
			case  R.id.wifi_security_type_eap:
				securityText.setText(R.string.wifi_security_eap);
				view.findViewById(R.id.wifi_password_layout).setVisibility(View.GONE);
				break;
			case R.id.wifi_security_type_wep:
				securityText.setText(R.string.wifi_security_wep);
				break;
			case  R.id.wifi_security_type_psk: {
				AccessPoint.PskType type = (PskType) args.getSerializable("pskType");
				switch(type) {
				case WPA:
					securityText.setText(R.string.wifi_security_wpa);
					break;
				case WPA2:
					securityText.setText(R.string.wifi_security_wpa2);
					break;
				case WPA_WPA2:
				default:
					securityText.setText(R.string.wifi_security_wpa_wpa2);
					break;
				}
				break;
			}
			}
			
			final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
			.setTitle(args.getString("ssid"))
			.setView(view)
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dismiss();
				}
			})
			.setPositiveButton(R.string.wifi_connect, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					boolean passwordChanged = true;
					WifiConfiguration selectedWifiConfig = wifiConfig;
					String passwordText = passwordField.getText().toString();

		            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
		                    Context.INPUT_METHOD_SERVICE);
		            if (imm != null) {
		                imm.hideSoftInputFromWindow(passwordField.getWindowToken(), 0);
		            }
		            passwordField.clearFocus();
					
					if (selectedWifiConfig == null) {
						String preSharedKey = AccessPoint.convertToQuotedString(passwordText);
						selectedWifiConfig = new WifiConfiguration();
						selectedWifiConfig.SSID = AccessPoint.convertToQuotedString(getArguments().getString("ssid"));
						selectedWifiConfig.BSSID = getArguments().getString("bssid");
						switch(wifiSecurity) {
						case R.id.wifi_security_type_wep:
							selectedWifiConfig.preSharedKey = preSharedKey;
						case R.id.wifi_security_type_psk:
							selectedWifiConfig.preSharedKey = preSharedKey;
							break;
						default:
							selectedWifiConfig.allowedKeyManagement.set(KeyMgmt.NONE);
							break;

						}
					} else {
						if (!TextUtils.isEmpty(passwordText)) {
							selectedWifiConfig.preSharedKey = AccessPoint.convertToQuotedString(passwordText);;
;
						} else {
							passwordChanged = false;
						}
					}
					((FragmentWirelessDirect)getTargetFragment()).setWifiConfiguration(selectedWifiConfig, passwordChanged);
					dismiss();
				}
			})
			.create();
						
			if (wifiConfig != null) {
				passwordField.setHint(R.string.wifi_unchanged);
			} else {
				
			}
			passwordField.addTextChangedListener(new TextWatcher() {

				@Override
				public void afterTextChanged(Editable s) {
					String text = s.toString();
					boolean passwordInvalid = true;
					if (wifiConfig != null) {
						passwordInvalid = !TextUtils.isEmpty(text);
					}
					if (passwordInvalid) {
						passwordInvalid = (((wifiSecurity == R.id.wifi_security_type_wep) && TextUtils.isEmpty(text)) ||
								((wifiSecurity == R.id.wifi_security_type_psk) && (text.length() < 8)));
					}
					alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!passwordInvalid);					
				}

				@Override
				public void beforeTextChanged(CharSequence s, int start,
						int count, int after) {					
				}

				@Override
				public void onTextChanged(CharSequence s, int start,
						int before, int count) {					
				}
				
			});
			
			((CheckBox)view.findViewById(R.id.wifi_show_password)).setOnCheckedChangeListener(new OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					int pos = passwordField.getSelectionEnd();
					passwordField.setInputType(InputType.TYPE_CLASS_TEXT | (isChecked ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
					if (pos >= 0) {
						passwordField.setSelection(pos);
					}
				}
				
			});
			
			dialog = alertDialog;
			break;
		}
		
		case R.id.printer_picker_dialog_no_wifi: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__wifi_disabled)
			.setMessage(R.string.dialog_body__wifi_disabled)
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					((PrinterPicker)getActivity()).keepWifiOff();
				}
			})
			.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
					try {
						getActivity().startActivity(intent);
					} catch(ActivityNotFoundException e) {
						((PrinterPicker)getActivity()).keepWifiOff();
					}
				}
			})
			.create();
			break;
		}
		
		case R.id.printer_picker_dialog_wifi_not_configured: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__wifi_not_configured)
			.setMessage(R.string.dialog_body__wifi_not_configured)
			.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					((PrinterPicker)getActivity()).keepWifiUnconfigued();
				}
			})
			.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
					try {
						getActivity().startActivity(intent);
					} catch(ActivityNotFoundException e) {
						((PrinterPicker)getActivity()).keepWifiUnconfigued();
					}
				}
			})
			.create();
			break;
		}
		
		case R.id.printer_picker_dialog_network_change_warning: {
			View view = getActivity().getLayoutInflater().inflate(R.layout.network_change_dialog, null);
			CheckBox checkBox = (CheckBox)view.findViewById(R.id.show_network_change_warning);
			checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
					prefs.edit().putBoolean(getActivity().getResources().getString(R.string.settings_key__network_change_warning), !isChecked).commit();
				}
			});
			final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__switch_networks_warning)
			.setView(view)
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dismiss();
				}
			})
			.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {	
					dismiss();
					DialogFragment fragment = PrinterSetupDialog.newInstance(getResources(), R.id.printer_picker_dialog_connect_to_printer, getArguments());
					fragment.setTargetFragment(getTargetFragment(), 0);
					getTargetFragment().getFragmentManager().beginTransaction().add(fragment, getResources().getResourceName(R.id.printer_picker_dialog_connect_to_printer)).commit();
				}
			})
			.create();

			dialog = alertDialog;
			break;	
		}
		
		case R.id.dialog_id__plugin_failure: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__plugin_problem)
			.setMessage(R.string.dialog_body__plugin_problem)
			.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
					getActivity().onBackPressed();
				}
			})
			.setNeutralButton(R.string.dialog_button__choose_another,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
					((PrinterSetup) getActivity())
					.startActivityForResult(
							new Intent(getActivity(),
									PrinterPicker.class),
									R.id.start_activity_id_printer_selection);
				}
			})
			.create();
			break;
		}
		
		case R.id.dialog_id__no_plugins: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__missing_plugins)
			.setMessage(R.string.dialog_body__missing_plugins)
			.setNegativeButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
					getActivity().onBackPressed();
				}
			})
			.create();
			break;
		}
		
		case R.id.dialog_id__printer_not_supported: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__printer_not_supported)
			.setMessage(R.string.dialog_body__printer_not_supported)
			.setNegativeButton(android.R.string.cancel,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
					getActivity().onBackPressed();
				}
			})
			.setNeutralButton(R.string.dialog_button__choose_another,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
					((PrinterSetup) getActivity())
					.startActivityForResult(
							new Intent(getActivity(),
									PrinterPicker.class),
									R.id.start_activity_id_printer_selection);
				}
			})
			.create();
			break;
		}
		
		case R.id.printer_setup_dialog_nothing_to_print: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__nothing_printable_found)
			.setMessage(R.string.dialog_body__nothing_printable_found)
			.setNegativeButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
					getActivity().onBackPressed();
				}
			})
			.create();
			break;
		}
		case R.id.dialog_id__connecting_to_wireless_direct: {
			ProgressDialog progressDialog = new ProgressDialog(getActivity());
			progressDialog
					.setMessage(getString(R.string.dialog_body__connecting_to_wireless_direct));
			progressDialog.setButton(
					ProgressDialog.BUTTON_NEGATIVE,
					getActivity().getResources().getString(
							android.R.string.cancel),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							((FragmentWirelessDirect)getTargetFragment()).stopDiscovery();
						}
					});
			dialog = progressDialog;
			setCancelable(false);
			break;
		}
		
		case R.id.dialog_id__failed_to_connect_to_wireless_direct: {
			dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.dialog_title__could_not_connect_to_wireless_direct)
			.setMessage(R.string.dialog_body__could_not_connect_to_wireless_direct)
			.setNegativeButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog,
						int which) {
				}
			})
			.create();
			break;
		}

		default:
			break;
		}
		if (dialog != null) {
			dialog.setCanceledOnTouchOutside(false);
		}
		return dialog;
	}
}