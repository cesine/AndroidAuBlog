package ca.ilanguage.aublog.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import ca.ilanguage.aublog.R;
import ca.ilanguage.aublog.preferences.NonPublicConstants;
import ca.ilanguage.aublog.preferences.PreferenceConstants;
import ca.ilanguage.aublog.ui.EditBlogEntryActivity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.NetworkInfo.State;

public class NotifyingTranscriptionIntentService extends IntentService {
	protected static String TAG = "NotifyingTranscriptionIntentService";
	  
    public NotifyingTranscriptionIntentService() {
		super(TAG);
	}

	private NotificationManager mNM;
	private Boolean mTranscriptionReturned =false;
    private int mMaxFileUploadOverMobileNetworkSize = 0;
    private int mMaxUploadFileSize = 15000000;  // Set maximum upload size to 1.5MB roughly 15 minutes of audio, 
    											// users shouldn't abuse transcription service by sending meetings and other sorts of audio.
    											// If you change this value, change the value in the arrays.xml as well look for:
    											// 15 minutes (AuBlog\'s max transcription length)
    private String mUriString ="";
    private String mAudioFilePath ="";
	private String mFileNameOnServer="";
    private int mSplitType = 0;
	private ArrayList<String> mTimeCodes;
	
	public static final String EXTRA_AUDIOFILE_FULL_PATH = "audioFilePath";
	public static final String EXTRA_RESULTS = "splitUpResults";
	public static final String EXTRA_SPLIT_TYPE = "splitOn";
	public static final String EXTRA_CORRESPONDING_DRAFT_URI_STRING = "aublogCorrespondingDraftUri";
	
	/**
	 * Splitting on Silence is relatively quick it only requires mathematic calculation on the audio sample, 
	 * this is used by default by all other split types.
	 */
	public static final int SPLIT_ON_SILENCE = 1;
	/**
	 * Subtitles should not exceed a certain length to fit on the screen, use this if you actually have the goal of generating subtitles
	 */
	public static final int SPLIT_ON_TOO_MANY_CHARS = 9;
	/**
	 * Warning: using prosodic cues requires more audio analysis (fourier transforms FFT) and so they will slow the service down substantially
	 */
	public static final int SPLIT_ON_ANY_PROSODIC_CUE = 2;

	/*
	 * List of Optional Prosodic cues
	 * 
	 * For more info: 
	 * 		http://en.wikipedia.org/wiki/Prosody_(linguistics)
	 * For more bleeding-edge info: 
	 * 		Experimental and Theoretical Advances in Prosody Conference http://prosodylab.org/etap/
	 */
	public static final int SPLIT_ON_LENGTHENED_VOWEL = 3;
	public static final int SPLIT_ON_LENGHTENED_CONSONANT = 4;
	public static final int SPLIT_ON_LENGTHENED_ANYTHING = 5;
	public static final int SPLIT_ON_GLOTTALIZATION = 6;
	public static final int SPLIT_ON_BREATH = 7;
	/**
	 * For those speakers of "valley-girl" English prosodic phrases can be 
	 * discovered using upspeak also known as: {high rising terminal (HRT), uptalk, upspeak, rising inflection or high rising intonation }
	 * 
	 * For more info: http://en.wikipedia.org/wiki/High_rising_terminal
	 */
	public static final int SPLIT_ON_UPSPEAK = 8;
	
    // Use a layout id for a unique identifier
    private static int AUBLOG_NOTIFICATIONS = R.layout.status_bar_notifications;
    private String mNotificationMessage;

