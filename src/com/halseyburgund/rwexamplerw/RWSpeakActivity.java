/*
    ROUNDWARE
	a participatory, location-aware media platform
	Android client library
   	Copyright (C) 2008-2013 Halsey Solutions, LLC
	with contributions by Rob Knapen (shuffledbits.com) and Dan Latham
	http://roundware.org | contact@roundware.org

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

 	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 	GNU General Public License for more details.

 	You should have received a copy of the GNU General Public License
 	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/ 		
package com.halseyburgund.rwexamplerw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings.RenderPriority;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.halseyburgund.rwframework.core.RW;
import com.halseyburgund.rwframework.core.RWRecordingTask;
import com.halseyburgund.rwframework.core.RWService;
import com.halseyburgund.rwframework.core.RWTags;
import com.halseyburgund.rwframework.util.RWList;


public class RWSpeakActivity extends Activity {

	private final static String TAG = "Speak";
	
	// Roundware tag type used in this activity
	private final static String ROUNDWARE_TAGS_TYPE = "speak";
	
	// settings for storing recording as file
	private final static String STORAGE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/rwexamplevw/";

    // fields
	private ViewFlipper mViewFlipper;
	private WebView mWebView;
	private TextView mHeaderLine2TextView;
	private Button mAgreeButton;
	private ToggleButton mRecordButton;
	private Button mRerecordButton;
	private Button mUploadButton;
	private Button mCancelButton;
	private RWService mRwBinder;
	private RWTags mProjectTags;
	private RWList mTagsList;
	private RWRecordingTask mRecordingTask;
	private boolean mHasRecording = false;
	private String mContentFileDir;
	
	
	/**
	 * Handles connection state to an RWService Android Service. In this
	 * activity it is assumed that the service has already been started
	 * by another activity and we only need to connect to it.
	 */
	private ServiceConnection rwConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mRwBinder = ((RWService.RWServiceBinder) service).getService();
			
			// create a tags list for display and selection
			mProjectTags = mRwBinder.getTags().filterByType(ROUNDWARE_TAGS_TYPE);
			mTagsList = new RWList(mProjectTags);
			mTagsList.restoreSelectionState(getSharedPreferences(RWExampleWebViewsActivity.APP_SHARED_PREFS, MODE_PRIVATE));

			// get the folder where the web content files are stored
			mContentFileDir = mRwBinder.getContentFilesDir();
			if ((mWebView != null) && (mContentFileDir != null)) {
// DEBUG:
                mWebView.loadUrl("http://halseyburgund.com/dev/rw/webview/sfms/speak.html");

//				String contentFileName = mRwBinder.getContentFilesDir() + "speak.html";
//				Log.d(TAG, "Content filename: " + contentFileName);
//				try {
//					String data = grabAsSingleString(new File(contentFileName));
//					data = data.replace("/*%roundware_tags%*/", mTagsList.toJsonForWebView(ROUNDWARE_TAGS_TYPE));
//					mWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
//				} catch (FileNotFoundException e) {
//					Log.e(TAG, "No content to load, missing file: " + contentFileName);
//					// TODO: dialog?? error??
//					// mWebView.loadUrl("file://" + contentFileName);
//				}
			}
			
			updateUIState();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mRwBinder = null;
		}
	};
	
	
	// TODO: method duplicated in RWListenActivity and RWSpeakActivity, move to RWFramework
	public static final String grabAsSingleString(File fileName) throws FileNotFoundException {
        BufferedReader reader = null;
        String returnString = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            char[] charArray = null;

            if(fileName.length() > Integer.MAX_VALUE) {
                // TODO: implement handling of large files.
                System.out.println("The file is larger than int max = " + Integer.MAX_VALUE);
            } else {
                charArray = new char[(int)fileName.length()];
                reader.read(charArray, 0, (int)fileName.length());
                returnString = new String(charArray);
            }
        } catch (FileNotFoundException ex) {
            throw ex;
        } catch(IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
            	if (reader != null) {
            		reader.close();
            	}
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return returnString;
    }	
	

	/**
	 * Handles events received from the RWService Android Service that we
	 * connect to. Sinds most operations of the service involve making calls
	 * to the Roundware server, the response is handle asynchronously with
	 * results passed back as broadcast intents. An IntentFilter is set up
	 * in the onResume method of this activity and controls which intents
	 * from the RWService will be received and processed here.
	 */
	private BroadcastReceiver rwReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateUIState();
			if (RW.SESSION_OFF_LINE.equals(intent.getAction())) {
				showMessage(getString(R.string.connection_to_server_lost_record), false, false);
			} else if (RW.USER_MESSAGE.equals(intent.getAction())) {
				showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), false, false);
			} else if (RW.ERROR_MESSAGE.equals(intent.getAction())) {
				showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), true, false);
			} else if (RW.SHARING_MESSAGE.equals(intent.getAction())) {
				confirmSharingMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE));
			}
		}
	};
	
	
    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.speak);
		initUIWidgets();

		// connect to service started by other activity
		try {
			Intent bindIntent = new Intent(this, RWService.class);
			bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);
		} catch (Exception ex) {
			showMessage(getString(R.string.connection_to_server_failed) + " " + ex.getMessage(), true, true);
		}
	}

    
	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(rwReceiver);
		if (mTagsList != null) {
			mTagsList.saveSelectionState(getSharedPreferences(RWExampleWebViewsActivity.APP_SHARED_PREFS, MODE_PRIVATE));
		}
	}


	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter filter = new IntentFilter();
		filter.addAction(RW.SESSION_ON_LINE);
		filter.addAction(RW.SESSION_OFF_LINE);
		filter.addAction(RW.TAGS_LOADED);
		filter.addAction(RW.ERROR_MESSAGE);
		filter.addAction(RW.USER_MESSAGE);
		filter.addAction(RW.SHARING_MESSAGE);
		registerReceiver(rwReceiver, filter);

		updateUIState();
	}


	@Override
	protected void onDestroy() {
		if (rwConnection != null) {
			unbindService(rwConnection);
		}
		super.onDestroy();
	}

	
	/**
	 * Sets up the primary UI widgets (spinner and buttons), and how to
	 * handle interactions.
	 */
	@SuppressLint("SetJavaScriptEnabled")
	private void initUIWidgets() {
		mHeaderLine2TextView = (TextView)findViewById(R.id.header_line2_textview);

		mViewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);

		mAgreeButton = (Button) findViewById(R.id.agree_button);
		mAgreeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewFlipper.showNext();
                updateUIState();
            }
        });
		
		mWebView = (WebView) findViewById(R.id.tagging_webview);
		WebSettings webSettings = mWebView.getSettings();
		webSettings.setRenderPriority(RenderPriority.HIGH);

        webSettings.setAppCachePath(this.getFilesDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

		webSettings.setJavaScriptEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
		webSettings.setSupportMultipleWindows(false);
		webSettings.setSupportZoom(false);
	    webSettings.setSavePassword(false);
	    webSettings.setGeolocationEnabled(false);
	    webSettings.setDatabaseEnabled(false);
	    webSettings.setDomStorageEnabled(false);

		mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("roundware")) {
                    Log.d(TAG, "Processing roundware uri: " + url);
                    String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
                    if ("//speak_cancel".equalsIgnoreCase(schemeSpecificPart)) {
                        cancel();
                    } else {
                        if (mTagsList != null) {
                            boolean done = mTagsList.setSelectionFromWebViewMessageUri(uri);
                            if (done) {
                                mViewFlipper.showNext();
                                updateUIState();
                            }
                        }
                    }
                    return true;
                }
                // open link in external browser
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                Log.d(TAG, "onLoadResource: " + url);
                super.onLoadResource(view, url);
            }

            @Override
            public void onScaleChanged(WebView view, float oldScale, float newScale) {
                Log.d(TAG, "onScaleChanged");
                super.onScaleChanged(view, oldScale, newScale);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished");
                if (mAgreeButton != null) {
                    mAgreeButton.setEnabled(true);
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(TAG, "onPageStarted");
                if (mAgreeButton != null) {
                    mAgreeButton.setEnabled(false);
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "Page load error: " + description);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });
		
		mRecordButton = (ToggleButton) findViewById(R.id.record_button);
		mRecordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if ((mRecordingTask != null) && (mRecordingTask.isRecording())) {
                    mRecordingTask.stopRecording();
                    mRecordButton.setChecked(false);
                    mRerecordButton.setEnabled(true);
                    mUploadButton.setEnabled(true);
                } else {
                    startRecording();
                    mRecordButton.setChecked(true);
                    mRerecordButton.setEnabled(false);
                    mUploadButton.setEnabled(false);
                }
            }
        });

		// TODO: rerecord button implementation
		mRerecordButton = (Button) findViewById(R.id.rerecord_button);
		
		mUploadButton = (Button) findViewById(R.id.upload_button);
		mUploadButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mRecordingTask != null) {
                    stopRecording();
                    mRerecordButton.setEnabled(false);
                    mHasRecording = false;
                    mUploadButton.setEnabled(false);
                    new SubmitTask(mTagsList, mRecordingTask.getRecordingFileName(),
                            getString(R.string.recording_submit_problem)).execute();
                }
            }
        });
		
		mCancelButton = (Button) findViewById(R.id.cancel_button);
		mCancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cancel();
            }
        });
	}
	
	
	private void cancel() {
		stopRecording();
		Intent homeIntent = new Intent(RWSpeakActivity.this, RWExampleWebViewsActivity.class);
		RWSpeakActivity.this.startActivity(homeIntent);
	}
	

	/**
	 * Updates the state of the primary UI widgets based on current
	 * connection state and other state variables.
	 */
	private void updateUIState() {
		if (mRwBinder == null) {
			// not connected to RWService
			mRecordButton.setEnabled(false);
			mRerecordButton.setEnabled(false);
			mUploadButton.setEnabled(false);
		} else {
			// update legal agreement text
			TextView tx = (TextView) findViewById(R.id.legal_agreement_textview);
			if (tx != null) {
				tx.setText(mRwBinder.getConfiguration().getLegalAgreement());
			}
			
			// connected to RWService
			if (!mTagsList.hasValidSelectionsForTags()) {
				mRecordButton.setEnabled(false);
				mRerecordButton.setEnabled(false);
				mUploadButton.setEnabled(false);
			} else {
				mRecordButton.setEnabled(true);
				mRerecordButton.setEnabled(mHasRecording);
				mUploadButton.setEnabled(mHasRecording);
			}
		}
		
	}
	
	
	/**
	 * Starts making a recording using the RWRecordingTask of the Roundware
	 * framework. A listener implementation is used to receive callbacks
	 * during recording and update the UI using runnables posted to the
	 * UI thread (the callbacks will be made from a background thread).
	 */
	private void startRecording() {
		mRecordingTask = new RWRecordingTask(mRwBinder, STORAGE_PATH, new RWRecordingTask.StateListener() {
        	private int maxTimeSec = mRwBinder.getConfiguration().getMaxRecordingTimeSec();
        	private long startTimeStampMillis;
        	
        	public void recording(long timeStampMillis, short [] samples) {
        		int elapsedTimeSec = (int)Math.round((timeStampMillis - startTimeStampMillis) / 1000.0);
        		final int min = elapsedTimeSec / 60;
        		final int sec = elapsedTimeSec - (min * 60);
        		mHeaderLine2TextView.post(new Runnable() {
                    public void run() {
                        mHeaderLine2TextView.setText(String.format("%1d:%02d", min, sec));
                    }
                });
        		if (elapsedTimeSec > maxTimeSec) {
        			mRecordingTask.stopRecording();
        		}
        	}
        	
        	public void recordingStarted(long timeStampMillis) {
        		if (mRwBinder != null) {
        			maxTimeSec = mRwBinder.getConfiguration().getMaxRecordingTimeSec();
        		}
        		startTimeStampMillis = timeStampMillis;
        		mHeaderLine2TextView.post(new Runnable() {
                    public void run() {
                        mHeaderLine2TextView.setText(R.string.recording_started);
                    }
                });
        	}
        	
        	public void recordingStopped(long timeStampMillis) {
        		mHeaderLine2TextView.post(new Runnable() {
                    public void run() {
                        mHeaderLine2TextView.setText(R.string.recording_stopped);
                    }
                });
        	}
        });
		
		mRecordingTask.execute();
    }
		
	
	/**
	 * Stops the recording task if it is running.
	 */
	private void stopRecording() {
		if ((mRecordingTask != null) && (mRecordingTask.isRecording())) {
			mRecordingTask.stopRecording();
			mHasRecording = true;
			updateUIState();
		}
	}
	
	
	/**
	 * Displays a dialog for a (social media) sharing message that was
	 * sent back to the app by the framework after succesfully submitting
	 * a recording. When confirmed an ACTION_SEND intent is created and
	 * used to allow the user to select a matching activity (app) to
	 * handle it. This can be Facebook, Twitter, email, etc., whatever
	 * matching app that is installed on the device.
	 * 
	 * @param message to be shared
	 */
	private void confirmSharingMessage(final String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.confirm_sharing_title)
			.setMessage(message)
			.setCancelable(true)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_SUBJECT, R.string.sharing_subject);
					intent.putExtra(Intent.EXTRA_TEXT, message);
					intent.setType("text/plain");
					startActivity(Intent.createChooser(intent, getString(R.string.sharing_chooser_title)));
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
		builder.create().show();		
	}
	
	
	/**
	 * Shows a standardized message dialog for the specified conditions.
	 * 
	 * @param message to be displayed
	 * @param isError type of notification
	 * @param isFatal dialog exits activity
	 */
	private void showMessage(String message, boolean isError, boolean isFatal) {
		Utils.showMessageDialog(this, message, isError, isFatal);
	}


	/**
	 * Async task that calls rwSubmit for direct processing, but in the
	 * background for Android to keep the UI responsive.
	 * 
	 * @author Rob Knapen
	 */
	private class SubmitTask extends AsyncTask<Void, Void, String> {

		private RWList selections;
		private String filename;
		private String errorMessage;
		
		public SubmitTask(RWList selections, String filename, String errorMessage) {
			this.selections = selections;
			this.filename = filename;
			this.errorMessage = errorMessage;
		}

		@Override
		protected String doInBackground(Void... params) {
			try {
				mRwBinder.rwSubmit(selections, filename, null, true, true);
				return null;
			} catch (Exception e) {
				return errorMessage;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (result != null) {
				showMessage(result, true, false);
			}
		}
	}
	
}
