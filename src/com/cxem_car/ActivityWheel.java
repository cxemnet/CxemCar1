package com.cxem_car;

import java.lang.ref.WeakReference;
import java.util.Locale;

import com.cxem_car.cBluetooth;
import com.cxem_car.R;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class ActivityWheel extends Activity implements SensorEventListener  {
	
	private SensorManager mSensorManager;
    private Sensor mAccel;
    private cBluetooth bl = null;
    private ToggleButton LightButton;
    private Button btn_Rear;
    private VerticalSeekBar VSeekBar;
	
	private int xAxis = 0;
    private int yAxis = 0;
    private int motorLeft = 0;
    private int motorRight = 0;
    private float xgl = 0;
    private boolean isRear = false;	// reverse is off (включен ли задний ход)
    private boolean BT_is_connect;	// bluetooh is connected (переменная для хранения информации подключен ли Bluetooth)
    private String address;			// MAC-address from settings (MAC-адрес устройства из настроек)
    private boolean show_Debug;		// show debug information (from settings) (отображение отладочной информации (из настроек))
    private int xMax;		    	// limit on the X axis from settings (предел по оси X, максимальное значение для ШИМ (0-10), чем больше, тем больше нужно наклонять Android-устройство)
    private int yMax;		    	// limit on the Y axis from settings (предел по оси Y, максимальное значение для ШИМ (0-10))
    private int yThreshold;  		// minimum value of PWM from settings (минимальное значение ШИМ (порог ниже которого не вращается двигатель))
    private int pwmMax;	   			// maximum value of PWM from settings (максимальное значение ШИМ из настроек)
    private int xR;					// pivot point from settings (точка разворота из настроек)
    private String commandLeft;		// command symbol for left motor from settings (символ команды левого двигателя из настроек)
    private String commandRight;	// command symbol for right motor from settings (символ команды правого двигателя из настроек)
    private String commandHorn;		// command symbol for optional command from settings (for example - horn) (символ команды для доп. канала (звуковой сигнал) из настроек)
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wheel);
                
        address = (String) getResources().getText(R.string.default_MAC);
        xMax = Integer.parseInt((String) getResources().getText(R.string.default_xMax));
        xR = Integer.parseInt((String) getResources().getText(R.string.default_xR));
        yMax = Integer.parseInt((String) getResources().getText(R.string.default_yMax));
        yThreshold = Integer.parseInt((String) getResources().getText(R.string.default_yThreshold));
        pwmMax = Integer.parseInt((String) getResources().getText(R.string.default_pwmMax));
        commandLeft = (String) getResources().getText(R.string.default_commandLeft);
        commandRight = (String) getResources().getText(R.string.default_commandRight);
        commandHorn = (String) getResources().getText(R.string.default_commandHorn);

        loadPref();
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);          
        
        bl = new cBluetooth(this, mHandler);
        bl.checkBTState();
        
        VSeekBar = (VerticalSeekBar) findViewById(R.id.calcVerticalSeekBar);  
        VSeekBar.setMaximum(pwmMax);
        
        LightButton = (ToggleButton) findViewById(R.id.LightButton);
        btn_Rear = (Button) findViewById(R.id.btnRear);

        LightButton.setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			if(LightButton.isChecked()){
    				if(BT_is_connect) bl.sendData(String.valueOf(commandHorn+"1\r"));
    			}else{
    				if(BT_is_connect) bl.sendData(String.valueOf(commandHorn+"0\r"));
    			}
    		}
    	});
        
        btn_Rear.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
		        	isRear = true;
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
		        	isRear = false;
		        }
				return false;
		    }
		});
        
        VSeekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				//Toast.makeText(getBaseContext(), String.valueOf(seekBar.getProgress()), Toast.LENGTH_SHORT).show();
				MotionChanged(xgl,progress);
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				VSeekBar.setProgressAndThumb(0);
			}
    	}); 
        
        mHandler.postDelayed(sRunnable, 600000);
        //finish();
    }
    
    private static class MyHandler extends Handler {
        private final WeakReference<ActivityWheel> mActivity;
     
        public MyHandler(ActivityWheel activity) {
          mActivity = new WeakReference<ActivityWheel>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
        	ActivityWheel activity = mActivity.get();
        	if (activity != null) {
        		switch (msg.what) {
	            case cBluetooth.BL_NOT_AVAILABLE:
	               	Log.d(cBluetooth.TAG, "Bluetooth is not available. Exit");
	            	Toast.makeText(activity.getBaseContext(), "Bluetooth is not available", Toast.LENGTH_SHORT).show();
	                activity.finish();
	                break;
	            case cBluetooth.BL_INCORRECT_ADDRESS:
	            	Log.d(cBluetooth.TAG, "Incorrect MAC address");
	            	Toast.makeText(activity.getBaseContext(), "Incorrect Bluetooth address", Toast.LENGTH_SHORT).show();
	                break;
	            case cBluetooth.BL_REQUEST_ENABLE:   
	            	Log.d(cBluetooth.TAG, "Request Bluetooth Enable");
	            	BluetoothAdapter.getDefaultAdapter();
	            	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	            	activity.startActivityForResult(enableBtIntent, 1);
	                break;
	            case cBluetooth.BL_SOCKET_FAILED:
	            	Toast.makeText(activity.getBaseContext(), "Socket failed", Toast.LENGTH_SHORT).show();
	            	//activity.finish();
	                break;
	          	}
          	}
        }
	}
     
	private final MyHandler mHandler = new MyHandler(this);
     
	private final static Runnable sRunnable = new Runnable() {
		public void run() { }
	};
	
	public void onSensorChanged(SensorEvent e) {
	    float xRaw;		// RAW-value from Accelerometer sensor (RAW-значение от акселлерометра)
	    
		WindowManager windowMgr = (WindowManager)this.getSystemService(WINDOW_SERVICE);
        int rotationIndex = windowMgr.getDefaultDisplay().getRotation();
        if (rotationIndex == 1 || rotationIndex == 3){
        	xRaw = -e.values[1];
        }
        else{
        	xRaw = e.values[0]; 	
        }
		MotionChanged(xRaw,VSeekBar.getProgress());
		xgl = xRaw;
	}
        
	private void MotionChanged(float x, int y) {
    	String directionL = "";
    	String directionR = "";
    	String cmdSendL,cmdSendR;
    	
    	Log.d(cBluetooth.TAG, Math.round(x)+"  "+y);
    	    	
    	xAxis = Math.round(x*pwmMax/xR);
        //yAxis = -y;
    	if(isRear) yAxis = y;
    	else yAxis = -y;
        
        if(xAxis > pwmMax) xAxis = pwmMax;
        else if(xAxis < -pwmMax) xAxis = -pwmMax;		// negative - tilt right (отриц. значение - наклон вправо)
        
        if(yAxis > pwmMax) yAxis = pwmMax;
        else if(yAxis < -pwmMax) yAxis = -pwmMax;		// negative - tilt forward (отриц. значение - наклон вперед)
        else if(yAxis >= 0 && yAxis < yThreshold) yAxis = 0;
        else if(yAxis < 0 && yAxis > -yThreshold) yAxis = 0;
        
        if(xAxis > 0) {		// if tilt to left, slow down the left engine (если влево, то тормозим левый мотор)
        	motorRight = yAxis;
        	if(Math.abs(Math.round(x)) > xR){
        		motorLeft = Math.round((x-xR)*pwmMax/(xMax-xR));
        		motorLeft = Math.round(-motorLeft * yAxis/pwmMax);
        		//if(motorLeft < -pwmMax) motorLeft = -pwmMax;
        	}
        	else motorLeft = yAxis - yAxis*xAxis/pwmMax;
        }
        else if(xAxis < 0) {		// tilt to right (наклон вправо)
        	motorLeft = yAxis;
        	if(Math.abs(Math.round(x)) > xR){
        		motorRight = Math.round((Math.abs(x)-xR)*pwmMax/(xMax-xR));
        		motorRight = Math.round(-motorRight * yAxis/pwmMax);
        		//if(motorRight > -pwmMax) motorRight = -pwmMax;
        	}
        	else motorRight = yAxis - yAxis*Math.abs(xAxis)/pwmMax;
        }
        else if(xAxis == 0) {
        	motorLeft = yAxis;
        	motorRight = yAxis;
        }
        
        if(motorLeft > 0) {			// tilt to backward (наклон назад)
        	directionL = "-";
        }      
        if(motorRight > 0) {		// tilt to backward (наклон назад)
        	directionR = "-";
        }
        motorLeft = Math.abs(motorLeft);
        motorRight = Math.abs(motorRight);
               
        if(motorLeft > pwmMax) motorLeft = pwmMax;
        if(motorRight > pwmMax) motorRight = pwmMax;
                
        cmdSendL = String.valueOf(commandLeft+directionL+motorLeft+"\r");
        cmdSendR = String.valueOf(commandRight+directionR+motorRight+"\r");
        
        if(BT_is_connect) bl.sendData(cmdSendL+cmdSendR);

        TextView textX = (TextView) findViewById(R.id.textViewX);
        TextView textY = (TextView) findViewById(R.id.textViewY);
        TextView mLeft = (TextView) findViewById(R.id.mLeft);
        TextView mRight = (TextView) findViewById(R.id.mRight);
        TextView textCmdSend = (TextView) findViewById(R.id.textViewCmdSend);
        
        if(show_Debug){
        	textX.setText(String.valueOf("X:" + String.format("%.1f",x) + "; xPWM:"+xAxis));
	        textY.setText(String.valueOf("Y:" + String.valueOf(y) + "; yPWM:"+yAxis));
	        mLeft.setText(String.valueOf("MotorL:" + directionL + "." + motorLeft));
	        mRight.setText(String.valueOf("MotorR:" + directionR + "." + motorRight));
	        textCmdSend.setText(String.valueOf("Send:" + cmdSendL.toUpperCase(Locale.getDefault()) + cmdSendR.toUpperCase(Locale.getDefault())));
        }
        else{
        	textX.setText("");
        	textY.setText("");
        	mLeft.setText("");
        	mRight.setText("");
        	textCmdSend.setText("");
        }
        
    }
   
    private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);  
    	
    	address = mySharedPreferences.getString("pref_MAC_address", address);			// the first time we load the default values (первый раз загружаем дефолтное значение)
    	xMax = Integer.parseInt(mySharedPreferences.getString("pref_xMax", String.valueOf(xMax)));
    	xR = Integer.parseInt(mySharedPreferences.getString("pref_xR", String.valueOf(xR)));
    	yMax = Integer.parseInt(mySharedPreferences.getString("pref_yMax", String.valueOf(yMax)));
    	yThreshold = Integer.parseInt(mySharedPreferences.getString("pref_yThreshold", String.valueOf(yThreshold)));
    	pwmMax = Integer.parseInt(mySharedPreferences.getString("pref_pwmMax", String.valueOf(pwmMax)));
    	show_Debug = mySharedPreferences.getBoolean("pref_Debug", false);
    	commandLeft = mySharedPreferences.getString("pref_commandLeft", commandLeft);
    	commandRight = mySharedPreferences.getString("pref_commandRight", commandRight);
    	commandHorn = mySharedPreferences.getString("pref_commandHorn", commandHorn);
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
    	BT_is_connect = bl.BT_Connect(address, false);
    	mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
    	super.onPause();
    	bl.BT_onPause();
    	
    	mSensorManager.unregisterListener(this);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
    }
    
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub
    }
}
