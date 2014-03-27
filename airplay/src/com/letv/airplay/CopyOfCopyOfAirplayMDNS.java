package com.letv.airplay;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;

class CopyOfCopyOfAirplayMDNS {
	public static CopyOfCopyOfAirplayMDNS _instance;
	private HandlerThread handlerThread;
	private MyHandler myHandler;

	public MyHandler getMyHandler() {
		return myHandler;
	}

	private AirplayService service = null;
	/**
	 * bind interface only one.
	 */
	private Map<String, JmDNS> jmdns = new HashMap<String, JmDNS>();

	/**
	 * bind interface
	 */
	private String connected_device = null;

	private static final int MDNS = 1000;
	public static final int MDNS_START = MDNS + 1;
	public static final int MDNS_STOP = MDNS + 2;
	private static final int MDNS_REFRESH = MDNS + 3;
	private static final int MDNS_CHANGE = MDNS + 4; // devicename

	private static final String AIRPLAY = "_airplay._tcp.local";
	private static final String AIRTUNES = "_raop._tcp.local";
	private static int PORT1 = 36667; // airplay
	private static int PORT2 = 36666; // airtune

	private ServiceInfo serviceInfoAirplay = null;
	private ServiceInfo serviceInfoAirtunes = null;
	private static final String TAG = "jmdns";

	private Timer pTimer;
	private TimerTask pTimerTask;

	boolean first = true;

