package com.android.internal.telephony;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import android.util.Log;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import com.android.internal.telephony.CallManager;
import android.content.BroadcastReceiver;
import android.telephony.ServiceState;
import android.telephony.PhoneStateListener;
import android.content.IntentFilter;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.DataConnectionTracker;
import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.ims.IsimRecords;
import com.android.internal.telephony.SamsungChargeRIL;
import com.android.internal.telephony.RIL;

import java.util.List;

public class MultiModePhoneProxy extends PhoneProxy {

	private static String LOG_TAG = "MultiModePhoneProxy";
	public static Object lockForRadioTechnologyChange = new Object();
	static int preferredCdmaSubscription = 1;
	static int preferredNetworkMode = 4;
	
	Phone mActivePhone;
	CommandsInterface mCommandsInterfaceCDMA;
	CommandsInterface mCommandsInterfaceGSM;
	Phone mDataPhone;
	int mDataPhoneType;
	BroadcastReceiver mIntentReceiver;
	GSMPhone mLtePhone;
	CDMAPhone meCDMAPhone;
	String mOutgoingPhone;
	int mPendingPreferredNetworkCnt;
	PhoneStateListener mPhoneStateListener;
	int mRadioTechnology;

    protected static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;
    protected static final int EVENT_SIM_RECORDS_LOADED                = 16;
    

	public MultiModePhoneProxy(Phone cdmaPhone, Phone ltePhone, Context context){
		super(cdmaPhone);
		logd("MultiModePhoneProxy");		
		setLtePhone(ltePhone);
		mActivePhone = cdmaPhone;
		meCDMAPhone = (CDMAPhone)cdmaPhone;
		mLtePhone = (GSMPhone)ltePhone;     
		mActivePhone = meCDMAPhone;
		//mRadioTechnology = SamsungChargeRIL.getPreferredNetwork();

		mCommandsInterfaceCDMA = ((PhoneBase)cdmaPhone).mCM;
		mCommandsInterfaceGSM = ((PhoneBase)ltePhone).mCM;

        mCommandsInterfaceGSM.registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
        mCommandsInterfaceGSM.registerForOn(this, EVENT_RADIO_ON, null);
        mCommandsInterfaceGSM.registerForVoiceRadioTechChanged(
                             this, EVENT_VOICE_RADIO_TECH_CHANGED, null);
	//mCommandsInterfaceCDMA.registerForNVReady(this, 0x1d, null);


		//SelectActivePhone(mRadioTechnology);		
		
		mLtePhone.getIccCard().setDualPhones((PhoneBase)ltePhone,(PhoneBase)cdmaPhone);
		meCDMAPhone.getIccCard().setDualPhones((PhoneBase)ltePhone,(PhoneBase)cdmaPhone);

		mLtePhone.mIccRecords.registerForRecordsLoaded(meCDMAPhone.mDataConnectionTracker,EVENT_SIM_RECORDS_LOADED,mLtePhone.mIccRecords);
	}
	
	private void SelectActivePhone(int radioTech){
		loge("Active Phone call from SelectActivePhone, bCastReceived");
		
		int modeVal = SamsungChargeRIL.getPreferredNetwork();
		
		//meCDMAPhone.setPreferredNetworkType(mCommandsInterfaceCDMA.mPreferredNetworkType, null);
		meCDMAPhone.setPreferredNetworkType(4, null);
		meCDMAPhone.setCdmaRoamingPreference(2,null);
	    mLtePhone.setPreferredNetworkType(4, null);
		//mLtePhone.setPreferredNetworkType(mCommandsInterfaceGSM.mPreferredNetworkType, null);
		mLtePhone.setCdmaRoamingPreference(2,null);
		handleActivePhoneSelection(modeVal);
	}
	
	private int handleActivePhoneSelection(int radioTech){
		loge("Active Phone call from handleActivePhoneSelection");
		//int mode = getNetworkSelectionMode();

	        mActivePhone = meCDMAPhone;

		setActivePhone(mActivePhone);

		meCDMAPhone.setPreferredNetworkType(7, null);
		//meCDMAPhone.setPreferredNetworkType(mCommandsInterfaceGSM.mPreferredNetworkType, null);
		meCDMAPhone.setCdmaRoamingPreference(0,null);
	   // mLtePhone.setPreferredNetworkType(mCommandsInterfaceCDMA.mPreferredNetworkType, null);
		mLtePhone.setPreferredNetworkType(7, null);
		mLtePhone.setCdmaRoamingPreference(0,null);
	      return 1;
	}

	private boolean isSMSFormat3GPP2(){
		//String smsFormat = IMSICCSmsInterfaceManager.readSmsSetting("smsformat");
		
		if(IccCard.isSMSFormat3GPP()){
			return false;
		}else{
			return true;
		}
	}


	private static void logd(String msg){
		Log.d("[MultiModePhoneProxy]","[PhoneProxy]" + msg.toString());
	}


	private void loge(String msg){
		Log.e("[MultiModePhoneProxy]","[PhoneProxy]" + msg.toString());
	}
	
/*	private int mapDataType(ServiceState ss){
		int mapVal = getRadioTechnology();
		int radioTechnology = ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT;;
		
		/*switch(mapVal){
			case:
				radioTechnology = 4;
			case:
				radioTechnology = 3;
			case:
				radioTechnology = 4;
			case:
				radioTechnology = 2;
			default:
				loge("Not Supported Technology");
		}
		
		return radioTechnology;
	}*/
		
