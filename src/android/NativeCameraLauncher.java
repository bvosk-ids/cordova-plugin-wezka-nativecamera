/*
	    Copyright 2014 Giovanni Di Gregorio.

		Licensed under the Apache License, Version 2.0 (the "License");
		you may not use this file except in compliance with the License.
		You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

		Unless required by applicable law or agreed to in writing, software
		distributed under the License is distributed on an "AS IS" BASIS,
		WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		See the License for the specific language governing permissions and
   		limitations under the License.
 */

package pro.alyans.nativecamera;
//package com.wezka.nativecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;

import org.apache.cordova.ExifHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import android.Manifest;
import org.apache.cordova.PermissionHelper;

/**
 * This class launches the camera view, allows the user to take a picture,
 * closes the camera view, and returns the captured image. When the camera view
 * is closed, the screen displayed before the camera view was shown is
 * redisplayed.
 */
public class NativeCameraLauncher extends CordovaPlugin {

	private static final String LOG_TAG = "NativeCameraLauncher";

	private static final int DATA_URL = 0;              // Return base64 encoded string
	private static final int FILE_URI = 1;              // Return file uri (content://media/external/images/media/2 for Android)
	private static final int NATIVE_URI = 2;                    // On Android, this is the same as FILE_URI

	private static final int PHOTOLIBRARY = 0;          // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
	private static final int CAMERA = 1;                // Take picture from camera
	private static final int SAVEDPHOTOALBUM = 2;       // Choose image from picture library (same as PHOTOLIBRARY for Android)

	private static final int PICTURE = 0;               // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
	private static final int VIDEO = 1;                 // allow selection of video only, ONLY RETURNS URL
	private static final int ALLMEDIA = 2;              // allow selection from all media types

	private static final int JPEG = 0;                  // Take a picture of type JPEG
	private static final int PNG = 1;                   // Take a picture of type PNG
	private static final String GET_PICTURE = "Get Picture";
	private static final String GET_VIDEO = "Get Video";
	private static final String GET_All = "Get All";

	public static final int PERMISSION_DENIED_ERROR = 20;
	public static final int TAKE_PIC_SEC = 0;
	public static final int SAVE_TO_ALBUM_SEC = 1;

	private int mQuality;
	private int targetWidth;
	private int targetHeight;
	private int srcType;
	private int destType;
	private int encodingType;
	private Uri imageUri;
	private File photo;
	private static final String _DATA = "_data";
	private CallbackContext callbackContext;
	private String date = null;

	public NativeCameraLauncher() {
	}

