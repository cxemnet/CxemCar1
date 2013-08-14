/**
 *  Class for Bluetooth
 *  @version 1.2.1
 *  14.08.2013
 *  Koltykov A.V. http://cxem.net, http://english.cxem.net
 *  
 */

package com.cxem_car;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

public class cBluetooth{
	
	public final static String TAG = "BL_4WD";
	
	private static BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;
	private ConnectedThread mConnectedThread;

    // SPP UUID service 
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    private final Handler mHandler;
    
    // statuses for Handler (статусы для Handler)
    public final static int BL_NOT_AVAILABLE = 1;        	// Bluetooth is not available (Bluetooth не включен)
    public final static int BL_INCORRECT_ADDRESS = 2;		// incorrect MAC-address (неправильный MAC-адрес)
    public final static int BL_REQUEST_ENABLE = 3;			// request enable Bluetooth (запрос на включение Bluetooth)
    public final static int BL_SOCKET_FAILED = 4;			// socket error (ошибка SOCKET)
    public final static int RECIEVE_MESSAGE = 5;			// receive message (приняли сообщение)
      
    cBluetooth(Context context, Handler handler){
    	btAdapter = BluetoothAdapter.getDefaultAdapter();
    	mHandler = handler;
        if (btAdapter == null) {
        	mHandler.sendEmptyMessage(BL_NOT_AVAILABLE);
            return;
        }
    }
   
    public void checkBTState() {
    	if(btAdapter == null) { 
     		mHandler.sendEmptyMessage(BL_NOT_AVAILABLE);
    	} else {
    		if (btAdapter.isEnabled()) {
    			Log.d(TAG, "Bluetooth ON");
    		} else {
    			mHandler.sendEmptyMessage(BL_REQUEST_ENABLE);
    		}
    	}
	}
    
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }
    
    public boolean BT_Connect(String address, boolean listen_InStream) {   	
    	Log.d(TAG, "...On Resume...");  
    	
    	boolean connected = false;
    	
    	if(!BluetoothAdapter.checkBluetoothAddress(address)){
    		mHandler.sendEmptyMessage(BL_INCORRECT_ADDRESS);
    		return false;
    	}
    	else{
    		    		
    		BluetoothDevice device = btAdapter.getRemoteDevice(address);
    		try {
				btSocket = createBluetoothSocket(device);
			} catch (IOException e1) {
	        	Log.e(TAG, "In BT_Connect() socket create failed: " + e1.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	    		return false;
			}
	        
    		if (btAdapter.isDiscovering()) {
    			btAdapter.cancelDiscovery();
    		}
	        
	        Log.d(TAG, "...Connecting...");
	        try {
	          btSocket.connect();
	          Log.d(TAG, "...Connection ok...");
	        } catch (IOException e) {
	          try {
	            btSocket.close();
	          } catch (IOException e2) {
	        	  Log.e(TAG, "In BT_Connect() unable to close socket during connection failure" + e2.getMessage());
	        	  mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	        	  return false;
	          }
	        }
	         
	        // Create a data stream so we can talk to server.
	        Log.d(TAG, "...Create Socket...");
	     
	        try {
	        	outStream = btSocket.getOutputStream();
	        	connected = true;
	        } catch (IOException e) {
	        	Log.e(TAG, "In BT_Connect() output stream creation failed:" + e.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	        	return false;
	        }
			if(listen_InStream) {		// whether to create a thread for the incoming data (нужно ли создавать поток для входящих данных)
				mConnectedThread = new ConnectedThread();
		    	mConnectedThread.start();
    		}
    	}
    	return connected;
	}
           
    public void BT_onPause() {
    	Log.d(TAG, "...On Pause...");
    	if (outStream != null) {
    		try {
    	        outStream.flush();
    	    } catch (IOException e) {
	        	Log.e(TAG, "In onPause() and failed to flush output stream: " + e.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	        	return;
    	    }
    	}

    	if (btSocket != null) {
	    	try {
	    		btSocket.close();
	    	} catch (IOException e2) {
	        	Log.e(TAG, "In onPause() and failed to close socket." + e2.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	        	return;
	    	}
    	}
    }
        
    public void sendData(String message) {
        byte[] msgBuffer = message.getBytes();
     
        Log.i(TAG, "Send data: " + message);
        
        if (outStream != null) {
	        try {
	        	outStream.write(msgBuffer);
	        } catch (IOException e) {
	        	Log.e(TAG, "In onResume() exception occurred during write: " + e.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	        	return;      
	        }
        } else Log.e(TAG, "Error Send data: outStream is Null");
	}
    
    private class ConnectedThread extends Thread {

    	private final InputStream mmInStream;
		 
    	public ConnectedThread() {
    		InputStream tmpIn = null;
		 
    		// Get the input and output streams, using temp objects because
    		// member streams are final
    		try {
    			tmpIn = btSocket.getInputStream();
    		} catch (IOException e) {
    			Log.e(TAG, "In ConnectedThread() error getInputStream(): " + e.getMessage());
    		}
		 
    		mmInStream = tmpIn;
    	}
		 
    	public void run() {
    		byte[] buffer = new byte[256];  // buffer store for the stream
    		int bytes; 						// bytes returned from read()

    		// Keep listening to the InputStream until an exception occurs
    		while (true) {
    			try {
    				// Read from the InputStream
    				bytes = mmInStream.read(buffer);
    				mHandler.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();
    			} catch (IOException e) {
    				break;
    			}
    		}
    	}
    }
}