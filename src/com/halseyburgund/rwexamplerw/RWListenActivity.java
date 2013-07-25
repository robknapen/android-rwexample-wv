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

import com.halseyburgund.rwframework.core.RW;
import com.halseyburgund.rwframework.core.RWService;
import com.halseyburgund.rwframework.core.RWTags;
import com.halseyburgund.rwframework.util.RWList;


public class RWListenActivity extends Activity {

	private final static String TAG = "Listen";
	
	// Roundware tag type used in this activity
	private final static String ROUNDWARE_TAGS_TYPE = "listen";

    // Roundware voting types used in this activity
    private final static String AS_VOTE_TYPE_FLAG = "flag";
    private final static String AS_VOTE_TYPE_LIKE = "like";

    // fields
	private ProgressDialog mProgressDialog;
	private ViewFlipper mViewFlipper;
	private TextView mHeaderLine2TextView;
	private TextView mHeaderLine3TextView;
	private WebView mWebView;
	private ToggleButton mPlayButton;
	private Button mHomeButton;
	private Button mRefineButton;
	private ToggleButton mLikeButton;
	private ToggleButton mFlagButton;
	private int mVolumeLevel = 80;
	private RWService mRwBinder;
	private RWTags mProjectTags;
	private RWList mTagsList;
	private String mContentFileDir;
    private int mCurrentAssetId;
    private int mPreviousAssetId;

	
	/**
	 * Handles connection state to an RWService Android Service. In this
	 * activity it is assumed that the service has already been started
	 * by another activity and we only need to connect to it.
	 */
	private ServiceConnection rwConnection = new ServiceConnection() {
		@SuppressLint("SetJavaScriptEnabled")
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mRwBinder = ((RWService.RWServiceBinder) service).getService();
			mRwBinder.playbackFadeIn(mVolumeLevel);
			mRwBinder.setVolumeLevel(mVolumeLevel, false);
			
			// create a tags list for display and selection
			mProjectTags = mRwBinder.getTags().filterByType(ROUNDWARE_TAGS_TYPE);
			mTagsList = new RWList(mProjectTags);
			mTagsList.restoreSelectionState(getSharedPreferences(RWExampleWebViewsActivity.APP_SHARED_PREFS, MODE_PRIVATE));

			// get the folder where the web content files are stored
			mContentFileDir = mRwBinder.getContentFilesDir();
			if ((mWebView != null) && (mContentFileDir != null)) {
// DEBUG:
                mWebView.loadUrl("http://halseyburgund.com/dev/rw/webview/sfms/listen.html");

//				String contentFileName = mRwBinder.getContentFilesDir() + "listen.html";
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
				if (mProgressDialog != null) {
					mProgressDialog.dismiss();
				}
            } else if (RW.STREAM_METADATA_UPDATED.equals(intent.getAction())) {
                // new recording started playing - update title display
                int previousAssetId = intent.getIntExtra(RW.EXTRA_STREAM_METADATA_PREVIOUS_ASSET_ID, -1);
                int currentAssetId = intent.getIntExtra(RW.EXTRA_STREAM_METADATA_CURRENT_ASSET_ID, -1);
                String title = intent.getStringExtra(RW.EXTRA_STREAM_METADATA_TITLE);
                handleRecordingChange(previousAssetId, currentAssetId, title);
                // remove progress dialog when needed
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
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
		if (mTagsList != null) {
			mTagsList.saveSelectionState(getSharedPreferences(RWExampleWebViewsActivity.APP_SHARED_PREFS, MODE_PRIVATE));
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

        startPlayback();
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
		mHeaderLine2TextView = (TextView) findViewById(R.id.header_line2_textview);
		mHeaderLine3TextView = (TextView) findViewById(R.id.header_line3_textview);
		
		mWebView = (WebView) findViewById(R.id.filter_webview);
		
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
	    webSettings.setGeolocationDatabasePath(this.getFilesDir().getAbsolutePath());
	    webSettings.setGeolocationEnabled(false);
	    webSettings.setDatabaseEnabled(false);
	    webSettings.setDomStorageEnabled(false);

		mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading");
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals("roundware")) {
                    Log.d(TAG, "Processing roundware uri: " + url);
                    String schemeSpecificPart = uri.getSchemeSpecificPart(); // everything from : to #
                    if ("//listen_done".equalsIgnoreCase(schemeSpecificPart)) {
                        // request update of audio stream directly when needed
                        if ((mRwBinder != null) && (mTagsList.hasValidSelectionsForTags())) {
                            if (mRwBinder.isPlaying()) {
                                new ModifyStreamTask(mTagsList, getString(R.string.modify_stream_problem)).execute();
                            }
                        }
                        mViewFlipper.showPrevious();
                    } else {
                        if (mTagsList != null) {
                            mTagsList.setSelectionFromWebViewMessageUri(uri);
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
                if (mRefineButton != null) {
                    mRefineButton.setEnabled(true);
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(TAG, "onPageStarted");
                if (mRefineButton != null) {
                    mRefineButton.setEnabled(false);
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "Page load error: " + description);
                if (mRefineButton != null) {
                    mRefineButton.setEnabled(false);
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });
		
		mViewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);

		mHomeButton = (Button) findViewById(R.id.home_button);
		mHomeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRwBinder.playbackStop();
                Intent homeIntent = new Intent(RWListenActivity.this, RWExampleWebViewsActivity.class);
                RWListenActivity.this.startActivity(homeIntent);
            }
        });
		
		mRefineButton = (Button) findViewById(R.id.refine_button);
		mRefineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mViewFlipper != null) {
                    mViewFlipper.showNext();
                }
            }
        });

		mPlayButton = (ToggleButton) findViewById(R.id.play_button);
		mPlayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!mRwBinder.isPlaying()) {
                    startPlayback();
                } else {
                    stopPlayback();
                }
                updateUIState();
            }
        });

		mFlagButton = (ToggleButton) findViewById(R.id.flag_button);
		mFlagButton.setEnabled(false);
		
		mLikeButton = (ToggleButton) findViewById(R.id.like_button);
		mLikeButton.setEnabled(false);
	}


    private void startPlayback() {
        if (mRwBinder != null) {
            if (!mRwBinder.isPlaying()) {
                if (!mRwBinder.isPlayingMuted()) {
                    showProgress(getString(R.string.starting_playback_title), getString(R.string.starting_playback_message), true, true);
                    mCurrentAssetId = -1;
                    mPreviousAssetId = -1;
                    mRwBinder.playbackStart(mTagsList);
                }
                mRwBinder.playbackFadeIn(mVolumeLevel);
            }
        }
        updateUIState();
    }


    private void stopPlayback() {
        sendVotingState(mCurrentAssetId);
        mVolumeLevel = mRwBinder.getVolumeLevel();
        mRwBinder.playbackFadeOut();
        mCurrentAssetId = -1;
        mPreviousAssetId = -1;
        updateUIState();
    }


    private void handleRecordingChange(int previousAssetId, int currentAssetId, String newTitle) {
        Log.d(TAG, String.format("Recording changed, new asset ID: %d, new title: %s", currentAssetId, newTitle));

        mCurrentAssetId = currentAssetId;
        mPreviousAssetId = previousAssetId;

        // send asset voting if needed
        sendVotingState(mPreviousAssetId);

        // show info about new recording
        if ((mHeaderLine3TextView != null) && (mRwBinder != null)) {
            mHeaderLine3TextView.setText(newTitle);
        }

        // reset vote buttons
        if (mLikeButton != null) {
            mLikeButton.setEnabled(true);
            mLikeButton.setChecked(false);
        }
        if (mFlagButton != null) {
            mFlagButton.setEnabled(true);
            mFlagButton.setChecked(false);
        }
    }


    private void sendVotingState(int assetId) {
        if (assetId != -1) {
            if (mFlagButton.isChecked()) {
                new VoteAssetTask(assetId, AS_VOTE_TYPE_FLAG, null, getString(R.string.vote_asset_problem)).execute();
            }
            if (mLikeButton.isChecked()) {
                new VoteAssetTask(assetId, AS_VOTE_TYPE_LIKE, null, getString(R.string.vote_asset_problem)).execute();
            }
        }
    }


    /**
	 * Updates the state of the primary UI widgets based on current
	 * connection state and other state variables.
	 */
	private void updateUIState() {
		if (mRwBinder == null) {
			// not connected to RWService
			mPlayButton.setChecked(false);
			mPlayButton.setEnabled(false);
            mLikeButton.setChecked(false);
            mLikeButton.setEnabled(false);
            mFlagButton.setChecked(false);
            mFlagButton.setEnabled(false);
            mRefineButton.setEnabled(false);
			mHeaderLine2TextView.setText(R.string.off_line);
		} else {
			// connected to RWService
			boolean isPlaying = mRwBinder.isPlaying();
			mPlayButton.setEnabled(true);
			mPlayButton.setChecked(isPlaying);

			if (isPlaying) {
				if (mRwBinder.isPlayingStaticSoundtrack()) {
					mHeaderLine2TextView.setText(R.string.playing_static_soundtrack_msg);
				} else {
					mHeaderLine2TextView.setText(R.string.playing);
				}
			} else {
				mHeaderLine2TextView.setText(R.string.paused);
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
		if (mProgressDialog == null) {
			mProgressDialog = Utils.showProgressDialog(this, title, message, isIndeterminate, isCancelable);
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
				mRwBinder.rwModifyStream(selections, true);
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


    /**
     * Async task that calls rwSendVoteAsset for direct processing, but in
     * the background for Android to keep the UI responsive.
     *
     * @author Rob Knapen
     */
    private class VoteAssetTask extends AsyncTask<Void, Void, String> {

        private int mAssetId;
        private String mVoteType;
        private String mVoteValue;
        private String mErrorMessage;

        public VoteAssetTask(int assetId, String voteType, String voteValue, String errorMessage) {
            mAssetId = assetId;
            mVoteType = voteType;
            mVoteValue = voteValue;
            mErrorMessage = errorMessage;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                mRwBinder.rwSendVoteAsset(mAssetId, mVoteType, mVoteValue, true);
                return null;
            } catch (Exception e) {
                return mErrorMessage;
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