	void failPicture(String reason) {
		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, reason));
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";
		this.callbackContext = callbackContext;
		try {
			if (action.equals("takePicture")) {
				this.targetHeight = 0;
				this.targetWidth = 0;
				this.mQuality = 80;
				this.encodingType = args.getInt(5);
				this.targetHeight = args.getInt(4);
				this.targetWidth = args.getInt(3);
				this.mQuality = args.getInt(0);
				this.srcType = args.getInt(2);
				this.destType = args.getInt(1);
				this.takePicture();
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				callbackContext.sendPluginResult(r);
				return true;
			}
			return false;
		} catch (JSONException e) {
			e.printStackTrace();
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
			return true;
		}
	}

	public void takePicture() {
		// Camera
		if (this.srcType == 1) {
			// Save the number of images currently on disk for later
			Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), CameraActivity.class);
			this.photo = createCaptureFile();
			this.imageUri = Uri.fromFile(photo);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, this.imageUri);
			this.cordova.startActivityForResult((CordovaPlugin) this, intent, 1);
		}
		else if ((this.srcType == 0) || (this.srcType == 2)) {
			// FIXME: Stop always requesting the permission
			if(!PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
				PermissionHelper.requestPermission(this, SAVE_TO_ALBUM_SEC, Manifest.permission.READ_EXTERNAL_STORAGE);
			} else {
				this.getImage(this.srcType, this.destType, this.encodingType);
			}
		}
	}

	private File createCaptureFile() {
		File oldFile = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		if(oldFile.exists())
			oldFile.delete();
		Calendar c = Calendar.getInstance();
	    this.date = "" + c.get(Calendar.DAY_OF_MONTH)
					+ c.get(Calendar.MONTH)
					+ c.get(Calendar.YEAR)
					+ c.get(Calendar.HOUR_OF_DAY)
					+ c.get(Calendar.MINUTE)
					+ c.get(Calendar.SECOND);

		File photo = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		return photo;
	}

	/**
	 * Get image from photo library.
	 *
	 * @param srcType           The album to get image from.
	 * @param returnType        Set the type of image to return.
	 * @param encodingType
	 */
	// TODO: Images selected from SDCARD don't display correctly, but from CAMERA ALBUM do!
	// TODO: Images from kitkat filechooser not going into crop function
	public void getImage(int srcType, int returnType, int encodingType) {
		Intent intent = new Intent();
		String title = GET_PICTURE;
		// TODO only pictures
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		if (this.cordova != null) {
			this.cordova.startActivityForResult((CordovaPlugin) this, Intent.createChooser(intent,
					new String(title)), (srcType + 1) * 16 + returnType + 1);
		}
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// If image available
		if (resultCode == Activity.RESULT_OK) {
			if (this.srcType == CAMERA) {
				int rotate = 0;
				try {
					// Create an ExifHelper to save the exif data that is lost
					// during compression
					ExifHelper exif = new ExifHelper();
					exif.createInFile(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext())
							+ "/Pic-" + this.date + ".jpg");
					exif.readExifData();
					rotate = exif.getOrientation();

					// Read in bitmap of captured image
					Bitmap bitmap;
					try {
						bitmap = android.provider.MediaStore.Images.Media
								.getBitmap(this.cordova.getActivity().getContentResolver(), imageUri);
					} catch (FileNotFoundException e) {
						Uri uri = intent.getData();
						android.content.ContentResolver resolver = this.cordova.getActivity().getContentResolver();
						bitmap = android.graphics.BitmapFactory
								.decodeStream(resolver.openInputStream(uri));
					}

					// If bitmap cannot be decoded, this may return null
					if (bitmap == null) {
						this.failPicture("Error decoding image.");
						return;
					}

					bitmap = scaleBitmap(bitmap);

					// Add compressed version of captured image to returned media
					// store Uri
					bitmap = getRotatedBitmap(rotate, bitmap, exif);
					Log.i(LOG_TAG, "URI: " + this.imageUri.toString());
					OutputStream os = this.cordova.getActivity().getContentResolver()
							.openOutputStream(this.imageUri);
					bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
					os.close();

					// Restore exif data to file
					exif.createOutFile(this.imageUri.getPath());
					exif.writeExifData();

					// Send Uri back to JavaScript for viewing image
					this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, this.imageUri.toString()));

					bitmap.recycle();
					bitmap = null;
					System.gc();
				} catch (IOException e) {
					e.printStackTrace();
					this.failPicture("Error capturing image.");
				}
			} else if ((this.srcType == PHOTOLIBRARY) || (this.srcType == SAVEDPHOTOALBUM)) {
				if (resultCode == Activity.RESULT_OK && intent != null) {
					final Intent i = intent;
					final int finalDestType = destType;
					cordova.getThreadPool().execute(new Runnable() {
						public void run() {
							processResultFromGallery(finalDestType, i);
						}
					});
				} else if (resultCode == Activity.RESULT_CANCELED) {
					this.failPicture("No Image Selected");
				} else {
					this.failPicture("Selection did not complete!");
				}
			}
		}

		// If cancelled
		else if (resultCode == Activity.RESULT_CANCELED) {
			this.failPicture("Camera cancelled.");
		}

		// If something else
		else {
			this.failPicture("Did not complete!");
		}
	}

	/**
   * Applies all needed transformation to the image received from the gallery.
   *
   * @param destType In which form should we return the image
   * @param intent   An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
   */
  private void processResultFromGallery(int destType, Intent intent) {
    Uri uri = intent.getData();
    if (uri == null) {
        this.failPicture("null data from photo library");
        return;
    }

    this.callbackContext.success(uri.toString());
	}

	public Bitmap scaleBitmap(Bitmap bitmap) {
		int newWidth = this.targetWidth;
		int newHeight = this.targetHeight;
		int origWidth = bitmap.getWidth();
		int origHeight = bitmap.getHeight();

		// If no new width or height were specified return the original bitmap
		if (newWidth <= 0 && newHeight <= 0) {
			return bitmap;
		}
		// Only the width was specified
		else if (newWidth > 0 && newHeight <= 0) {
			newHeight = (newWidth * origHeight) / origWidth;
		}
		// only the height was specified
		else if (newWidth <= 0 && newHeight > 0) {
			newWidth = (newHeight * origWidth) / origHeight;
		}
		// If the user specified both a positive width and height
		// (potentially different aspect ratio) then the width or height is
		// scaled so that the image fits while maintaining aspect ratio.
		// Alternatively, the specified width and height could have been
		// kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
		// would result in whitespace in the new image.
		else {
			double newRatio = newWidth / (double) newHeight;
			double origRatio = origWidth / (double) origHeight;

			if (origRatio > newRatio) {
				newHeight = (newWidth * origHeight) / origWidth;
			} else if (origRatio < newRatio) {
				newWidth = (newHeight * origWidth) / origHeight;
			}
		}

		return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
	}

	private Bitmap getRotatedBitmap(int rotate, Bitmap bitmap, ExifHelper exif) {
        Matrix matrix = new Matrix();
        matrix.setRotate(rotate);
        try
        {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            exif.resetOrientation();
        }
        catch (OutOfMemoryError oom)
        {
            // You can run out of memory if the image is very large:
            // http://simonmacdonald.blogspot.ca/2012/07/change-to-camera-code-in-phonegap-190.html
            // If this happens, simply do not rotate the image and return it unmodified.
            // If you do not catch the OutOfMemoryError, the Android app crashes.
        }
        return bitmap;
    }

	private String getTempDirectoryPath(Context ctx) {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			cache = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ "/Android/data/"
					+ ctx.getPackageName() + "/cache/");
		}
		// Use internal storage
		else {
			cache = ctx.getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		if (!cache.exists()) {
			cache.mkdirs();
		}

		return cache.getAbsolutePath();
	}
}
