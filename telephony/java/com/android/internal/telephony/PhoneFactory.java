/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.net.LocalServerSocket;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;

import java.lang.reflect.Constructor;

import com.android.internal.telephony.SamsungChargeRIL;
import com.android.internal.telephony.MultiModePhoneProxy;

/**
 * {@hide}
 */
public class PhoneFactory {
    static final String LOG_TAG = "PHONE";
    static final int SOCKET_OPEN_RETRY_MILLIS = 2 * 1000;
    static final int SOCKET_OPEN_MAX_RETRY = 3;

    //***** Class Variables

    static private Phone sProxyPhone = null;
    static private Phone sProxyPhoneGSM = null;
    static private Phone mMultiProxyPhone = null;
    static private CommandsInterface[] sCommandsInterface = new CommandsInterface[2];

    static private boolean sMadeDefaults = false;
    static private PhoneNotifier sPhoneNotifier;
    static private Looper sLooper;
    static private Context sContext;
    static public int mChargePhoneType = 0;

    static final int preferredCdmaSubscription =
                         CdmaSubscriptionSourceManager.PREFERRED_CDMA_SUBSCRIPTION;

    //***** Class Methods

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    /**
     * FIXME replace this with some other way of making these
     * instances
     */
    public static void makeDefaultPhone(Context context) {
        synchronized(Phone.class) {
            if (!sMadeDefaults) {
                sLooper = Looper.myLooper();
                sContext = context;

                if (sLooper == null) {
                    throw new RuntimeException(
                        "PhoneFactory.makeDefaultPhone must be called from Looper thread");
                }

                int retryCount = 0;
                for(;;) {
                    boolean hasException = false;
                    retryCount ++;

                    try {
                        // use UNIX domain socket to
                        // prevent subsequent initialization
                        new LocalServerSocket("com.android.internal.telephony");
                    } catch (java.io.IOException ex) {
                        hasException = true;
                    }

                    if ( !hasException ) {
                        break;
                    } else if (retryCount > SOCKET_OPEN_MAX_RETRY) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    } else {
                        try {
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException er) {
                        }
                    }
                }

                sPhoneNotifier = new DefaultPhoneNotifier();

                // Get preferred network mode
                int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;
                if (BaseCommands.getLteOnCdmaModeStatic() == Phone.LTE_ON_CDMA_TRUE) {
                    preferredNetworkMode = Phone.NT_MODE_GLOBAL;
                }
                if (BaseCommands.getLteOnGsmModeStatic() != 0) {
                    preferredNetworkMode = Phone.NT_MODE_LTE_GSM_WCDMA;
                }
                int networkMode = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                Log.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));

                // Get cdmaSubscription
                // TODO: Change when the ril will provides a way to know at runtime
                //       the configuration, bug 4202572. And the ril issues the
                //       RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED, bug 4295439.
                int cdmaSubscription;
                int lteOnCdma = BaseCommands.getLteOnCdmaModeStatic();
                switch (lteOnCdma) {
                    case Phone.LTE_ON_CDMA_FALSE:
                        cdmaSubscription = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_NV;
                        Log.i(LOG_TAG, "lteOnCdma is 0 use SUBSCRIPTION_FROM_NV");
                        break;
                    case Phone.LTE_ON_CDMA_TRUE:
                        cdmaSubscription = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM;
                        Log.i(LOG_TAG, "lteOnCdma is 1 use SUBSCRIPTION_FROM_RUIM");
                        break;
                    case Phone.LTE_ON_CDMA_UNKNOWN:
                    default:
                        //Get cdmaSubscription mode from Settings.System
                        cdmaSubscription = Settings.Secure.getInt(context.getContentResolver(),
                                Settings.Secure.PREFERRED_CDMA_SUBSCRIPTION,
                                preferredCdmaSubscription);
                        Log.i(LOG_TAG, "lteOnCdma not set, using PREFERRED_CDMA_SUBSCRIPTION");
                        break;
                }
                Log.i(LOG_TAG, "Cdma Subscription set to " + cdmaSubscription);

                //reads the system properties and makes commandsinterface
                String sRILClassname = SystemProperties.get("ro.telephony.ril_class", "RIL");
                Log.i(LOG_TAG, "RILClassname is " + sRILClassname);

                // Use reflection to construct the RIL class (defaults to RIL)
                try {
                    Class<?> classDefinition = Class.forName("com.android.internal.telephony." + sRILClassname);
                    Constructor<?> constructor = classDefinition.getConstructor(new Class[] {Context.class, int.class, int.class});
		    RIL.setChargePhone(2);
                    sCommandsInterface[0] = (RIL) constructor.newInstance(new Object[] {sContext, networkMode, cdmaSubscription});	
		    try {Thread.sleep(50);} catch (InterruptedException e) {}
		    RIL.setChargePhone(1);
		    sCommandsInterface[1] = (RIL) constructor.newInstance(new Object[] {sContext, networkMode, cdmaSubscription});
		} catch (Exception e) {
                    // 6 different types of exceptions are thrown here that it's
                    // easier to just catch Exception as our "error handling" is the same.
                    Log.wtf(LOG_TAG, "Unable to construct command interface", e);
                    throw new RuntimeException(e);
                }

