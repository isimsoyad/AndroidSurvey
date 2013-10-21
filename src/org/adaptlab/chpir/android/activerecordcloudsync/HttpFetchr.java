package org.adaptlab.chpir.android.activerecordcloudsync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

public class HttpFetchr {
    private static final String TAG = "HttpFetchr";
    private Class<? extends ReceiveTable> mReceiveTableClass;
    private String mRemoteTableName;
    
    public HttpFetchr(String remoteTableName, Class<? extends ReceiveTable> receiveTableClass) {
        mReceiveTableClass = receiveTableClass;
        mRemoteTableName = remoteTableName;
    }
    
    public String getUrl(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }
    
    public void fetch() {
        if (ActiveRecordCloudSync.getEndPoint() == null) {
            Log.i(TAG, "ActiveRecordCloudSync end point is not set!");
            return;
        }
        
        try {
            String url = ActiveRecordCloudSync.getEndPoint() + mRemoteTableName;
            Log.i(TAG, "Attempting to access " + url);
            String jsonString = getUrl(url);
            Log.i(TAG, "Got JSON String: " + jsonString);
            JSONArray jsonArray = new JSONArray(jsonString);
            Log.i(TAG, "Received json result: " + jsonArray);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                ReceiveTable tableInstance = mReceiveTableClass.newInstance();
                Log.i(TAG, "Creating object from JSON Object: " + jsonArray.getJSONObject(i));
                tableInstance.createObjectFromJSON(jsonArray.getJSONObject(i));
            }
            
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to fetch items", ioe);
        } catch (JSONException je) {
            Log.e(TAG, "Failed to parse items", je);            
        } catch (InstantiationException ie) {
            Log.e(TAG, "Failed to instantiate receive table", ie);
        } catch (IllegalAccessException iae) {
            Log.e(TAG, "Failed to access receive table", iae);
        }
    }
    
    private byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();
            
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            
            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}