	private void refreshRild(Intent intent){
		if(intent.getIntExtra("PHONE_TYPE",0) == 1){
			mCommandsInterfaceCDMA.setRadioPower(false,null);
		}else if(intent.getIntExtra("PHONE_TYPE",0) == 2){
			mCommandsInterfaceGSM.setRadioPower(false,null);
		}
	}
	
	/*public boolean IsEmergencyCallingSupported(){
		if(getNetworkSelectionMode() == 8){
			return false;
		}else{
			return true;
		}
	}*/
	
	/*public void NotifyMultimodechange(String mode){
		SelectActivePhone();
	}*/
	
        @Override
	public int disableApnType(String type){
		return mActivePhone.disableApnType(type);
	}
	
        @Override
	public int enableApnType(String type){
		logd("sbrissen - MMP - enableApnType");
		return mActivePhone.enableApnType(type);
	}

        @Override
	public String[] getActiveApnTypes(){
		return mActivePhone.getActiveApnTypes();
	}


	public DataState getActiveDataConnectionState(){
		return getDataConnectionState();
	}


	public ServiceState getActiveServiceState(){
		return mActivePhone.getServiceState();
	}
	
	public boolean getAutoConnectEnable(){
		return true;
	}
	
        @Override
	public void getAvailableNetworks(Message response){
		logd("getAvailableNetworks");
		mLtePhone.getAvailableNetworks(response);
	}
	
	public CDMAPhone getCdmaPhone(){
		return meCDMAPhone;
	}

    @Override
    public String getImei() {
	logd("getImei");
        return mLtePhone.getImei();
    }

	
/*	@Override
	public DataActivityState getDataActivityState(){
		logd("getDataActivityState");
		return mActivePhone.getDataActivityState();
	}*/

/*	public DataState getDataConnectionState(){
		logd("getDataConnectionState");
		
		return mActivePhone.getDataConnectionState();
	}*/
	
	public Phone getDataPhone(){
		if(mRadioTechnology == 0xd){
			return mLtePhone;
		}else{
			return meCDMAPhone;
		}
	}
	
/*	public int getDataPhoneType(){
		logd("getDataPhoneType");
		mDataPhoneType = mapDataType(mActivePhone.getServiceState());
		return mDataPhoneType;
	}*/

	@Override
	public boolean getDataRoamingEnabled(){
	      logd("getDataRoamingEnabled: " + mActivePhone.getDataRoamingEnabled());
		return mActivePhone.getDataRoamingEnabled();
	}
	
	public GSMPhone getGsmPhone(){
		return mLtePhone;
	}
	
	@Override
	public String getIccSerialNumber(){
		logd("getIccSerialNumber()");
		//SelectActivePhone(0);
		return mLtePhone.getIccSerialNumber();
	}
	

	public IccSmsInterfaceManager getIccSmsInterfaceManager(){
		logd("getIccSmsInterfaceManager");
		if(isSMSFormat3GPP2()){
			return meCDMAPhone.getIccSmsInterfaceManager();
		}else{
			return mLtePhone.getIccSmsInterfaceManager();
		}
	}

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
	//mLtePhone.setNetworkSelectionModeAutomatic(response);
	mActivePhone.setNetworkSelectionModeAutomatic(response);
	//SelectActivePhone(0);
    }
	
	/*public int getNetworkSelectionMode(){
	    logd("getNetworkSelectionMode");
		int retMode = -1;
		retMode = SamsungChargeRIL.getCDMAVoiceRadioTechnology();
		/*setNetworkSelectionMode(mmsAp.getModeType());
		
		if(mNetworkSelectionMode.equals("LTE")){
			retMode = 8;
		}else if(mNetworkSelectionMode.equals("CDMA")){
			retMode = 9;
		}else if(mNetworkSelectionMode.equals("GLOBAL")){
			retMode = 7;
		}
		
		return retMode;
	}*/
	
	public int getRadioTechnology(){
		return mRadioTechnology;
	}
	@Override
	public void getSmscAddress(Message result){
		if(isSMSFormat3GPP2()){
			meCDMAPhone.getSmscAddress(result);
		}else{
			mLtePhone.getSmscAddress(result);
		}
	}
	/*@Override
	public String getSubscriberId(){
		logd("getSubsciberId()");
		return mLtePhone.getSubscriberId();
	}*/
    @Override
    public void setRadioPower(boolean power) {
	logd("setRadioPower");
        mActivePhone.setRadioPower(power);
	mLtePhone.setRadioPower(power);
    }

    @Override
    public boolean getIccRecordsLoaded() {
	logd("getIccRecordsLoaded");
	
        return mLtePhone.getIccRecordsLoaded();
    }

    @Override
    public IccCard getIccCard() {
	logd("getIccCard");
	//SelectActivePhone(0);
	return mLtePhone.getIccCard();
    }

    @Override
    public String getMsisdn() {
	logd("getMsisdn");
        return mLtePhone.getMsisdn();
    }

    @Override
    public IsimRecords getIsimRecords() {
	return mLtePhone.getIsimRecords();
    }

	
		
}	
	
	
	
	
	
