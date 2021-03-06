package io.gobelieve.im.demo;

import android.app.Application;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.beetle.bauhinia.api.IMHttpAPI;
import com.beetle.bauhinia.api.body.PostDeviceToken;
import com.beetle.bauhinia.db.GroupMessageDB;
import com.beetle.bauhinia.db.GroupMessageHandler;
import com.beetle.bauhinia.db.PeerMessageDB;
import com.beetle.bauhinia.db.PeerMessageHandler;
import com.beetle.bauhinia.tools.FileCache;
import com.beetle.im.IMService;
import com.huawei.android.pushagent.api.PushManager;
import com.xiaomi.mipush.sdk.MiPushClient;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;


/**
 * PushDemoApplication
 * Description:
 */
public class PushDemoApplication extends Application {
    private static PushDemoApplication sApplication;

    private String mXiaomiPushToken = null;
    private String mHuaweiPushToken = null;
    private boolean mIsLogin = false;
    private boolean mIsBind = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;

        IMService mIMService = IMService.getInstance();
        //app可以单独部署服务器，给予第三方应用更多的灵活性
        //sandbox地址:"sandbox.imnode.gobelieve.io", "sandbox.pushnode.gobelieve.io"
        //"http://sandbox.api.gobelieve.io",
        mIMService.setHost("imnode.gobelieve.io");
        IMHttpAPI.setAPIURL("http://api.gobelieve.io");
        initPush();

        String androidID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        //设置设备唯一标识,用于多点登录时设备校验
        mIMService.setDeviceID(androidID);

        //监听网路状态变更
        mIMService.registerConnectivityChangeReceiver(getApplicationContext());

        //可以在登录成功后，设置每个用户不同的消息存储目录
        FileCache fc = FileCache.getInstance();
        fc.setDir(this.getDir("cache", MODE_PRIVATE));
        PeerMessageDB db = PeerMessageDB.getInstance();
        db.setDir(this.getDir("peer", MODE_PRIVATE));
        GroupMessageDB groupDB = GroupMessageDB.getInstance();
        groupDB.setDir(this.getDir("group", MODE_PRIVATE));

        mIMService.setPeerMessageHandler(PeerMessageHandler.getInstance());
        mIMService.setGroupMessageHandler(GroupMessageHandler.getInstance());
    }

    private void initPush() {
        // 华为设备启动华为push，其他设备启动小米push
        if (isHuaweiDevice()) {
            initHuaweiPush();
        } else {
            initXiaomiPush();
        }
    }

    public static PushDemoApplication getApplication() {
        return sApplication;
    }

    private boolean isXiaomiDevice() {
        String os = Build.HOST;
        return !TextUtils.isEmpty(os) && os.toLowerCase().contains("miui");
    }

    private void initXiaomiPush() {
        // 注册push服务，注册成功后会向XiaomiPushReceiver发送广播
        // 可以从onCommandResult方法中MiPushCommandMessage对象参数中获取注册信息
        String appId = "2882303761517422920";
        String appKey = "5111742288920";
        MiPushClient.registerPush(this, appId, appKey);
    }

    public void setXiaomiPushToken(String token) {
        this.mXiaomiPushToken = token;
        if (!TextUtils.isEmpty(mXiaomiPushToken) && mIsLogin && !mIsBind) {
            // 已登录尚未绑定时
            bindWithXiaomi();
        }
    }

    private boolean isHuaweiDevice() {
        String os = Build.HOST;
        return !TextUtils.isEmpty(os) && os.toLowerCase().contains("huawei");
    }

    private void initHuaweiPush() {
        PushManager.requestToken(this);
    }

    public void setHuaweiPushToken(String token) {
        this.mHuaweiPushToken = token;
        if (!TextUtils.isEmpty(mHuaweiPushToken) && mIsLogin && !mIsBind) {
            // 已登录尚未绑定时
            bindWithHuawei();
        }
    }

    public void bindDeviceTokenToIM() {
        mIsLogin = true;
        if (isHuaweiDevice()) {
            // 由于华为推送的token是通过回调返回，此时可能还未获取，需等HuaweiPushReceiver执行setHuaweiPushToken
            if (!TextUtils.isEmpty(mHuaweiPushToken)) {
                bindWithHuawei();
            }
        } else {
            // 小米情况同华为
            if (!TextUtils.isEmpty(mXiaomiPushToken)) {
                bindWithXiaomi();
            }
        }
    }

    private void bindWithHuawei() {
        PostDeviceToken postDeviceToken = new PostDeviceToken();
        postDeviceToken.hwDeviceToken = mHuaweiPushToken;
        bindDeviceTokenToIM(postDeviceToken);
    }

    private void bindWithXiaomi() {
        PostDeviceToken postDeviceToken = new PostDeviceToken();
        postDeviceToken.xmDeviceToken = mXiaomiPushToken;
        bindDeviceTokenToIM(postDeviceToken);
    }

    private void bindDeviceTokenToIM(PostDeviceToken postDeviceToken) {
        IMHttpAPI.Singleton().bindDeviceToken(postDeviceToken)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object obj) {
                        Log.i("im", "bind success");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.i("im", "bind fail");
                    }
                });
    }
}
