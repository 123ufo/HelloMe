package com.leo618.hellome.libcore.manager.net;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.leo618.hellome.libcore.interf.IRequestCallback;
import com.leo618.hellome.libcore.manager.net.cookie.PersistentCookieStore;
import com.leo618.hellome.libcore.util.FileStorageUtil;
import com.leo618.hellome.libcore.util.Logg;
import com.leo618.hellome.libcore.util.PackageManagerUtil;
import com.leo618.hellome.libcore.util.UIUtil;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * function:基于okhttp网络请求的封装.包含GET、POST、文件上传、文件下载
 * <p></p>
 * Created by lzj on 2016/4/26.
 */
@SuppressWarnings("WeakerAccess")
public class OkHttpClientWrap {
    private static final String TAG = OkHttpClientWrap.class.getSimpleName();
    private static final OkHttpClientWrap instance = new OkHttpClientWrap();
    private final OkHttpClient mOkHttpClient;
    private final PersistentCookieStore mPersistentCookieStore;
    private final Handler mUIHandler;
    private final AtomicLong mDefaultRequestTag = new AtomicLong(0);
    /** SSL通信CA证书 */
    private static final String CERT_CA_NAME = "";//ca.crt
    /** SSL通信私钥交换信任证书 */
    private static final String CERT_CLIENT_PFX_NAME = "";//xxx.pfx
    /** SSL通信客户端校验服务端通信密码 */
    private static final String CERT_PASSWORD = "";//client-xxx-passwd
    /** header:user-agent */
    private static final String User_Agent = UIUtil.getContext().getPackageName() + "/" + PackageManagerUtil.getPackageVersionName(UIUtil.getContext());

    private static final int DEFAULT_TIME_CONNECT = 15;//链接超时时间
    private static final int DEFAULT_TIME_READ = 15;//读超时时间
    private static final int DEFAULT_TIME_WRITE = 30;//写超时时间


    public static OkHttpClientWrap getInstance() {
        return instance;
    }

    private OkHttpClientWrap() {
        mOkHttpClient = new OkHttpClient();
        mOkHttpClient.setConnectTimeout(DEFAULT_TIME_CONNECT, TimeUnit.SECONDS);
        mOkHttpClient.setReadTimeout(DEFAULT_TIME_READ, TimeUnit.SECONDS);
        mOkHttpClient.setWriteTimeout(DEFAULT_TIME_WRITE, TimeUnit.SECONDS);
        mPersistentCookieStore = new PersistentCookieStore();
        mOkHttpClient.setCookieHandler(new CookieManager(mPersistentCookieStore, CookiePolicy.ACCEPT_ALL));// cookie enabled
        mUIHandler = new Handler(Looper.getMainLooper());
        Logg.d(TAG, "created OkHttpClientWrap instance!");
    }

    /**
     * Application onCreate调用.   初始化SSL,支持双向证书认证.
     */
    public void initSSLAccess() {
        if (TextUtils.isEmpty(CERT_CA_NAME)
                || TextUtils.isEmpty(CERT_CLIENT_PFX_NAME)
                || TextUtils.isEmpty(CERT_PASSWORD)) {
            getInstance().initFreeAllAccess();
        } else {
            try {
                new HttpsProxy().setCertificates(
                        new InputStream[]{UIUtil.getContext().getAssets().open(CERT_CA_NAME)},
                        UIUtil.getContext().getAssets().open(CERT_CLIENT_PFX_NAME), CERT_PASSWORD);
            } catch (IOException e) {
                Logg.e(TAG, "IOException : " + e.getMessage());
            }
        }
    }

    public void clearUserCookie() {
        if (mPersistentCookieStore == null) {
            return;
        }
        mPersistentCookieStore.removeAll();
    }

    //proxy class
    private final GetProxy mGetProxy = new GetProxy();//GET模块
    private final PostProxy mPostProxy = new PostProxy();//POST模块
    private final DownloadProxy mDownloadProxy = new DownloadProxy();//Download模块
    private final UploadProxy mUploadProxy = new UploadProxy();//Upoad模块

    //method for other
    public void get(String url, IRequestCallback callback, Object tag) {
        mGetProxy.get(url, callback, tag);
    }

    public void post(String url, Map<String, String> params, IRequestCallback callback, Object tag) {
        mPostProxy.post(url, params, callback, tag);
    }

    public void download(String url, String filePath, IRequestCallback callback, Object tag) {
        mDownloadProxy.download(url, filePath, callback, tag);
    }

    public void upload(String url, Map<String, String> paramMap, Map<String, File> fileMap, IRequestCallback callback, Object tag) {
        mUploadProxy.upload(url, paramMap, fileMap, callback, tag);
    }

    // ====================GetProxy======================= //
    private class GetProxy {

        public void get(String url, final IRequestCallback callback, Object tag) {
            deliveryResult(callback, buildGetRequest(url, tag));
        }

