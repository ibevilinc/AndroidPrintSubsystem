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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.hp.android.printplugin.support.PrintServiceStrings;
import com.hp.pps.print.sdk.DocumentInfo;
import com.hp.pps.print.sdk.PageInfo;
import com.hp.pps.print.sdk.PrintSetupMessages;
//import android.util.Log;

public class FragmentPreparePrintJob extends Fragment {

	static final String TAG = "FragmentPreparePrintJob";

	private static class BackgroundTaskPreparePrintJob extends BackgroundTask {

		private Bundle mJobSettings;
		private Messenger mClientMessenger;
		private ArrayList<DocumentInfo> mDocumentList;
		private File mDataDir;
		private ContentResolver mContentResolver;

		private ClientMessengerHandler mClientCallbackHandler = new ClientMessengerHandler(
				this);
		private Messenger mClientCallbackMessenger = new Messenger(
				mClientCallbackHandler);

		public BackgroundTaskPreparePrintJob(Activity context) {
			super(context, PrintServiceStrings.ACTION_PRINT_SERVICE_GET_FINAL_PRINT_SETTINGS);
			
			// get the job settings from PrinterSetup
			mJobSettings = mContext.getSelectedPrinterSettings();
			// grab an instance of the client messenger
			mClientMessenger = mContext.getClientMessenger();
			mDocumentList    = mContext.getSelectedDocumentList();
			mDataDir = mContext.getDir(UUID.randomUUID().toString(), Context.MODE_PRIVATE);
			mContentResolver = context.getContentResolver();
		}

		@Override
		public void reportResult(long postTime) {
			// give the result to PrinterSetup
			mContext.finalPrintSettingsReceived(mResult, postTime);
		}

		@Override
		protected BackgroundTask.Result doInBackground(Void... params) {
			super.doInBackground();
			// prepare the print job
			Bundle wprintData;
			Message message;
			BackgroundTask.ResultCode resultCode = BackgroundTask.ResultCode.NONE;

			// note the time we posted the dialog, we'll use it to keep the
			// dialog up
			// for a minimum amount of time so the UI doesn't flash something up
			// that
			// disappears before the user can see it

			// create the action to get the final job parameters (including
			// printable area) from the print service
			Intent intent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_FINAL_PRINT_SETTINGS)
					.putExtras(mJobSettings);
			
			message = Message.obtain(null, 0, intent);
			message.replyTo = mPrintServiceCallbackMessenger;
			try {
				mPrintPlugin.mMessenger.send(message);
				mSem.acquire();
				resultCode = ResultCode.WPRINT_OK;

//				// wait for a result to come back from wprint
//				try {
//					if (mSem.tryAcquire(WPRINT_TIMEOUT, TimeUnit.MILLISECONDS)) {
//						resultCode = ResultCode.WPRINT_OK;
//					} else {
//						resultCode = ResultCode.WPRINT_TIMEOUT;
//					}
//				} catch (InterruptedException e) {
//					resultCode = ResultCode.OTHER_ERROR;
//				}
			} catch (RemoteException e) {
				// couldn't communicate with wprint
				resultCode = ResultCode.OTHER_ERROR;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
			}

