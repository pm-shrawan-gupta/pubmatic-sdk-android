package com.adserver.adview;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.VideoView;

import com.adserver.adview.bridges.AdBridgeAbstract;
import com.adserver.adview.bridges.AdBridgeFactory;
import com.adserver.adview.ormma.OrmmaAssetController;
import com.adserver.adview.ormma.OrmmaDisplayController;
import com.adserver.adview.ormma.OrmmaLocationController;
import com.adserver.adview.ormma.OrmmaNetworkController;
import com.adserver.adview.ormma.OrmmaSensorController;
import com.adserver.adview.ormma.OrmmaUtilityController;
import com.adserver.adview.ormma.OrmmaController.Dimensions;
import com.adserver.adview.ormma.OrmmaController.PlayerProperties;
import com.adserver.adview.ormma.OrmmaController.Properties;
import com.adserver.adview.ormma.util.OrmmaPlayer;
import com.adserver.adview.ormma.util.OrmmaUtils;

/**
 * Viewer of advertising.
 */
public abstract class AdServerViewCore extends WebView {
    /**
     * Type of advertisement: text only.
	 */
	public static final int ADS_TYPE_TEXT_ONLY = 1;
	/**
     * Type of advertisement: image only.
	 */
	public static final int ADS_TYPE_IMAGES_ONLY = 2;
	/**
     * Type of advertisement: image and text.
	 */
	public static final int ADS_TYPE_TEXT_AND_IMAGES = 3;
	/**
     * Type of advertisement: SMS ad. SMS will be returned in XML.
	 */
	public static final int ADS_TYPE_SMS = 6;
	/**
	 * Premium type: premium and non-premium.
	 */
	public static final int PREMIUM_STATUS_BOTH = 2;
	/**
	 * Premium type: premium only.
	 */
	public static final int PREMIUM_STATUS_PREMIUM = 1;
	/**
	 * Premium type: non-premium.
	 */
	public static final int PREMIUM_STATUS_NON_PREMIUM = 0;
	/**
	 * Make server requests no matter if ad viewer is visible to user or not. Default case.
	 */
	public static final int VISIBLE_MODE_CASE1 = 1; 
	/**
	 * Make server requests and refresh enable only if ad viewer is visible to user.
	 */
	public static final int VISIBLE_MODE_CASE2 = 2; 
	/**
	 * Make server requests no matter if ad viewer is visible to user or not. However refresh is disabled to the ads that are not visible.
	 */
	public static final int VISIBLE_MODE_CASE3 = 3; 
	
	private static final String PREFS_FILE_NAME = "AdserverViewPrefs";
	private static final String PREF_IS_FIRST_APP_LAUNCH = "isFirstAppLaunch";
	private static final String FIRST_APP_LAUNCH_URL = "http://www.moceanmobile.com/appconversion.php";
	private static final long AD_RELOAD_PERIOD = 120000; //in milliseconds
	private static final long AD_STOP_CHECK_PERIOD = 10000; //in milliseconds
	private static final long AD_RELOAD_SHORT_PERIOD = 100; //in milliseconds
	Handler handler = new Handler(Looper.getMainLooper());
	private Integer defaultImageResource;
	protected AdserverRequest adserverRequest;
	private OnAdClickListener adClickListener;
	private Long adReloadPeriod;
	private Integer visibleMode;
	private Integer advertiserId; 
	private String groupCode;
	private OnAdDownload adDownload;
	
	protected Context _context;
	private LocationManager locationManager;
	private WhereamiLocationListener listener;	
	
	private static final int MESSAGE_RESIZE = 1000;
	private static final int MESSAGE_CLOSE = 1001;
	private static final int MESSAGE_HIDE = 1002;
	private static final int MESSAGE_SHOW = 1003;
	private static final int MESSAGE_EXPAND = 1004;
	private static final int MESSAGE_ANIMATE = 1005;
	private static final int MESSAGE_OPEN = 1006;
	private static final int MESSAGE_PLAY_VIDEO = 1007;
	private static final int MESSAGE_PLAY_AUDIO = 1008;
	private static final int MESSAGE_RAISE_ERROR = 1009;

	// layout constants
	protected static final int BACKGROUND_ID = 101;
	protected static final int PLACEHOLDER_ID = 100;
	public static final int ORMMA_ID = 102;
	
	private static final String EXPAND_DIMENSIONS = "exand_initial_dimensions";
	private static final String EXPAND_PROPERTIES = "expand_properties";
	private static final String RESIZE_WIDTH = "resize_width";
	private static final String RESIZE_HEIGHT = "resize_height";
	private static final String ERROR_MESSAGE = "message";
	private static final String ERROR_ACTION = "action";
	public static final String DIMENSIONS = "expand_dimensions";
	public static final String ACTION_KEY = "action";
	public static final String PLAYER_PROPERTIES = "player_properties";
	public static final String EXPAND_URL = "expand_url";
	public enum ACTION {PLAY_AUDIO, PLAY_VIDEO}
	private enum ViewState {DEFAULT, RESIZED, EXPANDED, HIDDEN}
	private OrmmaAssetController mAssetController;
	private OrmmaDisplayController mDisplayController;
	private OrmmaUtilityController mUtilityController;
	private OrmmaLocationController mLocationController;
	private OrmmaNetworkController mNetworkController;
	private OrmmaSensorController mSensorController;
	private ViewState mViewState = ViewState.DEFAULT;
	private AdServerViewCore mParentAd = null;
	private ViewGroup mExpandedFrame;
	public String mDataToInject = null;
	private static String mScriptPath = null;
	private String mContent;
	private HashSet<String> excampaigns = new HashSet<String>();
	
	AdLog adLog = new AdLog(this);
	Dialog dialog;
	private static OrmmaPlayer player;
	private WebView view;

	private boolean isFirstTime;
	ReloadTask reloadTask;
	private ContentThread contentThread;
	private Timer reloadTimer;
	private boolean IsManualUpdate = false;
	
	private String lastRequest;
	private String lastResponse;

	private boolean internalBrowser = false;

		
	/**
	 * Creation of viewer of advertising.
	 * @param context - The reference to the context of Activity.
	 * @param site - The id of the publisher site.
	 * @param zone - The id of the zone of publisher site.
	 */
	public AdServerViewCore(Context context, Integer site, Integer zone) {
		super(context);
		AutoDetectParameters(context);
		isFirstTime =true;
		loadContent(context, 
				null, null, null, null, 
				null,   
				site, zone, null, null,
				null, null, null, null, null,
				null, null, null, null, null);
	}
	
	/**
	 * Creation of viewer of advertising. It is used for element creation in a XML template.
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public AdServerViewCore(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		AutoDetectParameters(context);
		initialize(context, attrs);
	}

	/**
	 * Creation of viewer of advertising. It is used for element creation in a XML template.
	 * @param context
	 * @param attrs
	 */
	public AdServerViewCore(Context context, AttributeSet attrs) {
		super(context, attrs);
		AutoDetectParameters(context);
		initialize(context, attrs);
	}

	/**
	 * Creation of viewer of advertising. It is used for element creation in a XML template.
	 * @param context
	 */
	public AdServerViewCore(Context context) {
		super(context);
		AutoDetectParameters(context);
		initialize(context, null);
	}
	
	
	void AutoDetectParameters(Context context)
	{
		_context = context;
		if(adserverRequest==null) adserverRequest = new AdserverRequest(adLog);
	}
	
		/**
	 * Get interface for advertising opening.
	 */
	public OnAdClickListener getOnAdClickListener() {
		return adClickListener;
	}

	/**
	 * Set interface for advertising opening.
	 * @param adClickListener
	 */
	public void setOnAdClickListener(OnAdClickListener adClickListener) {
		this.adClickListener = adClickListener;
	}
	
	/**
	 * The interface for advertising opening in an internal browser.
	 */
	public interface OnAdClickListener {
		public void click(String url);
	}

	/**
	 * The interface for advertising downloading.
	 */
	public interface OnAdDownload {
		/**
		 * This event is fired before banner download begins. 
		 */
		public void begin();
		/**
		 * This event is fired after banner content fully downloaded. 
		 */
		public void end();
		/**
		 * This event is fired after fail to download content. 
		 */
		public void error(String error);
	}
	
	/**
	 * Get interface for advertising downloading.
	 */
	public OnAdDownload getOnAdDownload() {
		return adDownload;
	}

	/**
	 * Set interface for advertising downloading.
	 * @param adDownload
	 */
	public void setOnAdDownload(OnAdDownload adDownload) {
		this.adDownload = adDownload;
	}

