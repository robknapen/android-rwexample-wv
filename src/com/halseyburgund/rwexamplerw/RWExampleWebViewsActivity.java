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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebSettings.RenderPriority;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.halseyburgund.rwexamplerw.R;
import com.halseyburgund.rwframework.core.RW;
import com.halseyburgund.rwframework.core.RWService;
import com.halseyburgund.rwframework.util.RWList;


public class RWExampleWebViewsActivity extends Activity {

	private final static String TAG = "Home";
	
	// name of shared preferences used by all activities in the app
	public final static String APP_SHARED_PREFS = "com.halseyburgund.rwexamplerw.preferences";
	
	// menu items
	private final static int MENU_ITEM_PREFERENCES = Menu.FIRST;
	private final static int MENU_ITEM_EXIT = Menu.FIRST + 1;

	// fields
	private ProgressDialog progressDialog;
	private Intent rwService;
	private RWService rwBinder;
	private String deviceId;
	private String projectId;
	private boolean connected;
	private Button listenButton;
	private Button speakButton;
	private Button exitButton;
    private Button prefsButton;
	private TextView headerLine1TextView;
	private TextView headerLine2TextView;
	private ViewFlipper viewFlipper;
	private WebView webView;

	
	/**
	 * Handles connection state to an RWService Android Service. In this
	 * activity it is assumed that the service has already been started
	 * by another activity and we only need to connect to it.
	 */
	private ServiceConnection rwConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			rwBinder = ((RWService.RWServiceBinder) service).getService();
			updateServerForPreferences();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			rwBinder = null;
			updateUIState(false);
		}
	};

	
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
			if (RW.SESSION_ON_LINE.equals(intent.getAction())) {
				updateUIState(true);
				updateServerForPreferences();
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
			} else if (RW.SESSION_OFF_LINE.equals(intent.getAction())) {
				updateUIState(false);
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
			} else if (RW.CONFIGURATION_LOADED.equals(intent.getAction())) {
				if (headerLine2TextView != null) {
					headerLine2TextView.setText(rwBinder.getConfiguration().getProjectName());
				}
				updateUIState(connected);
			} else if (RW.NO_CONFIGURATION.equals(intent.getAction())) {
				if (headerLine2TextView != null) {
					headerLine2TextView.setText(R.string.no_project_info);
				}
				updateUIState(false);
				showMessage(getString(R.string.unable_to_retrieve_configuration), true, true);
			} else if (RW.TAGS_LOADED.equals(intent.getAction())) {
				if (rwBinder.getConfiguration().isResetTagsDefaultOnStartup()) {
					RWList allTags = new RWList(rwBinder.getTags());
					allTags.saveSelectionState(getSharedPreferences(APP_SHARED_PREFS, MODE_PRIVATE));
				}
			} else if (RW.CONTENT_LOADED.equals(intent.getAction())) {
				String contentFileDir = rwBinder.getContentFilesDir();
				if ((webView != null) && (contentFileDir != null)) {
// DEBUG:
                    webView.loadUrl("http://halseyburgund.com/dev/rw/webview/sfms/home.html");

//					String contentFileName = rwBinder.getContentFilesDir() + "home.html";
//					Log.d(TAG, "Content filename: " + contentFileName);
//					webView.loadUrl("file://" + contentFileName);
				}
			} else if (RW.USER_MESSAGE.equals(intent.getAction())) {
				showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), false, false);
			} else if (RW.ERROR_MESSAGE.equals(intent.getAction())) {
				showMessage(intent.getStringExtra(RW.EXTRA_SERVER_MESSAGE), true, false);
			}
		}
	};


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		initUIWidgets();
		updateUIState(false);

		// create session start unless one is passed in
		startRWService(getIntent());
	}


	@Override
	protected void onPause() {
		unregisterReceiver(rwReceiver);
		super.onPause();
	}


	@Override
	protected void onResume() {
		// set up filter for the RWFramework events this activity is interested in
		IntentFilter filter = new IntentFilter();
		
		// get the operation name and add the intents to the filter
		String opName = getString(R.string.rw_op_get_tags);
		RWService.addOperationsToIntentFilter(filter, opName);
		
		// add predefined (high-level) intents
		filter.addAction(RW.SESSION_ON_LINE);
		filter.addAction(RW.SESSION_OFF_LINE);
		filter.addAction(RW.CONFIGURATION_LOADED);
		filter.addAction(RW.NO_CONFIGURATION);
		filter.addAction(RW.TAGS_LOADED);
		filter.addAction(RW.CONTENT_LOADED);
		filter.addAction(RW.ERROR_MESSAGE);
		filter.addAction(RW.USER_MESSAGE);

		registerReceiver(rwReceiver, filter);
		
		// in case resuming after user edited settings
		updateServerForPreferences();

		updateUIState(connected);
		super.onResume();
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopRWService();
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, MENU_ITEM_PREFERENCES, Menu.NONE, R.string.preferences).setShortcut('4', 'p')
				.setIcon(android.R.drawable.ic_menu_preferences);

		menu.add(0, MENU_ITEM_EXIT, Menu.NONE, R.string.exit).setShortcut('3', 'e')
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

		return true;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_ITEM_PREFERENCES: {
				Intent settingsActivity = new Intent(getBaseContext(), RWPreferenceActivity.class);
				startActivity(settingsActivity);
				return true;
			}
			case MENU_ITEM_EXIT: {
				confirmedExit();
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	
	/**
	 * Starts (or restarts) a RWService Android Service.
	 * 
	 * @param intent
	 */
	private void startRWService(Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras != null) {
			deviceId = extras.getString(RW.EXTRA_DEVICE_ID);
		}
		if (deviceId == null) {
			deviceId = UUID.randomUUID().toString();
		}
		projectId = getString(R.string.rw_spec_project_id);
		
		showProgress(getString(R.string.initializing), getString(R.string.connecting_to_server_message), true, true);
		try {
			// create connection to the RW service
			Intent bindIntent = new Intent(RWExampleWebViewsActivity.this, RWService.class);
			bindService(bindIntent, rwConnection, Context.BIND_AUTO_CREATE);

			// create the intent to start the RW service
			rwService = new Intent(this, RWService.class);
			rwService.putExtra(RW.EXTRA_DEVICE_ID, deviceId);
			rwService.putExtra(RW.EXTRA_PROJECT_ID, projectId);

			// check if there is a server URL override in the preferences
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			String serverName = prefs.getString(RWPreferenceActivity.SERVER_NAME, null);
			String serverPage = prefs.getString(RWPreferenceActivity.SERVER_PAGE, null);
			if ((serverName != null) && (serverPage != null)) {
				rwService.putExtra(RW.EXTRA_SERVER_URL_OVERRIDE, serverName + "/" + serverPage);				
			}
			
			// web content downloading customizations
			rwService.putExtra(RW.EXTRA_WEB_CONTENT_ALWAYS_DOWNLOAD, 
					prefs.getBoolean(RWPreferenceActivity.ALWAYS_DOWNLOAD_WEB_CONTENT, false));
			rwService.putExtra(RW.EXTRA_WEB_CONTENT_EXTERNAL_STORAGE, 
					prefs.getBoolean(RWPreferenceActivity.USE_EXTERNAL_STORAGE_FOR_WEB_CONTENT, false));
			
			// notification customizations
			rwService.putExtra(RW.EXTRA_NOTIFICATION_TITLE, getString(R.string.notification_title));
			rwService.putExtra(RW.EXTRA_NOTIFICATION_DEFAULT_TEXT, getString(R.string.notification_default_text));
			rwService.putExtra(RW.EXTRA_NOTIFICATION_ICON_ID, R.drawable.status_icon);
			rwService.putExtra(RW.EXTRA_NOTIFICATION_ACTIVITY_CLASS_NAME, this.getClass().getName());
			
			// start the service
			startService(rwService);
			
		} catch (Exception ex) {
			showMessage(getString(R.string.connection_to_server_failed) + " " + ex.getMessage(), true, true);
		}
	}

	
	/**
	 * Stops the RWService Android Service.
	 *
	 * @return true when successful
	 */
	private boolean stopRWService() {
		if (rwBinder != null) {
			rwBinder.stopService();
			unbindService(rwConnection);
		} else {
			if (rwService != null) {
				return stopService(rwService);
			}
		}
		return true;
	}
	
	
	/**
	 * Sets up the primary UI widgets (spinner and buttons), and how to
	 * handle interactions.
	 */
	@SuppressLint("SetJavaScriptEnabled")
	private void initUIWidgets() {
		headerLine1TextView = (TextView) findViewById(R.id.header_line1_textview);
		headerLine2TextView = (TextView) findViewById(R.id.header_line2_textview);
		
		viewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);
		webView = (WebView) findViewById(R.id.webview);

		// set-up the webview
		WebSettings webSettings = webView.getSettings();

		webSettings.setRenderPriority(RenderPriority.HIGH);

        webSettings.setAppCachePath(this.getFilesDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading");
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
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(TAG, "onPageStarted");
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "Page load error: " + description);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });

		listenButton = (Button) findViewById(R.id.listen_button);
		listenButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(RWExampleWebViewsActivity.this, RWListenActivity.class);
				startActivity(intent);
			}
		});

		speakButton = (Button) findViewById(R.id.speak_button);
		speakButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(RWExampleWebViewsActivity.this, RWSpeakActivity.class);
				startActivity(intent);
			}
		});

        prefsButton = (Button) findViewById(R.id.prefs_button);
        prefsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsActivity = new Intent(getBaseContext(), RWPreferenceActivity.class);
                startActivity(settingsActivity);
            }
        });

		exitButton = (Button) findViewById(R.id.exit_button);
		exitButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				confirmedExit();
			}
		});
	}
	
	
	/**
	 * Updates the state of the primary UI widgets based on current
	 * connection state and other state variables.
	 */
	private void updateUIState(boolean connectedState) {
		connected = connectedState;

		if (listenButton != null) {
			listenButton.setEnabled(connected);
		}
		if (speakButton != null) {
			speakButton.setEnabled(true);
		}

		// update offline or online server version indication
		String version = getString(R.string.off_line);
		if ((rwBinder != null) && (rwBinder.isConnected())) {
			version = rwBinder.getConfiguration().getServerVersion();
			if ((version == null) || (version.length() == 0)) {
				version = getString(R.string.no_version_info);
			} else {
				version = String.format(getString(R.string.current_version_STRING), version);					
			}
		}
		headerLine1TextView.setText(version);
	}
	
	
	/**
	 * Creates a confirmation dialog when exiting the app but there are
	 * still some items in the queue awaiting processing. The user can
	 * choose to leave them in the queue so that they will be processed
	 * the next time the app is started, or to erase the queue.
	 */
	private void confirmedExit() {
		if (rwBinder != null) {
			int itemsInQueue = rwBinder.getQueueSize();
			if (itemsInQueue > 0) {
				Builder alertBox;
				alertBox = new AlertDialog.Builder(this);
				alertBox.setTitle(R.string.confirm_exit);
				alertBox.setMessage(R.string.confirm_exit_message);
				
				alertBox.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						Toast.makeText(getApplicationContext(), R.string.thank_you_for_participating, Toast.LENGTH_SHORT).show();
						finish();
					}
				});

				alertBox.setNegativeButton(R.string.erase, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						if (!rwBinder.deleteQueue()) {
							Toast.makeText(getApplicationContext(), R.string.cannot_delete_queue, Toast.LENGTH_SHORT).show();
						}
						finish();
					}
				});
				
				alertBox.show();
			} else {
				Toast.makeText(getApplicationContext(), R.string.thank_you_for_participating, Toast.LENGTH_SHORT).show();
				finish();
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
	 * Updates settings of the RWService Service from the preferences.
	 */
	private void updateServerForPreferences() {
		if (rwBinder != null) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			boolean showDetailedMessages = prefs.getBoolean(RWPreferenceActivity.SHOW_DETAILED_MESSAGES, false);
			rwBinder.setShowDetailedMessages(showDetailedMessages);

			String mockLat = prefs.getString(RWPreferenceActivity.MOCK_LATITUDE, "");
			String mockLon = prefs.getString(RWPreferenceActivity.MOCK_LONGITUDE, "");
			rwBinder.setMockLocation(mockLat, mockLon);
			
			boolean useOnlyWiFi = prefs.getBoolean(RWPreferenceActivity.USE_ONLY_WIFI, false);
			rwBinder.setOnlyConnectOverWifi(useOnlyWiFi);
			updateUIState(rwBinder.isConnected());
		}
	}

}
