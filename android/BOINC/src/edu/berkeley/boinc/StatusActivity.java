/*******************************************************************************
 * This file is part of BOINC.
 * http://boinc.berkeley.edu
 * Copyright (C) 2012 University of California
 * 
 * BOINC is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 * 
 * BOINC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with BOINC.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package edu.berkeley.boinc;

import java.util.ArrayList;

import edu.berkeley.boinc.adapter.GalleryAdapter;
import edu.berkeley.boinc.client.ClientStatus;
import edu.berkeley.boinc.client.ClientStatus.ImageWrapper;
import edu.berkeley.boinc.client.Monitor;
import edu.berkeley.boinc.utils.BOINCDefs;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class StatusActivity extends Activity implements OnClickListener{
	
	private final String TAG = "BOINC StatusActivity";
	
	private Monitor monitor;
	private Boolean mIsBound = false;
	
	// keep computingStatus and suspend reason to only adapt layout when changes occur
	private Integer computingStatus = -1;
	private Integer suspendReason = -1;
	
	//slide show
    private RelativeLayout slideshowWrapper;

	private BroadcastReceiver mClientStatusChangeRec = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context,Intent intent) {
			//Log.d(TAG+"-localClientStatusRecNoisy","received action " + intent.getAction());
			loadLayout(); // load layout, function distincts whether there is something to do
		}
	};
	private IntentFilter ifcsc = new IntentFilter("edu.berkeley.boinc.clientstatuschange");
	
	// connection to Monitor Service.
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	    	Log.d(TAG, "onServiceConnected");

	    	monitor = ((Monitor.LocalBinder)service).getService();
		    mIsBound = true;
		    loadLayout();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	    	Log.d(TAG, "onServiceDisconnected");

	    	monitor = null;
	        mIsBound = false;
	    }
	};

	void doBindService() {
		if(!mIsBound) {
			getApplicationContext().bindService(new Intent(this, Monitor.class), mConnection, 0); //calling within Tab needs getApplicationContext() for bindService to work!
		}
	}

	void doUnbindService() {
	    if (mIsBound) {
	        getApplicationContext().unbindService(mConnection);
	        mIsBound = false;
	    }
	}
	
	public void onCreate(Bundle savedInstanceState) {
		//bind to monitor in order to call its functions and access ClientStatus singleton
		doBindService();
		super.onCreate(savedInstanceState);
	}
	
	public void onResume() {
		//register noisy clientStatusChangeReceiver here, so only active when Activity is visible
		Log.d(TAG+"-onResume","register receiver");
		registerReceiver(mClientStatusChangeRec,ifcsc);
		loadLayout();
		super.onResume();
	}
	
	public void onPause() {
		//unregister receiver, so there are not multiple intents flying in
		Log.d(TAG+"-onPause","remove receiver");
		unregisterReceiver(mClientStatusChangeRec);
		super.onPause();
	}

	@Override
	protected void onDestroy() {
	    doUnbindService();
	    super.onDestroy();
	}
	
	private void loadLayout() {
		//load layout, if service is available and ClientStatus can be accessed.
		//if this is not the case, "onServiceConnected" will call "loadLayout" as soon as the service is bound
		if(mIsBound) {
			// get data
			ClientStatus status = Monitor.getClientStatus();
			
			// layout only if client RPC connection is established
			// otherwise BOINCActivity does not start Tabs
			if(status.setupStatus == ClientStatus.SETUP_STATUS_AVAILABLE) { 
				
				// return in cases nothing has changed
				if (computingStatus == status.computingStatus && computingStatus != ClientStatus.COMPUTING_STATUS_SUSPENDED) return; 
				if (computingStatus == status.computingStatus && computingStatus == ClientStatus.COMPUTING_STATUS_SUSPENDED && status.computingSuspendReason == suspendReason) return;
				
				// set layout and retrieve elements
				setContentView(R.layout.status_layout);
				LinearLayout centerWrapper = (LinearLayout) findViewById(R.id.center_wrapper);
				TextView statusHeader = (TextView) findViewById(R.id.status_header);
				ImageView statusImage = (ImageView) findViewById(R.id.status_image);
				TextView statusDescriptor = (TextView) findViewById(R.id.status_long);
				slideshowWrapper = (RelativeLayout) findViewById(R.id.slideshow_wrapper);
				
				// adapt to specific computing status
				switch(status.computingStatus) {
				case ClientStatus.COMPUTING_STATUS_NEVER:
					slideshowWrapper.setVisibility(View.GONE);
					statusHeader.setText(R.string.status_computing_disabled);
					statusImage.setImageResource(R.drawable.playw48);
					statusImage.setContentDescription(getString(R.string.status_computing_disabled));
					statusImage.setClickable(true);
					statusImage.setOnClickListener(this);
					statusDescriptor.setText(R.string.status_computing_disabled_long);
					break;
				case ClientStatus.COMPUTING_STATUS_SUSPENDED:
					slideshowWrapper.setVisibility(View.GONE);
					statusHeader.setText(R.string.status_paused);
					statusImage.setImageResource(R.drawable.pausew48);
					statusImage.setContentDescription(getString(R.string.status_paused));
					statusImage.setClickable(false);
					switch(status.computingSuspendReason) {
					case BOINCDefs.SUSPEND_REASON_BATTERIES:
						statusDescriptor.setText(R.string.suspend_batteries);
						statusImage.setImageResource(R.drawable.notconnectedw48);
						statusHeader.setVisibility(View.GONE);
						break;
					case BOINCDefs.SUSPEND_REASON_USER_ACTIVE:
						statusDescriptor.setText(R.string.suspend_useractive);
						break;
					case BOINCDefs.SUSPEND_REASON_USER_REQ:
						// state after user stops and restarts computation
						centerWrapper.setVisibility(View.GONE);
						LinearLayout restartingWrapper = (LinearLayout) findViewById(R.id.restarting_wrapper);
						restartingWrapper.setVisibility(View.VISIBLE);
						statusDescriptor.setText(R.string.suspend_user_req);
						break;
					case BOINCDefs.SUSPEND_REASON_TIME_OF_DAY:
						statusDescriptor.setText(R.string.suspend_tod);
						break;
					case BOINCDefs.SUSPEND_REASON_BENCHMARKS:
						statusDescriptor.setText(R.string.suspend_bm);
						statusImage.setImageResource(R.drawable.watchw48);
						statusHeader.setVisibility(View.GONE);
						break;
					case BOINCDefs.SUSPEND_REASON_DISK_SIZE:
						statusDescriptor.setText(R.string.suspend_disksize);
						break;
					case BOINCDefs.SUSPEND_REASON_CPU_THROTTLE:
						statusDescriptor.setText(R.string.suspend_cputhrottle);
						break;
					case BOINCDefs.SUSPEND_REASON_NO_RECENT_INPUT:
						statusDescriptor.setText(R.string.suspend_noinput);
						break;
					case BOINCDefs.SUSPEND_REASON_INITIAL_DELAY:
						statusDescriptor.setText(R.string.suspend_delay);
						break;
					case BOINCDefs.SUSPEND_REASON_EXCLUSIVE_APP_RUNNING:
						statusDescriptor.setText(R.string.suspend_exclusiveapp);
						break;
					case BOINCDefs.SUSPEND_REASON_CPU_USAGE:
						statusDescriptor.setText(R.string.suspend_cpu);
						break;
					case BOINCDefs.SUSPEND_REASON_NETWORK_QUOTA_EXCEEDED:
						statusDescriptor.setText(R.string.suspend_network_quota);
						break;
					case BOINCDefs.SUSPEND_REASON_OS:
						statusDescriptor.setText(R.string.suspend_os);
						break;
					case BOINCDefs.SUSPEND_REASON_WIFI_STATE:
						statusDescriptor.setText(R.string.suspend_wifi);
						break;
					case BOINCDefs.SUSPEND_REASON_BATTERY_CHARGING:
						statusDescriptor.setText(R.string.suspend_battery_charging);
						statusImage.setImageResource(R.drawable.batteryw48);
						statusHeader.setVisibility(View.GONE);
						break;
					case BOINCDefs.SUSPEND_REASON_BATTERY_OVERHEATED:
						statusDescriptor.setText(R.string.suspend_battery_overheating);
						statusImage.setImageResource(R.drawable.batteryw48);
						statusHeader.setVisibility(View.GONE);
						break;
					default:
						statusDescriptor.setText(R.string.suspend_unknown);
						break;
					}
					suspendReason = status.computingSuspendReason;
					break;
				case ClientStatus.COMPUTING_STATUS_IDLE: 
					slideshowWrapper.setVisibility(View.GONE);
					statusHeader.setText(R.string.status_idle);
					statusImage.setImageResource(R.drawable.pausew48);
					statusImage.setContentDescription(getString(R.string.status_idle));
					statusImage.setClickable(false);
					Integer networkState = 0;
					try{
						networkState = status.networkSuspendReason;
					} catch (Exception e) {}
					if(networkState == BOINCDefs.SUSPEND_REASON_WIFI_STATE){
						// Network suspended due to wifi state
						statusDescriptor.setText(R.string.suspend_wifi);
					}else {
						statusDescriptor.setText(R.string.status_idle_long);
					}
					break;
				case ClientStatus.COMPUTING_STATUS_COMPUTING:
					// load slideshow
					if(!loadSlideshow()) {
						Log.d(TAG, "slideshow not available, load plain old status instead...");
						statusHeader.setText(R.string.status_running);
						statusImage.setImageResource(R.drawable.cogsw48);
						statusImage.setContentDescription(getString(R.string.status_running));
						statusDescriptor.setText(R.string.status_running_long);
					}
					break;
				}
				computingStatus = status.computingStatus; //save new computing status
			} else { // BOINC client is not available
				//invalid computingStatus, forces layout on next event
				computingStatus = -1;
			}
		}
	}
	
	private Boolean loadSlideshow() {
		// get slideshow images
		final ArrayList<ImageWrapper> images = Monitor.getClientStatus().getSlideshowImages();
		if(images == null || images.size() == 0) return false;
		
		// images available, adapt layout
	    Gallery gallery = (Gallery) findViewById(R.id.gallery);
	    final ImageView imageView = (ImageView) findViewById(R.id.image_view);
	    final TextView imageDesc = (TextView)findViewById(R.id.image_description);
        imageView.setImageBitmap(images.get(0).image);
        imageDesc.setText(images.get(0).projectName);
		LinearLayout centerWrapper = (LinearLayout) findViewById(R.id.center_wrapper);
		centerWrapper.setVisibility(View.GONE);
        slideshowWrapper.setVisibility(View.VISIBLE);
        
        //gallery.setVisibility(View.GONE);
        
        //setup gallery
        gallery.setAdapter(new GalleryAdapter(this,images));

        gallery.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                imageView.setImageBitmap(images.get(position).image);
                imageDesc.setText(images.get(position).projectName);
            }
        });
        
        /*
        // create views for all available bitmaps
    	for (Bitmap image: images) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageBitmap(image);
            viewFlipper.addView(imageView);
    	}*/
        /*
        // capture click events and pass on to Gesture Detector
        slideshowWrapper.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                gdt.onTouchEvent(event);
                return true;
            }
         });*/
        
        return true;
	}

	@Override
	public void onClick(View v) {
		new WriteClientRunModeAsync().execute(BOINCDefs.RUN_MODE_AUTO);
	}
	
	private final class WriteClientRunModeAsync extends AsyncTask<Integer, Void, Boolean> {

		private final String TAG = "WriteClientRunModeAsync";
		
		@Override
		protected Boolean doInBackground(Integer... params) {
			return monitor.setRunMode(params[0]);
		}
		
		@Override
		protected void onPostExecute(Boolean success) {
			if(success) monitor.forceRefresh();
			else Log.w(TAG,"setting run mode failed");
		}
	}
}