	/**
	 * Optional.
	 * Get Custom Parameters.
	 * @param customParameters
	 */
	public Hashtable<String, String> getCustomParameters() {
		if(adserverRequest != null) {
			return adserverRequest.getCustomParameters();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set Custom Parameters.
	 * @param customParameters
	 */
	public void setCustomParameters(Hashtable<String, String> customParameters) {
		if(adserverRequest != null) {
			adserverRequest.setCustomParameters(customParameters);
		}
	}
	
	/**
	 * Optional.
	 * Get image resource which will be shown during advertising loading if there is no advertising in a cache.
	 */
	public Integer getDefaultImage() {
		return defaultImageResource==null ? 0 : defaultImageResource;
	}
	
	/**
	 * Optional.
	 * Set image resource which will be shown during advertising loading if there is no advertising in a cache.
	 */
	public void setDefaultImage(Integer defaultImage) {
		defaultImageResource = defaultImage;
	}

	public String GetLastRequest()
	{
		return lastRequest;
	}
	
	public String GetLastResponse()
	{
		return lastResponse;
	}
	
	/**
	 * Optional.
	 * Get Advertiser id (if both AdvertiserId and GroupCode are specified then install notification is enabled).
	 */
	public Integer getAdvertiserId() {
		return advertiserId==null ? 0 : advertiserId;
	}
	
	/**
	 * Optional.
	 * Set Advertiser id (if both AdvertiserId and GroupCode are specified then install notification is enabled).
	 */
	public void setAdvertiserId(Integer advertiserId) {
		if(advertiserId!=null)
		{
			if(advertiserId>0)
				this.advertiserId = advertiserId;
			else
				adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, AdserverRequest.INVALID_PARAM_TITLE,"advertiserId="+advertiserId.toString()+" valid>0");
		}
	}

	/**
	 * Optional.
	 * Get Group code (if both AdvertiserId and GroupCode are specified then install notification is enabled).
	 */
	public String getGroupCode() {
		return groupCode;
	}
	
	/**
	 * Optional.
	 * Set Group code (if both AdvertiserId and GroupCode are specified then install notification is enabled).
	 */
	public void setGroupCode(String groupCode) {
		this.groupCode = groupCode;
	}

	/**
	 * Optional.
	 * Get banner refresh interval (in seconds).
	 */
	public Integer getUpdateTime() {
		if(adReloadPeriod != null) {
			return new Long(adReloadPeriod/1000).intValue();
		} else {
			return new Long(AD_RELOAD_PERIOD/1000).intValue();
		}
	}

	/**
	 * Optional.
	 * Set banner refresh interval (in seconds).
	 */
	public void setUpdateTime(Integer updateTime) {
		if(updateTime != null) {
			adLog.log(AdLog.LOG_LEVEL_2, AdLog.LOG_TYPE_INFO, "setUpdateTime", String.valueOf(updateTime));
			this.adReloadPeriod = new Long(updateTime*1000); //in milliseconds
			update(false);
		}
	}
	
	private void initialize(Context context, AttributeSet attrs) {
		if(attrs != null) {
			isFirstTime =true;
			Integer logLevel = getIntParameter(attrs.getAttributeValue(null, "logLevel"));
			if(logLevel!=null) setLogLevel(logLevel);
			
			Integer site = getIntParameter(attrs.getAttributeValue(null, "site"));
			Integer zone = getIntParameter(attrs.getAttributeValue(null, "zone"));
			Boolean isTestModeEnabled = getBooleanParameter(attrs.getAttributeValue(null, "test"));
			Integer premium = attrs.getAttributeIntValue(null, "premium",PREMIUM_STATUS_BOTH); 
			String keywords = attrs.getAttributeValue(null, "keywords");
			Integer adsType = attrs.getAttributeIntValue(null, "adsType",ADS_TYPE_TEXT_AND_IMAGES);
			Integer minSizeX = getIntParameter(attrs.getAttributeValue(null, "minSizeX"));
			Integer minSizeY = getIntParameter(attrs.getAttributeValue(null, "minSizeY"));
			Integer maxSizeX = getIntParameter(attrs.getAttributeValue(null, "maxSizeX"));
			Integer maxSizeY = getIntParameter(attrs.getAttributeValue(null, "maxSizeY"));
			Integer paramBG = GetColor(attrs.getAttributeValue(null, "backgroundColor"));
			
			Boolean locationDetection= getBooleanParameter(attrs.getAttributeValue(null, "locationDetection"));
			Boolean internelBr = getBooleanParameter(attrs.getAttributeValue(null, "internalBrowser")); 
		
			Integer textColor = GetColor(attrs.getAttributeValue(null, "textColor"));
			String adserverURL = attrs.getAttributeValue(null, "adserverURL");
			
			String image = attrs.getAttributeValue(null, "defaultImage");
			Integer defaultImage=null;
			if(image!=null)
				defaultImage = context.getResources().getIdentifier(image, null, context.getPackageName());
			//Integer defaultImage = getIntParameter(attrs.getAttributeValue(null, "defaultImage"));
			
			this.advertiserId = getIntParameter(attrs.getAttributeValue(null, "advertiserId"));
			this.groupCode = attrs.getAttributeValue(null, "groupCode");
			setUpdateTime(getIntParameter(attrs.getAttributeValue(null, "updateTime")));
			
			String latitude = attrs.getAttributeValue(null, "latitude");
			String longitude = attrs.getAttributeValue(null, "longitude");
			String country = attrs.getAttributeValue(null, "country");
			String region = attrs.getAttributeValue(null, "region");
			String city = attrs.getAttributeValue(null, "city");
			String area = attrs.getAttributeValue(null, "area");
			String metro = attrs.getAttributeValue(null, "metro");
			String zip = attrs.getAttributeValue(null, "zip");
			String carrier = attrs.getAttributeValue(null, "carrier");
			//String customParameters = attrs.getAttributeValue(null, "customParameters");
			
			
			
			String ua = attrs.getAttributeValue(null, "ua");
			//Integer defaultImage = attrs.getAttributeResourceValue(null, "defaultImage", -1); 
			//Integer sizeX = getIntParameter(attrs.getAttributeValue(null, "sizeX"));
			//Integer sizeY = getIntParameter(attrs.getAttributeValue(null, "sizeY"));
			Integer visibleMode = getIntParameter(attrs.getAttributeValue(null, "visibleMode"));
			
			if(visibleMode != null) {
				this.visibleMode = visibleMode;
			}
			
			if(adserverRequest==null) adserverRequest = new AdserverRequest(adLog);
			if(adsType!=null) setAdsType(adsType);			
			//if(textColor!=null) setTextColor(textColor);
			if(adserverURL!=null) setAdserverURL(adserverURL);
			if(city!=null)setCity(city);
			if(area!=null)setArea(area);
			if(metro!=null)setMetro(metro);
			if(zip!=null)setZip(zip);
			if(locationDetection!=null) setLocationDetection(locationDetection);
			if(internelBr != null) setInternalBrowser(internelBr);
			
			loadContent(context, 
					minSizeX, minSizeY, maxSizeX, maxSizeY, 
					defaultImage,   
					site, zone, keywords, latitude,
					longitude, ua, premium, isTestModeEnabled, country,
					region, paramBG, textColor, carrier,
					null);
			
			
			
		}else loadContent(context, 
				null, null, null, null, 
				null,   
				null, null, null, null,
				null, null, null, null, null,
				null, null, null, null,
				null);
	}
	
	private Integer GetColor(String colorString)
	{
		if(colorString==null) return null;
		int a,r,g,b;
		
		int length = colorString.length();
		if((length>0)&&(colorString.charAt(0)=='#'))
		{
			switch(length)
			{
			case 4:	
				a=255;
				r= Integer.decode("#"+colorString.substring(1,2))*17;
				g= Integer.decode("#"+colorString.substring(2,3))*17;
				b= Integer.decode("#"+colorString.substring(3,4))*17;
				break;
			case 5:
				a= Integer.decode("#"+colorString.substring(1,2))*17;
				r= Integer.decode("#"+colorString.substring(2,3))*17;
				g= Integer.decode("#"+colorString.substring(3,4))*17;
				b= Integer.decode("#"+colorString.substring(4,5))*17;
				break;
			case 7: case 9:
				return Color.parseColor(colorString);				
			default: return Constants.DEFAULT_COLOR;	
			}
			
			return Color.argb(a, r, g, b);
		}else return Constants.DEFAULT_COLOR;		
	}
	
	private void loadContent(Context context,
			Integer minSizeX, Integer minSizeY, Integer sizeX, Integer sizeY, 
			Integer defaultImage,   
			Integer site, Integer zone, 
			String keywords, String latitude,
			String longitude, String ua, Integer premium,
			Boolean isTestModeEnabled, String country,
			String region, 
			Integer paramBG, Integer paramLINK, String carrier, 
			Hashtable<String, String> customParameters) {
		if(adserverRequest==null) adserverRequest = new AdserverRequest(adLog);
		adserverRequest.InitDefaultParameters(context);
		adserverRequest.setUa(ua);
		adserverRequest.setCount(1);
		adserverRequest.setSizeRequired(0);
		setSite(site);
		setKeywords(keywords);
		setPremium(premium);
		setZone(zone);
		setTest(isTestModeEnabled);
		setCountry(country);
		setRegion(region);
		setAdsType(getAdsType());
		setLatitude(latitude);
		setLongitude(longitude);
		if(paramBG!=null)setBackgroundColor(paramBG);
		if(paramLINK!=null)setTextColor(paramLINK);
		setCarrier(carrier);
		setMinSizeX(minSizeX);
		setMinSizeY(minSizeY);
		setMaxSizeX(sizeX);
		setMaxSizeY(sizeY);		
		setCustomParameters(customParameters);		
		
		defaultImageResource = defaultImage;
		
		WebSettings webSettings = getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(true);
		webSettings.setSupportZoom(false);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		
		mAssetController = new OrmmaAssetController(this, context);
		mDisplayController = new OrmmaDisplayController(this, context);
		mUtilityController = new OrmmaUtilityController(this, context);
		mLocationController = new OrmmaLocationController(this, context);
		mNetworkController = new OrmmaNetworkController(this, context);
		mSensorController = new OrmmaSensorController(this, context);
		
		addJavascriptInterface(mDisplayController, "ORMMADisplayControllerBridge");
		addJavascriptInterface(mUtilityController, "ORMMAUtilityControllerBridge");
		addJavascriptInterface(mLocationController, "ORMMALocationControllerBridge");
		addJavascriptInterface(mNetworkController, "ORMMANetworkControllerBridge");
		addJavascriptInterface(mSensorController, "ORMMASensorControllerBridge");
		addJavascriptInterface(mAssetController, "ORMMAAssetsControllerBridge");
		
		setScriptPath();
		//mScriptPath ="/data/data/checkpoint.forms/files/OrmmaAdController.js";
		
		setWebViewClient(new AdWebViewClient(context));
		setWebChromeClient(mWebChromeClient);
		
		StartLoadContent(getContext(), this);
		/*if(contentThread == null) {
			contentThread = new ContentThread(context, this, true, true);
		}*/
	}
	
	@Override
	protected void onAttachedToWindow() {
		reloadTimer = new Timer();
		
		StartLoadContent(getContext(), this);
		
		/*if(contentThread != null) {
			if(contentThread.getState().equals(Thread.State.NEW)) {
				contentThread.start();			
			} else if(contentThread.getState().equals(Thread.State.TERMINATED)) {
				contentThread = new ContentThread(getContext(), this, false, true);
				contentThread.start();			
			}
		}*/
		
		super.onAttachedToWindow();
	}
	
	@Override
	protected void onDetachedFromWindow() {
//		loadDataWithBaseURL(null, "", "text/html", "UTF-8", null);	
		
		if((locationManager != null) && (listener != null)) {
			locationManager.removeUpdates(listener);
		}
		
		removeAllViews();
		if(reloadTimer != null) {
			try {
				reloadTimer.cancel();
				reloadTimer = null;
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "onDetachedFromWindow", e.getMessage());				
			}
		}
		
		if(contentThread != null) {
			try {
				contentThread.interrupt();
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "onDetachedFromWindow", e.getMessage());
			}
		}

