package com.hexilon.icbcgoldmarket;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

//import org.w3c.dom.Document;
//import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection;

public class UpdateService extends Service {
    private final static String TAG = "UpdateService";
    public final static int ICBC_RMB_GOLD = 0x0001;
    public final static int ICBC_RMB_SILVER = 0x0002;
    public final static int ICBC_RMB_PT = 0x0004;
    public final static int ICBC_RMB_PD = 0x0008;
    public final static int ICBC_US_GOLD = 0x0010;
    public final static int ICBC_US_SILVER = 0x0020;
    public final static int ICBC_US_PT = 0x0040;
    public final static int ICBC_US_PD = 0x0080;
    private static float ICBC_RMB_GOLD_PRICE_DIFF = 0.04f;
    private static float ICBC_RMB_SILVER_PRICE_DIFF = 0.04f;
    private static float ICBC_US_GOLD_PRICE_DIFF = 0.04f;
    private static float ICBC_US_SILVER_PRICE_DIFF = 0.04f;

    public static int ICBC_UPDATE_PERIOD_WIFI = 10*1000;//10s
    public static int ICBC_UPDATE_PERIOD_GPRS = 30*1000;//30s
    public static int ICBC_UPDATE_PERIOD_NO_CONNECT = 60*1000;

    private static String URL_ICBC_SUMMARY =
            "http://m.icbc.com.cn/WapDynamicSite/Windroid/GoldMarket/AccResponse.aspx";


    private GoldElement mGoldElements[] = new GoldElement[8];
    private Date mCurrDate;
    int mNotifyType = 1;
    boolean mIsGprsConnect = false;
    boolean mIsWIFIConnect = false;
    NotificationManager mNM;
    final static int NOTIFY_ID = 1000;

    class GoldElement {
       String mName;
       float mPrice = 0f;
       float mChange = 0f;

       public GoldElement(){}
   };