			if ((resultCode == ResultCode.WPRINT_OK) &&  
				mWPrintData.getAction().equals(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_FINAL_PARAMS)) {
				Bundle fileParameters = new Bundle();
				wprintData = mWPrintData.getExtras();
				fileParameters
						.putInt(PrintSetupMessages.BUNDLE_KEY__PRINT_RESOLUTION,
								wprintData
										.getInt(PrintServiceStrings.PRINT_RESOLUTION,
												PrintSetupMessages.DEFAULT_PRINT_RESOLUTION));
				fileParameters
						.putInt(PrintSetupMessages.BUNDLE_KEY__PRINTABLE_PIXEL_WIDTH,
								wprintData
										.getInt(PrintServiceStrings.PRINTABLE_PIXEL_WIDTH,
												PrintSetupMessages.DEFAULT_PIXEL_WIDTH));
				fileParameters
						.putInt(PrintSetupMessages.BUNDLE_KEY__PRINTABLE_PIXEL_HEIGHT,
								wprintData
										.getInt(PrintServiceStrings.PRINTABLE_PIXEL_HEIGHT,
												PrintSetupMessages.DEFAULT_PIXEL_HEIGHT));

				fileParameters
						.putBoolean(
								PrintSetupMessages.BUNDLE_KEY__PRINT_LANDSCAPE_ORIENTATION,
								mJobSettings.getBoolean(PrintSetupMessages.BUNDLE_KEY__PRINT_LANDSCAPE_ORIENTATION));

				message = Message.obtain(null,
						PrintSetupMessages.MSG_ID__GET_FILES, fileParameters);
				message.replyTo = mClientCallbackMessenger;

				if (mClientMessenger == null) {
					message.what = PrintSetupMessages.MSG_ID__FILES_TO_PRINT;
					if (mDocumentList != null) {
						Bundle printBundle = new Bundle();
						printBundle.putParcelableArrayList(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY, mDocumentList);
						message.obj = printBundle;
					}
				}
				// wait as long as we need to for the application to return
				// the data
				boolean dataReceived = false;
				try {

					if (mClientMessenger != null) {
						mClientMessenger.send(message);
					} else {
						this.mClientCallbackMessenger.send(message);
					}

					do {
						// check to see if the job's been cancelled
						if (isCancelled()) {
							// job cancelled, break out
							break;
						}
						try {
							// wait a little more for the data to arrive
							dataReceived = mSem.tryAcquire(500,
									TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
						}
					} while (!dataReceived);
				} catch (RemoteException e) {
					resultCode = ResultCode.CLIENT_ERROR;
				}
				
				if (dataReceived && !isCancelled()) {
					int index = 0;
					wprintData = mWPrintData.getExtras();
					ArrayList<DocumentInfo> docList = wprintData.getParcelableArrayList(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY);
					ArrayList<DocumentInfo> verifiedDocList = new ArrayList<DocumentInfo>();
					mWPrintData.removeExtra(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY);
					if (docList != null) {
						for(DocumentInfo doc : docList) {
							ArrayList<PageInfo> pagesToCheck = new ArrayList<PageInfo>(doc.getPageList());
							doc.clearPages();
							ListIterator<PageInfo> pageIter = pagesToCheck.listIterator();
							while(pageIter.hasNext() && !isCancelled()) {
								PageInfo page = pageIter.next();
								if (ContentResolver.SCHEME_FILE.equals(page.mPageUri.getScheme())) {
									doc.addPage(page);
								} else if (ContentResolver.SCHEME_CONTENT.equals(page.mPageUri.getScheme())) {
									InputStream is = null;
									FileOutputStream fos = null;
									int bytesRead;
									File tempFile = new File(mDataDir, String.format("temp%d", index++));
									try {
										byte[] byteBuffer = new byte[32 * 1024];
										is  = mContentResolver.openInputStream(page.mPageUri);
										fos = new FileOutputStream(tempFile);
										do {
											bytesRead = is.read(byteBuffer);
											if (bytesRead > 0) {
												fos.write(byteBuffer, 0, bytesRead);
											}
										} while((bytesRead > 0) && !isCancelled());
									} catch (FileNotFoundException e) {
										e.printStackTrace();
									} catch (IOException e) {
										e.printStackTrace();
									} finally {
										try {
											if (is != null)
												is.close();
										} catch(IOException e) {
											
										}
										try {
											if (fos != null)
												fos.close();
										} catch(IOException e) {
											
										}
									}
									tempFile.setReadable(true, false);
									PageInfo copiedPage = new PageInfo(tempFile);
									copiedPage.setTemporary(true);
									copiedPage.setDescription(page.getDescription());
									doc.addPage(copiedPage);
								}
							}
							verifiedDocList.add(doc);
						}
					}
					if (!verifiedDocList.isEmpty()) {
						mWPrintData.putExtra(PrintSetupMessages.BUNDLE_KEY__DOCUMENT_LIST_KEY, verifiedDocList);
					}
				}
			}
			
			// final check to see if the job has been cancelled
			if (isCancelled()) {
				resultCode = ResultCode.CANCELLED;
			}
			
			mDataDir.delete();

			// build up the result
			mResult = new BackgroundTask.Result(resultCode, mWPrintData);

			// return the result
			return mResult;
		}
		
	}

	private BackgroundTaskPreparePrintJob mCurrentTask = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// prevent the fragment from getting destroyed until the background task
		// is complete
		setRetainInstance(true);

		// put up a dialog to let the user know what we're up to
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		DialogFragment dialog = PrinterSetupDialog
				.newInstance(getResources(), R.id.printer_setup_dialog_preparing_print, null);
		ft.add(dialog,
				getResources().getResourceName(R.id.printer_setup_dialog_preparing_print));
		ft.commit();

		// create & kick off the background task to prepare the print job
		mCurrentTask = new BackgroundTaskPreparePrintJob(getActivity());
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
			mCurrentTask.cancel(false);
			mCurrentTask = null;
		}
	}
}
