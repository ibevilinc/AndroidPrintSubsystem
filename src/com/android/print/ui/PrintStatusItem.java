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

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.widget.Checkable;

import com.hp.android.printplugin.support.PrintServiceStrings;

public class PrintStatusItem implements Checkable, Parcelable {
	
	public enum State {
		UNKNOWN,
		WAITING,
		RUNNING_OK,
		RUNNING_WTIH_WARNING,
		BLOCKED,
		CANCELLING,
		SUCCESSFUL,
		CANCELLED,
		FAILED,
		CORRUPT,
	};
	
	public final String mJobID;
	public final String mDescription;
	public final String mPrinterName;
	private String mJobStatus = null;
	private PrintStatusItem.State mState = State.WAITING;
	private String[] mWarningsErrors = null;
	private boolean mCancelled = false;
	private boolean mIsChecked = false;
	
	private String mStateText           = null;
	private String mNotificationTitle   = null;
		
	private final Resources mResources;
	
	public PrintStatusItem(String jobID, String description, String printerName, Resources resources) {
		mResources = resources;
		mJobID = jobID;
		mDescription = description;
		mPrinterName = printerName;
		mJobStatus = "";
		mState = State.WAITING;
		mStateText = mResources.getString(R.string.print_job_state__queued);
	}
	
	public PrintStatusItem(PrintStatusItem other) {
		mJobID               = other.mJobID;
		mDescription         = other.mDescription;
		mPrinterName         = other.mPrinterName;
		mJobStatus           = other.mJobStatus;
		mState               = other.mState;
		mWarningsErrors      = other.mWarningsErrors;
		mCancelled           = other.mCancelled;
		mNotificationTitle   = other.mNotificationTitle;
		mStateText           = other.mStateText;
		mResources           = null; 
	}
	
	public void update(PrintStatusItem other) {
		mJobStatus           = other.mJobStatus;
		mState               = other.mState;
		mWarningsErrors      = other.mWarningsErrors;
		mCancelled           = other.mCancelled;
		mNotificationTitle   = other.mNotificationTitle;
		mStateText              = other.mStateText;
		if (!isCheckable()) {
			setChecked(false);
		}
	}
	
