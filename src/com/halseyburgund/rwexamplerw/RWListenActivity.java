/*
    ROUNDWARE
	a participatory, location-aware media platform
	Android client library
   	Copyright (C) 2008-2012 Halsey Solutions, LLC
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
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.halseyburgund.rwexamplerw.R;
import com.halseyburgund.rwframework.core.RW;
import com.halseyburgund.rwframework.core.RWService;
import com.halseyburgund.rwframework.core.RWTags;
import com.halseyburgund.rwframework.util.RWList;


public class RWListenActivity extends Activity {

	private final static String TAG = "Listen";
	
	// Roundware tag type used in this activity
	private final static String ROUNDWARE_TAGS_TYPE = "listen";
	
	// fields
	private ProgressDialog progressDialog;
	private ViewFlipper viewFlipper;
	private TextView headerLine2TextView;
	private TextView headerLine3TextView;
	private WebView filterWebView;
	private ToggleButton playButton;
	private Button homeButton;
	private Button refineButton;
	private ToggleButton likeButton;
	private ToggleButton flagButton;
	private int volumeLevel = 80;
	private RWService rwBinder;
	private RWTags projectTags;
	private RWList tagsList;
	private String contentFileDir;

	
	/**
	 * Handles connection state to an RWService Android Service. In this
	 * activity it is assumed that the service has already been started
	 * by another activity and we only need to connect to it.
	 */
	private ServiceConnection rwConnection = new ServiceConnection() {
		@SuppressLint("SetJavaScriptEnabled")
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			rwBinder = ((RWService.RWServiceBinder) service).getService();
			rwBinder.playbackFadeIn(volumeLevel);
			rwBinder.setVolumeLevel(volumeLevel, false);
			
			// create a tags list for display and selection
			projectTags = rwBinder.getTags().filterByType(ROUNDWARE_TAGS_TYPE);
			tagsList = new RWList(projectTags);
			tagsList.restoreSelectionState(getSharedPreferences(RWExampleWebViewsActivity.APP_SHARED_PREFS, MODE_PRIVATE));

			// get the folder where the web content files are stored
			contentFileDir = rwBinder.getContentFilesDir();
			if ((filterWebView != null) && (contentFileDir != null)) {
				String contentFileName = rwBinder.getContentFilesDir() + "listen.html";
				Log.d(TAG, "Content filename: " + contentFileName);
				try {
					String data = grabAsSingleString(new File(contentFileName));
					data = data.replace("/*%roundware_tags%*/", tagsList.toJsonForWebView(ROUNDWARE_TAGS_TYPE));
					filterWebView.loadDataWithBaseURL("file://" + contentFileName, data, null, null, null);
				} catch (FileNotFoundException e) {
					Log.e(TAG, "No content to load, missing file: " + contentFileName);
					// TODO: dialog?? error??
					filterWebView.loadUrl("file://" + contentFileName);
				}
			}
			
			updateUIState();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			rwBinder = null;
		}
	};
	
	
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
			if (RW.READY_TO_PLAY.equals(intent.getAction())) {
				// remove progress dialog when needed
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
			} else if (RW.STREAM_METADATA_UPDATED.equals(intent.getAction())) {
				String title = intent.getStringExtra(RW.EXTRA_STREAM_METADATA_TITLE);
				if (headerLine3TextView != null) {
					headerLine3TextView.setText(title);
				}
			} else if (RW.USER_MESSAGE.equals(intent.getAction())) {
				showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), false, false);
			} else if (RW.ERROR_MESSAGE.equals(intent.getAction())) {
				showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), true, false);
			} else if (RW.SESSION_OFF_LINE.equals(intent.getAction())) {
				showMessage(getString(R.string.connection_to_server_lost_play), true, false);
			}
		}
	};
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		
		setContentView(R.layout.listen);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
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
		if (tagsList != null) {
			tagsList.saveSelectionState(getSharedPreferences(RWExampleWebViewsActivity.APP_SHARED_PREFS, MODE_PRIVATE));
		}
	}


	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter filter = new IntentFilter();
		filter.addAction(RW.READY_TO_PLAY);
		filter.addAction(RW.SESSION_ON_LINE);
		filter.addAction(RW.CONTENT_LOADED);
		filter.addAction(RW.SESSION_OFF_LINE);
		filter.addAction(RW.UNABLE_TO_PLAY);
		filter.addAction(RW.ERROR_MESSAGE);
		filter.addAction(RW.USER_MESSAGE);
		filter.addAction(RW.STREAM_METADATA_UPDATED);
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
		headerLine2TextView = (TextView) findViewById(R.id.header_line2_textview);
		headerLine3TextView = (TextView) findViewById(R.id.header_line3_textview);
		
		filterWebView = (WebView) findViewById(R.id.filter_webview);
		
		WebSettings webSettings = filterWebView.getSettings();
		webSettings.setRenderPriority(RenderPriority.HIGH);
		webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
		webSettings.setJavaScriptEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
		webSettings.setSupportMultipleWindows(false);
		webSettings.setSupportZoom(false);
	    webSettings.setSavePassword(false);
	    webSettings.setGeolocationDatabasePath(this.getFilesDir().getAbsolutePath());
	    webSettings.setGeolocationEnabled(false);
	    webSettings.setDatabaseEnabled(false);
	    webSettings.setDomStorageEnabled(false);

		filterWebView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				Log.d(TAG, "shouldOverrideUrlLoading");
				Uri uri = Uri.parse(url);
				if (uri.getScheme().equals("roundware")) {
					Log.d(TAG, "Processing roundware uri: " + url);
					String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
					if ("//listen_done".equalsIgnoreCase(schemeSpecificPart)) {
						// request update of audio stream directly when needed
						if ((rwBinder != null) && (tagsList.hasValidSelectionsForTags())) {
							if (rwBinder.isPlaying()) {
								new ModifyStreamTask(tagsList, getString(R.string.modify_stream_problem)).execute();
							}
						}
						viewFlipper.showPrevious();
					} else {
						if (tagsList != null) {
							tagsList.setSelectionFromWebViewMessageUri(uri);
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
				if (refineButton != null) {
					refineButton.setEnabled(true);
				}
				super.onPageFinished(view, url);
			}

			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				Log.d(TAG, "onPageStarted");
				if (refineButton != null) {
					refineButton.setEnabled(false);
				}
				super.onPageStarted(view, url, favicon);
			}

			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				Log.e(TAG, "Page load error: " + description);
				if (refineButton != null) {
					refineButton.setEnabled(false);
				}
				super.onReceivedError(view, errorCode, description, failingUrl);
			}
		});
		
		viewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);

		homeButton = (Button) findViewById(R.id.home_button);
		homeButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				rwBinder.playbackStop();
				Intent homeIntent = new Intent(RWListenActivity.this, RWExampleWebViewsActivity.class);
				RWListenActivity.this.startActivity(homeIntent);
			}
		});
		
		refineButton = (Button) findViewById(R.id.refine_button);
		refineButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (viewFlipper != null) {
					viewFlipper.showNext();
				}
			}
		});

		playButton = (ToggleButton) findViewById(R.id.play_button);
		playButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (!rwBinder.isPlaying()) {
					if (!rwBinder.isPlayingMuted()) {
						showProgress(getString(R.string.starting_playback_title), getString(R.string.starting_playback_message), true, true);
						rwBinder.playbackStart(null);
					}
					rwBinder.playbackFadeIn(volumeLevel);
					// playButton.setChecked(true);
				} else {
					volumeLevel = rwBinder.getVolumeLevel();
					rwBinder.playbackFadeOut();
					// playButton.setChecked(false);
				}
				updateUIState();
			}
		});

		flagButton = (ToggleButton) findViewById(R.id.flag_button);
		// TODO: add button click handler for flagging recordings
		flagButton.setEnabled(false);
		
		likeButton = (ToggleButton) findViewById(R.id.like_button);
		// TODO: add button click handler for liking recordings
		likeButton.setEnabled(false);
	}


	/**
	 * Updates the state of the primary UI widgets based on current
	 * connection state and other state variables.
	 */
	private void updateUIState() {
		if (rwBinder == null) {
			// not connected to RWService
			playButton.setChecked(false);
			playButton.setEnabled(false);
			refineButton.setEnabled(false);
			headerLine2TextView.setText(R.string.off_line);
		} else {
			// connected to RWService
			boolean isPlaying = rwBinder.isPlaying();
			playButton.setEnabled(true);
			playButton.setChecked(isPlaying);
			// refineButton.setEnabled(true);
			
			if (isPlaying) {
				if (rwBinder.isPlayingStaticSoundtrack()) {
					headerLine2TextView.setText(R.string.playing_static_soundtrack_msg);		
				} else {
					headerLine2TextView.setText(R.string.playing);
				}
			} else {
				headerLine2TextView.setText(R.string.paused);
			}
		}
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
	 * Shows a standardized progress dialog for the specified conditions.
	 * 
	 * @param title to be displayed
	 * @param message to be displayed
	 * @param isIndeterminate setting for the progress dialog
	 * @param isCancelable setting for the progress dialog
	 */
	private void showProgress(String title, String message, boolean isIndeterminate, boolean isCancelable) {
		if (progressDialog == null) {
			progressDialog = Utils.showProgressDialog(this, title, message, isIndeterminate, isCancelable);
		}
	}
	

	/**
	 * Async task that calls rwModifyStream for direct processing, but in
	 * the background for Android to keep the UI responsive.
	 * 
	 * @author Rob Knapen
	 */
	private class ModifyStreamTask extends AsyncTask<Void, Void, String> {

		private RWList selections;
		private String errorMessage;
		
		public ModifyStreamTask(RWList selections, String errorMessage) {
			this.selections = selections;
			this.errorMessage = errorMessage;
		}

		@Override
		protected String doInBackground(Void... params) {
			try {
				rwBinder.rwModifyStream(selections, true);
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
