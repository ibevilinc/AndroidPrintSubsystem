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

import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import com.hp.android.printplugin.support.PrintServiceStrings;

class PrintServiceCallbackHandler extends Handler {
	private final CallbackFinishedListener mCallback;
	private final String mAction;
	
	public PrintServiceCallbackHandler(String action, CallbackFinishedListener callback) {
		mAction = action;
		mCallback = callback;
	}
	
	@Override
	public void handleMessage(Message msg) {
		
		// check context and message
		if ((mCallback == null) || (msg == null)) {
			return;
		}
		
		// check message object
		if ((msg.obj == null) || !(msg.obj instanceof Intent)) {
			return;
		}
		
		Intent intent = (Intent)msg.obj;
		String action = intent.getStringExtra(PrintServiceStrings.PRINT_REQUEST_ACTION);
		if (action.equals(mAction)) {
			mCallback.callComplete(intent);
		}
	}
}
