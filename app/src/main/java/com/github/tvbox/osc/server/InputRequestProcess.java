package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "live": {
                            mDataReceiver.onLiveReceived(params.get("url").trim());
                            break;
                        }
                        case "epg": {
                            mDataReceiver.onEpgReceived(params.get("url").trim());
                            break;
                        }
                        case "proxys": {
                            mDataReceiver.onProxysReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            // 暂未实现
                            mDataReceiver.onPushReceived(params.get("url").trim());
                            break;
                        }
                        case "mirror": {
                            //推送当前电影、电视剧……
                            mDataReceiver.onMirrorReceived(params.get("id").trim(), params.get("sourceKey").trim());
                            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "mirrored");
                        }
                        case "voicecmd": {
                            // 语音指令
                            String cmd = params.get("cmd").trim();
                            if(cmd.startsWith("搜索")) {
                                mDataReceiver.onTextReceived(cmd.replace("搜索", ""));
                            }if(cmd.startsWith("选择第")) {
                                int beginIndex = "选择第".length();
                                int endIndex = cmd.endsWith("个") ? cmd.length() - "个".length() : cmd.length();
                                String posStr = cmd.substring(beginIndex, endIndex);
                                ControlManager.obtainMessageSearch(posStr);
                            } else {
                                ControlManager.obtainMessagePlayControl(cmd);
                            }
                            break;
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