		if(mNetworkController != null) {
			mNetworkController.stopAllNetworkListeners();
		}
		
		if(mDisplayController != null) {
			mDisplayController.stopAllOrientationListeners();
		}
		
		super.onDetachedFromWindow();
	}

	/**
	 * Immediately update banner contents.
	 */
	public void update()
	{
		update(true);
	}
	
	void update(boolean isManual) {
		if(isShown() || isManual) 
		{
			adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "update", "");
			if(isManual) IsManualUpdate = true;
			StartLoadContent(getContext(), this);
			//ContentThread updateContentThread = new ContentThread(getContext(), this, false, false);
			//updateContentThread.start();			
		}
	}
	
	private class InstallNotificationThread extends Thread {
		private Context context;
		private Integer advertiserId;
		private String groupCode;

		public InstallNotificationThread(Context context, Integer advertiserId, String groupCode) {
			this.context = context;
			this.advertiserId = advertiserId;
			this.groupCode = groupCode;
		}

		@Override
		public synchronized void run() {
			try {
				if(context != null) {
					if((advertiserId != null) && (advertiserId > 0)
							&& (groupCode != null) && (groupCode.length() > 0)) {
						SharedPreferences settings = context.getSharedPreferences(PREFS_FILE_NAME, 0);
						boolean isFirstAppLaunch = settings.getBoolean(PREF_IS_FIRST_APP_LAUNCH, true);

						if(isFirstAppLaunch) {
							String deviceId;
							TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
							String temp = tm.getDeviceId();
							if (null !=  temp) deviceId = temp;
							else {
								temp = Secure.getString(context.getContentResolver(),Secure.ANDROID_ID); ;
								if (null != temp) deviceId = temp;
								else deviceId = "";
							}
							
							String deviceIdMD5 = Utils.md5(deviceId);
							
							if((deviceIdMD5 != null) && (deviceIdMD5.length() > 0)) {
								StringBuilder url = new StringBuilder(FIRST_APP_LAUNCH_URL);
								url.append("?advertiser_id=" + advertiserId.toString());
								url.append("&group_code=" + URLEncoder.encode(groupCode));
								url.append("&"+AdserverRequest.parameter_device_id+"=" + URLEncoder.encode(deviceIdMD5));
								
								sendGetRequest(url.toString());
								
								SharedPreferences.Editor editor = settings.edit();
								editor.putBoolean(PREF_IS_FIRST_APP_LAUNCH, false);
								editor.commit();
							}
						}
					}
				}
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "InstallNotificationThread", e.getMessage());
			}
		}
	}
	
	private class ContentThread extends Thread {
		private Context context;

		public ContentThread(Context context, WebView webView) {
			this.context = context;
//			this.view = webView;
			view = webView;
		}

		@Override
		public void run() {
			_contentThreadAction(context, view);
		}
	}
	
	void StartLoadContent(Context context, WebView view)
	{
		if((contentThread==null) || (contentThread.getState().equals(Thread.State.TERMINATED)))
		{
			contentThread = new ContentThread(getContext(), this);
			contentThread.start();
		} else if(contentThread.getState().equals(Thread.State.NEW))
			contentThread.start();
		else
			StartTimer(context, view);
	}
	
	private void StartTimer(Context context, WebView view)
	{
		{	
			try
			{
				if(reloadTimer== null) return;
				
				if(reloadTask!=null)
				{
					reloadTask.cancel();
					reloadTask = null;
				}
				
				ReloadTask newReloadTask = new ReloadTask(context, view);
				
				if(IsManualUpdate)
				{
					adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "Manual Update, StartTimer", String.valueOf(AD_RELOAD_SHORT_PERIOD/1000));
					reloadTimer.schedule(newReloadTask, AD_RELOAD_SHORT_PERIOD);
					reloadTask = newReloadTask;
					return;
				}
				
				if((adReloadPeriod != null) && (adReloadPeriod >= 0)) {
					if(adReloadPeriod > 0) {
						adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", String.valueOf(adReloadPeriod/1000));					
						reloadTimer.schedule(newReloadTask, adReloadPeriod);
					} else {
						//adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", String.valueOf(AD_STOP_CHECK_PERIOD/1000)+" idle");					
						//reloadTimer.schedule(reloadTask, AD_STOP_CHECK_PERIOD);
						adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", "stopped");
					}
				} else {
					reloadTimer.schedule(newReloadTask, AD_RELOAD_PERIOD);
					adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", String.valueOf(AD_RELOAD_PERIOD/1000)+" default");
				}
				
				reloadTask = newReloadTask; 
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_3,AdLog.LOG_TYPE_ERROR,"StartTimer",e.getMessage());
			}
			/*ReloadTask reloadTask = new ReloadTask(context, view);
			
			if((adReloadPeriod != null) && (adReloadPeriod >= 0)) {
				if(adReloadPeriod > 0) {
					adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", String.valueOf(adReloadPeriod));					
					reloadTimer.schedule(reloadTask, adReloadPeriod);
				} else {
					adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", String.valueOf(AD_STOP_CHECK_PERIOD)+" idle");					
					reloadTimer.schedule(reloadTask, AD_STOP_CHECK_PERIOD);
				}
			} else {
				reloadTimer.schedule(reloadTask, AD_RELOAD_PERIOD);
				adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "StartTimer", String.valueOf(AD_RELOAD_PERIOD)+" default");
			}*/
		}
	}
		
	private void _contentThreadAction(Context context, WebView view) {
		InstallNotificationThread installNotificationThread = 
			new InstallNotificationThread(context, advertiserId, groupCode);
		installNotificationThread.start();

		setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

		boolean isRequestAd, isRefreshAd;
		//PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		//boolean isScreenOn = pm.isScreenOn();
		
		adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "contentThreadAction", "");
		
		boolean isShownView = view.isShown() || isFirstTime || IsManualUpdate;//&&isScreenOn;
		
		IsManualUpdate = false;		
		
		if((getSite()==0) || (getZone()==0))
		{
			StartTimer(context,view);
			adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_WARNING, "contentThreadAction", "site=0 or zone=0");
			return;
		}
		
		if(visibleMode == null) {
			visibleMode = VISIBLE_MODE_CASE2;
		}
		
    	switch (visibleMode) {
		case VISIBLE_MODE_CASE1:
			isRequestAd = true;
			isRefreshAd = true;
			break;
		case VISIBLE_MODE_CASE2:
			isRequestAd = isShownView;
			isRefreshAd = isShownView;
			break;
		case VISIBLE_MODE_CASE3:
			isRequestAd = true;
			isRefreshAd = isShownView;
			break;
		default:
			isRequestAd = true;
			isRefreshAd = true;
			break;
		}
		
		/*if((adReloadPeriod != null) && (adReloadPeriod == 0)) {
    		isRequestAd = false;
			isRefreshAd = false;			
		}*/
    	
		String data = "";
		
		if(isRefreshAd) {
			if(isFirstTime) {
				try {
					handler.post(new SetBackgroundResourceAction(view, defaultImageResource));
				} catch (Exception e) {
					adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "contentThreadAction", e.getMessage());
				}
			}
		}
		
		InterceptOnAdDownload interceptOnAdDownload = new InterceptOnAdDownload(context,view);
		
		if(isRequestAd || isFirstTime) {
			try {
				if(mViewState != ViewState.EXPANDED) {
					if(adserverRequest != null) {
						interceptOnAdDownload.begin();
						
						adserverRequest.setExcampaigns(getExcampaignsString());
						data = requestGet(adserverRequest.createURL());						
					}
				}
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "contentThreadAction.requestGet", e.getMessage());
				interceptOnAdDownload.error(e.getMessage());				
			}
		}
		
		try {
			if((data != null) && (data.length() > 0)) {
				String dataToLowercase = data.toLowerCase();
				if (dataToLowercase.contains("invalid params"))
				{	
					InterstitialClose();
					StartTimer(context,view);
					if(adDownload!= null) adDownload.error("invalid params");
				}else
				/*if(data.startsWith("<!--") && data.endsWith("-->")) {
					if(adDownload != null) {
						adDownload.error(Utils.scrape(data, "<!--", "-->"));
					}
				} else*/ {
					if(isRefreshAd || isFirstTime) {
						//handler.post(new RemoveAllChildViews(view));
						String externalCampaignData = Utils.scrapeIgnoreCase(data, "<external_campaign", "</external_campaign>");
	
						if((externalCampaignData != null) && (externalCampaignData.length() > 0)) {
							String type = Utils.scrapeIgnoreCase(externalCampaignData, "<type>", "</type>");
							String campaignId = Utils.scrapeIgnoreCase(externalCampaignData, "<campaign_id>", "</campaign_id>");
							String trackUrl = Utils.scrapeIgnoreCase(externalCampaignData, "<track_url>", "</track_url>");
							String externalParams = Utils.scrapeIgnoreCase(externalCampaignData, "<external_params>", "</external_params>");
							interceptOnAdDownload.SetCampaingId(campaignId);
							
							AdBridgeAbstract currentBrige = AdBridgeFactory.CreateBridge(context,view,adLog,campaignId,type, externalParams,trackUrl);
							if(currentBrige != null)
							{		
								currentBrige.OnAdDownload(interceptOnAdDownload);
								currentBrige.OnAdClickListener(adClickListener);
								handler.post(currentBrige);
							} else
							{
								RestartExcampaings(campaignId,context,view);
							}
						} else {
							handler.post(new RemoveAllChildViews(view));
							String videoData = Utils.scrapeIgnoreCase(data, "<video", "/>");
							//StartTimer(context, view, false, isRepeat);
							
							if((videoData != null) && (videoData.length() > 0)) {
								String videoUrl = Utils.scrapeIgnoreCase(videoData, "src=\"", "\"");
								String clickUrl = Utils.scrapeIgnoreCase(data, "href=\"", "\"");
								handler.post(new SetupVideoAction(context, view, videoUrl, clickUrl));
								StartTimer(context,view);
							} else {
								
								data = "<html><head>" +
								"<style>*{margin:0;padding:0}</style>"+
								"<script src=\"file://" + mScriptPath + "\" type=\"text/javascript\"></script>" +
								"</head>" +
								"<body style=\"background-color:#"+getBackgroundColor()+";margin: 0px; padding: 0px; width: 100%; height: 100%\"><table height=\"100%\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td style=\"text-align:center;vertical-align:middle;\">" + data + "</td></tr></table></body></html>";
								
								mContent = data;
								view.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
								StartTimer(context,view);
							}							
						}
					}
				}
			}else
			{
				adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_WARNING, "contentThreadAction", "data = null isShownView="+Boolean.toString(isShownView));
				
				if(isShownView) InterstitialClose();
				StartTimer(context,view);
			}
		} catch (Exception e) {
			adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "contentThreadAction", e.getMessage());
			StartTimer(context,view);
		}
		isFirstTime = false;

    	/*try {
    		Thread.sleep(10);
		} catch (Exception e) {
			return;
		}*/
		
		//StartTimer(context, view, false, isRepeat);
	}
	
	void InterstitialClose()
	{
		
	}
	
	private void RestartExcampaings(String campaignId,Context context, WebView view)
	{
		adLog.log(AdLog.LOG_LEVEL_2, AdLog.LOG_TYPE_WARNING, "RestartExcampaings", campaignId);
		if (excampaigns.contains(campaignId))
			StartTimer(context, view);
		else
		{
			excampaigns.add(campaignId);
			update(false);
		}
	}
	
	private class InterceptOnAdDownload implements OnAdDownload
	{
		private Context context;
		private WebView view;
		String campaignId = null;
		int childCount;
		
		public InterceptOnAdDownload(Context context, WebView view) {
			this.context = context;
			this.view = view;
			this.childCount = view.getChildCount();					
		}
		
		public void SetCampaingId(String campaignId)
		{
			this.campaignId = campaignId;
		}
		
		
		@Override
		public void begin() {
			if(adDownload!= null) adDownload.begin();			
		}

		@Override
		public void end() {
			view.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null);
			handler.post(new RemoveChildsView(view,childCount,true));
			StartTimer(context, view);			
			if(adDownload!= null) adDownload.end();
		}

		@Override
		public void error(String error) {
			handler.post(new RemoveChildsView(view,childCount,false));
			
			if(campaignId != null)
				RestartExcampaings(campaignId,context, view);
			else StartTimer(context, view);
			if(adDownload!= null) adDownload.error(error);			
		}
		
	}

	private String getExcampaignsString() {
		StringBuilder result = new StringBuilder();
		
		for (Iterator<String> iterator = excampaigns.iterator(); iterator.hasNext();) {
			String id = (String) iterator.next();
			result.append(id);
			result.append(",");
		}
		
		if(excampaigns.size() > 0) {
			result.deleteCharAt(result.length()-1);
		}
		
		return result.toString();
	}
	
	private class RemoveChildsView implements Runnable {
		private ViewGroup view;
		private int beforChildCount;
		private boolean removeOld;
		
		public RemoveChildsView(ViewGroup view, int beforChildCount, boolean removeOld) {
			this.view = view;
			this.beforChildCount = beforChildCount;
			this.removeOld =removeOld;
		}

		@Override
		public void run() {
			try {
				if(removeOld)
				{	
					for(int x=0;x<beforChildCount;x++)
						AdBridgeFactory.DeinitObject(view.getChildAt(x));
					view.removeViews(0, beforChildCount);					
				}
				else
				{
					for(int x=beforChildCount;x<view.getChildCount();x++)
						AdBridgeFactory.DeinitObject(view.getChildAt(x));
					view.removeViews(beforChildCount, view.getChildCount() - beforChildCount);
				}
				
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "RemoveChildsView", e.getMessage());
			}
		}
	}	
	
	private class RemoveAllChildViews implements Runnable {
		private ViewGroup view;
		
		public RemoveAllChildViews(ViewGroup view) {
			this.view = view;
		}

		@Override
		public void run() {
			try {
				view.removeAllViews();
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "RemoveAllChildViews", e.getMessage());
			}
		}
	}	
	
	private class SetupVideoAction implements Runnable {
		private Context context;
		private WebView view;
		private String url;
		private String clickUrl;
		
		public SetupVideoAction(Context context, WebView view, String url,
				String clickUrl) {
			this.context = context;
			this.view = view;
			this.url = url;
			this.clickUrl = clickUrl;			
		}

		@Override
		public void run() {
			try {
				if((url != null) && (url.length() > 0)) {
					VideoView videoView = new VideoView(context);
					videoView.setLayoutParams(view.getLayoutParams());
					videoView.setMediaController(new MediaController(context));
					Uri video = Uri.parse(url);
					videoView.setVideoURI(video);
					
					videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
		                public void onCompletion(MediaPlayer mp) {
	                        try {
	                        	mp.seekTo(0);
	                        	mp.start();
	                        } catch(Exception e){
	                        	adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "SetupVideoAction", e.getMessage());
	                        }
		                }
					});
					
					videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
						
						@Override
						public boolean onError(MediaPlayer mp, int what, int extra) {							
							adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "Play video",
									"what="+String.valueOf(what)+";extra="+String.valueOf(extra) );
							return true;
						}
					});
					
					if((clickUrl != null) && (clickUrl.length() > 0)) {
						videoView.setOnClickListener(new View.OnClickListener(){
							@Override
							public void onClick(View v) {
								openUrlInExternalBrowser(context, clickUrl);
							}
						});
						videoView.setOnTouchListener(new View.OnTouchListener(){
							@Override
							public boolean onTouch(View v, MotionEvent event) {
								if(event.getAction() == MotionEvent.ACTION_DOWN) {
									openUrlInExternalBrowser(context, clickUrl);
								}
								return false;
							}
						});
					}
					
					view.addView(videoView);
					videoView.start();
					
					view.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null);
				}
			} catch (Exception e) {
			}
		}
	}

	private class AdWebViewClient extends WebViewClient {
		private Context context;
		
		public AdWebViewClient(Context context) {
			this.context = context;
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			try
			{
				adLog.log(AdLog.LOG_LEVEL_2,AdLog.LOG_TYPE_INFO,"OverrideUrlLoading",url);
				if(adClickListener != null) {
						adClickListener.click(url);
				}else {
			    	int isAccessNetworkState = context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE);
			    	
			    	if(isAccessNetworkState == PackageManager.PERMISSION_GRANTED) {
						if(isInternetAvailable(context)) {
							openUrlInExternalBrowser(context, url);
						} else {
		    				Toast.makeText(context, "Internet is not available", Toast.LENGTH_LONG).show();
						}
			    	} else if(isAccessNetworkState == PackageManager.PERMISSION_DENIED) {
						openUrlInExternalBrowser(context, url);
			    	}
				}
			}catch(Exception e){
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "shouldOverrideUrlLoading", e.getMessage());
			}
			
			return true;
		}
		
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);

			if(mDisplayController != null) {
				mDisplayController.setDefaultPosition();
			}
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			((AdServerViewCore) view).onPageFinished();
			
			if(adDownload != null) {
				adDownload.end();
			}
		}

		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			if(adDownload != null) {
				adDownload.error(description);
			}
		}
	}
	
	WebChromeClient mWebChromeClient = new WebChromeClient() {
		@Override
		public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
			return super.onJsAlert(view, url, message, result);
		}
	};
	
	private class ReloadTask extends TimerTask {
		private Context context;
		private WebView view;
		
		public ReloadTask(Context context, WebView view) {
			this.context = context;
			this.view = view;			
		}

		@Override
		public void run() {
			StartLoadContent(context, view);
			//contentThreadAction(context, view);
		}
	}
	
	private class SetBackgroundResourceAction implements Runnable {
		private WebView view;
		private Integer backgroundResource;
		
		public SetBackgroundResourceAction(WebView view, Integer backgroundResource) {
			this.view = view;
			this.backgroundResource = backgroundResource;
		}
		
		@Override
		public void run() {
			try {
				if(backgroundResource != null) {
					view.setBackgroundResource(backgroundResource);
					view.setBackgroundColor(0); 
				}
			} catch (Exception e) {
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "SetBackgroundResourceAction", e.getMessage());
			}
		}
	}	

	private void openUrlInExternalBrowser(Context context, String url) {
		
		if(url==null) return;
			String lastUrl = null;
			String newUrl =  url;
			URL connectURL;
			while(!newUrl.equals(lastUrl) )
			{
				lastUrl = newUrl;
				try {
					connectURL = new URL(url);
					HttpURLConnection conn = (HttpURLConnection)connectURL.openConnection();
					newUrl = conn.getHeaderField("Location");
					if(newUrl==null)newUrl=conn.getURL().toString();
				} catch (Exception e) {
					newUrl = lastUrl;
				}				
			}
			
			if(newUrl==null) newUrl = url;
			
			Uri uri = Uri.parse(newUrl);
			if(internalBrowser && (uri.getScheme().equals("http") || uri.getScheme().equals("https")))
			{
				try
				{
					new InternelBrowser(context,url).show();
				}catch (Exception e) {
					adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "openUrlInInternalBrowser", e.getMessage());
				}
			}
			else
			{
				try
				{
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(newUrl));
					context.startActivity(intent);
				} catch (Exception e) {
					adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "openUrlInExternalBrowser","url="+ newUrl+"; error="+e.getMessage());
				}
			}		
	}
	
	private boolean isInternetAvailable(Context context) {
    	boolean result = false;
    	ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    	NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
    	
		if ((networkInfo != null) && (networkInfo.isAvailable())) {
			result = true ;
		}
    	
    	return result;
    }
	
	/*private String replaceImages(String source) {
		StringBuffer newImg = new StringBuffer();
        Pattern imgPattern = Pattern.compile("<img([^>]*[^/])", Pattern.CASE_INSENSITIVE);
        Matcher imgMatcher = imgPattern.matcher(source);
    	String srcData;
        String replacementImgTag;
    	Pattern srcPattern = Pattern.compile("(src\\s*=\\s*(\"|').*?(\"|'))|(src\\s*=.*?\\s)", Pattern.CASE_INSENSITIVE);
    	Matcher srcMatcher;
        imgMatcher.reset();
        StringBuffer newSrc;
        while (imgMatcher.find()) {
	        replacementImgTag = imgMatcher.group();
	        newSrc = new StringBuffer();
			srcMatcher = srcPattern.matcher(replacementImgTag);

			while (srcMatcher.find()) {
				srcData = srcMatcher.group().replaceAll("src", "");
	        	srcData = srcData.replaceAll("\"", "");
	        	srcData = srcData.replaceAll("'", "");
	        	srcData = srcData.replaceAll("=", "");
	        	srcData = srcData.trim();

	        	String imageEncodeData = "";
	        	
	        	try {
		        	byte[] imageBytes = readImageContent(srcData); 
	        		char[] chars = Base64.encode(imageBytes);
	        		imageEncodeData = new String(chars);
				} catch (Exception e) {
					imageEncodeData = "";
					adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "replaceImages", e.getMessage());
				}
				
        		srcMatcher.appendReplacement(newSrc, " src=\"data:image/*;base64," + imageEncodeData + "\" ");
        		srcData = null;
	        }
			
	        replacementImgTag = srcMatcher.appendTail(newSrc).toString();
	        imgMatcher.appendReplacement(newImg, replacementImgTag);
	        srcMatcher = null;
	        newSrc = null;
	        replacementImgTag = null;
        }
        imgMatcher.appendTail(newImg);
        imgMatcher = null;
		return newImg.toString();
	}

	private static byte[] readImageContent(String url) throws IOException {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		HttpResponse response = client.execute(get);
		HttpEntity entity = response.getEntity();
		InputStream inputStream = entity.getContent();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
	    byte[] buffer = new byte[8192];
		int bytesRead = -1;
		
		while((bytesRead = inputStream.read(buffer)) > -1) {
		      out.write(buffer, 0, bytesRead);
		}
		
		inputStream.close();
		byte[] imageBytes = out.toByteArray();		
		return imageBytes;
	}*/
	
	private static void sendGetRequest(String url) throws IOException {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		client.execute(get);
	}
	
	static int RequestCounter = 0;
	
	private String requestGet(String url) throws IOException {
		lastRequest = url;
		
		RequestCounter++;
		int rcounterLocal = RequestCounter;
		
		adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "requestGet["+String.valueOf(rcounterLocal)+"]" , url);
		
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		HttpResponse response = client.execute(get);
		HttpEntity entity = response.getEntity();
		InputStream inputStream = entity.getContent();
		BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream,8192);
		String responseValue = readInputStream(bufferedInputStream);
		bufferedInputStream.close();
		inputStream.close();
		adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "requestGet result["+String.valueOf(rcounterLocal)+"]", responseValue);
		lastResponse=responseValue;
		return responseValue;
	}
	
	private static String readInputStream(BufferedInputStream in) throws IOException {
	    StringBuffer out = new StringBuffer();
	    byte[] buffer = new byte[8192];
	    for (int n; (n = in.read(buffer)) != -1;) {
	        out.append(new String(buffer, 0, n));
	    }
	    return out.toString();
	}
	
	Integer getIntParameter(String stringValue) {
		if(stringValue != null) {
			try
			{
				//long i = Long.decode(stringValue);
				
				return (int) Long.decode(stringValue).longValue();
			}catch (Exception e) {
				return null;
			}
		} else {
			return null;
		}
	}

	/*private static Long getLongParameter(String stringValue) {
		if(stringValue != null) {
			return Long.parseLong(stringValue);
		} else {
			return null;
		}
	}*/
	
	private static Boolean getBooleanParameter(String stringValue) {
		if(stringValue != null) {
			return Boolean.parseBoolean(stringValue);
		} else {
			return null;
		}
	}

	public void injectJavaScript(String str) {
		super.loadUrl("javascript:" + str);
	}

	public String getState(){
		return mViewState.toString().toLowerCase();
	}

	private Handler mHandler = new Handler() {
		private int mOldHeight;
		private int mOldWidth;

		@Override
		public void handleMessage(Message msg) {
			Bundle data = msg.getData();
			switch (msg.what) {
				case MESSAGE_RESIZE: {
					mViewState = ViewState.RESIZED;
					ViewGroup.LayoutParams lp = getLayoutParams();
					mOldHeight = lp.height;
					mOldWidth = lp.width;
					lp.height = data.getInt(RESIZE_HEIGHT, lp.height);
					lp.width = data.getInt(RESIZE_WIDTH, lp.width);
					requestLayout();
					break;
				}
				case MESSAGE_CLOSE: {
					switch (mViewState) {
					case RESIZED:
						ViewGroup.LayoutParams lp = getLayoutParams();
						lp.height = mOldHeight;
						lp.width = mOldWidth;
						requestLayout();
						mViewState = ViewState.DEFAULT;
						break;
					case EXPANDED:
						if(mExpandedFrame != null) {
							closeExpanded(mExpandedFrame);
							mViewState = ViewState.DEFAULT;
						}
						break;
					}
					break;
				}
				case MESSAGE_HIDE: {
					setVisibility(View.INVISIBLE);
					break;
				}
				case MESSAGE_SHOW: {
					setVisibility(View.VISIBLE);
					break;
				}
				case MESSAGE_EXPAND: {
					mViewState = ViewState.EXPANDED;
					expandInUIThread((Dimensions) data.getParcelable(EXPAND_DIMENSIONS), data.getString(EXPAND_URL),
							(Properties) data.getParcelable(EXPAND_PROPERTIES));
					break;
				}
	
				case MESSAGE_PLAY_AUDIO: {
					handler.post(new SetupOrmmaAudioPlayer(data));
					break;
				}
				case MESSAGE_PLAY_VIDEO: {
					handler.post(new SetupOrmmaPlayer(view, data));
					break;
				}
				case MESSAGE_RAISE_ERROR:{
					String strMsg = data.getString(ERROR_MESSAGE);
					String action = data.getString(ERROR_ACTION);
					String injection = "window.ormmaview.fireErrorEvent(\""+strMsg+"\", \""+action+"\")";
					injectJavaScript(injection);
					break;
				}
			}
			super.handleMessage(msg);
		}
	};
	
	public void raiseError(String strMsg, String action){
		
		Message msg = mHandler.obtainMessage(MESSAGE_RAISE_ERROR);

		Bundle data = new Bundle();
		data.putString(ERROR_MESSAGE, strMsg);
		data.putString(ERROR_ACTION, action);
		msg.setData(data);
		mHandler.sendMessage(msg);
	}

	
	public void resize(int width, int height) {
		Message msg = mHandler.obtainMessage(MESSAGE_RESIZE);

		Bundle data = new Bundle();
		data.putInt(RESIZE_WIDTH, width);
		data.putInt(RESIZE_HEIGHT, height);
		msg.setData(data);

		mHandler.sendMessage(msg);
	}
	
	public void close() {
		mHandler.sendEmptyMessage(MESSAGE_CLOSE);
	}

	public void hide() { 
		mHandler.sendEmptyMessage(MESSAGE_HIDE);
	}

	public void show() {
		mHandler.sendEmptyMessage(MESSAGE_SHOW);
	}

	public void expand(Dimensions dimensions, String URL, Properties properties) {
		Message msg = mHandler.obtainMessage(MESSAGE_EXPAND);

		Bundle data = new Bundle();
		data.putParcelable(EXPAND_DIMENSIONS, dimensions);
		data.putString(EXPAND_URL, URL);
		data.putParcelable(EXPAND_PROPERTIES, properties);
		msg.setData(data);

		mHandler.sendMessage(msg);
	}

	protected void closeExpanded(View expandedFrame) {
		((ViewGroup)((Activity) getContext()).getWindow().getDecorView()).removeView(expandedFrame);
		requestLayout();
	}
	
	private void expandInUIThread(Dimensions dimensions, String URL, Properties properties) {
		boolean dontLoad = false;
		if (URL == null || URL.equals("undefined")) {
			URL = getUrl();
			dontLoad = true;
		}
		mExpandedFrame = new RelativeLayout(getContext());
		if(properties.use_background) {
			int opacity = new Float(255*properties.background_opacity).intValue();
			if(opacity < 0) opacity = 0;
			if(opacity > 255) opacity = 255;
			mExpandedFrame.setBackgroundColor(
					Color.argb(opacity, 
							Color.red(properties.background_color), 
							Color.green(properties.background_color), 
							Color.blue(properties.background_color))
					);
		}
		android.widget.RelativeLayout.LayoutParams adLp = new RelativeLayout.LayoutParams(dimensions.width,
				dimensions.height);
		adLp.leftMargin = dimensions.x;
		adLp.topMargin = dimensions.y;

		android.view.WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
				ViewGroup.LayoutParams.FILL_PARENT);

		AdServerViewCore expandedView = new AdServerView(getContext());
		expandedView.setContent(mContent);
		expandedView.loadExpandedUrl(URL, this, mExpandedFrame, dontLoad);
		mExpandedFrame.addView(expandedView, adLp);

		((ViewGroup)((Activity) getContext()).getWindow().getDecorView()).addView(mExpandedFrame, lp);
	}
	
	private void loadExpandedUrl(String Url, AdServerViewCore parentAd, ViewGroup expandedFrame, boolean dontLoad) {
		mParentAd = parentAd;
		mExpandedFrame = expandedFrame;
		mViewState = ViewState.EXPANDED;
		loadUrl(Url, dontLoad, mDataToInject);
	}
	
	public void loadUrl(String url, boolean dontLoad, String dataToInject) {
		mDataToInject = dataToInject;
		if (!dontLoad) {
			try {
				if((url != null) && (url.length() > 0)) {
					super.loadUrl(url);
				}
				return;
			} catch (Exception e) {
				e.printStackTrace();
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "loadUrl", e.getMessage());
				return;
			}
		} else {
			if((mContent != null) && (mContent.length() > 0)) {
				super.loadDataWithBaseURL(null, mContent, "text/html", "UTF-8", null);
			}
		}
	}
	
	protected void onPageFinished() {
		if (mDataToInject != null){
			injectJavaScript(mDataToInject);
		}
		
		injectJavaScript("Ormma.ready();");
	}
	
	private synchronized void setScriptPath(){
		if (mScriptPath == null){
//			mScriptPath = mAssetController.copyTextFromJarIntoAssetDir("/js/OrmmaAdController.js",
//			"/js/OrmmaAdController.js");
			mScriptPath = mAssetController.copyTextFromJarIntoAssetDir("/OrmmaAdController.js",
			"/OrmmaAdController.js");
		}
	}

	public void setContent(String content) {
		mContent = content;
	}

	/**
	 * Required.
	 * Set the id of the publisher site. 
	 * @param site
	 *            Id of the site assigned by Adserver
	 */
	public void setSite(Integer site) {
		if(adserverRequest != null) {
			adserverRequest.setSite(site);
		}
	}
	
	/**
	 * Get the id of the publisher site. 
	 */
	public Integer getSite() {
		if(adserverRequest != null) {
			return adserverRequest.getSite();
		} else {
			return 0;
		}
	}
	
	/**
	 * Required.
	 * Set the id of the zone of publisher site.
	 * @param zone
	 */
	public void setZone(Integer zone) {
		if(adserverRequest != null) {
			adserverRequest.setZone(zone);
		}
	}

	/**
	 * Get the id of the zone of publisher site.
	 */
	public Integer getZone() {
		if(adserverRequest != null) {
			return adserverRequest.getZone();
		} else {
			return 0;
		}
	}
	
	/**
	 * Optional.
	 * Set Default setting is test mode where, if the ad code is properly installed, 
	 * the ad response is "Test MODE".
	 * @param enabled
	 */
	public void setTest(Boolean enabled) {
		if(adserverRequest != null) {
			adserverRequest.setTestModeEnabled(enabled);
		}
	}

	/**
	 * Optional.
	 * Get test mode setting.
	 */
	public Boolean getTest() {
		if(adserverRequest != null) {
			return adserverRequest.getTestModeEnabled();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set Filter by premium (PREMIUM_STATUS_NON_PREMIUM - non-premium, 
	 * PREMIUM_STATUS_PREMIUM - premium only, PREMIUM_STATUS_BOTH - both). 
	 * Can be used only by premium publishers.
	 * @param premium
	 */
	public void setPremium(Integer premium) {
		if(adserverRequest != null) {
			adserverRequest.setPremium(premium);
		}
	}

	/**
	 * Optional.
	 * Get Filter by premium.
	 */
	public Integer getPremium() {
		if(adserverRequest != null) {
			return adserverRequest.getPremium();
		} else {
			return PREMIUM_STATUS_BOTH;
		}
	}
	
	/**
	 * Optional.
	 * Set Keywords to search ad delimited by commas.
	 * @param keywords
	 */
	public void setKeywords(String keywords) {
		if(adserverRequest != null) {
			adserverRequest.setKeywords(keywords);
		}
	}
	
	/**
	 * Optional.
	 * Get Keywords to search ad delimited by commas.
	 */
	public String getKeywords() {
		if(adserverRequest != null) {
			return adserverRequest.getKeywords();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set minimum width of advertising. 
	 * @param minSizeX
	 */
	public void setMinSizeX(Integer minSizeX) {
		if((adserverRequest != null)) {
			adserverRequest.setMinSizeX(minSizeX);
		}
	}
	
	/**
	 * Optional.
	 * Get minimum width of advertising. 
	 */
	public Integer getMinSizeX() {
		if(adserverRequest != null) {
			return adserverRequest.getMinSizeX();
		} else {
			return 0;
		}
	}
	
	/**
	 * Optional.
	 * Set minimum height of advertising. 
	 * @param minSizeY
	 */
	public void setMinSizeY(Integer minSizeY) {
		if((adserverRequest != null)) {
			adserverRequest.setMinSizeY(minSizeY);
		}
	}
	
	/**
	 * Optional.
	 * Get minimum height of advertising. 
	 */
	public Integer getMinSizeY() {
		if(adserverRequest != null) {
			return adserverRequest.getMinSizeY();
		} else {
			return 0;
		}
	}
	
	/**
	 * Optional.
	 * Set maximum width of advertising. 
	 * @param maxSizeX
	 */
	public void setMaxSizeX(Integer maxSizeX) {
		if((adserverRequest != null)) {
			adserverRequest.setSizeX(maxSizeX);
		}
	}
	
	/**
	 * Optional.
	 * Get maximum width of advertising. 
	 */
	public Integer getMaxSizeX() {
		if(adserverRequest != null) {
			return adserverRequest.getSizeX();
		} else {
			return 0;
		}
	}
	
	/**
	 * Optional.
	 * Set maximum height of advertising. 
	 * @param maxSizeY
	 */
	public void setMaxSizeY(Integer maxSizeY) {
		if((adserverRequest != null) ) {
			adserverRequest.setSizeY(maxSizeY);
		}
	}
	
	/**
	 * Optional.
	 * Get maximum height of advertising. 
	 */
	public Integer getMaxSizeY() {
		if(adserverRequest != null) {
			return adserverRequest.getSizeY();
		} else {
			return 0;
		}
	}
	
	int bgColor = Constants.DEFAULT_COLOR;
	
	/**
	 * Optional.
	 * Set Background color of advertising in HEX.
	 * @param backgroundColor
	 */
	@Override
	public void setBackgroundColor(int backgroundColor) {
		if(adserverRequest != null) {
			try
			{
				bgColor = backgroundColor;
				//AdLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "setBackgroundColor", "#"+Integer.toHexString(backgroundColor));
				adserverRequest.setParamBG(backgroundColor);
				super.setBackgroundColor(backgroundColor);
			}catch(Exception e)
			{
				adLog.log(AdLog.LOG_LEVEL_1, AdLog.LOG_TYPE_ERROR, "AdServerViewCore.setBackgroundColor", e.getMessage());
			}
		}	
	}

	/**
	 * Optional.
	 * Get Background color of advertising in HEX.
	 */
	public int getBackgroundColor() {
		if(adserverRequest != null) {
			return bgColor;//adserverRequest.getParamBG()|0xFF000000;
		} else {
			return Constants.DEFAULT_COLOR;
		}
	}

	/**
	 * Optional.
	 * Set Text color of links in HEX.
	 * @param textColor
	 */
	public void setTextColor(int textColor) {
		if(adserverRequest != null) {
			adserverRequest.setParamLINK(textColor);
		}
	}
	
	/**
	 * Optional.
	 * Get Text color of links in HEX.
	 */
	public int getTextColor() {
		if(adserverRequest != null) {
			return adserverRequest.getParamLINK();
		} else {
			return Constants.DEFAULT_COLOR;
		}
	}

	/**
	 * Optional.
	 * Overrides the URL of ad server.
	 * @param adserverURL
	 */
	public void setAdserverURL(String adserverURL) {
		if(adserverRequest != null) {
			adserverRequest.setAdserverURL(adserverURL);
		}
	}

	/**
	 * Optional.
	 * Get URL of ad server.
	 */
	public String getAdserverURL() {
		if(adserverRequest != null) {
			return adserverRequest.getAdserverURL();
		} else {
			return null;
		}
	}

	/**
	 * Optional.
	 * Set user location latitude value (given in degrees.decimal degrees).
	 * @param latitude
	 */
	public void setLatitude(String latitude) {
		if((adserverRequest != null) && (latitude != null)) {
			adserverRequest.setLatitude(latitude);
		}
	}
	
	/**
	 * Optional.
	 * Get user location latitude value (given in degrees.decimal degrees).
	 */
	public String getLatitude() {
		if(adserverRequest != null) {
			String latitude = adserverRequest.getLatitude();

			if(latitude != null) {
				return latitude;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set user location longtitude value (given in degrees.decimal degrees).
	 * @param longitude
	 */
	public void setLongitude(String longitude) {
		if((adserverRequest != null) && (longitude != null)) {
			adserverRequest.setLongitude(longitude);
		}
	}
	
	/**
	 * Optional.
	 * Get user location longtitude value (given in degrees.decimal degrees).
	 */
	public String getLongitude() {
		if(adserverRequest != null) {
			String longitude = adserverRequest.getLongitude();

			if(longitude != null) {
				return longitude;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set Country of visitor. 
	 * @param country
	 */
	public void setCountry(String country) {
		if(adserverRequest != null) {
			adserverRequest.setCountry(country);
		}
	}
	
	/**
	 * Optional.
	 * Get Country of visitor.
	 */
	public String getCountry() {
		if(adserverRequest != null) {
			return adserverRequest.getCountry();
		} else {
			return null;
		}
	}

	/**
	 * Optional.
	 * Set Region of visitor. 
	 * @param region
	 */
	public void setRegion(String region) {
		if(adserverRequest != null) {
			adserverRequest.setRegion(region);
		}
	}
	
	/**
	 * Optional.
	 * Get Region of visitor.
	 */
	public String getRegion() {
		if(adserverRequest != null) {
			return adserverRequest.getRegion();
		} else {
			return null;
		}
	}

	/**
	 * Optional.
	 * Set City of the device user (with state). For US only. 
	 * @param city
	 */
	public void setCity(String city) {
		if(adserverRequest != null) {
			adserverRequest.setCity(city);
		}
	}

	/**
	 * Optional.
	 * Get City of the device user (with state). For US only. 
	 */
	public String getCity() {
		if(adserverRequest != null) {
			return adserverRequest.getCity();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set Area code of a user. For US only. 
	 * @param area
	 */
	public void setArea(String area) {
		if(adserverRequest != null) {
			adserverRequest.setArea(area);
		}
	}

	/**
	 * Optional.
	 * Get Area code of a user. For US only. 
	 */
	public String getArea() {
		if(adserverRequest != null) {
			return adserverRequest.getArea();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set Metro code of a user. For US only. 
	 * @param metro
	 */
	public void setMetro(String metro) {
		if(adserverRequest != null) {
			adserverRequest.setMetro(metro);
		}
	}
	
	/**
	 * Optional.
	 * Get Metro code of a user. For US only. 
	 */
	public String getMetro() {
		if(adserverRequest != null) {
			return adserverRequest.getMetro();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set Zip/Postal code of user. For US only. 
	 * @param zip
	 */
	public void setZip(String zip) {
		if(adserverRequest != null) {
			adserverRequest.setZip(zip);
		}
	}
	
	/**
	 * Optional.
	 * Get Zip/Postal code of user. For US only. 
	 */
	public String getZip() {
		if(adserverRequest != null) {
			return adserverRequest.getZip();
		} else {
			return null;
		}
	}
	
	/**
	 * Optional.
	 * Set User carrier.
	 * @param carrier
	 */
	public void setCarrier(String carrier) {
		if(adserverRequest != null) {
			adserverRequest.setCarrier(carrier);
		}
	}
	
	/**
	 * Optional.
	 * Get User carrier.
	 */
	public String getCarrier() {
		if(adserverRequest != null) {
			return adserverRequest.getCarrier();
		} else {
			return null;
		}
	}

	public void setAdsType(int adsType) {
		if(adserverRequest != null) {
			adserverRequest.setAdstype(adsType);
		}
	}

	public int getAdsType() {
		if(adserverRequest != null) {
			return adserverRequest.getAdstype();
		} else {
			return 3;
		}
	}
	
	public void setLogLevel(int logLevel)
	{
		adLog.setLogLevel(logLevel);
	}
	
	public void setInternalBrowser(boolean value)
	{
		internalBrowser = value;
	}
	
	public void setLocationDetection(boolean detect)
	{
		if(detect)
		{
			AutoDetectParameters autoDetectParameters = AutoDetectParameters.getInstance();
			
			if((adserverRequest.getLatitude() == null) || (adserverRequest.getLongitude() == null)) {
				if((autoDetectParameters.getLatitude() == null) || (autoDetectParameters.getLongitude() == null)) {
			    	int isAccessFineLocation = _context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
			    	
			    	if(isAccessFineLocation == PackageManager.PERMISSION_GRANTED) {
						locationManager = (LocationManager)_context.getSystemService(Context.LOCATION_SERVICE);
						boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		
						if(isGpsEnabled) {
							listener = new WhereamiLocationListener(locationManager, autoDetectParameters); 
							locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, listener, Looper.getMainLooper());
						}else
						{
							adLog.log(AdLog.LOG_LEVEL_2, AdLog.LOG_TYPE_WARNING, "AutoDetectParameters.Gps", "not avalable");
						}
			    	}else adLog.log(AdLog.LOG_LEVEL_2, AdLog.LOG_TYPE_WARNING, "AutoDetectParameters.Gps", "no permission ACCESS_FINE_LOCATION");
				} else {
					adserverRequest.setLatitude(autoDetectParameters.getLatitude());
					adserverRequest.setLongitude(autoDetectParameters.getLongitude());
					adLog.log(AdLog.LOG_LEVEL_2, AdLog.LOG_TYPE_WARNING, "AutoDetectParameters.Gps=", "("+autoDetectParameters.getLatitude()+";"+autoDetectParameters.getLongitude()+")");
				}
			}
		}
	}
	
	private class WhereamiLocationListener implements LocationListener {
		private LocationManager locationManager;
		private AutoDetectParameters autoDetectParameters;
		
		public WhereamiLocationListener(LocationManager locationManager, 
				AutoDetectParameters autoDetectParameters) {
			this.locationManager = locationManager;
			this.autoDetectParameters = autoDetectParameters;
		}

		public void onLocationChanged(Location location) {
			locationManager.removeUpdates(this);
			
			try {
				double latitude = location.getLatitude();
				double longitude = location.getLongitude();
				
				
				adserverRequest.setLatitude(Double.toString(latitude));
				adserverRequest.setLongitude(Double.toString(longitude));
				autoDetectParameters.setLatitude(Double.toString(latitude));
				autoDetectParameters.setLongitude(Double.toString(longitude));
				adLog.log(AdLog.LOG_LEVEL_3, AdLog.LOG_TYPE_INFO, "GPSLocationChanged=", "("+autoDetectParameters.getLatitude()+";"+autoDetectParameters.getLongitude()+")");
				
    		} catch (Exception e) {
    			adLog.log(AdLog.LOG_LEVEL_2,AdLog.LOG_TYPE_ERROR,"GPSLocationChanged",e.getMessage());
    		}
	    }

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	}


	/**
	 * Play video
	 * 
	 * @param url
	 *            - video URL
	 * @param audioMuted
	 *            - should audio be muted
	 * @param autoPlay
	 *            - should video play immediately
	 * @param controls
	 *            - should native player controls be visible
	 * @param loop
	 *            - should video start over again after finishing
	 * @param d
	 *            - inline area dimensions
	 * @param startStyle
	 *            - normal/fullscreen; full screen if video should play in full
	 *            screen
	 * @param stopStyle
	 *            - normal/exit; exit if video should exit after video stops
	 */
	public void playVideo(String url, boolean audioMuted, boolean autoPlay,
			boolean controls, boolean loop, Dimensions d, String startStyle,
			String stopStyle) {

		Message msg = mHandler.obtainMessage(MESSAGE_PLAY_VIDEO);

		PlayerProperties properties = new PlayerProperties();

		properties.setProperties(audioMuted, autoPlay, controls, false,loop,
				startStyle, stopStyle);

		Bundle data = new Bundle();
		data.putString(EXPAND_URL, url);
		data.putString(ACTION_KEY, ACTION.PLAY_VIDEO.toString());		
		
		data.putParcelable(PLAYER_PROPERTIES, properties);
		
		if(d != null)
			data.putParcelable(DIMENSIONS, d);

		if (properties.isFullScreen()) {
			try {
				Intent intent = new Intent();
				intent.setAction("ORMMA_ANCION_HANDLER");
				intent.putExtras(data);
				getContext().startActivity(intent);
			}
			catch(ActivityNotFoundException e){
				e.printStackTrace();
			}
		} else {
			msg.setData(data);
			mHandler.sendMessage(msg);
		}
	}
	
	
	private class SetupOrmmaPlayer implements Runnable {
		private WebView view;
		private Bundle data;
		
		public SetupOrmmaPlayer(WebView view,Bundle data) {
			this.view = view;
			this.data = data;
		}

		@Override
		public void run() {
			try {
				PlayerProperties properties = (PlayerProperties) data.getParcelable(PLAYER_PROPERTIES);
				Dimensions d = (Dimensions) data.getParcelable(DIMENSIONS);
				String url = data.getString(EXPAND_URL);

				OrmmaPlayer videoPlayer = getPlayer();
				videoPlayer.setPlayData(properties, url);
				
				if (d != null) {
					FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams((int) (d.width), (int) (d.height));
					videoPlayer.setLayoutParams(fl);
					FrameLayout backGround = new FrameLayout(getContext());
					backGround.setId(BACKGROUND_ID);
					backGround.setPadding((int) (d.x), (int) (d.y), 0, 0);
					view.addView(backGround, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.FILL_PARENT));
					backGround.addView(videoPlayer);
				} else {
					videoPlayer.setLayoutParams(view.getLayoutParams());
					view.addView(videoPlayer);
				}
				videoPlayer.playVideo();
			} catch (Exception e){
			}
		}
	}

	OrmmaPlayer getPlayer() {
		if(player != null) player.releasePlayer();
		player = new OrmmaPlayer(getContext());	
		return player;
	}
	
	/**
	 * Play audio
	 * 
	 * @param url
	 *            - audio URL
	 * @param autoPlay
	 *            - should audio play immediately
	 * @param controls
	 *            - should native controls be visible
	 * @param loop
	 *            - should audio start over again after finishing
	 * @param position
	 *            - should audio be included with ad content
	 * @param startStyle
	 *            - normal/fullscreen; full screen if audio should play in full
	 *            screen
	 * @param stopStyle
	 *            - normal/exit; exit if audio should exit after audio stops
	 */
	public void playAudio(String url, boolean autoPlay, boolean controls,
			boolean loop, boolean position, String startStyle, String stopStyle) {

		PlayerProperties properties = new PlayerProperties();

		 properties.setProperties(false, autoPlay, controls, position,loop, startStyle, stopStyle);

		Bundle data = new Bundle();

		data.putString(ACTION_KEY, ACTION.PLAY_AUDIO.toString());
		data.putString(EXPAND_URL, url);
		data.putParcelable(PLAYER_PROPERTIES, properties);

		if (properties.isFullScreen()) {
			try {
				Intent intent = new Intent();
				intent.setAction("ORMMA_ANCION_HANDLER");
				intent.putExtras(data);
				getContext().startActivity(intent);
			}
			catch(ActivityNotFoundException e){
				e.printStackTrace();
			}
		} else {
			Message msg = mHandler.obtainMessage(MESSAGE_PLAY_AUDIO);
			msg.setData(data);
			mHandler.sendMessage(msg);
		}
	}
	private class SetupOrmmaAudioPlayer implements Runnable {
		private Bundle data;
		
		public SetupOrmmaAudioPlayer(Bundle data) {
			this.data = data;
		}

		@Override
		public void run() {
			PlayerProperties properties = (PlayerProperties) data.getParcelable(PLAYER_PROPERTIES);
			String url = data.getString(EXPAND_URL);

			OrmmaPlayer audioPlayer = getPlayer();
			audioPlayer.setPlayData(properties, url);
			audioPlayer.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
			((ViewGroup) getParent()).addView(audioPlayer);
			audioPlayer.playAudio();
		}
	}
	
	/**
	 * Open Map
	 * 
	 * @param url
	 *            - map url
	 * @param fullscreen
	 *            - should map be shown in full screen
	 */
	public void openMap(String POI, boolean fullscreen) {
		POI = POI.trim();
		POI = OrmmaUtils.convert(POI);
		POI = "geo:0,0?" + POI; 

//		if(fullscreen){
			try {
				// start google maps
				Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(POI));
				mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				getContext().startActivity(mapIntent);
				
			} catch (ActivityNotFoundException e) {
				e.printStackTrace();
				Toast.makeText(getContext(), "Error: no Google Api or error in parameters", Toast.LENGTH_LONG).show();
			}
//		}
//		else{
//			// if not fullscreen, display map in current OrmmaView space
//		}
	}
}