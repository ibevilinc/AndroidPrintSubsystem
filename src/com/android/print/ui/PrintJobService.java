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
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.text.TextUtils;

import com.hp.android.printplugin.support.PrintServiceStrings;

//import android.util.Log;

public class PrintJobService extends Service implements PrintPlugin.PluginServiceConnection {

	public static Uri getJobUri(final Resources resources) {
		return Uri.fromParts(resources.getString(R.string.printer_setup_uri_scheme), UUID.randomUUID().toString(), null);
	}
	
	private static class PrintCancelRequest {
		
		ArrayList<String> mCancelList;
		final PrintCancelCallback mCallback;
		boolean mResult = true;
		
		PrintCancelRequest(ArrayList<String> jobIDs, PrintCancelCallback callback) {
			mCancelList = ((jobIDs != null) ? jobIDs : new ArrayList<String>());
			mCallback = callback;
		}
	}
	
	public interface PrintCancelCallback {
		public void cancelResult(boolean result);
	}
	
	public interface PrintStatusCallback {
		public void statusChanged(ArrayList<PrintStatusItem> jobStatusList);
		public void jobFinished(PrintStatusItem jobStatus);
	}
		
	private ArrayList<PrintJob> mJobList = new ArrayList<PrintJob>();
	private HashMap<UUID, PrintJob> mJobHashMap = new HashMap<UUID, PrintJob>();
	private JobHandler mJobHandler = null;
	private int mStartID = -1;
	private HashMap<String, PrintPlugin> mPluginHash = new  HashMap<String, PrintPlugin>();
	private HashMap<PrintPlugin, ArrayList<PrintJob>> mPluginJobList = new HashMap<PrintPlugin, ArrayList<PrintJob>>();
	private WakeLock mWakeLock;
	private WifiManager.WifiLock mWifiLock;
	
	private PrintCancelRequest mCancelRequest = null;
	
	public static class PrintJobServiceBinder extends Binder {
		
		private final WeakReference<PrintJobService> mService;
		private PrintJobServiceBinder(PrintJobService service) {
			mService = new WeakReference<PrintJobService>(service);
		}
		
		public PrintJobService getService() {
			return mService.get();
		}
	}
	
	private static class JobStatusHandler extends Handler {
		
		private final WeakReference<PrintJobService> mServiceRef;
		public JobStatusHandler(PrintJobService service) {
			super();
			mServiceRef = new WeakReference<PrintJobService>(service);
		}
		