	class MyHandler extends Handler {
		public MyHandler(Looper looper) {
			super(looper);
		}

		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			Log.d(TAG, "handleMessage: " + msg.what);
			switch (msg.what) {
			case MDNS_START:
			case MDNS_REFRESH:
				if (true == first) {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					stopJmdns();
					BindInterface();
					first = false;
				} else {
					stopJmdns();
					BindInterface();
				}
				break;
			case MDNS_CHANGE:
				break;
			case MDNS_STOP:
				stopJmdns();
				break;

			}
		}
	}

	public CopyOfCopyOfAirplayMDNS(AirplayService airplayService) {
		service = airplayService;
		handlerThread = new HandlerThread("jmdns_handler_thread");
		handlerThread.start();
		myHandler = new MyHandler(handlerThread.getLooper());
	}

	public static CopyOfCopyOfAirplayMDNS getInstance(
			AirplayService airplayService) {
		if (_instance == null) {
			_instance = new CopyOfCopyOfAirplayMDNS(airplayService);
		}
		return _instance;
	}

	public void clearInstance() {
		if (pTimer != null) {
			pTimer.cancel();
			pTimer = null;
		}
		if (pTimerTask != null) {
			pTimerTask.cancel();
			pTimerTask = null;
		}

		if (myHandler != null) {
			myHandler.removeMessages(MDNS_START);
			myHandler.removeMessages(MDNS_REFRESH);
			myHandler.sendEmptyMessage(MDNS_STOP);
		}

		if (_instance != null)
			_instance = null;

		release();
		myHandler = null;
		handlerThread.quit();
	}

	private boolean stopJmdns() {

		Log.d(TAG, "stopJmdns start");
		if (jmdns != null) {
			Set<String> keys = jmdns.keySet();
			for (String key : keys) {
				JmDNS jmdn = jmdns.get(key);
				jmdn.unregisterAllServices();
				try {
					jmdn.close();
					jmdns = null;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		Log.d(TAG, "stopJmdns end");
		return true;

	}

	private boolean release() {
		Log.d(TAG, "release start");
		if (jmdns != null) {
			Set<String> keys = jmdns.keySet();
			for (String key : keys) {
				JmDNS jmdn = jmdns.get(key);
				jmdn.unregisterAllServices();
				try {
					jmdn.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.d(TAG, "release end close false");
					return false;
				}
				jmdns = null;
			}
		}
		Log.d(TAG, "release end true");
		return true;
	}

	private boolean BindInterface() {

		InetAddress ip = null;

		String deviceName = "Letv-";
		ConnectivityManager manager = (ConnectivityManager) service
				.getApplicationContext().getSystemService(
						Context.CONNECTIVITY_SERVICE);
		if (manager == null) {
			return false;
		}
		NetworkInfo networkinfo = manager.getActiveNetworkInfo();

		if (networkinfo == null || !networkinfo.isAvailable()) {
			return false;
		}

		String type = networkinfo.getTypeName();

		Log.d(TAG, "start: " + type);

		if (networkinfo.isConnected() == true) {
			try {
				List<NetworkInterface> interfaces = Collections
						.list(NetworkInterface.getNetworkInterfaces());
				for (NetworkInterface iface : interfaces) {
					System.out.println(iface.getDisplayName());
					List<InetAddress> addresses = Collections.list(iface
							.getInetAddresses());
					for (InetAddress address : addresses) {
						if (address instanceof Inet4Address) {
							String mac = getMacAddress(iface, "");
							if (mac == null || mac.equals("")) {
								continue;
							}
							createJMDN(deviceName, address, iface);

							System.out.println(mac);
						}
					}
				}
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}

		}
		return true;

	}

	private boolean createJMDN(String deviceName, InetAddress ip,
			NetworkInterface inet) {
		try {

			SimpleDateFormat formatter = new SimpleDateFormat("HHmmss");
			Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
			String str = formatter.format(curDate);

			String tmp1, tmp2, tmp3 = null;
			tmp1 = getMacAddress(inet, "");
			tmp3 = deviceName + tmp1 + str;

			Log.d(TAG, "BindInterface target: " + tmp3);

			deviceName = SystemProperties.get("net.hostname");

			tmp1 = getMacAddress(inet, ":");
			final HashMap<String, String> values1 = new HashMap<String, String>();
			values1.put("deviceid", tmp1);
			values1.put("features", "0x11B");
			values1.put("model", "Letv,1");
			values1.put("srcvers", "130.14");
			tmp1 = getMacAddress(inet, "");
			tmp3 = tmp1 + "@" + deviceName;
			final HashMap<String, String> values2 = new HashMap<String, String>();

			values2.put("txtvers", "1");
			values2.put("cn", "0,1");
			values2.put("ch", "2");
			values2.put("ek", "1");
			values2.put("et", "0,1");
			values2.put("sv", "false");
			values2.put("tp", "UDP");
			values2.put("sm", "false");
			values2.put("ss", "16");
			values2.put("sr", "44100");
			values2.put("pw", "false");
			values2.put("vn", "3");
			values2.put("da", "true");
			values2.put("vs", "130.14");
			values2.put("md", "0,1,2");
			values2.put("am", "Letv,1");

			serviceInfoAirplay = ServiceInfo.create(AIRPLAY, deviceName,
					PORT1 += 2, 0, 0, values1);
			serviceInfoAirtunes = ServiceInfo.create(AIRTUNES, tmp3,
					PORT2 -= 2, 0, 0, values2);
			JmDNS jmdn = JmDNS.create(ip, tmp3);
			// for (JmDNS jmdn : jmdns) {
			jmdn.registerService(serviceInfoAirplay);
			jmdn.registerService(serviceInfoAirtunes);
			jmdn.registerFlushAn();
			jmdns.put(ip.getHostAddress(), jmdn);
			// }
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.d(TAG, "BindInterface end jmdns create false");
			return false;
		}
		// }

		// return false;
	}

	private void StoreCurrent(String name) {
		Log.d(TAG, "StoreCurrent start " + name);
		connected_device = name;
		Log.d(TAG, "StoreCurrent end " + connected_device);
	}

	private String getMacAddress(NetworkInterface ni, String insert) {
		String mac = "";
		StringBuffer sb = new StringBuffer();

		byte[] macs;
		try {
			macs = ni.getHardwareAddress();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		if (macs != null) {
			for (int i = 0; i < macs.length; i++) {
				mac = Integer.toHexString(macs[i] & 0xFF);

				if (mac.length() == 1) {
					mac = '0' + mac;
				}

				sb.append(mac + insert);
			}

			mac = sb.toString();

			if (insert == ":")
				mac = mac.substring(0, mac.length() - 1);
		}
		return mac;
	}

}