        private Request buildGetRequest(String url, Object tag) {
            return new Request.Builder().url(url)
                    .get()
                    .header("User-Agent", User_Agent)
                    .tag(tag == null ? REQ_TAG() : tag)
                    .build();
        }
    }

    // ====================PostProxy======================= //
    private class PostProxy {

        public void post(String url, Map<String, String> params, final IRequestCallback callback, Object tag) {
            deliveryResult(callback, buildPostFormRequest(url, params, tag));
        }

        private Request buildPostFormRequest(String url, Map<String, String> params, Object tag) {
            if (params == null || params.size() == 0) {
                params = new HashMap<>();
            }
            FormEncodingBuilder builder = new FormEncodingBuilder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (param.getKey() != null && param.getValue() != null) {
                    builder.add(param.getKey(), param.getValue());
                }
            }
            RequestBody requestBody = builder.build();
            return new Request.Builder().url(url)
                    .post(requestBody)
                    .header("User-Agent", User_Agent)
                    .tag(tag == null ? REQ_TAG() : tag)
                    .build();
        }
    }

    // ====================DownloadProxy======================= //
    private class DownloadProxy {

        public void download(String url, String filePath, final IRequestCallback callback, Object tag) {
            deliveryFileDownload(callback, filePath, buildDownloadRequest(url, tag));
        }

        private Request buildDownloadRequest(String url, Object tag) {
            return new Request.Builder().url(url)
                    .get()
                    .header("User-Agent", User_Agent)
                    .tag(tag == null ? REQ_TAG() : tag)
                    .build();
        }
    }

    // ====================UploadProxy======================= //
    private class UploadProxy {

        public void upload(String url, Map<String, String> paramMap, Map<String, File> fileMap, IRequestCallback callback, Object tag) {
            deliveryResult(callback, buildUploadRequest(url, paramMap, fileMap, callback, tag));
        }

        private Request buildUploadRequest(String url, Map<String, String> paramMap, Map<String, File> fileMap, final IRequestCallback callback, Object tag) {
            MultipartBuilder mb = new MultipartBuilder().type(MultipartBuilder.FORM);
            if (paramMap != null) {
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        mb.addPart(Headers.of("Content-Disposition", "form-data; name=\"" + entry.getKey() + "\""), RequestBody.create(null, entry.getValue()));
                    }
                }
            }
            if (fileMap != null) {
                RequestBody fileBody;
                for (Map.Entry<String, File> entry : fileMap.entrySet()) {
                    String fileKey = entry.getKey();
                    File file = entry.getValue();
                    if (file == null) {
                        continue;
                    }
                    fileBody = RequestBody.create(MediaType.parse(guessMimeType(file.getName())), file);
                    mb.addPart(Headers.of("Content-Disposition", "form-data; name=\"" + fileKey + "\"; filename=\"" + file.getName() + "\""), fileBody);
                }
            }
            RequestBody requestBody = mb.build();

            ProgressRequestBody progressRequestBody = new ProgressRequestBody(requestBody, new IProgressUpdateListener() {
                @Override
                public void onProgressUpdate(long writedSize, long totalSize, boolean completed) {
                    sendProgress2UICallback(callback, writedSize, totalSize, completed);
                }
            });

            return new Request.Builder().url(url)
                    .post(progressRequestBody)
                    .header("User-Agent", User_Agent)
                    .tag(tag == null ? REQ_TAG() : tag)
                    .build();
        }

    }

    private void deliveryResult(final IRequestCallback callback, Request request) {
        callback.onStart(); // UI thread
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(final Request request, final IOException e) {
                sendFailed2UICallback(e, callback);
            }

            @Override
            public void onResponse(final Response response) {
                String result;
                try {
                    result = response.body().string();
                } catch (Exception e) {
                    sendFailed2UICallback(e, callback);
                    return;
                }
                sendSuccess2UICallback(result, callback);
            }
        });
    }

    private void deliveryFileDownload(final IRequestCallback callback, final String filePath, Request request) {
        callback.onStart(); // UI thread
        try {
            OkHttpClient clone = mOkHttpClient.clone();
            clone.networkInterceptors().add(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Response originalResponse = chain.proceed(chain.request()); //拦截
                    //包装响应体并返回
                    return originalResponse.newBuilder().body(new ProgressResponseBody(originalResponse.body(), new IProgressUpdateListener() {
                        @Override
                        public void onProgressUpdate(long writedSize, long totalSize, boolean completed) {
                            sendProgress2UICallback(callback, writedSize, totalSize, completed);
                        }
                    })).build();
                }
            });
            clone.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    sendFailed2UICallback(e, callback);
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    File file = FileStorageUtil.writeInputStream(response.body().byteStream(), filePath);
                    if (callback.mClassType != File.class) {// 回调不是file 失败
                        sendFailed2UICallback(new RuntimeException("IRequestCallback<T> T msut be File"), callback);
                        return;
                    }
                    sendSuccessFile2UICallback(file, callback);
                }
            });
        } catch (Exception e) {
            sendFailed2UICallback(e, callback);
        }
    }

    /** 更新进度到UI */
    private void sendProgress2UICallback(final IRequestCallback callback, final long writedSize, final long totalSize, final boolean completed) {
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onProgressUpdate(writedSize, totalSize, completed);
            }
        });
    }

    /** 将请求正确结果信息抛给主线程 */
    private void sendSuccessFile2UICallback(final File file, final IRequestCallback requestCallback) {
        mUIHandler.post(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                requestCallback.onSuccess(file);
            }
        });
    }

    /** 将请求出错信息抛给主线程 */
    private void sendFailed2UICallback(final Exception e, final IRequestCallback callback) {
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(e == null ? new RuntimeException("unknown exception.") : e);
            }
        });
    }

    /** 将请求正确结果信息抛给主线程 */
    private void sendSuccess2UICallback(final String result, final IRequestCallback requestCallback) {
        mUIHandler.post(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                requestCallback.onSuccess(result);
            }
        });
    }

    @SuppressWarnings("unused")
    public void cancelTag(final Object tag) {
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                mOkHttpClient.cancel(tag);
            }
        });
    }

    public interface IProgressUpdateListener {
        void onProgressUpdate(long writedSize, long totalSize, boolean completed);
    }

    private String guessMimeType(String path) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String contentTypeFor = fileNameMap.getContentTypeFor(path);
        if (contentTypeFor == null) {
            contentTypeFor = "application/octet-stream";
        }
        return contentTypeFor;
    }

    /** 生成唯一的TAG */
    private long REQ_TAG() {
        return mDefaultRequestTag.getAndIncrement();
    }

    // ====================HttpsProxy======================= //
    @SuppressWarnings("unused")
    private class HttpsProxy {
        public void setCertificates(InputStream... certificates) {
            setCertificates(certificates, null, null);
        }

        public void setCertificates(InputStream[] certificates, InputStream bksFile, String password) {
            try {
                TrustManager[] trustManagers = prepareTrustManager(certificates);
                KeyManager[] keyManagers = prepareKeyManager(bksFile, password);
                SSLContext sslContext = SSLContext.getInstance("TLS");

                sslContext.init(keyManagers,
                        new TrustManager[]{new MyTrustManager(chooseTrustManager(trustManagers))},
                        new SecureRandom());
                mOkHttpClient.setSslSocketFactory(sslContext.getSocketFactory());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public TrustManager[] prepareTrustManager(InputStream... certificates) {
            if (certificates == null || certificates.length <= 0)
                return null;
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null);
                int index = 0;
                for (InputStream certificate : certificates) {
                    String certificateAlias = Integer.toString(index++);
                    keyStore.setCertificateEntry(certificateAlias, certificateFactory.generateCertificate(certificate));
                    try {
                        if (certificate != null)
                            certificate.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                TrustManagerFactory trustManagerFactory;
                trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);

                return trustManagerFactory.getTrustManagers();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public KeyManager[] prepareKeyManager(InputStream bksFile, String password) {
            try {
                if (bksFile == null || password == null)
                    return null;

                KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");//not use BKS because server use PKCS12
                clientKeyStore.load(bksFile, password.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(clientKeyStore, password.toCharArray());

                return keyManagerFactory.getKeyManagers();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private X509TrustManager chooseTrustManager(TrustManager[] trustManagers) {
            for (TrustManager trustManager : trustManagers) {
                if (trustManager instanceof X509TrustManager) {
                    return (X509TrustManager) trustManager;
                }
            }
            return null;
        }

        public class MyTrustManager implements X509TrustManager {
            private X509TrustManager defaultTrustManager;
            private X509TrustManager localTrustManager;

            public MyTrustManager(X509TrustManager localTrustManager) throws NoSuchAlgorithmException,
                    KeyStoreException {
                TrustManagerFactory var4 = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                var4.init((KeyStore) null);
                defaultTrustManager = chooseTrustManager(var4.getTrustManagers());
                this.localTrustManager = localTrustManager;
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    defaultTrustManager.checkServerTrusted(chain, authType);
                } catch (CertificateException ce) {
                    localTrustManager.checkServerTrusted(chain, authType);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }
    }

    /** 初始化信任证书的方式，暂时使用全部信任所有证书 */
    private void initFreeAllAccess() {
        SSLContext sc;
        try {
            sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new MyTrustAllManager()}, new SecureRandom());
            mOkHttpClient.setSslSocketFactory(sc.getSocketFactory());
            mOkHttpClient.setHostnameVerifier(new MyHostnameVerifier());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 用于信任所有证书的校验，直接通过 */
    private class MyHostnameVerifier implements HostnameVerifier {
        @SuppressWarnings("FieldCanBeLocal")
        private final boolean verifyHostnameVerifierOk = true;

        @SuppressLint("BadHostnameVerifier")
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return verifyHostnameVerifierOk;
        }
    }

    @SuppressLint("TrustAllX509TrustManager")
    private class MyTrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
