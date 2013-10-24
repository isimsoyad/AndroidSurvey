package org.adaptlab.chpir.android.activerecordcloudsync;

import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import com.activeandroid.query.Select;

import android.os.Looper;
import android.util.Log;

public class HttpPushr {
    private static final String TAG = "HttpPushr";
    private Class<? extends SendModel> mSendTableClass;
    private String mRemoteTableName;
    
    public HttpPushr(String remoteTableName, Class<? extends SendModel> sendTableClass) {
        mSendTableClass = sendTableClass;
        mRemoteTableName = remoteTableName;
    }
    
    public void push() {
        if (ActiveRecordCloudSync.getEndPoint() == null) {
            Log.i(TAG, "ActiveRecordCloudSync end point is not set!");
            return;
        }
        
        Thread t = new Thread() {
           public void run() {
                Looper.prepare(); //For Preparing Message Pool for the child Thread
                HttpClient client = new DefaultHttpClient();
                HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); //Timeout Limit
                HttpResponse response;
                
                List<? extends SendModel> allElements =
                        new Select().from(mSendTableClass).orderBy("Id ASC").execute();
                
                for (SendModel element : allElements) {
                    if (!element.isSent()) {
                        try {
                            HttpPost post = new HttpPost(ActiveRecordCloudSync.getEndPoint() + mRemoteTableName);
                            StringEntity se = new StringEntity(element.toJSON().toString());  
                            se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                            post.setEntity(se);
                            Log.i(TAG, "Sending post request: " + element.toJSON().toString());
                            response = client.execute(post);
    
                            /*Checking response */
                            if(response!=null){
                                InputStream in = response.getEntity().getContent(); //Get the data in the entity
                                element.setAsSent();
                                element.save();
                                Log.i(TAG, in.toString());
                            }
    
                        } catch(Exception e) {
                            Log.e(TAG, "Cannot establish connection", e);
                        }
    
                        Looper.loop(); //Loop in the message queue
                        }
                    }
                }

                
            };

            t.start();      
    }
}