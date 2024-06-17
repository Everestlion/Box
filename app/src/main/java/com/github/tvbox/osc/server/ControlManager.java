package com.github.tvbox.osc.server;

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
import com.github.tvbox.osc.ui.activity.PlayActivity;
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
        instance.mVodController = vodController;
    }

    public static void playFinish() {
        instance.mPlayActivity = null;
    }

    public static void setPlayActivity(PlayActivity playActivity) {
        instance.mPlayActivity = playActivity;
    }

    public static void obtainMessage(String cmd) {
        Message msg = Message.obtain();
        msg.what = 10086;
        msg.obj = cmd;
        if (get().mPlayActivity != null) {
            instance.mPlayActivity.obtainMessage(msg);
        } else {
            Log.d("Linkman", "mPlayActivity is null");
        }
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
