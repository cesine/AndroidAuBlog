/*
 * Copyright (C) 2010 The Android Open Source Project
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

package ca.ilanguage.aublog.preferences;

import java.io.File;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import ca.ilanguage.aublog.R;

public class SetPreferencesActivity extends PreferenceActivity implements 
		YesNoDialogPreference.YesNoDialogListener {
	String mBloggerAccount;
	String mBloggerPassword;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
        getPreferenceManager().setSharedPreferencesName(PreferenceConstants.PREFERENCE_NAME);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
        Preference exportTree = findPreference(PreferenceConstants.PREFERENCE_EMAIL_DRAFTS_TREE);
        exportTree.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				File file = new File("/sdcard/Android/data/ca.ilanguage.aublog/files/json_only_draft_space_tree.js.txt");

		    	Intent mailto = new Intent(Intent.ACTION_SEND); 
		        mailto.setType("message/rfc822") ; // use from live device
		        mailto.putExtra(Intent.EXTRA_EMAIL, new String[]{""});
		        mailto.putExtra(Intent.EXTRA_SUBJECT,"Backup of AuBlog Drafts");
		        mailto.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
		        mailto.putExtra(Intent.EXTRA_TEXT,"Attached is a backup of the Blog drafts, exported in json format.");
		        startActivity(Intent.createChooser(mailto, "Select email application."));
		   
				return true;
			}
		});
        
        
        Preference eraseGameButton = getPreferenceManager().findPreference("erasegame");
        if (eraseGameButton != null) {
        	YesNoDialogPreference yesNo = (YesNoDialogPreference)eraseGameButton;
        	yesNo.setListener(this);
        }
        if (getIntent().getBooleanExtra("controlConfig", false)) {
        	PreferenceScreen controlConfig = (PreferenceScreen)getPreferenceManager().findPreference("controlConfigScreen");
        	if (controlConfig != null) {
        		setPreferenceScreen(controlConfig);
        	}
        }
        
        
        final boolean fileManagerAvailable = isIntentAvailable(this,
        "org.openintents.action.PICK_FILE");
        SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		Boolean fileManagerChecked = prefs.getBoolean(PreferenceConstants.PREFERENCE_FILE_MANAGER_INSTALLED, false);
        if ( fileManagerChecked && ! fileManagerAvailable ){
        	Toast.makeText(SetPreferencesActivity.this, "To open and export recorded files or " +
        			"draft data you can install the OI File Manager, " +
        			"it allows you to browse your SDCARD directly on your mobile device.", Toast.LENGTH_LONG).show();
        	Intent goToMarket = new Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("market://details?id=org.openintents.filemanager"));
        	startActivity(goToMarket);
        }
        if ( ! fileManagerChecked &&  fileManagerAvailable){
        	/*
        	 * Enable the export and open possibilities
        	 */
			editor.putBoolean(PreferenceConstants.PREFERENCE_FILE_MANAGER_INSTALLED,
					true);
			editor.commit();
        }
		if (fileManagerAvailable){
			editor.putBoolean(PreferenceConstants.PREFERENCE_FILE_MANAGER_INSTALLED,
					true);
			editor.commit();
			Preference exportJson = getPreferenceManager().findPreference("openJsonTxt");
			exportJson.setEnabled(true);
			exportJson.setSelectable(true);
			Preference openAudioDir = getPreferenceManager().findPreference("openAudioDir");
			openAudioDir.setEnabled(true);
			openAudioDir.setSelectable(true);
		}

        
        
    }
	/**
	 * Indicates whether the specified action can be used as an intent. This
	 * method queries the package manager for installed packages that can
	 * respond to an intent with the specified action. If no suitable package is
	 * found, this method returns false.
	 *
	 * @param context The application's environment.
	 * @param action The Intent action to check for availability.
	 *
	 * @return True if an Intent with the specified action can be sent and
	 *         responded to, false otherwise.
	 */
	public static boolean isIntentAvailable(Context context, String action) {
	    final PackageManager packageManager = context.getPackageManager();
	    final Intent intent = new Intent(action);
	    List<ResolveInfo> list =
	            packageManager.queryIntentActivities(intent,
	                    PackageManager.MATCH_DEFAULT_ONLY);
	    return list.size() > 0;
	}
	public void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			/*
			 * TODO open the database provider, delete database and recreate with the default entries
			 */
			
			SharedPreferences prefs = getSharedPreferences(PreferenceConstants.PREFERENCE_NAME, MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			//editor.remove(PreferenceConstants.PREFERENCE_LEVEL_ROW);


			editor.commit();
			Toast.makeText(this, R.string.saved_game_erased_notification,
                    Toast.LENGTH_SHORT).show();
		}
	}
}
