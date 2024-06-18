package com.github.tvbox.osc.server;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.receiver.DetailReceiver;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.PlayActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
public class ControlManager {
    private static ControlManager instance;
    private VodController mVodController;
    private RemoteServer mServer = null;
    public static Context mContext;
    private PlayActivity mPlayActivity;
    private FastSearchActivity mFastSearchActivity;
    private SearchActivity mSearchActivity;

    private ControlManager() {

    }

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) {
                    instance = new ControlManager();
                }
            }
        }
        return instance;
    }

    public static void init(Context context) {
        mContext = context;
    }

    public static void setVodController(VodController vodController) {
        Log.d("Linkman", "setVodController");
        get().mVodController = vodController;
    }

    public static void playFinish() {
        Log.d("Linkman", "playActivity playFinish");
        get().mPlayActivity = null;
    }

    public static void setPlayActivity(PlayActivity playActivity) {
        Log.d("Linkman", "setPlayActivity");
        get().mPlayActivity = playActivity;
    }

    public static void obtainMessagePlayControl(String cmd) {
        Message msg = Message.obtain();
        msg.what = 10086;
        msg.obj = cmd;
        if (get().mVodController != null) {
            get().mVodController.obtainMessage(msg);
        } else {
            Log.d("Linkman", "mVodController is null");
        }
    }

    public static void obtainMessageFastSearch(String cmd) {
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = cmd;
        if (get().mVodController != null) {
            get().mVodController.obtainMessage(msg);
        } else {
            Log.d("Linkman", "mVodController is null");
        }
    }

    public static void obtainMessageSearch(String posStr) {
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = chineseToArabic(posStr) - 1;
        Log.d("Linkman", "obtainMessageSearch:" + msg.obj);
        if (get().mSearchActivity != null) {
            get().mSearchActivity.obtainMessage(msg);
        } else {
            Log.d("Linkman", "mSearchActivity is null");
        }

    }

    public static int chineseToArabic(String chineseNumber) {
        String[] simpleChineseNumbers = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        char[] chineseNumberCharArray = chineseNumber.toCharArray();
        int result = 0;
        int temp = 1;
        int count = 0;

        for (int i = 0; i < chineseNumberCharArray.length; i++) {
            boolean isUnit = false;
            for (int j = 0; j < simpleChineseNumbers.length; j++) {
                if (String.valueOf(chineseNumberCharArray[i]).equals(simpleChineseNumbers[j])) {
                    if (j == 0) {
                        temp = 1;
                    } else if (i == chineseNumberCharArray.length - 1 && (j == 1 || j == 2 || j == 3 || j == 4)) { // 十-一十
                        temp = j;
                    } else if (i == chineseNumberCharArray.length - 1 && j == 9) { // 十一
                        temp = 11;
                    } else if (i != chineseNumberCharArray.length - 1 && (j == 4 || j == 9)) { // 百十、千十
                        isUnit = true;
                        temp = j + 1;
                    } else {
                        temp = j + 1;
                    }

                    if (isUnit) {
                        result += temp * Math.pow(10, count);
                        count++;
                        isUnit = false;
                    }

                    break;
                }
            }
        }

        return result;
    }

    public static void setFastSearchActivity(FastSearchActivity fastSearchActivity) {
        Log.d("Linkman", "setFastSearchActivity");
        get().mFastSearchActivity = fastSearchActivity;
    }

    public static void setSearchActivity(SearchActivity searchActivity) {
        get().mSearchActivity = searchActivity;
    }

    public String getAddress(boolean local) {
        return local ? mServer.getLoadAddress() : mServer.getServerAddress();
    }

    public void startServer() {
        if (mServer != null) {
            return;
        }
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, mContext);
            mServer.setDataReceiver(new DataReceiver() {
                @Override
                public void onTextReceived(String text) {
                    if (!TextUtils.isEmpty(text)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("title", text);
                        intent.setAction(SearchReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, SearchReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }

                @Override
                public void onApiReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url));
                }

                @Override
                public void onLiveReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_LIVE_URL_CHANGE, url));
                }

                @Override
                public void onEpgReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_EPG_URL_CHANGE, url));
                }

                @Override
                public void onProxysReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PROXYS_CHANGE, url));
                }

                @Override
                public void onPushReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url));
                }

                @Override
                public void onMirrorReceived(String id, String sourceKey) {
                    if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(sourceKey)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("id", id);
                        bundle.putString("sourceKey", sourceKey);
                        intent.setAction(DetailReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, DetailReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }

                @Override
                public void onVoiceCmdReceived(String text) {
                    Log.d("Linkman", "onVoiceCmdReceived" + text);
                    if(mVodController != null && text.length() > 0) {
                        mVodController.processCmd(text);
                    }
                }
            });
            try {
                mServer.start();
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort);
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) {
            mServer.stop();
        }
    }
}