	@Override
	public void onCreate() {
		super.onCreate();
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}
/**
 * {@inheritDoc}
 * 
 * This method is called each time an intent is delivered to this service. 
 * 
 * 1. check if audio file is shorter than the users upload preferences, check if wifi is on
 * 2. if conditions are satisfied, upload the file and set a pending intent that can load the result from the server into the corresponding AuBlog draft
 * 
 * 
 */
	@Override
	protected void onHandleIntent(Intent intent) {
		
		/*
		 * get data from extras bundle, store it in the member variables
		 */
		try {
			mAudioFilePath = intent.getExtras().getString(EXTRA_AUDIOFILE_FULL_PATH);
			mSplitType = intent.getExtras().getInt(EXTRA_SPLIT_TYPE);
			mUriString = intent.getExtras().getString(EXTRA_CORRESPONDING_DRAFT_URI_STRING);
		} catch (Exception e) {
			//Toast.makeText(SRTGeneratorActivity.this, "Error "+e,Toast.LENGTH_LONG).show();
		}
		if (mAudioFilePath.length() > 0) {
			mNotificationMessage= mAudioFilePath;
		}else{
			mNotificationMessage ="No file";
		}

		/*
		 * Check if wifi is active, or if this file can be uploaded as per the users
		 * preference settings
		 */
		SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
		mMaxFileUploadOverMobileNetworkSize = prefs.getInt(PreferenceConstants.PREFERENCE_MAX_UPLOAD_ON_MOBILE_NETWORK, 2000000);
		Boolean wifiOnly = prefs.getBoolean(PreferenceConstants.PREFERENCE_UPLOAD_WAIT_FOR_WIFI, true);
		File audioFile = new File(mAudioFilePath);
		//audioFile.length() < mMaxFileUploadOverMobileNetworkSize ||
		ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();


		if ( audioFile.length() < mMaxUploadFileSize && 
				( 	    	(audioFile.length() < mMaxFileUploadOverMobileNetworkSize || wifiOnly == false ) 
						|| (wifi == State.CONNECTED || wifi == State.CONNECTING) 
				) 
			){
			//if the audio file 
			//   A: is  smaller than max upload size, and
			//   either B: smaller than the limit allowed on mobile, or user doesnt care about being connected to wifi
			//   or C: the wifi is on
			//then, upload it for transcription. otherwise say it was too big to upload

			/*
			 * Upload file
			 */
			try {
				HttpClient httpClient = new DefaultHttpClient();
				HttpContext localContext = new BasicHttpContext();
				Long uniqueId = System.currentTimeMillis();
				HttpPost httpPost = new HttpPost(NonPublicConstants.NONPUBLIC_TRANSCRIPTION_WEBSERVICE_URL+"stuff"+uniqueId.toString());

				MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
				
				
//				ByteArrayOutputStream bos = new ByteArrayOutputStream();
//				bitmap.compress(CompressFormat.JPEG, 100, bos);
//				byte[] data = bos.toByteArray();
				entity.addPart("title", new StringBody("thetitle"));
				///entity.addPart("returnformat", new StringBody("json"));
				//entity.addPart("uploaded", new ByteArrayBody(data,"myImage.jpg"));
				//entity.addPart("aublogInstallID",new StringBody(mAuBlogInstallId));
				String splitCode=""+mSplitType;
				entity.addPart("splitCode",new StringBody(splitCode));
				entity.addPart("file", new FileBody(audioFile));
				///entity.addPart("photoCaption", new StringBody("thecaption"));
				httpPost.setEntity(entity);
				
				HttpResponse response = httpClient.execute(httpPost,localContext);
				
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(
								response.getEntity().getContent(), "UTF-8"));

				String firstLine = reader.readLine();
				reader.readLine();//mFileNameOnServer = reader.readLine().replaceAll(":filename","");
				mFileNameOnServer = reader.readLine().replaceAll(":path","");
				/*
				 * Read response into timecodes
				 */
				String line ="";
				while((line = reader.readLine()) != null){
					mTimeCodes.add(line);
					
				}
				
				//showNotification(R.drawable.stat_stat_aublog,  mFileNameOnServer);
	        	mNotificationMessage = firstLine + "\nSelect to import transcription.";
			} catch (Exception e) {
				//Log.e(e.getClass().getName(), e.getMessage(), e);
				mNotificationMessage = "Connection error.";// null;
			}
			
			
			/*
			 * Append fake time codes for testing purposes
			 */
			splitOnSilence();

			File outSRTFile =  new File(mAudioFilePath.replace(".mp3",".srt"));
			FileOutputStream outSRT;
			try {
				outSRT = new FileOutputStream(outSRTFile);
				outSRT.write(mFileNameOnServer.getBytes());
				outSRT.write("\n".getBytes());
				/*
				 * Append time codes SRT array to srt file.
				 * the time codes and transcription are read line by line from the in the server's response. 
				 */
				for(int i = 0; i < mTimeCodes.size(); i++){
					outSRT.write(mTimeCodes.get(i).getBytes());
					outSRT.write("\n".getBytes());
				}

				outSRT.flush();
				outSRT.close();
				mTranscriptionReturned = true;
				//mNotificationMessage = "Select to import transcription.";
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				mNotificationMessage ="Cannot write results to SDCARD";
			}
			

		}else{
			//no wifi, and the file is larger than the users settings for upload over mobile network.
			mNotificationMessage = "Dication was too long to send for transcription. Check settings.";
		}//end if for max file size for upload


		showNotification(R.drawable.stat_aublog,  mNotificationMessage);

	}//end onhandle intent

	  
    private void showNotification(int iconId, String message) {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        //CharSequence text = message;

        // Set the icon, scrolling text and timestamp.
        // Note that in this example, we pass null for tickerText.  We update the icon enough that
        // it is distracting to show the ticker text every time it changes.  We strongly suggest
        // that you do this as well.  (Think of of the "New hardware found" or "Network connection
        // changed" messages that always pop up)
        Notification notification = new Notification(iconId, message, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        //tried sending it to Edit activity but couldnt get extras to be extracted in either onResume or onStart, so cant 
        //pull in new transcription if user relaunches edit activiyt by clicking on the notification.
        Intent intent = new Intent(this, NotifyingController.class);
        Uri uri = Uri.parse(mUriString);
        intent.setData(uri);
        //intent.putExtra(EXTRA_CORRESPONDING_DRAFT_URI_STRING, mUriString);
        //intent.putExtra(EditBlogEntryActivity.EXTRA_TRANSCRIPTION_RETURNED,mTranscriptionReturned);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, "AuBlog Transcription Service",
                       message, contentIntent);
        //notification will disapear when user clicks and launches the pending intent
        notification.flags  |= Notification.FLAG_AUTO_CANCEL;
        // Send the notification.
        // We use a layout id because it is a unique number.  We use it later to cancel.
        mNM.notify(AUBLOG_NOTIFICATIONS, notification);
    }
    public String splitOnSilence(){
    	mTimeCodes = new ArrayList<String>();
    	mTimeCodes.add("0:00:02.350,0:00:06.690");
    	mTimeCodes.add("0:00:07.980,0:00:12.780");
    	mTimeCodes.add("0:00:14.529,0:00:17.970");
    	mTimeCodes.add("0:00:17.970,0:00:20.599");
    	return "right now, these are fake timecodes";
    }
}
