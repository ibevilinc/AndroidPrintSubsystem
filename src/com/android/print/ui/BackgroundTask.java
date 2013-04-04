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
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Messenger;
import android.os.SystemClock;

public abstract class BackgroundTask extends
		AsyncTask<Void, Void, BackgroundTask.Result> implements
		CallbackFinishedListener {

	protected static final String TAG = "wPrintTask";

	protected final Semaphore mSem = new Semaphore(0);
	protected PrinterSetup mContext = null;
	protected final PrintServiceCallbackHandler mPrintServiceCallbackHandler;
	protected final Messenger mPrintServiceCallbackMessenger;
	protected final Bundle mSelectedPrinter;
	protected PrintPlugin mPrintPlugin;
	protected ArrayList<PrintPlugin> mSupportedPlugins;
	protected long mPostTime;
	protected Intent mWPrintData = null;
	protected Result mResult = null;
	protected final Resources mResources;
	protected final ClassLoader mClassLoader;

	protected final int MIN_CLEAR_TIME;
	protected final int WPRINT_TIMEOUT;

	// result holder
	public static final class Result {
		public final ResultCode mCode;
		public final Intent mData;

		// result constructor
		protected Result(ResultCode code, Intent data) {
			mCode = code;
			mData = data;
		}
	}

	public enum ResultCode {
		NONE, CANCELLED, WPRINT_OK, WPRINT_TIMEOUT, CLIENT_ERROR, OTHER_ERROR,
	};

	public BackgroundTask(Activity context, String action) {

		mClassLoader = context.getClassLoader();
		mContext = (PrinterSetup)context;
		mResources = mContext.getResources();

		WPRINT_TIMEOUT = mResources.getInteger(R.integer.failed_wprint_op_timeout);
		MIN_CLEAR_TIME = mResources.getInteger(R.integer.min_runnable_delay);

		mSelectedPrinter = mContext.getSelectedPrinter();
		mSupportedPlugins = mContext.getSupportedPrintPlugins();
		mPrintPlugin = mContext.getSelectedPrintPlugin();

		mPrintServiceCallbackHandler = new PrintServiceCallbackHandler(action, this);
		mPrintServiceCallbackMessenger = new Messenger(
				mPrintServiceCallbackHandler);
	}

	@Override
	protected BackgroundTask.Result doInBackground(Void... params) {
		mPostTime = getCurrentTime() + MIN_CLEAR_TIME;
		return null;
	}

	public synchronized void attach(Activity context) {
		if ((context != null) && (context instanceof PrinterSetup)) {
			mContext = (PrinterSetup) context;
			if ((getStatus() == AsyncTask.Status.FINISHED)
					&& (mContext != null)) {
				reportResult(getCurrentTime());
			}
		} else {
		}
	}

	public synchronized void prepare() {
		if (mContext != null) {

		}
	}

	@Override
	protected synchronized void onPostExecute(Result unused) {
		if (!isCancelled() && (mContext != null)) {
			reportResult(mPostTime);
		}
	}

	public synchronized void detach() {
		mContext = null;
	}

	private long getCurrentTime() {
		return SystemClock.uptimeMillis();
	}

	public abstract void reportResult(long postTime);

	@Override
	public void callComplete(Intent intent) {
		intent.setExtrasClassLoader(mClassLoader);
		if (mWPrintData == null) {
			mWPrintData = intent;
		} else {
			mWPrintData.putExtras(intent);
		}
		mSem.release();
	}
}