		@Override
		public void handleMessage(Message msg) {
			PrintJobService service = mServiceRef.get();
			if (service == null) {
				return;
			}
			if ((msg.obj == null) || !(msg.obj instanceof Intent)) {
				return;
			}
			Intent intent = (Intent)msg.obj;
			String requestAction = intent.getStringExtra(PrintServiceStrings.PRINT_REQUEST_ACTION);
			if (TextUtils.isEmpty(intent.getAction())) {
				return;
			}
			
			int msgID = -1;
			if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_PRINTER_STARTED.equals(intent.getAction())) {
				
			}
			else if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_PRINT_JOB_STATUS.equals(intent.getAction())) {
				msgID = R.id.print_service_msg__job_status;
			}
			else if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_CANCEL_JOB.equals(intent.getAction())) {
				msgID = R.id.print_service_msg__cancel_result_ok;
			}
			else if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_CANCEL_ALL_JOBS.equals(intent.getAction())) {
				msgID = R.id.print_service_msg__cancel_all_result_ok;
			}
			else if (PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_ERROR.equals(intent.getAction())) {
				if (TextUtils.isEmpty(requestAction)) {	
				}
				else if (requestAction.equals(PrintServiceStrings.ACTION_PRINT_SERVICE_PRINT)) {
					msgID = R.id.print_service_msg__job_start_failed;
				}
				else if (requestAction.equals(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_CANCEL_JOB)) {
					msgID = R.id.print_service_msg__cancel_result_failed;
				}
				else if (requestAction.equals(PrintServiceStrings.ACTION_PRINT_SERVICE_RETURN_CANCEL_ALL_JOBS)) {
					msgID = R.id.print_service_msg__cancel_all_result_failed;
				}
			}
			if (msgID != -1) {
				service.mJobHandler.sendMessage(Message.obtain(null, msgID, intent.getExtras()));
			}
		}
	}
		
	private static class JobHandler extends Handler {
		private final WeakReference<PrintJobService> mServiceRef;

		public JobHandler(PrintJobService service, Looper looper) {
			super(looper);
			mServiceRef = new WeakReference<PrintJobService>(service);
		}
		
		@SuppressWarnings("deprecation")
		@SuppressLint("NewApi") @Override
		public void handleMessage(Message msg) {
			PrintJobService service = mServiceRef.get();
			if (service == null) {
				getLooper().quit();
				return;
			}
			
			switch(msg.what) {
			case R.id.print_service_msg__quit: {
				getLooper().quit();
				break;
			}
			
			case R.id.print_service_msg__add_job: {
				// get the print job information
				Bundle printSettings = (Bundle)msg.obj;
				PrintPlugin plugin = null;
				
				boolean failJob = true;
				String pluginPackage = printSettings.getString(service.getResources().getResourceName(R.id.bundle_key__selected_printer_plugin_package));
				if (!TextUtils.isEmpty(pluginPackage)) {
					// look for an existing known plugin
					plugin = service.mPluginHash.get(pluginPackage);
					if (plugin == null) {
						// no known plugin, ask the OS to find it
						ResolveInfo resolveInfo = service.getPackageManager().resolveService(new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_GET_PRINT_SERVICE).setPackage(pluginPackage), PackageManager.GET_META_DATA);
						// did they find something? 
						if (resolveInfo != null) {
							// add it to the known list
							failJob = false;
							plugin = new PrintPlugin(service, resolveInfo.serviceInfo);
							service.mPluginHash.put(pluginPackage, plugin);
							service.mPluginJobList.put(plugin, new ArrayList<PrintJob>());
						}
					} else {
						failJob = false;
					}
				}
				
				LinkedList<PrintJob> jobList = null;
				if (!failJob) {
					jobList = PrintJob.parseJobRequest(plugin, service, printSettings);
					failJob = ((jobList == null) || jobList.isEmpty());
 				}
				if (!failJob) {
					switch(plugin.getConnectionState()) {
					case CONNECTED:
						for(PrintJob job : jobList) {
							job.updateSentToPlugin();
							sendMessage(obtainMessage(R.id.print_service_msg__run_job, job.mJobID));
						}
						break;
					case CONNECTING:
						// wait for service connection
						break;
					case NOT_CONNECTED:
						// try to connect
						failJob = !plugin.bind(service);
						break;
					}
					
					if (!failJob) {
						ArrayList<PrintJob> pluginJobList = service.mPluginJobList.get(plugin);
						for(PrintJob job : jobList) {
							service.mJobList.add(job);
							service.mJobHashMap.put(job.mJobID, job);
							pluginJobList.add(job);
						}
						service.mPluginJobList.put(plugin, pluginJobList);
						updateServiceStatus();
					}
				}
				
				if (failJob) {
					//TODO notify the user of job start failure
				}
				
				break;
			}
			
			case R.id.print_service_msg__run_job: {
				PrintJob job = service.mJobHashMap.get((UUID)msg.obj);
				if (job != null) {
					job.updateSentToPlugin();
					try {
						Message printMessage = Message.obtain(null, 0, job.mJobIntent);
						printMessage.replyTo = service.mPrintServiceCallback;
						job.mPrintPlugin.mMessenger.send(printMessage);
					} catch (RemoteException e) {
					}
				}
				break;
			}
			
			case R.id.print_service_msg__job_start_failed: {
				Bundle requestData = (Bundle)msg.obj;
				if (requestData != null) {
					requestData.putString(PrintServiceStrings.PRINT_JOB_DONE_RESULT, PrintServiceStrings.JOB_DONE_ERROR);
				}
				msg.obj = requestData;
				// intentional fall-through to treat job start failure as a status callback
			}
			
			case R.id.print_service_msg__job_status: {

				if (msg.obj == null) {
					break;
				}
				Bundle status = (Bundle)msg.obj;
				String jobIDStr = status.getString(PrintServiceStrings.PRINT_JOB_HANDLE_KEY);
				if (TextUtils.isEmpty(jobIDStr)) {
					break;
				}
				UUID jobID = null;
				try {
					if (!TextUtils.isEmpty(jobIDStr) && (jobIDStr.charAt(0) == '/')) {
						jobIDStr = jobIDStr.substring(1);
					}
					jobID = UUID.fromString(jobIDStr);
				} catch(IllegalArgumentException e) {
					jobID = null;
				}
				if (jobID != null) {
					PrintJob job = service.mJobHashMap.get(jobID);

					if (job != null) {
						job.updateStatus(status);
						
						switch(job.getStatus().getState()) {
						case SUCCESSFUL: {
							service.mNotificatoinManager.cancel(job.mJobID.toString(), R.id.print_service_printing_notification);
							break;
						}
						case CANCELLED:
							if (job.getStatus().wasUserCancelled()) {
								service.mNotificatoinManager.cancel(job.mJobID.toString(), R.id.print_service_printing_notification);
								break;
							}
							// fall-through
						case FAILED:
						case CORRUPT: {
							Notification.Builder notificationBuilder = new Notification.Builder(service)
							.setSmallIcon(R.drawable.ic_stat_printing)
							.setOnlyAlertOnce(true)
							.setAutoCancel(true)
							.setContentTitle(job.getStatus().getNotificationTitle())
							.setContentText(job.getStatus().mDescription);
							
							if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
								notificationBuilder.setSubText(job.getStatus().mPrinterName);
							}

							Notification notification;
							if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
								notification = notificationBuilder.build();
							} else {
								notification = notificationBuilder.getNotification();
							}
							service.mNotificatoinManager.notify(job.mJobID.toString(), R.id.print_service_printing_notification, notification);
							break;
						}
						default:
						{
							break;
						}
						}
						switch(job.getStatus().getState()) {
						// job done in one of the following states
						case FAILED:
						case CORRUPT:
						case CANCELLED:
						case SUCCESSFUL: {
							
							// remove the job mapping
							service.mJobHashMap.remove(job.mJobID);
							
							// let the job know it's finished
							job.jobFinished();
							
							// remove job from our queue
							service.mJobList.remove(job);
							
							if (job.mPrintPlugin != null) {
								ArrayList<PrintJob> pluginJobList = service.mPluginJobList.get(job.mPrintPlugin);
								if (pluginJobList != null) {
									pluginJobList.remove(job);
									service.mPluginJobList.put(job.mPrintPlugin, pluginJobList);
								}
							}
							
							service.notifyJobFinished(job);
							
							break;
						}
						
						// job in waiting state
						case WAITING:
							break;
							
						// job must be running
						default:
							break;
						}
						updateServiceStatus();
					}

				}
				 
				break;
			}
			
			case R.id.print_service_msg__cancel_all_jobs:
				// intentional fall-through
			case R.id.print_service_msg__cancel_job: {
				PrintCancelRequest request = (PrintCancelRequest)msg.obj;
				if (request == null) {
					break;
				}
				if (service.mJobList.isEmpty()) {
					if (request.mCallback != null) {
						request.mCallback.cancelResult(true);
					}
					break;
				}

				service.mCancelRequest = request;
				if (msg.what == R.id.print_service_msg__cancel_all_jobs) {
					ListIterator<PrintJob> iter = service.mJobList.listIterator();
					while(iter.hasNext()) {
						iter.next().cancel();
					}
		
					Set<PrintPlugin> plugins = service.mPluginJobList.keySet();
					for(PrintPlugin plugin : plugins) {
						Intent cancelAllIntent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_CANCEL_ALL_PRINT_JOBS);
						try {
							Message cancelMsg = Message.obtain(null, 0, cancelAllIntent);
							cancelMsg.replyTo = service.mPrintServiceCallback;
							if (plugin.getConnectionState() == PrintPlugin.ConnectionState.CONNECTED) {
								plugin.mMessenger.send(cancelMsg);
							}
						} catch (RemoteException e) {
							if (request.mCallback != null) {
								request.mCallback.cancelResult(false);
							}
						}
					}
					break;
				} else {
					ListIterator<String> iter = request.mCancelList.listIterator();
					while(iter.hasNext()) {
						Intent cancelJobIntent = new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_CANCEL_PRINT_JOB);
						String jobIDStr = iter.next();
						UUID jobID = UUID.fromString(jobIDStr);
						PrintJob job = service.mJobHashMap.get(jobID);
						if (job != null) {
							job.cancel();
						}
						cancelJobIntent.putExtra(PrintServiceStrings.PRINT_JOB_HANDLE_KEY, jobIDStr);
						try {
							Message cancelMsg = Message.obtain(null, 0, cancelJobIntent);
							cancelMsg.replyTo = service.mPrintServiceCallback;
							if (job.mPrintPlugin.getConnectionState() == PrintPlugin.ConnectionState.CONNECTED) {
								job.mPrintPlugin.mMessenger.send(cancelMsg);
							}
						} catch (RemoteException e) {
							if (request.mCallback != null) {
								request.mCallback.cancelResult(false);
							}

						}
					}
				}

				service.notifyStatusChanged();
				break;
			}
			
			case R.id.print_service_msg__cancel_result_ok:
			case R.id.print_service_msg__cancel_result_failed: {
				if (service.mCancelRequest != null) {
					Bundle status = (Bundle)msg.obj;
					String jobIDStr = status.getString(PrintServiceStrings.PRINT_JOB_HANDLE_KEY);
					if (TextUtils.isEmpty(jobIDStr)) {
						break;
					}
					try {
						if (!TextUtils.isEmpty(jobIDStr) && (jobIDStr.charAt(0) == '/')) {
							jobIDStr = jobIDStr.substring(1);
						}
						UUID.fromString(jobIDStr);
					} catch(IllegalArgumentException e) {
						jobIDStr = null;
					}
					if ((jobIDStr != null) && service.mCancelRequest.mCancelList.contains(jobIDStr)) {
						service.mCancelRequest.mCancelList.remove(jobIDStr);
						service.mCancelRequest.mResult &= (msg.what == R.id.print_service_msg__cancel_result_ok);
						if (service.mCancelRequest.mCancelList.isEmpty()) {
							if(service.mCancelRequest.mCallback != null) { 
								service.mCancelRequest.mCallback.cancelResult(service.mCancelRequest.mResult);
							}
							service.mCancelRequest = null;
						}
					}
				}
				break;
			}
			
			case R.id.print_service_msg__cancel_all_result_ok:
			case R.id.print_service_msg__cancel_all_result_failed: {
				if ((service.mCancelRequest != null) && (service.mCancelRequest.mCallback != null)){
					service.mCancelRequest.mCallback.cancelResult(msg.what == R.id.print_service_msg__cancel_all_result_ok);
				}
				service.mCancelRequest = null;
				break;
			}
			
			case R.id.print_service_msg__plugin_connected: {
				PrintPlugin plugin = (PrintPlugin)msg.obj;
				Message message = Message.obtain(null, 0, new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_REGISTER_STATUS_RECEIVER));
				message.replyTo = service.mPrintServiceCallback;
				try {
					plugin.mMessenger.send(message);
				} catch (RemoteException e) {
				}
				ArrayList<PrintJob> pluginJobList = service.mPluginJobList.get(plugin);
				if (pluginJobList != null) {
					for(PrintJob job : pluginJobList) {
						if (!job.sentToPlugin()) {
							job.updateSentToPlugin();
							sendMessage(obtainMessage(R.id.print_service_msg__run_job, job.mJobID));
						}
					}
				}
				break;
			}
			
			case R.id.print_service_msg__plugin_connect_failed:
			case R.id.print_service_msg__plugin_disconected: {
				PrintPlugin plugin = (PrintPlugin)msg.obj;
				ArrayList<PrintJob> pluginJobList = service.mPluginJobList.get(plugin);
				if (pluginJobList != null) {
					for(PrintJob job : pluginJobList) {
						sendMessage(obtainMessage(R.id.print_service_msg__job_status, job.getFailedBundle()));
					}
				}
				break;
			}
			
			default: {
				break;
			}
				
			}
		}
		
		@SuppressLint("NewApi") @SuppressWarnings("deprecation")
		private void updateServiceStatus() {
			PrintJobService service = mServiceRef.get();
			if (service == null) {
				return;
			}
			
			if (service.mJobList.isEmpty()) {
				service.mQuitHandler.postDelayed(service.mQuitRunnable, service.getResources().getInteger(R.integer.service_quit_delay));
			} else {
				for(PrintJob job : service.mJobList) {
					Notification.Builder notificationBuilder = new Notification.Builder(service)
					.setSmallIcon(R.drawable.ic_stat_printing)
					.setOnlyAlertOnce(true)
					.setAutoCancel(false)
					.setOngoing(true)
					.setTicker(service.getText(R.string.notification_ticker__now_printing))
					.setContentTitle(service.getText(R.string.notification_title__now_printing))
					.setContentIntent(PendingIntent.getActivity(service,
							R.id.notification_request_show_job_status,
							new Intent(service, PrintStatus.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
							PendingIntent.FLAG_UPDATE_CURRENT));


					String description = job.getStatus().mDescription; 
					notificationBuilder.setContentText(description);

					notificationBuilder.setContentInfo(job.getStatus().getStateText());

					if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
						String status = job.getStatus().getJobStatus();
						if (!TextUtils.isEmpty(status)) {
							notificationBuilder.setSubText(status);
						}
					}

					Notification notification;
					if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
						notification = notificationBuilder.build();
					} else {
						notification = notificationBuilder.getNotification();
					}
					service.mNotificatoinManager.notify(job.mJobID.toString(), R.id.print_service_printing_notification, notification);
				}

				service.notifyStatusChanged();
			}
		}

	}
	
	private Messenger mPrintServiceCallback = new Messenger(new JobStatusHandler(this));
	
	private NotificationManager mNotificatoinManager;
	
	private Handler mQuitHandler = new Handler();
	private Runnable mQuitRunnable = new Runnable() {

		@Override
		public void run() {
			stopSelfResult(mStartID);
		}
		
	};

	@SuppressLint("Wakelock")
	@Override
	public synchronized void onCreate() {
		super.onCreate();
		HandlerThread jobThread = new HandlerThread(getResources().getString(R.string.printer_setup_handler_thread_id));
		jobThread.start();
		mNotificatoinManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		mJobHandler = new JobHandler(this, jobThread.getLooper());
		PowerManager pm = ((PowerManager)getSystemService(Context.POWER_SERVICE));
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getString(R.string.print_service_wakelock));
		mWakeLock.acquire();
		WifiManager wm = ((WifiManager)getSystemService(Context.WIFI_SERVICE));
		mWifiLock = wm.createWifiLock(getString(R.string.print_service_wakelock));
		mWifiLock.acquire();
 	}
	
	private PrintStatusCallback mStatusCallback = null;
	public synchronized void registerStatusCallback(PrintStatusCallback callback) {
		mStatusCallback = callback;
		notifyStatusChanged();
	}
	
	public void cancelJob(ArrayList<String> jobIDs, PrintCancelCallback callback) {
		mJobHandler.sendMessage(mJobHandler.obtainMessage(R.id.print_service_msg__cancel_job, new PrintCancelRequest(jobIDs, callback)));
	}
	
	public void cancelAll(PrintCancelCallback callback) {
		mJobHandler.sendMessage(mJobHandler.obtainMessage(R.id.print_service_msg__cancel_all_jobs, new PrintCancelRequest(null, callback)));
	}
	
	public synchronized void notifyStatusChanged() {
		if (mStatusCallback != null) {
			ArrayList<PrintStatusItem> statusList = new ArrayList<PrintStatusItem>();
			ListIterator<PrintJob> jobIter = mJobList.listIterator();
			while(jobIter.hasNext()) {
				statusList.add(jobIter.next().getStatusCopy());
			}
			mStatusCallback.statusChanged(statusList);
		}
	}
	
	public synchronized void notifyJobFinished(PrintJob job) {
		if (mStatusCallback != null) {
			mStatusCallback.jobFinished(job.getStatusCopy());
		}
	}
	
	@Override
	public synchronized int onStartCommand(Intent intent, int flags, int startID) {
		mStartID = startID;
		mQuitHandler.removeCallbacks(mQuitRunnable);
		mJobHandler.sendMessage(mJobHandler.obtainMessage(R.id.print_service_msg__add_job, intent.getExtras()));
		return Service.START_NOT_STICKY;
	}

	private final IBinder mBinder = new PrintJobServiceBinder(this);
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public synchronized void onDestroy() {
		mJobHandler.sendEmptyMessage(R.id.print_service_msg__quit);
		
		Set<PrintPlugin> plugins = mPluginJobList.keySet();
		
		for(PrintPlugin plugin : plugins) {
			if (plugin.getConnectionState() == PrintPlugin.ConnectionState.CONNECTED) {
				Message message = Message.obtain(null, 0, new Intent(PrintServiceStrings.ACTION_PRINT_SERVICE_UNREGISTER_STATUS_RECEIVER));
				message.replyTo = mPrintServiceCallback;
				try {
					plugin.mMessenger.send(message);
				} catch (RemoteException e) {
				}
			}
			plugin.unbind();
		}

		mWifiLock.release();
		mWakeLock.release();
		mPrintServiceCallback = null;
	}

	@Override
	public void pluginConnected(PrintPlugin plugin) {
		mJobHandler.sendMessage(mJobHandler.obtainMessage(R.id.print_service_msg__plugin_connected, plugin));
	}

	@Override
	public void pluginConnectionFailed(PrintPlugin plugin) {
		mJobHandler.sendMessage(mJobHandler.obtainMessage(R.id.print_service_msg__plugin_connect_failed, plugin));
	}

	@Override
	public void pluginDisconnected(PrintPlugin plugin) {
		mJobHandler.sendMessage(mJobHandler.obtainMessage(R.id.print_service_msg__plugin_disconected, plugin));		
	}

}
