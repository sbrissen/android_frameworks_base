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
import android.telephony.SignalStrength;

import java.util.List;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

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
	private int oldBatteryLevel = -1;
	private int oldBatteryPlugStatus = -1;

    protected static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;
    protected static final int EVENT_SIM_RECORDS_LOADED                = 16;
    private static int EVENT_VIA_RESET_DONE = 0x24;
    private static int EVENT_LTE_RESET_DONE = 0x23;
    

	public MultiModePhoneProxy(Phone cdmaPhone, Phone ltePhone, Context context){
		super(cdmaPhone);

	  BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
	  @Override
	   public void onReceive(Context context, Intent intent) {
	      if (intent.getAction().equals("android.intent.action.BATTERY_CHANGED")) {
                sendBatteryInfo(intent);
	      }
	     }
	   };

	   /* PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
	      public void onDataConnectionStateChanged(int state, int networkType){
		int cdmaDataState = meCDMAPhone.getDataRegistrationState();
		int lteDataState = mLtePhone.getDataRegistrationState();
		logd("cdmaDataState: " + cdmaDataState + ", lteDataState: " + lteDataState);
		//if(lteDataState = 0){
	      }
	    };*/

		logd("MultiModePhoneProxy");		
		setLtePhone(ltePhone);
		mActivePhone = cdmaPhone;
		meCDMAPhone = (CDMAPhone)cdmaPhone;
		mLtePhone = (GSMPhone)ltePhone;     

		setActivePhone(mActivePhone);
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

	    IntentFilter filter = new IntentFilter();
	    filter.addAction("android.intent.action.BATTERY_CHANGED");
	    context.registerReceiver(mIntentReceiver,filter);
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
	
/*	public Phone getDataPhone(){
		if(mRadioTechnology == 0xd){
			return mLtePhone;
		}else{
			return meCDMAPhone;
		}
	}*/
	
/*	public int getDataPhoneType(){
		logd("getDataPhoneType");
		mDataPhoneType = mapDataType(mActivePhone.getServiceState());
		return mDataPhoneType;
	}*/

	@Override
	public boolean getDataRoamingEnabled(){
	      logd("getDataRoamingEnabled: " + mLtePhone.getDataRoamingEnabled());
		return mLtePhone.getDataRoamingEnabled();
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
	mLtePhone.setNetworkSelectionModeAutomatic(response);
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
/*    @Override
    public void setRadioPower(boolean power) {
	logd("setRadioPower");
        mActivePhone.setRadioPower(power);
	mLtePhone.setRadioPower(power);
    }*/

    @Override
    public boolean getIccRecordsLoaded() {
	logd("getIccRecordsLoaded");
	
        return mActivePhone.getIccRecordsLoaded();
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
	logd("getIsimRecords - mmpp");
	return mLtePhone.getIsimRecords();
    }

    private void sendBatteryInfo(Intent intent){
	int BatteryPlugStatus = intent.getIntExtra("plugged", 0);
	int BatteryLevel = intent.getIntExtra("level",100);
	boolean plugChanged = false;
	boolean isTaCharging = false;


	logd("BATTERY CHANGED - plugged: " + BatteryPlugStatus + ", level: " + BatteryLevel + ", old_level: " + oldBatteryLevel);

	if(oldBatteryPlugStatus != BatteryPlugStatus){
	    logd("sendBatteryInfo CHANGED!");
	    plugChanged = true;
	}
	if(plugChanged){
	  ByteArrayOutputStream bos = new ByteArrayOutputStream();
	  DataOutputStream dos = new DataOutputStream(bos);
	  if(BatteryPlugStatus == 1){
	    isTaCharging = true;
	  }
	  try{
	    dos.writeByte(23);
	    dos.writeByte(1);
	   // dos.writeByte(0);
	    dos.writeShort(5);
	    
	    if(isTaCharging){
	      dos.writeByte(1);
	    }else{
	      dos.writeByte(0);
	    }
	    mCommandsInterfaceCDMA.invokeOemRilRequestRaw(bos.toByteArray(),null);
	    dos.close();
	  }catch(IOException ioe){
	    logd("ioexception");
	  }
	}
	  ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
	  DataOutputStream dos2 = new DataOutputStream(bos2);
	  String string = "UNKNOWN";
	  int fileSize = (string.length() + 4) + 1;
	  int mainCmdPhone = 0x10;
	  int subCmdPhoneReset = 0x2;

	/*  try{
	    dos2.writeByte(mainCmdPhone);
	    dos2.writeByte(subCmdPhoneReset);
	    dos2.writeShort(fileSize);
	    dos2.writeBytes("UNKNOWN");
	    dos2.writeByte(0x0);
	   }catch(IOException ioe){
	      logd("ioexceptio3");
	   }
	    mCommandsInterfaceGSM.invokeOemRilRequestRaw(bos2.toByteArray(),this.obtainMessage(0x23));
	    mCommandsInterfaceCDMA.invokeOemRilRequestRaw(bos2.toByteArray(),this.obtainMessage(0x24));
	    
	*/
	if(oldBatteryLevel < 0){
	  oldBatteryLevel = BatteryLevel;
	}
	if(oldBatteryLevel < 5 || BatteryLevel >= 5){
	  ByteArrayOutputStream bos = new ByteArrayOutputStream();
	  DataOutputStream dos = new DataOutputStream(bos);
	  logd("Battery at normal level");
	  try{
	    dos.writeByte(23);
	    dos.writeByte(2);
	    dos.writeShort(5);
	    dos.writeByte(BatteryLevel);
	    mCommandsInterfaceGSM.invokeOemRilRequestRaw(bos.toByteArray(), null);
	    dos.close();
	  }catch(IOException ioe){
	    logd("ioexception2");
	  }
	}

	oldBatteryLevel = BatteryLevel;
	oldBatteryPlugStatus = BatteryPlugStatus;
      }
	  
@Override
    public DataState getDataConnectionState() {
	logd("sbrissen - MMPP - getDataConnectionState");
        return mLtePhone.getDataConnectionState(Phone.APN_TYPE_DEFAULT);
    }

@Override
    public DataState getDataConnectionState(String apnType) {
logd("sbrissen - MMPP - getDataConnectionState2");
        return mLtePhone.getDataConnectionState(apnType);
    }

@Override
    public DataActivityState getDataActivityState() {
logd("sbrissen - MMPP - getDataActivityState");
        return mLtePhone.getDataActivityState();
    }

@Override
    public String[] getActiveApnTypes() {
logd("sbrissen - MMPP - getActiveApnTypes");
        return mLtePhone.getActiveApnTypes();
    }

@Override
    public String getActiveApnHost(String apnType) {
logd("sbrissen - MMPP - getActiveApnHost");
        return mLtePhone.getActiveApnHost(apnType);
    }

@Override
    public LinkProperties getLinkProperties(String apnType) {
logd("sbrissen - MMPP - getLinkProperties");
        return mLtePhone.getLinkProperties(apnType);
    }

@Override
    public LinkCapabilities getLinkCapabilities(String apnType) {
logd("sbrissen - MMPP - getLinkCapabilities");
        return mLtePhone.getLinkCapabilities(apnType);	
    }
@Override
    public boolean isDataConnectivityPossible() {
	logd("sbrissen - MMPP - isDataPossible? ");
        return mLtePhone.isDataConnectivityPossible(Phone.APN_TYPE_DEFAULT);
    }
@Override
    public boolean isDataConnectivityPossible(String apnType) {
	logd("sbrissen - MMPP - isDataPossible2? ");
        return mLtePhone.isDataConnectivityPossible(apnType);
    }
		
}	
	
	
	
	
	
