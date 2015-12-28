/*
 * Copyright 2011 ODIN Working Group. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pubmatic.sdk.banner.pubmatic;

import java.security.MessageDigest;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public class ODIN {
	private final static String TAG = "ODIN";
	
	private final static String SHA1_ALGORITHM = "SHA-1";
	private final static String CHAR_SET = "iso-8859-1";
	
	/*
	 * Returns the ODIN-1 String for the Android device. For devices that have a null
	 * or invalid ANDROID_ID (such as the emulator), a null value will be returned.
	 * 
	 * This code is designed to be built against an Android API level 3 or greater,
	 * but supports all Android API levels.
	 * 
	 * @param  context   the context of the application.
	 * 
	 * @return           the ODIN-1 string or null if the ANDROID_ID is invalid.
	 */
    public static String getODIN1(Context context) {
    	String androidId;
    	try {
			androidId = Settings.System.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
		} catch (Exception e) {
			
			// In Android API levels 1-2, Settings.Secure wasn't implemented.
			// Fall back to deprecated methods.
			try {
				androidId = Settings.System.getString(context.getContentResolver(), Settings.System.ANDROID_ID);
			} catch (Exception e1) {
				Log.i(TAG, "Error generating ODIN-1: ", e1);
				return null;
			}
		}
		
		return SHA1(androidId);
    }
	
	private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while(two_halfs++ < 1);
        }
        return buf.toString();
    }

    private static String SHA1(String text) {
    	try {
		    MessageDigest md;
		    md = MessageDigest.getInstance(SHA1_ALGORITHM);
		    byte[] sha1hash = new byte[40];
		    md.update(text.getBytes(CHAR_SET), 0, text.length());
		    sha1hash = md.digest();
		    
		    return convertToHex(sha1hash);
    	} catch (Exception e) {
    		Log.i(TAG, "Error generating generating SHA-1: ", e);
    		return null;
    	}
    }
}