	public void update(Bundle jobStatusBundle) {
		if (mResources == null)
			return;
		if (jobStatusBundle != null) {
			if (jobStatusBundle.containsKey(PrintServiceStrings.PRINT_JOB_DONE_RESULT)) {
				String doneStatus = jobStatusBundle.getString(PrintServiceStrings.PRINT_JOB_DONE_RESULT);
				mWarningsErrors   = null;
				if (doneStatus.equals(PrintServiceStrings.JOB_DONE_ERROR)) {
					mState = State.FAILED;
					mNotificationTitle = mResources.getString(R.string.notification_title__print_status__failed);
					mJobStatus = mResources.getString(R.string.job_state_description__complete__failed);
				} else if (doneStatus.equals(PrintServiceStrings.JOB_DONE_CANCELLED)) {
					mState = State.CANCELLED;
					mNotificationTitle = mResources.getString(R.string.notification_title__print_status__cancelled);
					mJobStatus = mResources.getString(R.string.job_state_description__complete__cancelled);
				} else if (doneStatus.equals(PrintServiceStrings.JOB_DONE_CORRUPT)) {
					mState = State.CORRUPT;
					mNotificationTitle = mResources.getString(R.string.notification_title__print_status__corrupt);
					mJobStatus = mResources.getString(R.string.job_state_description__complete__corrupt);
				} else {
					mState = State.SUCCESSFUL;
					mJobStatus = mResources.getString(R.string.job_state_description__complete__successful);
					mNotificationTitle = null;
				}
			} else {
				mNotificationTitle = mResources.getString(R.string.notification_title__now_printing);
				mWarningsErrors = jobStatusBundle.getStringArray(PrintServiceStrings.PRINT_JOB_BLOCKED_STATUS_KEY);
				
				String status = jobStatusBundle.getString(PrintServiceStrings.PRINT_JOB_STATUS_KEY);
				if (mCancelled) {
					mStateText = mResources.getString(R.string.print_job_state__cancelling);
				}
				else if (TextUtils.isEmpty(status)) {
					mState = State.UNKNOWN;
					mJobStatus = null;
				} else if (status.equals(PrintServiceStrings.JOB_STATE_BLOCKED)) {
					mState = State.BLOCKED;

					String statusText = null;
					boolean hasErrors, hasWarnings;
					hasErrors = hasWarnings = false;
					String[] blockedReasons = mWarningsErrors;
					
					if (blockedReasons != null) {
						for(int i = 0; i < blockedReasons.length; i++) {
							hasWarnings |= !TextUtils.isEmpty(blockedReasons[i]);
						}
						if (!hasWarnings) {
							blockedReasons = null;
						}
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
							statusText = mResources.getString(R.string.printer_state__door_open);
						} else if (jammed) {
							statusText = mResources.getString(R.string.printer_state__jammed);
						} else if (no_paper) {
							statusText = mResources.getString(R.string.printer_state__out_of_paper);
						} else if (check_printer) {
							statusText = mResources.getString(R.string.printer_state__check_printer);
						} else if (no_ink) {
							statusText = mResources.getString(R.string.printer_state__out_of_ink);
						} else if (no_toner) {
							statusText = mResources.getString(R.string.printer_state__out_of_toner);
						} else if (low_ink) {
							statusText = mResources.getString(R.string.printer_state__low_on_ink);
						} else if (low_toner) {
							statusText = mResources.getString(R.string.printer_state__low_on_toner);
						} else {
//							statusText = mResources.getString(R.string.printer_state__unknown);
							statusText = mResources.getString(R.string.job_state_description__blocked);
						}
					}
					
					mJobStatus = statusText;
					
				} else if (status.equals(PrintServiceStrings.JOB_STATE_RUNNING)) {
					if ((mWarningsErrors != null) && (mWarningsErrors.length > 0)) {
						mState = State.RUNNING_WTIH_WARNING;
					} else {
						mState = State.RUNNING_OK;
					}
					int currentPage, totalPages;
					totalPages = jobStatusBundle.getInt(PrintServiceStrings.PRINT_STATUS_TOTAL_PAGE_COUNT);
					currentPage = jobStatusBundle.getInt(PrintServiceStrings.PRINT_STATUS_CURRENT_PAGE);
					if (currentPage > 0) {
						if (totalPages > 0) {
							mJobStatus = mResources.getString(R.string.job_state_description__running__sending_page_x_of_y, currentPage, totalPages);
						} else {
							mJobStatus = mResources.getString(R.string.job_state_description__running__sending_page_x, currentPage);
						}
					} else {
						mJobStatus = null;
					}
				} else if (status.equals(PrintServiceStrings.JOB_STATE_QUEUED)) {
					mState = State.WAITING;
					mJobStatus = null;
				} 
			}
			
			switch(mState) {

			case WAITING:
				mStateText = mResources.getString(R.string.print_job_state__queued);
				break;
			case RUNNING_OK:
			case RUNNING_WTIH_WARNING:
				mStateText = mResources.getString(R.string.print_job_state__running);
				break;
			case BLOCKED:
				mStateText = mResources.getString(R.string.print_job_state__blocked);
				break;
			case CANCELLING:
				mStateText = mResources.getString(R.string.print_job_state__cancelling);
				break;
			case SUCCESSFUL:
			case CANCELLED:
			case FAILED:
			case CORRUPT:
				mStateText = mResources.getString(R.string.print_job_state__completed);
				break;
			case UNKNOWN:
			default:
				mStateText = mResources.getString(R.string.print_job_state__unknown);
				break;
			}
		}
	}
	
	public String getJobStatus() {
		return mJobStatus;
	}
	
	public PrintStatusItem.State getState() {
		return mState;
	}
	
	public String[] getWarningsErrors() {
		return mWarningsErrors;
	}
	
	public void cancel() {
		if (!mCancelled) {
			mCancelled = true;
			mState = State.CANCELLING;
			mStateText = mResources.getString(R.string.print_job_state__cancelling);
		}
	}
	
	public boolean wasUserCancelled() {
		return mCancelled;
	}
	
	public String getNotificationTitle() {
		return mNotificationTitle;
	}
	
	public String getStateText() {
		return mStateText;
	}
	
	public boolean isCheckable() {
		switch(mState) {
		case SUCCESSFUL:
		case FAILED:
		case CORRUPT:
		case CANCELLED:
		case CANCELLING:
			return false;
		default:
			return true;
		}
	}

	@Override
	public boolean isChecked() {
		return mIsChecked;
	}

	@Override
	public void setChecked(boolean checked) {
		mIsChecked = checked;
	}

	@Override
	public void toggle() {
		mIsChecked = !mIsChecked;
		
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mJobID);
		dest.writeString(mDescription);
		dest.writeString(mPrinterName);
		dest.writeString(mJobStatus);
		dest.writeSerializable(mState);
		dest.writeStringArray(mWarningsErrors);
		dest.writeInt(mCancelled ? 1 : 0);
		dest.writeString(mNotificationTitle);
		dest.writeInt(mIsChecked ? 1 : 0);
		dest.writeString(mStateText);
	}
	
    private PrintStatusItem(Parcel in) {
    	int val;
		mJobID                  = in.readString();
		mDescription            = in.readString();
		mPrinterName            = in.readString();
		mJobStatus              = in.readString();
		mState                  = (State) in.readSerializable();
		mWarningsErrors         = in.createStringArray();
		val                     = in.readInt();
		mCancelled              = (val != 0);
		mNotificationTitle      = in.readString();
		val                     = in.readInt();
		mIsChecked              = (val != 0);
		mStateText              = in.readString();
		mResources              = null; 
    }
    
    @Override
    public boolean equals(Object o) {
    	if (o == null) {
    		return false;
    	} else if (!(o instanceof PrintStatusItem)) {
    		return false;
    	} else if (o == this) {
    		return true;
    	} else {
    		PrintStatusItem other = (PrintStatusItem)o;
    		return mJobID.equals(other.mJobID);
    	}
    	
    }

    public static final Parcelable.Creator<PrintStatusItem> CREATOR = new Parcelable.Creator<PrintStatusItem>() {
        /**
         * Return a new rectangle from the data in the specified parcel.
         */
        public PrintStatusItem createFromParcel(Parcel in) {
            return new PrintStatusItem(in);
        }

        /**
         * Return an array of rectangles of the specified size.
         */
        public PrintStatusItem[] newArray(int size) {
            return new PrintStatusItem[size];
        }
    };
}
