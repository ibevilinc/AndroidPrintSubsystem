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

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;

import com.hp.android.printplugin.support.PrintServiceStrings;

public class FragmentGetPrinterCapabilities extends Fragment {

	static final String TAG = "FragmentGetPrinterCapabilities";
	
	private static class BackgroundTaskGetCapabilities extends BackgroundTask {
		
		public BackgroundTaskGetCapabilities(Activity context) {
			super(context, PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINTER_CAPABILTIES);
		}

		@Override
		protected BackgroundTask.Result doInBackground(Void... params) {
			super.doInBackground(params);

			BackgroundTask.ResultCode resultCode = ResultCode.NONE;

			// request the printer capabilities from the print service

			// create the appropriate intent
			Intent intent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINTER_CAPABILTIES);
			
			intent.putExtra(PrintServiceStrings.PRINTER_ADDRESS_KEY,
					mSelectedPrinter.getString(mResources.getResourceName(R.id.bundle_key__selected_printer_address)));

			// send the request to the print service
			if (mPrintPlugin != null) {
				Message msg = Message.obtain(null, 0, intent);
				msg.replyTo = mPrintServiceCallbackMessenger;
				try {
					resultCode = ResultCode.CLIENT_ERROR;
					mPrintPlugin.mMessenger.send(msg);
					mSem.acquire();
					resultCode = ResultCode.WPRINT_OK;
/*					try {
//						 wait for a response from wprint
						if (mSem.tryAcquire(WPRINT_TIMEOUT, TimeUnit.MILLISECONDS)) {
							resultCode = ResultCode.WPRINT_OK;
						} else {
							resultCode = ResultCode.WPRINT_TIMEOUT;
						}
						mSem.acquireUninterruptibly();
						resultCode = ResultCode.WPRINT_OK;
					} catch (InterruptedException e) {
						resultCode = ResultCode.OTHER_ERROR;
					}*/
				} catch (RemoteException e) {
					resultCode = ResultCode.OTHER_ERROR;
				} catch (InterruptedException e) {
					resultCode = ResultCode.WPRINT_TIMEOUT;
				}
			} else {
				for(PrintPlugin plugin : mSupportedPlugins) {
					Message msg = Message.obtain(null, 0, intent);
					msg.replyTo = mPrintServiceCallbackMessenger;
					try {
						resultCode = ResultCode.CLIENT_ERROR;
						plugin.mMessenger.send(msg);
						mSem.acquire();
						if (mWPrintData.getAction().equals(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_PRINTER_CAPABILITIES) &&
							mWPrintData.getBooleanExtra(PrintServiceStrings.IS_SUPPORTED, true)) {
							mPrintPlugin = plugin;
							resultCode = ResultCode.WPRINT_OK;
							break;
						}
//
//						try {
//							// wait for a response from wprint
//							if (mSem.tryAcquire(WPRINT_TIMEOUT, TimeUnit.MILLISECONDS)) {
//								mPrintPlugin = plugin;
//								resultCode = ResultCode.WPRINT_OK;
//								break;
//							} else {
//								resultCode = ResultCode.WPRINT_TIMEOUT;
//							}
//						} catch (InterruptedException e) {
//							resultCode = ResultCode.OTHER_ERROR;
//							
//						}
					} catch (RemoteException e) {
						resultCode = ResultCode.OTHER_ERROR;
					} catch (InterruptedException e) {
						resultCode = ResultCode.WPRINT_TIMEOUT;
					}
				}
			}

			// final check to see if the job has been cancelled
			if (isCancelled()) {
				resultCode = ResultCode.CANCELLED;
			}

			// build up the result
			mResult = new BackgroundTask.Result(resultCode, mWPrintData);

			// return the result
			return mResult;
		}

		@Override
		public void reportResult(long postTime) {
			if (mPrintPlugin != null) {
				mContext.selectPrintServiceMessenger(mPrintPlugin.mPackageName);
			}
			mContext.capabilitiesReceived(mResult, postTime);
		}
	}

	private BackgroundTaskGetCapabilities mCurrentTask = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// prevent the fragment from being destroyed until the background task
		// is complete
		setRetainInstance(true);

		// put up a dialog to let the user know what we're up to
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		DialogFragment dialog = PrinterSetupDialog
				.newInstance(getResources(), R.id.printer_setup_dialog_get_caps_progress, null);
		ft.add(dialog,
				getResources().getResourceName(R.id.printer_setup_dialog_get_caps_progress));
		ft.commit();

		// create & kick off the background task to get the printer capabilities
		mCurrentTask = new BackgroundTaskGetCapabilities(getActivity());
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (mCurrentTask != null) {
			mCurrentTask.attach(getActivity());
			if ((mCurrentTask != null) && (mCurrentTask.getStatus() == AsyncTask.Status.PENDING)) {
				mCurrentTask.execute();
			}
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		if (mCurrentTask != null) {
			mCurrentTask.detach();
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		// detach from the background task
		if (mCurrentTask != null) {
			mCurrentTask.detach();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// cancel the background task if it's still active
		if (mCurrentTask != null) {
			mCurrentTask.cancel(true);
			mCurrentTask = null;
		}
	}
}