                int phoneType = getPhoneType(networkMode);
		Log.i(LOG_TAG, "sbrissen - phoneType: " + phoneType);

		Log.i(LOG_TAG, "3 Creating CDMAPhone");
		sProxyPhone = new CDMAPhone(context, sCommandsInterface[1], sPhoneNotifier);
		Log.i(LOG_TAG, "3 Creating GSMPhone");
		sProxyPhoneGSM = new GSMPhone(context, sCommandsInterface[0], sPhoneNotifier);	
			
		mMultiProxyPhone = new MultiModePhoneProxy(sProxyPhone, sProxyPhoneGSM, sContext);
                /*if (phoneType == Phone.PHONE_TYPE_GSM) {
                    Log.i(LOG_TAG, "1 Creating GSMPhone");
                    sProxyPhone = new PhoneProxy(new GSMPhone(context,
                            sCommandsInterface[0], sPhoneNotifier));
			   // Log.i(LOG_TAG, "1 Creating CDMAPhone");
			   // sProxyPhoneGSM = new PhoneProxy(new CDMAPhone(context, sCommandsInterface, sPhoneNotifier),0);
                } else if (phoneType == Phone.PHONE_TYPE_CDMA) {*/
                 /*   switch (BaseCommands.getLteOnCdmaModeStatic()) {
                        case Phone.LTE_ON_CDMA_TRUE:
                            Log.i(LOG_TAG, "2 Creating CDMALTEPhone");
                            sProxyPhone = new PhoneProxy(new CDMALTEPhone(sContext, sCommandsInterface[1], sPhoneNotifier));
			       break;
                        case Phone.LTE_ON_CDMA_FALSE:
                        default:
                            Log.i(LOG_TAG, "3 Creating CDMAPhone");
                            sProxyPhone = new PhoneProxy(new CDMAPhone(context, sCommandsInterface[1], sPhoneNotifier));
                            break;
                    }*/
                //}

                sMadeDefaults = true;
            }
        }
    }

    /*
     * This function returns the type of the phone, depending
     * on the network mode.
     *
     * @param network mode
     * @return Phone Type
     */
    public static int getPhoneType(int networkMode) {
		Log.i(LOG_TAG, "getPhoneType");
        switch(networkMode) {
        case RILConstants.NETWORK_MODE_CDMA:
        case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
        case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
            return Phone.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_WCDMA_PREF:
        case RILConstants.NETWORK_MODE_GSM_ONLY:
        case RILConstants.NETWORK_MODE_WCDMA_ONLY:
        case RILConstants.NETWORK_MODE_GSM_UMTS:
        case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
            return Phone.PHONE_TYPE_GSM;

        // Use CDMA Phone for the global mode including CDMA
        case RILConstants.NETWORK_MODE_GLOBAL:
        case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
        case RILConstants.NETWORK_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            return Phone.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_LTE_ONLY:
            if (BaseCommands.getLteOnCdmaModeStatic() == Phone.LTE_ON_CDMA_TRUE) {
                return Phone.PHONE_TYPE_CDMA;
            } else {
                return Phone.PHONE_TYPE_GSM;
            }
        default:
            return Phone.PHONE_TYPE_GSM;
        }
    }

    public static Phone getDefaultPhone() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "PhoneFactory.getDefaultPhone must be called from Looper thread");
        }

        if (!sMadeDefaults) {
            throw new IllegalStateException("Default phones haven't been made yet!");
        }
       return mMultiProxyPhone;
      //return sProxyPhone;
    }

    public static Phone getCdmaPhone() {
	Log.i(LOG_TAG, "getCdmaPhone");
	
       // Phone phone;
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
	  if(sProxyPhone == null){
            switch (BaseCommands.getLteOnCdmaModeStatic()) {
                case Phone.LTE_ON_CDMA_TRUE: {
                    sProxyPhone = new CDMALTEPhone(sContext, sCommandsInterface[1], sPhoneNotifier);
                    break;
                }
                case Phone.LTE_ON_CDMA_FALSE:
                case Phone.LTE_ON_CDMA_UNKNOWN:
                default: {
                    sProxyPhone = new CDMAPhone(sContext, sCommandsInterface[1], sPhoneNotifier);
                    break;
                }
            }
	  }
	}

	return sProxyPhone;
	
    }

    public static Phone getGsmPhone() {
	Log.i(LOG_TAG, "getGsmPhone");
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
	  if(sProxyPhoneGSM == null){
            sProxyPhoneGSM = new GSMPhone(sContext, sCommandsInterface[0], sPhoneNotifier);
	  }

            return sProxyPhone;
        }
    }

    /**
     * Makes a {@link SipPhone} object.
     * @param sipUri the local SIP URI the phone runs on
     * @return the {@code SipPhone} object or null if the SIP URI is not valid
     */
    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier);
    }
}