    public UpdateService() {
        for (int i = 0; i < 8; i++) {
            mGoldElements[i] = new GoldElement();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNM = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        ConnectivityManager connManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo gprs = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        mIsGprsConnect = gprs.isConnected();
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        mIsWIFIConnect = wifi.isConnected();
        Log.d(TAG, "gprs connect:" + mIsGprsConnect + " wifi connect:" + mIsWIFIConnect);

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(new NetworkConnectChangedReceiver(), filter);

        /** 创建一个线程，定时更新数据 */
        new Thread(new Runnable() {
            public void run() {
                int count = 0;

                while (true) {
                    if (mIsWIFIConnect || mIsGprsConnect) {
                        getUrlData(URL_ICBC_SUMMARY);
                        sendNotification();
                        count++;
                        try {
                            if (mIsWIFIConnect) {
                                Log.d(TAG, "WIFI network, sleep:" + ICBC_UPDATE_PERIOD_WIFI);
                                Thread.sleep(ICBC_UPDATE_PERIOD_WIFI);
                            } else {
                                Log.d(TAG, "GPRS network, sleep:" + ICBC_UPDATE_PERIOD_GPRS);
                                Thread.sleep(ICBC_UPDATE_PERIOD_GPRS);
                            }
                        } catch (InterruptedException e) {

                        }

                        Log.v(TAG, "Update data count is:" + count);
                    } else {
                        mNM.cancel(NOTIFY_ID);
                        Log.d(TAG, "Without network, sleep:" + ICBC_UPDATE_PERIOD_NO_CONNECT);
                        try {
                            Thread.sleep(ICBC_UPDATE_PERIOD_NO_CONNECT);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }).start();
    }

    void getUrlData(String strUrl) {
        try {
            //Log.v(TAG, "strUrl is:" + strUrl);
            /*org.jsoup.nodes.Document doc = Jsoup.parse(new URL(strUrl).openStream(),
                    "GBK", strUrl);
            org.jsoup.Connection conn = Jsoup.connect(strUrl).timeout(10000);
            if(conn.execute().statusCode() == 200) {
                //org.jsoup.nodes.Document doc = conn.get();
                org.jsoup.select.Elements elements = doc.getElementsByClass("ebdp-android-textlabel");
                for (org.jsoup.nodes.Element e : elements) {
                    Log.v(TAG, "e.value:" + e.val() + " data:" + e.data());
                }
            }*/
            URL url = new URL(strUrl);
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();

            int responseCode = urlConnection.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_OK) {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                Log.v(TAG, "in:" + in);
                parser(in);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setNofifyType(int type) {
        if (getIndexByType(type) < 8) {
            mNotifyType = getIndexByType(type);
        }
    }

    void sendNotification() {
        Notification.Builder builder = new Notification.Builder(getApplicationContext());
        //builder.setContentInfo("补充内容");
        String content = mGoldElements[mNotifyType].mName;
        content += "\t" + mGoldElements[mNotifyType].mPrice;
        content += "\t" + mGoldElements[mNotifyType].mChange;
        builder.setContentText(content);
        builder.setContentTitle("工行贵金属");
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setTicker("工行贵金属数据更新");
        builder.setAutoCancel(false);
        builder.setWhen(System.currentTimeMillis());
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);
        Notification notification = builder.getNotification();
        mNM.notify(NOTIFY_ID, notification);
    }

    private void parser(InputStream in) {
        char[] buffer = new char[128];
        int len = -1;
        InputStreamReader isr = new InputStreamReader(in);

        String tempStr;
        StringBuffer buff = new StringBuffer();

        try {
            while ((len = isr.read(buffer, 0, 128)) > -1) {
            //while ((len = in.read()) > -1) {
                //buff.append((char)len);
                //Log.i(TAG, "bytebuff:" + new String(buffer));
                buff.append(new String(buffer));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "buff len:" + buff.length());
        int start, next_start, end, index = 0, i = 0, j = 0;
        while ((index = buff.indexOf("<tr class=\"ebdp-android-clickabletr\"", index)) >= 0) {
            //Log.i(TAG, "<tr class=\"ebdp-android-clickabletr\" start:" + index + " j:" + j);
            i = 0;//一行开始
            while (i < 3) {
                //Log.i(TAG, "index:" + index);
                start = buff.indexOf("<span class=\"ebdp-android-textlabel\"", index);
                //Log.i(TAG, "<span class=\"ebdp-android-textlabel\" start:" + start);
                next_start = buff.indexOf("<span class=\"ebdp-android-textlabel\"", start+1);
                //Log.i(TAG, "after 512:" + buff.substring(start, start+ 512));
                start = buff.indexOf("\">\n", start);
                //Log.i(TAG, "> start:" + start);
                end = buff.indexOf("</span>", start);
                if (j < 7 && end > next_start) {
                    index = end;
                    break;
                }
                //Log.i(TAG, "</span> end:" + end);
                Log.i(TAG, "element " + i + " :"  + buff.substring(start+3, end));
                String elem = buff.substring(start+3, end);
                if (i == 0) {
                    mGoldElements[j].mName = elem;
                } else if (i == 1) {
                    if (elem.charAt(0) == '-'
                            || (elem.charAt(0) >= '0' && elem.charAt(0) <= '9')) {
                        mGoldElements[j].mPrice = Float.parseFloat(elem);
                    }
                } else if (i == 2) {
                    if (elem.charAt(0) == '-'
                            || (elem.charAt(0) >= '0' && elem.charAt(0) <= '9')) {
                        mGoldElements[j].mChange = Float.parseFloat(elem);
                    }
                }

                index = end;
                i++;
            }

            j ++;
        }

        /*domBuilder = domfac.newDocumentBuilder();
        Document doc = domBuilder.parse(in);
        Element root = doc.getDocumentElement();
        NodeList list0 = root.getChildNodes();
        if (list0 != null) {
            for (int i0 = 0; i0 < list0.getLength(); i0++ ) {
                Node node1 = list0.item(i0);
                if(node1.getNodeType() != Node.ELEMENT_NODE)
                    continue;
                Element item1 = (Element)node1;
                String name1 = item1.getNodeName();
                Log.i(TAG, "name1=[" + name1 + "]");
            }
        }*/
    }

    public String getGoldName(int type) {
        if (type < ICBC_RMB_GOLD || type > ICBC_US_PD) {
            return "";
        }

        return mGoldElements[getIndexByType(type)].mName;
    }

    int getIndexByType(int type) {
        int index;
        switch (type) {
            case ICBC_RMB_GOLD:
                index = 0;
                break;
            case ICBC_RMB_SILVER:
                index = 1;
                break;
            case ICBC_RMB_PT:
                index = 2;
                break;
            case ICBC_RMB_PD:
                index = 3;
                break;
            case ICBC_US_GOLD:
                index = 4;
                break;
            case ICBC_US_SILVER:
                index = 5;
                break;
            case ICBC_US_PT:
                index = 6;
                break;
            case ICBC_US_PD:
                index = 7;
                break;
            default:
                index = 0;
                break;
        }

        return index;
    }

    public float getGoldPrice(int type) {
        if (type < ICBC_RMB_GOLD || type > ICBC_US_PD) {
            return 0.0f;
        }
        return mGoldElements[getIndexByType(type)].mPrice;
    }

    public float getGoldChange(int type) {
        if (type < ICBC_RMB_GOLD || type > ICBC_US_PD) {
            return 0.0f;
        }
        return mGoldElements[getIndexByType(type)].mChange;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return new ServiceBinder();
    }

    class ServiceBinder extends Binder {
        public UpdateService getService() {
            return UpdateService.this;
        }
    }

    private class NetworkConnectChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() is calleld with " + intent);
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                ConnectivityManager manager =
                        (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo gprs = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                mIsGprsConnect = gprs.isConnected();
                NetworkInfo wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                mIsWIFIConnect = wifi.isConnected();
            }

            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int mWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN);
                mIsWIFIConnect = (mWifiState == WifiManager.WIFI_STATE_ENABLED) ? true:false;
            }
        }
    }
}
