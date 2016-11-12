package com.hexilon.icbcgoldmarket;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    private UpdateService mUpdateService;
    private LinearLayout mRmbGold;//黄金
    private LinearLayout mRmbSilver;//白银
    private LinearLayout mRmbPt;//铂金
    private LinearLayout mRmbPd;//钯金
    private LinearLayout mUSGold;
    private LinearLayout mUSSilver;
    private LinearLayout mUSPt;
    private LinearLayout mUSPd;

    private ServiceConnection mConnection = new ServiceConnection() {
        /** 获取服务对象时的操作 */
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            mUpdateService = ((UpdateService.ServiceBinder) service).getService();

        }

        /** 无法获取到服务对象时的操作 */
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
            mUpdateService = null;
        }
    };

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            if (mUpdateService == null) {
                return;
            }

            //Log.i(TAG, "handleMessage");
            int type = UpdateService.ICBC_RMB_GOLD
                    |UpdateService.ICBC_RMB_SILVER
                    |UpdateService.ICBC_RMB_PT
                    |UpdateService.ICBC_RMB_PD
                    |UpdateService.ICBC_US_GOLD
                    |UpdateService.ICBC_US_SILVER
                    |UpdateService.ICBC_US_PT
                    |UpdateService.ICBC_US_PD;
            updateGoldData(type);
        }
    };

    void updateGoldData(int types) {
        TextView goldName, goldPrice, goldChange;
        TableRow row;
        if ((types | UpdateService.ICBC_RMB_GOLD) != 0) {
            updateGoldData(mRmbGold, UpdateService.ICBC_RMB_GOLD);
        }
        if ((types | UpdateService.ICBC_RMB_SILVER) != 0) {
            updateGoldData(mRmbSilver, UpdateService.ICBC_RMB_SILVER);
        }
        if ((types | UpdateService.ICBC_RMB_PT) != 0) {
            updateGoldData(mRmbPt, UpdateService.ICBC_RMB_PT);
        }
        if ((types | UpdateService.ICBC_RMB_PD) != 0) {
            updateGoldData(mRmbPd, UpdateService.ICBC_RMB_PD);
        }
        if ((types | UpdateService.ICBC_US_GOLD) != 0) {
            updateGoldData(mUSGold, UpdateService.ICBC_US_GOLD);
        }
        if ((types | UpdateService.ICBC_US_SILVER) != 0) {
            updateGoldData(mUSSilver, UpdateService.ICBC_US_SILVER);
        }
        if ((types | UpdateService.ICBC_US_PT) != 0) {
            updateGoldData(mUSPt, UpdateService.ICBC_US_PT);
        }
        if ((types | UpdateService.ICBC_US_PD) != 0) {
            updateGoldData(mUSPd, UpdateService.ICBC_US_PD);
        }
    }

    void updateGoldData(LinearLayout row, int type) {
        TextView goldName, goldPrice, goldChange;
        goldName = (TextView) row.findViewById(R.id.gold_name);
        goldName.setText(mUpdateService.getGoldName(type));
        goldPrice = (TextView) row.findViewById(R.id.gold_price);
        goldPrice.setText("" + mUpdateService.getGoldPrice(type));
        if (mUpdateService.getGoldChange(type) > 0) {
            goldPrice.setTextColor(0xffff0000);
        } else if (mUpdateService.getGoldChange(type) < 0) {
            goldPrice.setTextColor(0xff00ff00);
        } else {
            goldPrice.setTextColor(0xff000000);
        }
        goldChange = (TextView) row.findViewById(R.id.gold_change);
        goldChange.setText("" + mUpdateService.getGoldChange(type));
        if (mUpdateService.getGoldChange(type) > 0) {
            goldChange.setTextColor(0xffff0000);
        } else if (mUpdateService.getGoldChange(type) < 0) {
            goldChange.setTextColor(0xff00ff00);
        } else {
            goldChange.setTextColor(0xff000000);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

       FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        mRmbGold = (LinearLayout) findViewById(R.id.rmb_gold);
        mRmbSilver = (LinearLayout) findViewById(R.id.rmb_silver);
        mRmbPt = (LinearLayout) findViewById(R.id.rmb_pt);
        mRmbPd = (LinearLayout) findViewById(R.id.rmb_pd);
        mUSGold = (LinearLayout) findViewById(R.id.us_gold);
        mUSSilver = (LinearLayout) findViewById(R.id.us_silver);
        mUSPt = (LinearLayout) findViewById(R.id.us_pt);
        mUSPd = (LinearLayout) findViewById(R.id.us_pd);
        //mHandler.post(mUpdateThd);
        new Thread(new Runnable() {
            @Override
            public void run(){
                while (!Thread.currentThread().isInterrupted()) {
                    Message message = new Message();
                    message.what = 1;
                    mHandler.sendMessage(message);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();


        Intent intent = new Intent(MainActivity.this, UpdateService.class);
        /** 进入Activity开始服务 */
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        this.unbindService(mConnection);
    }
}
