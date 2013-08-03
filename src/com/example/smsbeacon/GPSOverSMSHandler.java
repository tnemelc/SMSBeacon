package com.example.smsbeacon;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;

public class GPSOverSMSHandler implements LocationListener {
	
	private final String TAG = getClass().getName();

	// Internal class to make sure all data is in sync with the nested class
	protected class GPSOverSMSInformations {
		public Location mBestLocation;
		public boolean mIsLocationSMSSent;
		private int mNumRemainingProviders;
		private Timer mTimer;
	}
	
	protected GPSOverSMSInformations mGPSInfo = new GPSOverSMSInformations();
	protected Context mContext;
	
	LocationManager mLocationManager;
	private String mCallerNumber;

	public GPSOverSMSHandler(Context c) {
		mContext = c;
		mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);

	}
	
	public void stop() {
		if(!mGPSInfo.mIsLocationSMSSent) {
			sendLocationSMS(mGPSInfo.mBestLocation,  mCallerNumber, false);
			mGPSInfo.mIsLocationSMSSent = true;
			mGPSInfo.mTimer.cancel();
		}
	}
	
	public void startASyncLocService(String callerNumber) {
		mCallerNumber = callerNumber;

		// Make sure everything is in order BEFORE starting
		if(mGPSInfo.mTimer != null) {
			mGPSInfo.mTimer.cancel();
			if(!mGPSInfo.mIsLocationSMSSent) mLocationManager.removeUpdates(this);
		}
		
		mGPSInfo.mIsLocationSMSSent = false;
		
		List<String> lProviders = mLocationManager.getProviders(true);

		mGPSInfo.mNumRemainingProviders = lProviders.size();
		
		for(String provider:lProviders) {
			Log.i(TAG, "Available Provider : " + provider);
			mLocationManager.requestSingleUpdate(provider, this, Looper.getMainLooper());
		}
		
		mGPSInfo.mTimer = new Timer();
		Calendar c = Calendar.getInstance();
		c.add(Calendar.MINUTE, 3);
		
		mGPSInfo.mTimer.schedule( new TimerTask() {

			@Override
			public void run() {
				Log.i(TAG, "Timeout occured when waiting for the provider location data");
				mLocationManager.removeUpdates(GPSOverSMSHandler.this);
				if(!mGPSInfo.mIsLocationSMSSent)
					stop();
			}
		}, c.getTime());
		
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.i(TAG, provider + " status changed : " + status);
	}
	
	@Override
	public void onProviderEnabled(String provider) {
		Log.i(TAG, provider  + " is enabled");
	}
	
	@Override
	public void onProviderDisabled(String provider) {
		Log.i(TAG, provider  + " is disabled");
	}
	
	@Override
	public void onLocationChanged(Location location) {
		mGPSInfo.mBestLocation = bestBetween(mGPSInfo.mBestLocation,  location);
		mGPSInfo.mNumRemainingProviders -= 1;
		
		Log.i(TAG, "New location received, remains " + mGPSInfo.mNumRemainingProviders);
		
		if(mGPSInfo.mNumRemainingProviders <= 0) {
			Log.i(TAG, "Last location received");
			stop();
		}
	}

	/**
	 * Get Last known location from the GPS or using the cell tower/wifi. 
	 * @return A Location object representing the last known location of the device
	 */
	public Location getLastLocation() {
		Location loc = null, newLoc = null;
		List<String> lProviders;
		
		lProviders = mLocationManager.getProviders(true);
		
		for(String provider:lProviders) {
			Log.i(TAG, "Available Provider : " + provider);
			newLoc = mLocationManager.getLastKnownLocation(provider);
			loc = bestBetween(loc, newLoc);
		}
		
		return loc;
	}
	
	public void sendLocationSMS(Location loc, String receiver, Boolean isLastLocation) {
		double lon, lat, alt, prec;
		long temp;
		String lastFix;
		StringBuilder positionStr = new StringBuilder();
		
		if(isLastLocation)
			positionStr.append(mContext.getString(R.string.sms_lastloc_header));
		else 
			positionStr.append(mContext.getString(R.string.sms_newloc_header));
		
		if(loc == null) {
			positionStr.append(mContext.getString(R.string.sms_noloc));
		} else {
			lat = loc.getLatitude();
			lon = loc.getLongitude();
			alt = loc.getAltitude();
			prec = loc.getAccuracy();
			temp = loc.getTime();

			lastFix = DateFormat.getDateTimeInstance().format(new Date(temp));
			
			positionStr.append(String.format(mContext.getString(R.string.sms_loc_fmt), lon, lat, alt, prec, lastFix));
		}
		Log.i(TAG, "send sms to " + receiver + " with content '" + positionStr + "'");
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(receiver, null, positionStr.toString(), null, null);		
	}
	
	public Location bestBetween(Location a, Location b) {
		// If one location is null return the other
		if(a == null) 
			return b;
		else if(b == null)
			return a;
		// If one of the location is at least 60 minute older than the other
		else if(Math.abs(a.getTime() - b.getTime()) > 60 * 1000 * 60) {
			if(a.getTime() > b.getTime())
				return a;
			else
				return b;
		// Else return the one with the better accuracy
		} else if(a.getAccuracy() < b.getAccuracy()) 
			return a;
		else 
			return b;
	}	
}
