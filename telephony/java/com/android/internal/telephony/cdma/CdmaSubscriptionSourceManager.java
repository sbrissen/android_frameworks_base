/*
 * Copyright (c) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.cdma;

import java.util.concurrent.atomic.AtomicInteger;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.RILConstants;

import com.android.internal.telephony.SamsungChargeRIL;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings;
import android.util.Log;
import android.os.SystemProperties;

import android.os.SystemProperties;
import com.android.internal.telephony.SamsungChargeRIL;

/**
 * Class that handles the CDMA subscription source changed events from RIL
 */
public class CdmaSubscriptionSourceManager extends Handler {
    static final String LOG_TAG = "CDMA";
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 1;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_SOURCE     = 2;
    private static final int EVENT_RADIO_ON                         = 3;

    public static final int SUBSCRIPTION_SOURCE_UNKNOWN = -1;
    public static final int SUBSCRIPTION_FROM_RUIM      = 0; /* CDMA subscription from RUIM */
    public static final int SUBSCRIPTION_FROM_NV        = 1; /* CDMA subscription from NV */
    public static final int PREFERRED_CDMA_SUBSCRIPTION = SUBSCRIPTION_FROM_NV;

    private static CdmaSubscriptionSourceManager sInstance;
    private static final Object sReferenceCountMonitor = new Object();
    private static int sReferenceCount = 0;
	
	int mChargeSource = 0;

    // ***** Instance Variables
    private CommandsInterface mCM;
    private Context mContext;
    private RegistrantList mCdmaSubscriptionSourceChangedRegistrants = new RegistrantList();
	
	int mChargeSource = 0;

    // Type of CDMA subscription source
    private AtomicInteger mCdmaSubscriptionSource = new AtomicInteger(SUBSCRIPTION_FROM_NV);

    // Constructor
    private CdmaSubscriptionSourceManager(Context context, CommandsInterface ci) {
        mContext = context;
        mCM = ci;
        mCM.registerForCdmaSubscriptionChanged(this, EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED, null);
        mCM.registerForOn(this, EVENT_RADIO_ON, null);
        int subscriptionSource = getDefaultCdmaSubscriptionSource();
        mCdmaSubscriptionSource.set(subscriptionSource);
    }

    /**
     * This function creates a single instance of this class
     *
     * @return object of type CdmaSubscriptionSourceManager
     */
    public static CdmaSubscriptionSourceManager getInstance(Context context,
            CommandsInterface ci, Handler h, int what, Object obj) {
        synchronized (sReferenceCountMonitor) {
            if (null == sInstance) {
                sInstance = new CdmaSubscriptionSourceManager(context, ci);
            }
            sInstance.sReferenceCount++;
        }
        sInstance.registerForCdmaSubscriptionSourceChanged(h, what, obj);
        return sInstance;
    }

