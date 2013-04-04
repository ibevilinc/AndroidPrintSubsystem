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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class PrintStatus extends ListActivity implements PrintJobService.PrintStatusCallback, PrintJobService.PrintCancelCallback {

	private PrintJobService mService = null;

	private HashMap<String, PrintStatusItem> mStatusHash;
	private ListView mListView;
	public ArrayList<PrintStatusItem> mJobList;
	private PrintStatusAdapter mAdapter;
	
	private PrintStatusItem mNotificationJob = null;

	private static class PrintStatusAdapter extends BaseAdapter {
		
		private final WeakReference<PrintStatus> mContextRef;
		
		private final LayoutInflater mInflater; 
		PrintStatusAdapter(PrintStatus context) {
			mContextRef = new WeakReference<PrintStatus>(context);
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			return mContextRef.get().mJobList.size();
		}

		@Override
		public Object getItem(int position) {
			return mContextRef.get().mJobList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			final PrintStatus context = mContextRef.get();
			View view;;
			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.print_job_status_item, null);
			}
			
			final PrintStatusItem item = (PrintStatusItem)getItem(position);
			ImageView icon = (ImageView)view.findViewById(R.id.job_status_icon);

			switch(item.getState()) {
			case UNKNOWN:
			case CORRUPT:
			case RUNNING_WTIH_WARNING:
				icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_warning));
				icon.setVisibility(View.VISIBLE);
				break;
			case FAILED:
			case BLOCKED:
				icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_error));
				icon.setVisibility(View.VISIBLE);
				break;
			case CANCELLED:
				if (!item.wasUserCancelled()) {
					icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_error));
					icon.setVisibility(View.VISIBLE);
					break;
				}
			default:
				icon.setVisibility(View.INVISIBLE);	
			}
			
			TextView textView;
			textView = (TextView)view.findViewById(R.id.job_description);
			textView.setText(item.mDescription);
			
			textView = (TextView)view.findViewById(R.id.selected_printer);
			textView.setText(item.mPrinterName);
			
			textView = (TextView)view.findViewById(R.id.job_state);
			textView.setText(item.getStateText());
			
			textView = (TextView)view.findViewById(R.id.job_status);
			textView.setText(item.getJobStatus());
			
			CheckBox checkBox = (CheckBox) view.findViewById(R.id.job_select);
			checkBox.setChecked(item.isChecked());
			checkBox.setEnabled(item.isCheckable());
			checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					item.setChecked(isChecked);
					context.mListView.setItemChecked(position, item.isChecked());
				}
			});
			return view;
		}
	}
	
	private static class ModeCallback implements MultiChoiceModeListener {
		
		private final WeakReference<PrintStatus> mContextRef;
		public ModeCallback(PrintStatus context) {
			mContextRef = new WeakReference<PrintStatus>(context);
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			PrintStatus context = mContextRef.get();
			switch(item.getItemId()) {
			case R.id.menu_item_cancel_job:
				context.cancelSelectedJobs();
				context.clearSelections();
				break;
			default:
				break;
			}

			return true;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			PrintStatus context = mContextRef.get();
            final MenuInflater inflater = context.getMenuInflater();
            inflater.inflate(R.menu.activity_print_status_menu, menu);
            mode.setTitle(R.string.action_title__cancel_selected_jobs);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			PrintStatus context = mContextRef.get();
			context.clearSelections();
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return true;
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position,
				long id, boolean checked) {
		}
	}
	
	private void clearSelections() {
		ListIterator<PrintStatusItem> iter = mJobList.listIterator();
		while(iter.hasNext()) {
			iter.next().setChecked(false);
		}
		mListView.clearChoices();
		mAdapter.notifyDataSetChanged();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mStatusHash = new HashMap<String, PrintStatusItem>();
		mJobList = new ArrayList<PrintStatusItem>();
		mAdapter = new PrintStatusAdapter(this);
		
		mListView = getListView();
		mListView.setAdapter(mAdapter);
		mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		mListView.setMultiChoiceModeListener(new ModeCallback(this));
		
		if (savedInstanceState != null) {
			ArrayList<PrintStatusItem> statusList = savedInstanceState.getParcelableArrayList(getResources().getResourceName(R.id.bundle_key__job_status_list));
			statusChanged(statusList);
		}
		
		bindService(new Intent(this, PrintJobService.class), new ServiceConnection() {

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = ((PrintJobService.PrintJobServiceBinder)service).getService();
				mService.registerStatusCallback(PrintStatus.this);
				unbindService(this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
			}	
		}, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (mService != null) {
			mService.registerStatusCallback(this);
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelableArrayList(getResources().getResourceName(R.id.bundle_key__job_status_list), mJobList);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if (mService != null) {
			mService.registerStatusCallback(null);
		}
	}

	public void abortCancel() {
		mNotificationJob = null;
	}
	
	public void cancelJob() {
		if ((mService != null) && (mNotificationJob != null)) {
			ArrayList<String> cancelList = new ArrayList<String>();
			cancelList.add(mNotificationJob.mJobID);
			mService.cancelJob(cancelList, this);
		}
	}
	
	private void cancelSelectedJobs() {
		if (mService != null) {
			ArrayList<String> cancelList = new ArrayList<String>();
			ListIterator<PrintStatusItem> iter = mJobList.listIterator();
			while(iter.hasNext()) {
				PrintStatusItem entry = iter.next();
				if (entry.isChecked()) {
					cancelList.add(entry.mJobID);
				}
			}
			mService.cancelJob(cancelList, this);
		}
	}

	@Override
	public void cancelResult(boolean result) {
	}

	@Override
	public void statusChanged(final ArrayList<PrintStatusItem> jobStatusList) {
		if ((jobStatusList != null) && !jobStatusList.isEmpty()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ListIterator<PrintStatusItem> iter = jobStatusList.listIterator();
					while(iter.hasNext()) {
						PrintStatusItem entry = iter.next();
						PrintStatusItem previous = mStatusHash.get(entry.mJobID);
						if (previous != null) {
							previous.update(entry);
						} else {
							mJobList.add(entry);
							mStatusHash.put(entry.mJobID, entry);
						}
					}
					mAdapter.notifyDataSetChanged();
				}
			});
		}
	}

	@Override
	public void jobFinished(final PrintStatusItem jobStatus) {
		if (jobStatus != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					PrintStatusItem entry = mStatusHash.get(jobStatus.mJobID);
					if (entry != null) {
						entry.update(jobStatus);
						mListView.setItemChecked(mJobList.indexOf(entry), false);
						mAdapter.notifyDataSetChanged();
					}
				}
			});
		}
	}
}
