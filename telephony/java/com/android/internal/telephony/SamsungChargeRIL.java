
/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2011, 2012 The CyanogenMod Project
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

import java.util.ArrayList;
import java.util.Collections;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.os.AsyncResult;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import static com.android.internal.telephony.RILConstants.*;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.app.ActivityManagerNative;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.DataCallState;
import com.android.internal.telephony.DataConnection.FailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.IccCardApplication;
import com.android.internal.telephony.IccCardStatus;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.SignalToneUtil;

import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;

import android.telephony.ServiceState;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import java.io.InputStream;
import android.util.Log;
import java.lang.System;
import java.lang.StringBuilder;

import android.util.Log;
import java.io.IOException;

public class SamsungChargeRIL extends RIL implements CommandsInterface {
    protected HandlerThread mIccThread;
    protected IccHandler mIccHandler;
    protected String mAid;
    protected boolean mUSIM = false;
    protected String[] mLastDataIface = new String[20];
    boolean RILJ_LOGV = true;
    boolean RILJ_LOGD = true;
	boolean mInitialRadioStateChange;
	LocalSocket mSocketext;
	static int legacyState;
	Thread mChargeReceiverThread;
    ChargeRILReceiver mChargeRILReceiver;
	static final String SOCKET_NAME_RIL_EXT = "rildext";
	
 /*   public SamsungChargeRIL(Context context) {
        this(context, RILConstants.PREFERRED_NETWORK_MODE,
                Phone.PREFERRED_CDMA_SUBSCRIPTION);
    }*/
	

    public SamsungChargeRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
		mQANElements = 4;
		mInitialRadioStateChange = true;
		
		//mPhoneType = 1;
		mQANElements = 4;
		mInitialRadioStateChange = true;
		//mChargeRILReceiver = new ChargeRILReceiver();
		//mChargeReceiverThread = new Thread(mChargeRILReceiver, "ChargeRILReceiver");
		//mChargeReceiverThread.start();
		
		BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getAction().equals("android.intent.action.RILD_CRASH")) {
					Log.i(LOG_TAG, "RILD msg - resetting");
					refreshRild(intent);				
				}
			}
		};
		
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.intent.action.RILD_CRASH");
		context.registerReceiver(mIntentReceiver,filter);
	} 
	
    @Override
    public void setPhoneType(int phoneType) { // Called by CDMAPhone and GSMPhone constructor
        if (RILJ_LOGD) riljLog("sbrissen - setPhoneType=" + phoneType + " old value=" + mPhoneType);
		if(mPhoneType != phoneType){
			mPhoneType = phoneType;
			setRadioPower(false, null);
		}
    }


    //SAMSUNG SGS STATES
    static final int RIL_UNSOL_STK_SEND_SMS_RESULT = 11002;
    static final int RIL_UNSOL_O2_HOME_ZONE_INFO = 11007;
    static final int RIL_UNSOL_DEVICE_READY_NOTI = 11008;
    static final int RIL_UNSOL_GPS_NOTI = 11009;
    static final int RIL_UNSOL_AM = 11010;
    static final int RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL = 11011;
    static final int RIL_UNSOL_HSDPA_STATE_CHANGED = 11016;
    static final int RIL_UNSOL_DATA_SUSPEND_RESUME = 11012;
    static final int RIL_REQUEST_DIAL_EMERGENCY = 10016;
    static final int RIL_UNSOL_RADIO_REFRESH = 11025;
	static final int RIL_REQUEST_SIM_AUTH = 10035;
	
	@Override
    public void reportStkServiceIsRunning(Message result) {	
		Log.i(LOG_TAG, "sbrissen - RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING");
     RILRequest rr = RILRequest.obtain(RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING, result);
		if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
		send(rr);
		return;
    }
	
	@Override
    public void getVoiceRadioTechnology(Message result) {
	//Charge RIL doesn't like this 
		Log.i(LOG_TAG, "Ignoring GET_VOICE_RADIO_TECH request");
     /*   RILRequest rr = RILRequest.obtain(RIL_REQUEST_VOICE_RADIO_TECH, result);
		Log.i(LOG_TAG, "sbrissen - getVoiceRadioTechnology: " + result.toString() + "  " + requestToString(rr.mRequest));
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);*/
    }

		
		//Charge RIL doesn't like this 
		Log.i(LOG_TAG, "Ignoring GET_VOICE_RADIO_TECH request: " + legacyState);
		
	/*	switch (Integer.parseInt(legacyState)) {
			case 2: //RADIO_STATE_SIM_NOT_READY:
			case 3: //RADIO_STATE_SIM_LOCKED_OR_ABSENT:
			case 4: //RADIO_STATE_SIM_READY:
				SystemProperties.set("ro.ril.voicetech","3");
				

			case 5: //RADIO_STATE_RUIM_NOT_READY:
			case 6: //RADIO_STATE_RUIM_READY:
			case 7: //RADIO_STATE_RUIM_LOCKED_OR_ABSENT:
			case 8: //RADIO_STATE_NV_NOT_READY:
			case 9: //RADIO_STATE_NV_READY
				SystemProperties.set("ro.ril.voicetech","6");
		}*/
		
   /*   RILRequest rr = RILRequest.obtain(RIL_REQUEST_VOICE_RADIO_TECH, result);
		Log.i(LOG_TAG, "sbrissen - getVoiceRadioTechnology: " + result.toString() + "  " + requestToString(rr.mRequest));
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr); */
		return;
    }
	
	public static int getCDMAVoiceRadioTechnology(){
		int mVoiceTech = 0;
		if(legacyState == 2 || legacyState == 3 || legacyState == 4){
			//2: RADIO_STATE_SIM_NOT_READY:
			//3: RADIO_STATE_SIM_LOCKED_OR_ABSENT:
			//4: RADIO_STATE_SIM_READY:
				mVoiceTech = 3;
		}else if(legacyState == 5 || legacyState == 6 || legacyState == 7 || legacyState == 8 || legacyState == 9){
			//5: RADIO_STATE_RUIM_NOT_READY:
			//6: RADIO_STATE_RUIM_READY:
			//7: RADIO_STATE_RUIM_LOCKED_OR_ABSENT:
			//8: RADIO_STATE_NV_NOT_READY:
			//9: RADIO_STATE_NV_READY
				mVoiceTech = 6;
		}
		Log.i(LOG_TAG, "sbrissen - voiceradiotech: " + mVoiceTech);
		return mVoiceTech;
	}
			
    @Override
    public void getCdmaSubscriptionSource(Message response) {
	//Charge RIL doesn't like this 
		Log.i(LOG_TAG, "Ignoring GET_SUBSCRIPTION_SOURCE request");
    /*    RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);*/
		return;
    }
	
	public static int getCDMASource(){
		Log.i(LOG_TAG, "sbrissen - getCDMASource()");
		int mCdmaSource = 0;
		if(legacyState == 2 || legacyState == 3 || legacyState == 4 || legacyState == 5 || legacyState == 6 || legacyState == 7){
			//2: RADIO_STATE_SIM_NOT_READY:
			//3: RADIO_STATE_SIM_LOCKED_OR_ABSENT:
			//4: RADIO_STATE_SIM_READY:
			//5: RADIO_STATE_RUIM_NOT_READY:
			//6: RADIO_STATE_RUIM_READY:
			//7: RADIO_STATE_RUIM_LOCKED_OR_ABSENT:
				mCdmaSource = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_RUIM;
		}else if(legacyState == 8 || legacyState == 9){

			//8: RADIO_STATE_NV_NOT_READY:
			//9: RADIO_STATE_NV_READY
				mCdmaSource = CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_NV;
		}
		
		Log.i(LOG_TAG, "sbrissen - cdmasource: " + mCdmaSource);
		return mCdmaSource;
	}

	
	//@Override
    public void
    setupDataCall(String radioTechnology, String profile, String apn,
            String user, String password, String authType, String ipType,
			String pcscf, String dataConnType, String ipv4, String ipv6,
            String apn_type, Message result) {
			Log.i(LOG_TAG, "sbrissen - my setupDataCall");
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SETUP_DATA_CALL, result);

        rr.mp.writeInt(12);

        rr.mp.writeString(radioTechnology);
        rr.mp.writeString(profile);
        rr.mp.writeString(apn);
        rr.mp.writeString(user);
        rr.mp.writeString(password);
        rr.mp.writeString(authType);
        rr.mp.writeString(ipType);
		rr.mp.writeString(pcscf);
		rr.mp.writeString(dataConnType);
		rr.mp.writeString(ipv4);
		rr.mp.writeString(ipv6);
		rr.mp.writeString(apn_type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                + requestToString(rr.mRequest) + " " + radioTechnology + " "
                + profile + " " + apn + " " + user + " "
                + password + " " + authType + " " + ipType + " "
				+ pcscf + " " + dataConnType + " " + ipv4 + " "
				+ ipv6 + " " + apn_type);

        send(rr);
    }

    @Override
    protected void
    processSolicited (Parcel p) {
        int serial, error;
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

        Log.d(LOG_TAG,"Serial: "+ serial);
        Log.d(LOG_TAG,"Error: "+ error);

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Log.w(LOG_TAG, "Unexpected solicited response! sn: "
                    + serial + " error: " + error);
            return;
        }
		

        Object ret = null;

        if (error == 0 || p.dataAvail() > 0) {
		
            // either command succeeds or command fails but with data payload
            try {switch (rr.mRequest) {
            /*
            cat libs/telephony/ril_commands.h \
            | egrep "^ *{RIL_" \
            | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
             */
            case RIL_REQUEST_GET_SIM_STATUS: Log.i(LOG_TAG, "sbrissen - processsolicited responseIccCardStatus!"); ret =  responseIccCardStatus(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN2: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK2: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN2: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: ret =  responseInts(p); break;
            case RIL_REQUEST_GET_CURRENT_CALLS: ret =  responseCallList(p); break;
            case RIL_REQUEST_DIAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMSI: ret =  responseIMSI(p); break; //responseString(p); break;
            case RIL_REQUEST_HANGUP: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseLastCallFailCause(p); break;
            case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  /*responseVoiceRegistrationState(p); break;*/ responseStrings(p); break;
            case RIL_REQUEST_DATA_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
            case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
            case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseSetupDataCall(p); break;
            case RIL_REQUEST_SIM_IO: ret =  responseICC_IO(p); break;
			case RIL_REQUEST_SIM_AUTH: ret = responseICC_IO(p); break;
            case RIL_REQUEST_SEND_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_CANCEL_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_CLIR: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CLIR: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: ret =  responseCallForward(p); break;
            case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_WAITING: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CALL_WAITING: ret =  responseVoid(p); break;
            case RIL_REQUEST_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMEI: Log.i(LOG_TAG, "sbrissen - GET_IMEI"); ret =  responseImei(p); break;
            case RIL_REQUEST_GET_IMEISV: Log.i(LOG_TAG, "sbrissen - GET_IMEIV"); ret =  responseString(p); break;
            case RIL_REQUEST_ANSWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret =  responseDeactivateDataCall(p); break;
            case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseOperatorInfos(p); break;
            case RIL_REQUEST_DTMF_START: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF_STOP: ret =  responseVoid(p); break;
            case RIL_REQUEST_BASEBAND_VERSION: ret =  responseString(p); break;
            case RIL_REQUEST_SEPARATE_CONNECTION: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_MUTE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_MUTE: ret =  responseInts(p); break;
            case RIL_REQUEST_QUERY_CLIP: ret =  responseInts(p); break;
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_DATA_CALL_LIST: ret =  responseDataCallList(p); break;
            case RIL_REQUEST_RESET_RADIO: ret =  responseVoid(p); break;
            case RIL_REQUEST_OEM_HOOK_RAW: ret =  responseRaw(p); break;
            case RIL_REQUEST_OEM_HOOK_STRINGS: ret =  responseStrings(p); break;
            case RIL_REQUEST_SCREEN_STATE: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_WRITE_SMS_TO_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_DELETE_SMS_ON_SIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_BAND_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_STK_GET_PROFILE: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SET_PROFILE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseGetPreferredNetworkType(p); break;
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
            case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: Log.i(LOG_TAG, "sbrissen - RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE"); ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_TTY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_TTY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_FLASH: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BURST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: ret =  responseGmsBroadcastConfig(p); break;
            case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: ret =  responseCdmaBroadcastConfig(p); break;
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SUBSCRIPTION: ret =  /*responseStrings(p); break;*/ responseCdmaSubscription(p); break;
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
            case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseString(p); break;
            case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
            //case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: Log.i(LOG_TAG, "sbrissen - RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING"); ret =  responseVoid(p); break;
            //case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE: Log.i(LOG_TAG, "sbrissen - processsolicited RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE"); ret =  responseInts(p); break;
			//case RIL_REQUEST_VOICE_RADIO_TECH: Log.i(LOG_TAG, "sbrissen - processsolicited RIL_REQUEST_VOICE_RADIO_TECH"); ret =  responseInts(p); break;
            default:
                throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
                //break;
            }} catch (Throwable tr) {
                // Exceptions here usually mean invalid RIL responses

                Log.w(LOG_TAG, rr.serialString() + "< "
                        + requestToString(rr.mRequest)
                        + " exception, possible invalid RIL response", tr);

                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                rr.release();
                return;
            }
        }

        if (error != 0) {
			Log.i(LOG_TAG, "sbrissen - ERROR - " + requestToString(rr.mRequest));
            //ugly fix for Samsung messing up SMS_SEND request fail in binary RIL
            if(!(error == -1 && rr.mRequest == RIL_REQUEST_SEND_SMS))
            {
				rr.onError(error, ret);
                rr.release();
                return;
            }else{
                try
                {
					Log.i(LOG_TAG, "sbrissen - responseSMS fix");
                    ret =  responseSMS(p);
                } catch (Throwable tr) {
                    Log.w(LOG_TAG, rr.serialString() + "< "
                            + requestToString(rr.mRequest)
                            + " exception, Processing Samsung SMS fix ", tr);
                    rr.onError(error, ret);
                    rr.release();
                    return;
                }
            }
		}
		
        if (RILJ_LOGD) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
                + " " + retToString(rr.mRequest, ret));

        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        rr.release();
    }

    @Override
    public void
    dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr;
		Log.i(LOG_TAG, "sbrissen - dial()");

        rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);
        rr.mp.writeString(address);

        rr.mp.writeInt(clirMode);
        rr.mp.writeInt(0); // UUS information is absent

        if (uusInfo == null) {
            rr.mp.writeInt(0); // UUS information is absent
        } else {
            rr.mp.writeInt(1); // UUS information is present
            rr.mp.writeInt(uusInfo.getType());
            rr.mp.writeInt(uusInfo.getDcs());
            rr.mp.writeByteArray(uusInfo.getUserData());
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }
	
	@Override
    public void
    setRadioPower(boolean on, Message result) {
		Log.i(LOG_TAG, "sbrissen - setRadioPower()");

        //if (!on) {
            RILRequest rrCs = RILRequest.obtain(
                            RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE, null);
            rrCs.mp.writeInt(1);
            rrCs.mp.writeInt(mCdmaSubscription);
            if (RILJ_LOGD) riljLog(rrCs.serialString() + "> "
            + requestToString(rrCs.mRequest) + " : " + mCdmaSubscription);
            send(rrCs);
        //}

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);

        rr.mp.writeInt(1);
        rr.mp.writeInt(on ? 1 : 0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
	}
	
    public void
    setModemPower(boolean on, Message result) {
		Log.i(LOG_TAG, "sbrissen - setModemPower()");
		if(mInitialRadioStateChange){
			try{
				if(!mState.isOn()){
					RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE, null);
					rr.mp.writeInt(1);
					rr.mp.writeInt(mCdmaSubscription);
					if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + mCdmaSubscription);
					send(rr);
				}
			}catch  (Throwable t) {
                // Log the exception and continue
                Log.e(LOG_TAG, "Unexception exception", t);
			}
		}else{
			RILRequest rr = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);
            rr.mp.writeInt(2);
			if(result != null){
				rr.mp.writeInt(0);
			}else{
				rr.mp.writeInt(1);
			}
			if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
			send(rr);
        }       
    }


    @Override
    protected void
    processUnsolicited (Parcel p) {
        int response;
        Object ret;

        response = p.readInt();

        try {switch(response) {
        /*
				cat libs/telephony/ril_unsol_commands.h \
				| egrep "^ *{RIL_" \
				| sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: \2(rr, p); break;/'
         */

        case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
        case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: ret =  responseVoid(p); break;
        case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
        case RIL_UNSOL_RESPONSE_NEW_SMS: ret =  responseString(p); break;
        case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: ret =  responseString(p); break;
        case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: ret =  responseInts(p); break;
        case RIL_UNSOL_ON_USSD: ret =  responseStrings(p); break;
        case RIL_UNSOL_NITZ_TIME_RECEIVED: ret =  responseString(p); break;
        case RIL_UNSOL_SIGNAL_STRENGTH: ret = responseSignalStrength(p); break;
        case RIL_UNSOL_DATA_CALL_LIST_CHANGED: ret = responseDataCallList(p);break;
        case RIL_UNSOL_SUPP_SVC_NOTIFICATION: ret = responseSuppServiceNotification(p); break;
        case RIL_UNSOL_STK_SESSION_END: ret = responseVoid(p); break;
        case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;
        case RIL_UNSOL_STK_EVENT_NOTIFY: ret = responseString(p); break;
        case RIL_UNSOL_STK_CALL_SETUP: ret = responseInts(p); break;
        case RIL_UNSOL_SIM_SMS_STORAGE_FULL: ret =  responseVoid(p); break;
        case RIL_UNSOL_SIM_REFRESH: ret =  responseInts(p); break;
        case RIL_UNSOL_CALL_RING: ret =  responseCallRing(p); break;
        case RIL_UNSOL_RESTRICTED_STATE_CHANGED: ret = responseInts(p); break;
        case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:  ret =  responseVoid(p); break;
        case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:  ret =  responseCdmaSms(p); break;
        case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =  responseString(p); break;
        case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:  ret =  responseVoid(p); break;
        case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
        case RIL_UNSOL_CDMA_CALL_WAITING: ret = responseCdmaCallWaiting(p); break;
        case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: ret = responseInts(p); break;
        case RIL_UNSOL_CDMA_INFO_REC: ret = responseCdmaInformationRecord(p); break;
        case RIL_UNSOL_OEM_HOOK_RAW: ret = responseRaw(p); break;
        case RIL_UNSOL_RINGBACK_TONE: ret = responseInts(p); break;
        case RIL_UNSOL_RESEND_INCALL_MUTE: ret = responseVoid(p); break;
        case RIL_UNSOL_HSDPA_STATE_CHANGED: ret = responseInts(p); break;

        //fixing anoying Exceptions caused by the new Samsung states
        //FIXME figure out what the states mean an what data is in the parcel

        case RIL_UNSOL_O2_HOME_ZONE_INFO: ret = responseVoid(p); break;
        case RIL_UNSOL_STK_SEND_SMS_RESULT: ret = responseVoid(p); break;
        case RIL_UNSOL_DEVICE_READY_NOTI: ret = responseVoid(p); break;
        case RIL_UNSOL_GPS_NOTI: ret = responseVoid(p); break; // Ignored in TW RIL.
        case RIL_UNSOL_DATA_SUSPEND_RESUME: ret = responseVoid(p); break;
        case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL: ret = responseVoid(p); break;
        case RIL_UNSOL_AM: ret = responseStrings(p); break;
		
		case RIL_UNSOL_VOICE_RADIO_TECH_CHANGED: ret =  responseInts(p); break;
		case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
		case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED: ret = responseInts(p); break;
		case RIL_UNSOL_RADIO_REFRESH: ret = responseString(p); break;
		
        default:
            throw new RuntimeException("Unrecognized unsol response: " + response);
            //break; (implied)
        }} catch (Throwable tr) {
            Log.e(LOG_TAG, "Exception processing unsol response: " + response +
                    "Exception:" + tr.toString());
            return;
        }

        switch(response) {
        case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                /* has bonus radio state int */
                RadioState newState = getRadioStateFromInt(p.readInt());
                if (RILJ_LOGD) unsljLogMore(response, newState.toString());

                switchToRadioState(newState);
				Log.i(LOG_TAG, "sbrissen - RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED - mState: " + newState.toString());
                break;
        case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED:
            if (RILJ_LOGD) unsljLog(response);

            mCallStateRegistrants
            .notifyRegistrants(new AsyncResult(null, null, null));
            break;
        case RIL_UNSOL_HSDPA_STATE_CHANGED:
            if (RILJ_LOGD) unsljLog(response);

            boolean newHsdpa = ((int[])ret)[0] == 1;
            String curState = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE);
            boolean curHsdpa = false;

            if (curState.equals("HSDPA:9")) {
                curHsdpa = true;
            } else if (!curState.equals("UMTS:3")) {
                // Don't send poll request if not on 3g
                break;
            }

            if (curHsdpa != newHsdpa) {
                mVoiceNetworkStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            }
            break;

        case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED:
            if (RILJ_LOGD) unsljLog(response);

            mVoiceNetworkStateRegistrants
            .notifyRegistrants(new AsyncResult(null, null, null));
            break;
        case RIL_UNSOL_RESPONSE_NEW_SMS: {
            if (RILJ_LOGD) unsljLog(response);

            // FIXME this should move up a layer
            String a[] = new String[2];

            a[1] = (String)ret;

            SmsMessage sms;

            sms = SmsMessage.newFromCMT(a);
            if (mGsmSmsRegistrant != null) {
                mGsmSmsRegistrant
                .notifyRegistrant(new AsyncResult(null, sms, null));
            }
            break;
        }
        case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mSmsStatusRegistrant != null) {
                mSmsStatusRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
            }
            break;
        case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            int[] smsIndex = (int[])ret;

            if(smsIndex.length == 1) {
                if (mSmsOnSimRegistrant != null) {
                    mSmsOnSimRegistrant.
                    notifyRegistrant(new AsyncResult(null, smsIndex, null));
                }
            } else {
                if (RILJ_LOGD) riljLog(" NEW_SMS_ON_SIM ERROR with wrong length "
                        + smsIndex.length);
            }
            break;
        case RIL_UNSOL_ON_USSD:
            String[] resp = (String[])ret;

            if (resp.length < 2) {
                resp = new String[2];
                resp[0] = ((String[])ret)[0];
                resp[1] = null;
            }
            if (RILJ_LOGD) unsljLogMore(response, resp[0]);
            if (mUSSDRegistrant != null) {
                mUSSDRegistrant.notifyRegistrant(
                        new AsyncResult (null, resp, null));
            }
            break;
        case RIL_UNSOL_NITZ_TIME_RECEIVED:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            // has bonus long containing milliseconds since boot that the NITZ
            // time was received
            long nitzReceiveTime = p.readLong();

            Object[] result = new Object[2];

            String nitz = (String)ret;
            if (RILJ_LOGD) riljLog(" RIL_UNSOL_NITZ_TIME_RECEIVED length = "
                    + nitz.split("[/:,+-]").length);
            //remove the tailing information that samsung added to the string
            //it will screw the NITZ parser
            if(nitz.split("[/:,+-]").length >= 9)
                nitz = nitz.substring(0,(nitz.lastIndexOf(",")));
            if (RILJ_LOGD) riljLog(" RIL_UNSOL_NITZ_TIME_RECEIVED striped nitz = "
                    + nitz);
            result[0] = nitz;
            result[1] = Long.valueOf(nitzReceiveTime);

            if (mNITZTimeRegistrant != null) {

                mNITZTimeRegistrant
                .notifyRegistrant(new AsyncResult (null, result, null));
            } else {
                // in case NITZ time registrant isnt registered yet
                mLastNITZTimeInfo = nitz;
            }
            break;

        case RIL_UNSOL_SIGNAL_STRENGTH:
            // Note this is set to "verbose" because it happens
            // frequently
            if (RILJ_LOGV) unsljLogvRet(response, ret);

            if (mSignalStrengthRegistrant != null) {
                mSignalStrengthRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;
        case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
            break;

        case RIL_UNSOL_SUPP_SVC_NOTIFICATION:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mSsnRegistrant != null) {
                mSsnRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_STK_SESSION_END:
            if (RILJ_LOGD) unsljLog(response);

            if (mCatSessionEndRegistrant != null) {
                mCatSessionEndRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_STK_PROACTIVE_COMMAND:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mCatProCmdRegistrant != null) {
                mCatProCmdRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_STK_EVENT_NOTIFY:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mCatEventRegistrant != null) {
                mCatEventRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_STK_CALL_SETUP:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mCatCallSetUpRegistrant != null) {
                mCatCallSetUpRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
            if (RILJ_LOGD) unsljLog(response);

            if (mIccSmsFullRegistrant != null) {
                mIccSmsFullRegistrant.notifyRegistrant();
            }
            break;

        case RIL_UNSOL_SIM_REFRESH:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mIccRefreshRegistrants != null) {
                mIccRefreshRegistrants.notifyRegistrants(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_CALL_RING:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mRingRegistrant != null) {
                mRingRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_RESTRICTED_STATE_CHANGED:
            if (RILJ_LOGD) unsljLogvRet(response, ret);
            if (mRestrictedStateRegistrant != null) {
                mRestrictedStateRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:
            if (RILJ_LOGD) unsljLog(response);

            if (mIccStatusChangedRegistrants != null) {
                mIccStatusChangedRegistrants.notifyRegistrants();
            }
            break;

        case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
            if (RILJ_LOGD) unsljLog(response);

            SmsMessage sms = (SmsMessage) ret;

            if (mCdmaSmsRegistrant != null) {
                mCdmaSmsRegistrant
                .notifyRegistrant(new AsyncResult(null, sms, null));
            }
            break;

        case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
            if (RILJ_LOGD) unsljLog(response);

            if (mGsmBroadcastSmsRegistrant != null) {
                mGsmBroadcastSmsRegistrant
                .notifyRegistrant(new AsyncResult(null, ret, null));
            }
            break;

        case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:
            if (RILJ_LOGD) unsljLog(response);

            if (mIccSmsFullRegistrant != null) {
                mIccSmsFullRegistrant.notifyRegistrant();
            }
            break;

        case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE:
            if (RILJ_LOGD) unsljLog(response);

            if (mEmergencyCallbackModeRegistrant != null) {
                mEmergencyCallbackModeRegistrant.notifyRegistrant();
            }
            break;

        case RIL_UNSOL_CDMA_CALL_WAITING:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mCallWaitingInfoRegistrants != null) {
                mCallWaitingInfoRegistrants.notifyRegistrants(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mOtaProvisionRegistrants != null) {
                mOtaProvisionRegistrants.notifyRegistrants(
                        new AsyncResult (null, ret, null));
            }
            break;

        case RIL_UNSOL_CDMA_INFO_REC:
            ArrayList<CdmaInformationRecords> listInfoRecs;

            try {
                listInfoRecs = (ArrayList<CdmaInformationRecords>)ret;
            } catch (ClassCastException e) {
                Log.e(LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
                break;
            }

            for (CdmaInformationRecords rec : listInfoRecs) {
                if (RILJ_LOGD) unsljLogRet(response, rec);
                notifyRegistrantsCdmaInfoRec(rec);
            }
            break;

        case RIL_UNSOL_OEM_HOOK_RAW:
            if (RILJ_LOGD) unsljLogvRet(response, IccUtils.bytesToHexString((byte[])ret));
            if (mUnsolOemHookRawRegistrant != null) {
                mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
            }
            break;

        case RIL_UNSOL_RINGBACK_TONE:
            if (RILJ_LOGD) unsljLogvRet(response, ret);
            if (mRingbackToneRegistrants != null) {
                boolean playtone = (((int[])ret)[0] == 1);
                mRingbackToneRegistrants.notifyRegistrants(
                        new AsyncResult (null, playtone, null));
            }
            break;

        case RIL_UNSOL_RESEND_INCALL_MUTE:
            if (RILJ_LOGD) unsljLogRet(response, ret);

            if (mResendIncallMuteRegistrants != null) {
                mResendIncallMuteRegistrants.notifyRegistrants(
                        new AsyncResult (null, ret, null));
            }
			
        // SAMSUNG STATES
        case RIL_UNSOL_AM:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                String amString = (String) ret;
                Log.d(LOG_TAG, "Executing AM: " + amString);

                try {
                    Runtime.getRuntime().exec("am " + amString);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(LOG_TAG, "am " + amString + " could not be executed.");
                }
                break;
        }
    }
	
    protected RadioState getRadioStateFromInt(int stateInt) {
        RadioState state;
        HandlerThread handlerThread;
        Looper looper;
        IccHandler iccHandler;
		legacyState = stateInt;

        /* RIL_RadioState ril.h */
        switch(stateInt) {
            case 0: 
				state = RadioState.RADIO_OFF; break;
            case 1: state = RadioState.RADIO_UNAVAILABLE; break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10: 
				state = RadioState.RADIO_ON; break;

            default:
                throw new RuntimeException(
                            "Unrecognized RIL_RadioState: " + stateInt);
        }
        return state;
    }
	
	@Override
    protected void switchToRadioState(RadioState newState) {

        if (mInitialRadioStateChange) {
            if (newState.isOn()) {
                /* If this is our first notification, make sure the radio
                 * is powered off.  This gets the radio into a known state,
                 * since it's possible for the phone proc to have restarted
                 * (eg, if it or the runtime crashed) without the RIL
                 * and/or radio knowing.
                 */
                if (RILJ_LOGD) Log.d(LOG_TAG, "Radio ON @ init; reset to OFF");
                setRadioPower(false, null);
            } else {
                Log.d(LOG_TAG, "Radio OFF @ init");
                setRadioState(newState);
            }
            mInitialRadioStateChange = false;
        } else {
            setRadioState(newState);
        }
    }

    @Override
    public void
    getIMSIForApp(String aid, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_IMSI, result);

        //rr.mp.writeInt(1);
        //rr.mp.writeString(mAid);

        if (RILJ_LOGD) riljLog(rr.serialString() +
                              "> getIMSI:RIL_REQUEST_GET_IMSI " +
                              RIL_REQUEST_GET_IMSI +
                              " " + requestToString(rr.mRequest));

        send(rr);
    }

    @Override
	protected Object
	responseIMSI(Parcel p){
			String response = (String)responseString(p);
            Log.i(LOG_TAG, "sbrissen - responseIMSI(): " + response);
		return response;
	}		

 	@Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplication ca;

        IccCardStatus status = new IccCardStatus();
        status.setCardState(p.readInt());
        status.setUniversalPinState(p.readInt());
        status.setGsmUmtsSubscriptionAppIndex(p.readInt());
        status.setCdmaSubscriptionAppIndex(p.readInt() );

       // status.setImsSubscriptionAppIndex(p.readInt());

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        status.setNumApplications(numApplications);

        for (int i = 0 ; i < numApplications ; i++) {
            ca = new IccCardApplication();
            ca.app_type       = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state      = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            if ((ca.app_state == IccCardApplication.AppState.APPSTATE_SUBSCRIPTION_PERSO) &&
                ((ca.perso_substate == IccCardApplication.PersoSubState.PERSOSUBSTATE_READY) ||
                (ca.perso_substate == IccCardApplication.PersoSubState.PERSOSUBSTATE_UNKNOWN))) {
                // ridiculous hack for network SIM unlock pin
                ca.app_state = IccCardApplication.AppState.APPSTATE_UNKNOWN;
                Log.d(LOG_TAG, "ca.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO");
                Log.d(LOG_TAG, "ca.perso_substate == PersoSubState.PERSOSUBSTATE_READY");
            }
            ca.aid            = p.readString();
            ca.app_label      = p.readString();
            ca.pin1_replaced  = p.readInt();
            ca.pin1           = ca.PinStateFromRILInt(p.readInt());
            ca.pin2           = ca.PinStateFromRILInt(p.readInt());

            p.readInt(); //remaining_count_pin1   - pin1_num_retries
            p.readInt(); //remaining_count_puk1   - puk1_num_retries
            p.readInt(); //remaining_count_pin2   - pin2_num_retries
            p.readInt(); //remaining_count_puk2   - puk2_num_retries
            p.readInt(); //                       - perso_unblock_retries
            status.addApplication(ca);
        }
        int appIndex = -1;
        if (mPhoneType == RILConstants.CDMA_PHONE) {
            appIndex = status.getCdmaSubscriptionAppIndex();
            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
        } else {
            appIndex = status.getGsmUmtsSubscriptionAppIndex();
            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
        }

        if (numApplications > 0) {
            IccCardApplication application = status.getApplication(appIndex);
            mAid = application.aid;
            mUSIM = application.app_type
                      == IccCardApplication.AppType.APPTYPE_USIM;
            mSetPreferredNetworkType = mPreferredNetworkType;

            if (TextUtils.isEmpty(mAid))
               mAid = "";
            Log.d(LOG_TAG, "mAid " + mAid + " mUSIM=" + mUSIM + " mSetPreferredNetworkType=" + mSetPreferredNetworkType);
        }

        return status;
    }
	
    private void setRadioStateFromRILInt (int stateCode) {
        CommandsInterface.RadioState radioState;
        HandlerThread handlerThread;
        Looper looper;
        IccHandler iccHandler;

        switch (stateCode) {
            case 0:
                radioState = CommandsInterface.RadioState.RADIO_OFF;
                break;
            case 1:
                radioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                radioState = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateCode);
        }
		if(mInitialRadioStateChange){
			if(radioState.isOn()){
				Log.d(LOG_TAG, "Radio ON @ init; reset to OFF");
				setRadioPower(false, null);
			}
			mInitialRadioStateChange = false;
		}
		
        setRadioState (radioState);
    }

    class IccHandler extends Handler implements Runnable {
        private static final int EVENT_RADIO_ON = 1;
        private static final int EVENT_ICC_STATUS_CHANGED = 2;
        private static final int EVENT_GET_ICC_STATUS_DONE = 3;
        private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 4;

        private RIL mRil;
        private boolean mRadioOn = false;

        public IccHandler (RIL ril, Looper looper) {
            super (looper);
            mRil = ril;
        }

        public void handleMessage (Message paramMessage) {
            switch (paramMessage.what) {
                case EVENT_RADIO_ON:
                    mRadioOn = true;
                    Log.d(LOG_TAG, "Radio on -> Forcing sim status update");
                    sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    AsyncResult asyncResult = (AsyncResult) paramMessage.obj;
                    if (asyncResult.exception != null) {
                        Log.e (LOG_TAG, "IccCardStatusDone shouldn't return exceptions!", asyncResult.exception);
                        break;
                    }
                    IccCardStatus status = (IccCardStatus) asyncResult.result;
                    if (status.getNumApplications() == 0) {
                        if (!mRil.getRadioState().isOn()) {
                            break;
                        }

                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                    } else {
                        int appIndex = -1;
                        if (mPhoneType == RILConstants.CDMA_PHONE) {
                            appIndex = status.getCdmaSubscriptionAppIndex();
                            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
                        } else {
                            appIndex = status.getGsmUmtsSubscriptionAppIndex();
                            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
                        }

                        IccCardApplication application = status.getApplication(appIndex);
                        IccCardApplication.AppState app_state = application.app_state;
                        IccCardApplication.AppType app_type = application.app_type;

                        switch (app_state) {
                            case APPSTATE_PIN:
                            case APPSTATE_PUK:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            case APPSTATE_READY:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            default:
                                return;
                        }
                    }
                    break;
                case EVENT_ICC_STATUS_CHANGED:
                    if (mRadioOn) {
                        Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                         mRil.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, paramMessage.obj));
                    } else {
                         Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED while radio is not ON. Ignoring");
                    }
                    break;
                case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                    mRadioOn = false;
                    // disposeCards(); // to be verified;
                default:
                    Log.e(LOG_TAG, " Unknown Event " + paramMessage.what);
                    break;
            }
        }

        public void run () {
            mRil.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
            Message msg = obtainMessage(EVENT_RADIO_ON);
            mRil.getIccCardStatus(msg);
        }
    }
	
	protected Object responseImei(Parcel p) {
		String response = (String)responseString(p);
            Log.i(LOG_TAG, "sbrissen - responseImei(): " + response);
		return response;
	}
	
    @Override
    protected Object responseCallList(Parcel p) {
        int num;
        int voiceSettings;
        ArrayList<DriverCall> response;
        DriverCall dc;

        num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        for (int i = 0; i < num; i++) {
            dc = new DriverCall();
			Log.i(LOG_TAG , "sbrissen - responseCallList 1");
            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            dc.isMpty = (0 != p.readInt());
            dc.isMT = (0 != p.readInt());
            dc.als = p.readInt();
            voiceSettings = p.readInt();
            dc.isVoice = (0 == voiceSettings) ? false : true;
            dc.isVoicePrivacy = (0 != p.readInt());
            // Some Samsung magic data for Videocalls
            // hack taken from smdk4210ril class
            voiceSettings = p.readInt();
            // printing it to cosole for later investigation
            Log.d(LOG_TAG, "Samsung magic = " + voiceSettings);
            dc.number = p.readString();
            int np = p.readInt();
            dc.numberPresentation = DriverCall.presentationFromCLIP(np);
            dc.name = p.readString();
            dc.namePresentation = p.readInt();
            int uusInfoPresent = p.readInt();
            if (uusInfoPresent == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                byte[] userData = p.createByteArray();
                dc.uusInfo.setUserData(userData);
                riljLogv(String.format(
                        "Incoming UUS : type=%d, dcs=%d, length=%d",
                        dc.uusInfo.getType(), dc.uusInfo.getDcs(),
                        dc.uusInfo.getUserData().length));
                riljLogv("Incoming UUS : data (string)="
                        + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): "
                        + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }

            // Make sure there's a leading + on addresses with a TOA of 145
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number,
                    dc.TOA);

            response.add(dc);

            if (dc.isVoicePrivacy) {
                mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }

        Collections.sort(response);

        return response;
    }


    protected Object
    responseLastCallFailCause(Parcel p) {
        int response[] = (int[])responseInts(p);

		Log.d(LOG_TAG,"sbrissen - responseLastCallFailCause()");
        if (response.length > 0 &&
            response[0] == com.android.internal.telephony.cdma.CallFailCause.ERROR_UNSPECIFIED) {

            // Far-end hangup returns ERROR_UNSPECIFIED, which shows "Call Lost" dialog.
            Log.d(LOG_TAG, "Overriding ERROR_UNSPECIFIED fail cause with NORMAL_CLEARING.");
            response[0] = com.android.internal.telephony.cdma.CallFailCause.NORMAL_CLEARING;
        }

        return response;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

		Log.d(LOG_TAG,"sbrissen - responseSignalStrength()");
        /* TODO: Add SignalStrength class to match RIL_SignalStrength */
        response = new int[numInts];
        for (int i = 0 ; i < 7 ; i++) {
            response[i] = p.readInt();
        }
        // SamsungChargeRIL is a v3 RIL, fill the rest with -1
        for (int i = 7; i < numInts; i++) {
            response[i] = -1;
        }
		for (int i = 0; i < numInts; i++){
			Log.d(LOG_TAG, "SignalStrength: " + i + ": " + response[i]);
		}
        // Framework takes care of the rest for us.
        return response;
    }

    protected Object
    responseVoiceRegistrationState(Parcel p) {
        String response[] = (String[])responseStrings(p);
		Log.d(LOG_TAG,"sbrissen - responseVoiceRegistrationState");
        if (response.length > 6) {
            // These values are provided in hex, convert to dec.
            response[4] = Integer.toString(Integer.parseInt(response[4])); // baseStationId
            response[5] = Integer.toString(Integer.parseInt(response[5])); // baseStationLatitude
            response[6] = Integer.toString(Integer.parseInt(response[6])); // baseStationLongitude
        }

        return response;
    }

    @Override
    protected Object
    responseSetupDataCall(Parcel p) {
        DataCallState dataCall = new DataCallState();
        String strings[] = (String []) responseStrings(p);

		Log.d(LOG_TAG,"sbrissen - responseSetupDataCall() string.length: " + strings.length);
        if (strings.length >= 2) {
			Log.d(LOG_TAG,"sbrissen - responseSetupDataCall() 1");
            dataCall.cid = Integer.parseInt(strings[0]);

            // We're responsible for starting/stopping the pppd_cdma service.
            if (!startPppdCdmaService(strings[1])) {
				Log.d(LOG_TAG,"sbrissen - responseSetupDataCall() 2");
                // pppd_cdma service didn't respond timely.
                dataCall.status = FailCause.ERROR_UNSPECIFIED.getErrorCode();
                return dataCall;
            }

            // pppd_cdma service responded, pull network parameters set by ip-up script.
			Log.d(LOG_TAG,"sbrissen - responseSetupDataCall() 3");
            dataCall.ifname = SystemProperties.get("net.cdma.ppp.interface");
            String   ifprop = "net." + dataCall.ifname;

            dataCall.addresses = new String[] {SystemProperties.get(ifprop + ".local-ip")};
            dataCall.gateways  = new String[] {SystemProperties.get(ifprop + ".remote-ip")};
            dataCall.dnses     = new String[] {SystemProperties.get(ifprop + ".dns1"),
                                                   SystemProperties.get(ifprop + ".dns2")};
        } else {
		Log.d(LOG_TAG,"sbrissen - responseSetupDataCall() 4");
            dataCall.status = FailCause.ERROR_UNSPECIFIED.getErrorCode(); // Who knows?
        }

        return dataCall;
    }

    private boolean startPppdCdmaService(String ttyname) {
        SystemProperties.set("net.cdma.datalinkinterface", ttyname);

		Log.d(LOG_TAG,"sbrissen - startPppdCdmaService()");
        // Connecting: Set ril.cdma.data_state=1 to (re)start pppd_cdma service,
        // which responds by setting ril.cdma.data_state=2 once connection is up.
        SystemProperties.set("ril.cdma.data_state", "1");
        Log.d(LOG_TAG, "Set ril.cdma.data_state=1, waiting for ril.cdma.data_state=2.");

        // Typically takes < 200 ms on my Epic, so sleep in 100 ms intervals.
        for (int i = 0; i < 10; i++) {
            try {Thread.sleep(100);} catch (InterruptedException e) {}

            if (SystemProperties.getInt("ril.cdma.data_state", 1) == 2) {
                Log.d(LOG_TAG, "Got ril.cdma.data_state=2, connected.");
                return true;
            }
        }

        // Taking > 1 s here, try up to 10 s, which is hopefully long enough.
        for (int i = 1; i < 10; i++) {
            try {Thread.sleep(1000);} catch (InterruptedException e) {}

            if (SystemProperties.getInt("ril.cdma.data_state", 1) == 2) {
                Log.d(LOG_TAG, "Got ril.cdma.data_state=2, connected.");
                return true;
            }
        }

        // Disconnect: Set ril.cdma.data_state=0 to stop pppd_cdma service.
        Log.d(LOG_TAG, "Didn't get ril.cdma.data_state=2 timely, aborting.");
        SystemProperties.set("ril.cdma.data_state", "0");

        return false;
    }

    protected Object
    responseDeactivateDataCall(Parcel p) {
        // Disconnect: Set ril.cdma.data_state=0 to stop pppd_cdma service.
        Log.d(LOG_TAG, "Set ril.cdma.data_state=0.");
        SystemProperties.set("ril.cdma.data_state", "0");

        return null;
    }

    protected Object
    responseCdmaSubscription(Parcel p) {
        String response[] = (String[])responseStrings(p);

        if (response.length == 4) {
			Log.i(LOG_TAG, "sbrissen - responseCdmaSubsciption response == 4");
            // PRL version is missing in subscription parcel, add it from properties.
            String prlVersion = SystemProperties.get("ril.prl_ver_1").split(":")[1];
            response          = new String[] {response[0], response[1], response[2],
                                              response[3], prlVersion};
        }

        return response;
    }

    // Workaround for Samsung CDMA "ring of death" bug:
    //
    // Symptom: As soon as the phone receives notice of an incoming call, an
    //   audible "old fashioned ring" is emitted through the earpiece and
    //   persists through the duration of the call, or until reboot if the call
    //   isn't answered.
    //
    // Background: The CDMA telephony stack implements a number of "signal info
    //   tones" that are locally generated by ToneGenerator and mixed into the
    //   voice call path in response to radio RIL_UNSOL_CDMA_INFO_REC requests.
    //   One of these tones, IS95_CONST_IR_SIG_IS54B_L, is requested by the
    //   radio just prior to notice of an incoming call when the voice call
    //   path is muted.  CallNotifier is responsible for stopping all signal
    //   tones (by "playing" the TONE_CDMA_SIGNAL_OFF tone) upon receipt of a
    //   "new ringing connection", prior to unmuting the voice call path.
    //
    // Problem: CallNotifier's incoming call path is designed to minimize
    //   latency to notify users of incoming calls ASAP.  Thus,
    //   SignalInfoTonePlayer requests are handled asynchronously by spawning a
    //   one-shot thread for each.  Unfortunately the ToneGenerator API does
    //   not provide a mechanism to specify an ordering on requests, and thus,
    //   unexpected thread interleaving may result in ToneGenerator processing
    //   them in the opposite order that CallNotifier intended.  In this case,
    //   playing the "signal off" tone first, followed by playing the "old
    //   fashioned ring" indefinitely.
    //
    // Solution: An API change to ToneGenerator is required to enable
    //   SignalInfoTonePlayer to impose an ordering on requests (i.e., drop any
    //   request that's older than the most recent observed).  Such a change,
    //   or another appropriate fix should be implemented in AOSP first.
    //
    // Workaround: Intercept RIL_UNSOL_CDMA_INFO_REC requests from the radio,
    //   check for a signal info record matching IS95_CONST_IR_SIG_IS54B_L, and
    //   drop it so it's never seen by CallNotifier.  If other signal tones are
    //   observed to cause this problem, they should be dropped here as well.
    @Override
    protected void
    notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        final int response = RIL_UNSOL_CDMA_INFO_REC;

        if (infoRec.record instanceof CdmaSignalInfoRec) {
            CdmaSignalInfoRec sir = (CdmaSignalInfoRec)infoRec.record;
            if (sir != null && sir.isPresent &&
                sir.signalType == SignalToneUtil.IS95_CONST_IR_SIGNAL_IS54B &&
                sir.alertPitch == SignalToneUtil.IS95_CONST_IR_ALERT_MED    &&
                sir.signal     == SignalToneUtil.IS95_CONST_IR_SIG_IS54B_L) {

                Log.d(LOG_TAG, "Dropping \"" + responseToString(response) + " " +
                      retToString(response, sir) + "\" to prevent \"ring of death\" bug.");
                return;
            }
        }

        super.notifyRegistrantsCdmaInfoRec(infoRec);
    }

    protected class SamsungDriverCall extends DriverCall {
		public boolean isVideo;
        @Override
        public String
        toString() {
            // Samsung CDMA devices' call parcel is formatted differently
            // fake unused data for video calls, and fix formatting
            // so that voice calls' information can be correctly parsed
            return "id=" + index + ","
            + state + ","
            + "toa=" + TOA + ","
            + (isMpty ? "conf" : "norm") + ","
            + (isMT ? "mt" : "mo") + ","
            + "als=" + als + ","
            + (isVoice ? "voc" : "nonvoc") + ","
			+ (isVideo ? "video" : "no_video") + ","
			+ (isVoicePrivacy ? "evp" : "noevp") + ","
            + "cli=" + numberPresentation + ","
            + namePresentation;
        }
    }
	
    public class ChargeRILReceiver extends RILReceiver implements Runnable {
        byte[] bufferext;


        ChargeRILReceiver() {
            bufferext = new byte[RIL_MAX_COMMAND_BYTES];
        }
		@Override
        public void
        run() {
            int retryCount = 0;
			
            try {for (;;) {
                LocalSocket sext = null;
                LocalSocketAddress lext;
					
                try {
                    sext = new LocalSocket();
					Log.d (LOG_TAG, "Charge mPhoneType: " + mPhoneType);
					Log.d (LOG_TAG, "Creating RILDEXT Socket");
					lext = new LocalSocketAddress(SOCKET_NAME_RIL_EXT,
                            LocalSocketAddress.Namespace.RESERVED);
                    sext.connect(lext);
                } catch (IOException ex){
                    try {
                        if (sext != null) {
                            sext.close();
                        }
                    } catch (IOException ex2) {
                        //ignore failure to close after failure to connect
                    }

                    // don't print an error message after the the first time
                    // or after the 8th time
                    if (retryCount == 8) {
                        Log.e (LOG_TAG,
                            "Couldn't find '" + SOCKET_NAME_RIL_EXT
                            + "' socket after " + retryCount
                            + " times, continuing to retry silently");
                    } else if (retryCount > 0 && retryCount < 8) {
                        Log.i (LOG_TAG,
                            "Couldn't find '" + SOCKET_NAME_RIL_EXT
                            + "' socket; retrying after timeout");
                    }

                    try {
                        Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                    } catch (InterruptedException er) {
                    }

                    retryCount++;
                    continue;
                }
                retryCount = 0;				

                mSocketext = sext;				
	            Log.i(LOG_TAG, "Connected to '" + SOCKET_NAME_RIL_EXT + "' socket");
				
                int lengthext = 0;
                try {
                    InputStream isext = mSocketext.getInputStream();

                    for (;;) {
						
                        Parcel pext;

						lengthext = RIL.readRilMessage(isext, bufferext);

                        if (lengthext < 0) {
							SystemProperties.set("ril.rildReset","1");
							Log.i(LOG_TAG, "END OF STREAM");
                            // End-of-stream reached
                            break;
                        }


                        pext = Parcel.obtain();
                        pext.unmarshall(bufferext, 0, lengthext);
                        pext.setDataPosition(0);

                        Log.v(LOG_TAG, "Read packet: " + lengthext + " bytes");

                        processResponse(pext);
                        pext.recycle();
                    }
                } catch (java.io.IOException ex) {
                    Log.i(LOG_TAG, "'" + SOCKET_NAME_RIL + "' socket closed",
                          ex);
                } catch (Throwable tr) {
                    Log.e(LOG_TAG, "Uncaught exception read length=" + lengthext +
                        "Exception:" + tr.toString());
                }

                Log.i(LOG_TAG, "Disconnected from '" + SOCKET_NAME_RIL
                      + "' socket");

                setRadioState (RadioState.RADIO_UNAVAILABLE);
				Intent intent = new Intent("android.intent.action.RILD_CRASH");
				intent.putExtra("PHONE_TYPE",mPhoneType);
				ActivityManagerNative.broadcastStickyIntent(intent,null);

                try {
                    mSocketext.close();
                } catch (IOException ex) {
                }

                mSocketext = null;
                RILRequest.resetSerial();

                // Clear request list on close
                clearRequestsList(RADIO_NOT_AVAILABLE, false);
				
            }} catch (Throwable tr) {
                Log.e(LOG_TAG,"Uncaught exception", tr);
            }
			
            /* We're disconnected so we don't know the ril version */
           // notifyRegistrantsRilConnectionChanged(-1);
        }
    }
	
  protected void samsungUnsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    static String
    requestToString(int request) {
/*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
*/
        switch(request) {
            case RIL_REQUEST_GET_SIM_STATUS: return "GET_SIM_STATUS";
            case RIL_REQUEST_ENTER_SIM_PIN: return "ENTER_SIM_PIN";
            case RIL_REQUEST_ENTER_SIM_PUK: return "ENTER_SIM_PUK";
            case RIL_REQUEST_ENTER_SIM_PIN2: return "ENTER_SIM_PIN2";
            case RIL_REQUEST_ENTER_SIM_PUK2: return "ENTER_SIM_PUK2";
            case RIL_REQUEST_CHANGE_SIM_PIN: return "CHANGE_SIM_PIN";
            case RIL_REQUEST_CHANGE_SIM_PIN2: return "CHANGE_SIM_PIN2";
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: return "ENTER_NETWORK_DEPERSONALIZATION";
            case RIL_REQUEST_GET_CURRENT_CALLS: return "GET_CURRENT_CALLS";
            case RIL_REQUEST_DIAL: return "DIAL";
            case RIL_REQUEST_GET_IMSI: return "GET_IMSI";
            case RIL_REQUEST_HANGUP: return "HANGUP";
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: return "HANGUP_WAITING_OR_BACKGROUND";
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case RIL_REQUEST_CONFERENCE: return "CONFERENCE";
            case RIL_REQUEST_UDUB: return "UDUB";
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: return "LAST_CALL_FAIL_CAUSE";
            case RIL_REQUEST_SIGNAL_STRENGTH: return "SIGNAL_STRENGTH";
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: return "REGISTRATION_STATE";
            case RIL_REQUEST_DATA_REGISTRATION_STATE: return "GPRS_REGISTRATION_STATE";
            case RIL_REQUEST_OPERATOR: return "OPERATOR";
            case RIL_REQUEST_RADIO_POWER: return "RADIO_POWER";
            case RIL_REQUEST_DTMF: return "DTMF";
            case RIL_REQUEST_SEND_SMS: return "SEND_SMS";
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: return "SEND_SMS_EXPECT_MORE";
            case RIL_REQUEST_SETUP_DATA_CALL: return "SETUP_DATA_CALL";
            case RIL_REQUEST_SIM_IO: return "SIM_IO";
			case RIL_REQUEST_SIM_AUTH: return "RIL_REQUEST_SIM_AUTH";
            case RIL_REQUEST_SEND_USSD: return "SEND_USSD";
            case RIL_REQUEST_CANCEL_USSD: return "CANCEL_USSD";
            case RIL_REQUEST_GET_CLIR: return "GET_CLIR";
            case RIL_REQUEST_SET_CLIR: return "SET_CLIR";
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: return "QUERY_CALL_FORWARD_STATUS";
            case RIL_REQUEST_SET_CALL_FORWARD: return "SET_CALL_FORWARD";
            case RIL_REQUEST_QUERY_CALL_WAITING: return "QUERY_CALL_WAITING";
            case RIL_REQUEST_SET_CALL_WAITING: return "SET_CALL_WAITING";
            case RIL_REQUEST_SMS_ACKNOWLEDGE: return "SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GET_IMEI: return "GET_IMEI";
            case RIL_REQUEST_GET_IMEISV: return "GET_IMEISV";
            case RIL_REQUEST_ANSWER: return "ANSWER";
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: return "DEACTIVATE_DATA_CALL";
            case RIL_REQUEST_QUERY_FACILITY_LOCK: return "QUERY_FACILITY_LOCK";
            case RIL_REQUEST_SET_FACILITY_LOCK: return "SET_FACILITY_LOCK";
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: return "CHANGE_BARRING_PASSWORD";
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: return "QUERY_NETWORK_SELECTION_MODE";
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: return "SET_NETWORK_SELECTION_AUTOMATIC";
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: return "SET_NETWORK_SELECTION_MANUAL";
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : return "QUERY_AVAILABLE_NETWORKS ";
            case RIL_REQUEST_DTMF_START: return "DTMF_START";
            case RIL_REQUEST_DTMF_STOP: return "DTMF_STOP";
            case RIL_REQUEST_BASEBAND_VERSION: return "BASEBAND_VERSION";
            case RIL_REQUEST_SEPARATE_CONNECTION: return "SEPARATE_CONNECTION";
            case RIL_REQUEST_SET_MUTE: return "SET_MUTE";
            case RIL_REQUEST_GET_MUTE: return "GET_MUTE";
            case RIL_REQUEST_QUERY_CLIP: return "QUERY_CLIP";
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: return "LAST_DATA_CALL_FAIL_CAUSE";
            case RIL_REQUEST_DATA_CALL_LIST: return "DATA_CALL_LIST";
            case RIL_REQUEST_RESET_RADIO: return "RESET_RADIO";
            case RIL_REQUEST_OEM_HOOK_RAW: return "OEM_HOOK_RAW";
            case RIL_REQUEST_OEM_HOOK_STRINGS: return "OEM_HOOK_STRINGS";
            case RIL_REQUEST_SCREEN_STATE: return "SCREEN_STATE";
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: return "SET_SUPP_SVC_NOTIFICATION";
            case RIL_REQUEST_WRITE_SMS_TO_SIM: return "WRITE_SMS_TO_SIM";
            case RIL_REQUEST_DELETE_SMS_ON_SIM: return "DELETE_SMS_ON_SIM";
            case RIL_REQUEST_SET_BAND_MODE: return "SET_BAND_MODE";
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: return "QUERY_AVAILABLE_BAND_MODE";
            case RIL_REQUEST_STK_GET_PROFILE: return "REQUEST_STK_GET_PROFILE";
            case RIL_REQUEST_STK_SET_PROFILE: return "REQUEST_STK_SET_PROFILE";
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case RIL_REQUEST_SET_LOCATION_UPDATES: return "REQUEST_SET_LOCATION_UPDATES";
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION";
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RIL_REQUEST_SET_TTY_MODE: return "RIL_REQUEST_SET_TTY_MODE";
            case RIL_REQUEST_QUERY_TTY_MODE: return "RIL_REQUEST_QUERY_TTY_MODE";
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_FLASH: return "RIL_REQUEST_CDMA_FLASH";
            case RIL_REQUEST_CDMA_BURST_DTMF: return "RIL_REQUEST_CDMA_BURST_DTMF";
            case RIL_REQUEST_CDMA_SEND_SMS: return "RIL_REQUEST_CDMA_SEND_SMS";
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_SUBSCRIPTION: return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case RIL_REQUEST_DEVICE_IDENTITY: return "RIL_REQUEST_DEVICE_IDENTITY";
            case RIL_REQUEST_GET_SMSC_ADDRESS: return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case RIL_REQUEST_SET_SMSC_ADDRESS: return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE: return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case RIL_REQUEST_ISIM_AUTHENTICATION: return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU: return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS: return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
			case RIL_REQUEST_VOICE_RADIO_TECH: return "RIL_REQUEST_VOICE_RADIO_TECH";
            default: return "<unknown request>";
        }
    }

    static String
    responseToString(int request)
    {
/*
 cat libs/telephony/ril_unsol_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
*/
        switch(request) {
			case RIL_UNSOL_AM: return "RIL_UNSOL_AM";
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED: return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_NEW_SMS: return "UNSOL_RESPONSE_NEW_SMS";
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case RIL_UNSOL_ON_USSD: return "UNSOL_ON_USSD";
            case RIL_UNSOL_ON_USSD_REQUEST: return "UNSOL_ON_USSD_REQUEST";
            case RIL_UNSOL_NITZ_TIME_RECEIVED: return "UNSOL_NITZ_TIME_RECEIVED";
            case RIL_UNSOL_SIGNAL_STRENGTH: return "UNSOL_SIGNAL_STRENGTH";
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED: return "UNSOL_DATA_CALL_LIST_CHANGED";
            case RIL_UNSOL_SUPP_SVC_NOTIFICATION: return "UNSOL_SUPP_SVC_NOTIFICATION";
            case RIL_UNSOL_STK_SESSION_END: return "UNSOL_STK_SESSION_END";
            case RIL_UNSOL_STK_PROACTIVE_COMMAND: return "UNSOL_STK_PROACTIVE_COMMAND";
            case RIL_UNSOL_STK_EVENT_NOTIFY: return "UNSOL_STK_EVENT_NOTIFY";
            case RIL_UNSOL_STK_CALL_SETUP: return "UNSOL_STK_CALL_SETUP";
            case RIL_UNSOL_SIM_SMS_STORAGE_FULL: return "UNSOL_SIM_SMS_STORAGE_FULL";
            case RIL_UNSOL_SIM_REFRESH: return "UNSOL_SIM_REFRESH";
            case RIL_UNSOL_CALL_RING: return "UNSOL_CALL_RING";
            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED: return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS: return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS: return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL: return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case RIL_UNSOL_RESTRICTED_STATE_CHANGED: return "UNSOL_RESTRICTED_STATE_CHANGED";
            case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case RIL_UNSOL_CDMA_CALL_WAITING: return "UNSOL_CDMA_CALL_WAITING";
            case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case RIL_UNSOL_CDMA_INFO_REC: return "UNSOL_CDMA_INFO_REC";
            case RIL_UNSOL_OEM_HOOK_RAW: return "UNSOL_OEM_HOOK_RAW";
            case RIL_UNSOL_RINGBACK_TONE: return "UNSOL_RINGBACK_TONG";
            case RIL_UNSOL_RESEND_INCALL_MUTE: return "UNSOL_RESEND_INCALL_MUTE";
            case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED: return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case RIL_UNSOL_CDMA_PRL_CHANGED: return "UNSOL_CDMA_PRL_CHANGED";
            case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE: return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case RIL_UNSOL_RIL_CONNECTED: return "UNSOL_RIL_CONNECTED";
            case RIL_UNSOL_RADIO_REFRESH: return "RIL_UNSOL_RADIO_REFRESH";
			case RIL_UNSOL_DEVICE_READY_NOTI: return "UNSOL_DEVICE_READY_NOTI";
            default: return "<unknown reponse>";
        }
    }

}