    /**
     * Unregisters for the registered event with RIL
     */
    public void dispose(Handler h) {
        mCdmaSubscriptionSourceChangedRegistrants.remove(h);
        synchronized (sReferenceCountMonitor) {
            sReferenceCount--;
            if (sReferenceCount <= 0) {
                mCM.unregisterForCdmaSubscriptionChanged(this);
                mCM.unregisterForOn(this);
                sInstance = null;
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see android.os.Handler#handleMessage(android.os.Message)
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        switch (msg.what) {
            case EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
            case EVENT_GET_CDMA_SUBSCRIPTION_SOURCE:
            {
				if(SystemProperties.getBoolean("ro.ril.droidCharge",true)){
					log("sbrissen - CDMA_SUBSCRIPTION_SOURCE event");
					mChargeSource = SamsungChargeRIL.getCDMASource();
					handleChargeSource(mChargeSource);					
				}else{
                log("CDMA_SUBSCRIPTION_SOURCE event = " + msg.what);
                ar = (AsyncResult) msg.obj;
                handleGetCdmaSubscriptionSource(ar);
				}
            }
            break;
            case EVENT_RADIO_ON:
				log("CDMA_SUBSCRIPTION_SOURCE event = " + msg.what);
				if(SystemProperties.getBoolean("ro.ril.droidCharge",true)){
					mChargeSource = SamsungChargeRIL.getCDMASource();
					handleChargeSource(mChargeSource);
					obtainMessage(EVENT_GET_CDMA_SUBSCRIPTION_SOURCE);
				}else{
                mCM.getCdmaSubscriptionSource(obtainMessage(EVENT_GET_CDMA_SUBSCRIPTION_SOURCE));
				}
            break;
            default:
                super.handleMessage(msg);
        }
    }

    /**
     * Returns the current CDMA subscription source value
     * @return CDMA subscription source value
     */
    public int getCdmaSubscriptionSource() {
/*	int mChargeSource;
	String mCharge = System.getProperty("ro.ril.radiostate");
	if(mCharge == null){
		mChargeSource = 4;
	}else{
		mChargeSource = Integer.parseInt(mCharge);
	}
	int source = 0;
	log("sbrissen - CDMA Subscription: " + mChargeSource);

	
	   switch (mChargeSource) {
        case 2: //RADIO_STATE_SIM_NOT_READY:
        case 3: //RADIO_STATE_SIM_LOCKED_OR_ABSENT:
        case 4: //RADIO_STATE_SIM_READY:
        case 5: //RADIO_STATE_RUIM_NOT_READY:
        case 6: //RADIO_STATE_RUIM_READY:
        case 7: //RADIO_STATE_RUIM_LOCKED_OR_ABSENT:
            source = SUBSCRIPTION_FROM_RUIM;

        case 8: //RADIO_STATE_NV_NOT_READY:
        case 9: //RADIO_STATE_NV_READY
            source = SUBSCRIPTION_FROM_NV;
		}
		
		return source;*/
	
       return mCdmaSubscriptionSource.get();
    }

    /**
     * Gets the default CDMA subscription source
     *
     * @return Default CDMA subscription source from Settings DB if present.
     */
    private int getDefaultCdmaSubscriptionSource() {
        // Get the default value from the Settings
        int subscriptionSource = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.CDMA_SUBSCRIPTION_MODE, PREFERRED_CDMA_SUBSCRIPTION);
        return subscriptionSource;
    }

    /**
     * Clients automatically register for CDMA subscription source changed event
     * when they get an instance of this object.
     */
    private void registerForCdmaSubscriptionSourceChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCdmaSubscriptionSourceChangedRegistrants.add(r);
    }

    /**
     * Handles the call to get the subscription source
     *
     * @param ar AsyncResult object that contains the result of get CDMA
     *            subscription source call
     */
    private void handleGetCdmaSubscriptionSource(AsyncResult ar) {
		mCdmaSubscriptionSource.set(0);
        if ((ar.exception == null) && (ar.result != null)) {
            int newSubscriptionSource = ((int[]) ar.result)[0];
			log("Sbrissen - Subscription Source: " + newSubscriptionSource);
            if (newSubscriptionSource != mCdmaSubscriptionSource.get()) {
                log("Subscription Source Changed : " + mCdmaSubscriptionSource + " >> "
                        + newSubscriptionSource);
                mCdmaSubscriptionSource.set(newSubscriptionSource);

                // Notify registrants of the new CDMA subscription source
                mCdmaSubscriptionSourceChangedRegistrants.notifyRegistrants(new AsyncResult(null,
                        null, null));
            }
        } else {
            // GET_CDMA_SUBSCRIPTION is returning Failure. Probably
            // because modem created GSM Phone. If modem created
            // GSMPhone, then PhoneProxy will trigger a change in
            // Phone objects and this object will be destroyed.
            logw("Unable to get CDMA Subscription Source, Exception: " + ar.exception
                    + ", result: " + ar.result);
        }
    }
	
	public void handleChargeSource(int newSubscriptionSource){		    
			log("Sbrissen - Subscription Source: " + newSubscriptionSource);

            if (newSubscriptionSource != mCdmaSubscriptionSource.get()) {
                log("Subscription Source Changed : " + mCdmaSubscriptionSource + " >> "
                        + newSubscriptionSource);
                mCdmaSubscriptionSource.set(newSubscriptionSource);

                // Notify registrants of the new CDMA subscription source
                mCdmaSubscriptionSourceChangedRegistrants.notifyRegistrants(new AsyncResult(null,
                        null, null));
            }
	}

    private void log(String s) {
        Log.d(LOG_TAG, "[CdmaSSM] " + s);
    }

    private void loge(String s) {
        Log.e(LOG_TAG, "[CdmaSSM] " + s);
    }

    private void logw(String s) {
        Log.w(LOG_TAG, "[CdmaSSM] " + s);
    }

}
