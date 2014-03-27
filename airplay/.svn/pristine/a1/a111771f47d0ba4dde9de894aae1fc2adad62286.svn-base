package com.letv.airplay;

import com.letv.airplay.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

/**
 * 图片显示
 * @author 韦念欣
 *
 */
public class PictureShowActivity extends Activity {  
	
	public static final String TAG = MediaPlayerActivity.class.getSimpleName();
	
	private ImageView imageView;
	private final int PICTURE_SHOW = 12399838;
	private Handler handler;

	public byte[] pictureData;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.e(TAG, "PictureShowActivity onCreate");
		setContentView(R.layout.activity_picture);
		imageView = (ImageView)findViewById(R.id.imageView);
		JniInterface.getInstance().setPictureShow(this);
		
		Intent intent = getIntent();
		
		handler = new Handler(getMainLooper()){
			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				System.gc();
				byte[] data = (byte[]) msg.obj;
				Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
				imageView.setImageBitmap(bitmap);
			}
		};
		//pictureData = intent.getByteArrayExtra("picData");
		showPic(JniInterface.getInstance().Data);
		
		
	}
 
	@Override
	protected void onPause() {
		super.onPause();
		
		Log.e(TAG, "PictureShowActivity onPause");
		JniInterface.getInstance().setPictureShow(null);
		JniInterface.getInstance().SemaphorePictureRelease();
		
		this.finish();
	}

	@Override
	protected void onStop() {
		Log.e(TAG, "PictureShowActivity onStop");
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		JniInterface.getInstance().setPictureShow(null);
		JniInterface.getInstance().SemaphorePictureRelease();
		Log.e(TAG, "PictureShowActivity onDestroy");
	}
	
	/**
	 * 从主线程显示图片
	 */
	public void showPic(byte[] pictureData){
		handler.sendMessage(handler.obtainMessage(PICTURE_SHOW, 0, 0, pictureData));
		JniInterface.getInstance().SemaphorePictureRelease();
	}
}
