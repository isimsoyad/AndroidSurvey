package org.adaptlab.chpir.android.survey;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.query.Delete;
import com.crashlytics.android.Crashlytics;

import org.adaptlab.chpir.android.activerecordcloudsync.ActiveRecordCloudSync;
import org.adaptlab.chpir.android.activerecordcloudsync.PollService;
import org.adaptlab.chpir.android.survey.models.AdminSettings;
import org.adaptlab.chpir.android.survey.models.DefaultAdminSettings;
import org.adaptlab.chpir.android.survey.models.DeviceSyncEntry;
import org.adaptlab.chpir.android.survey.models.DeviceUser;
import org.adaptlab.chpir.android.survey.models.EventLog;
import org.adaptlab.chpir.android.survey.models.Grid;
import org.adaptlab.chpir.android.survey.models.GridLabel;
import org.adaptlab.chpir.android.survey.models.Image;
import org.adaptlab.chpir.android.survey.models.Instrument;
import org.adaptlab.chpir.android.survey.models.InstrumentTranslation;
import org.adaptlab.chpir.android.survey.models.Option;
import org.adaptlab.chpir.android.survey.models.OptionTranslation;
import org.adaptlab.chpir.android.survey.models.Question;
import org.adaptlab.chpir.android.survey.models.QuestionTranslation;
import org.adaptlab.chpir.android.survey.models.Response;
import org.adaptlab.chpir.android.survey.models.ResponsePhoto;
import org.adaptlab.chpir.android.survey.models.Roster;
import org.adaptlab.chpir.android.survey.models.Rule;
import org.adaptlab.chpir.android.survey.models.Section;
import org.adaptlab.chpir.android.survey.models.SectionTranslation;
import org.adaptlab.chpir.android.survey.models.Skip;
import org.adaptlab.chpir.android.survey.models.Survey;
import org.adaptlab.chpir.android.survey.vendor.BCrypt;

import java.util.List;
import java.util.UUID;

import io.fabric.sdk.android.Fabric;

public class AppUtil {
    private final static String TAG = "AppUtil";
    public final static boolean PRODUCTION = !BuildConfig.DEBUG;
    public final static boolean REQUIRE_SECURITY_CHECKS = PRODUCTION;
    public static boolean DEBUG = !PRODUCTION;

    public static String ADMIN_PASSWORD_HASH;
    public static String ACCESS_TOKEN;
    private static Context mContext;
    private static AdminSettings adminSettingsInstance;

    /*
     * Get the version code from the AndroidManifest
     */
    public static int getVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName
                    (), 0);
            return pInfo.versionCode;
        } catch (NameNotFoundException nnfe) {
            Log.e(TAG, "Error finding version code: " + nnfe);
        }
        return -1;
    }

    public static String getVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName
                    (), 0);
            return pInfo.versionName;
        } catch (NameNotFoundException nnfe) {
            Log.e(TAG, "Error finding version code: " + nnfe);
        }
        return "";
    }

    public static final void appInit(Context context) {
        mContext = context;
        if (AppUtil.REQUIRE_SECURITY_CHECKS) {
            if (!AppUtil.runDeviceSecurityChecks(context)) {
                // Device has failed security checks
                return;
            }
        }

        setAdminSettingsInstance();

        ADMIN_PASSWORD_HASH = context.getResources().getString(R.string.admin_password_hash);
        ACCESS_TOKEN = adminSettingsInstance.getApiKey();

        if (PRODUCTION) {
            Fabric.with(context, new Crashlytics());
            Crashlytics.setUserIdentifier(adminSettingsInstance.getDeviceIdentifier());
            Crashlytics.setString(mContext.getString(R.string.crashlytics_device_label),
                    adminSettingsInstance.getDeviceLabel());
        }

        DatabaseSeed.seed(context);

        if (adminSettingsInstance.getDeviceIdentifier() == null) {
            adminSettingsInstance.setDeviceIdentifier(UUID.randomUUID().toString());
        }

        if (adminSettingsInstance.getDeviceLabel() == null) {
            adminSettingsInstance.setDeviceLabel("");
        }

        ActiveRecordCloudSync.setAccessToken(ACCESS_TOKEN);
        ActiveRecordCloudSync.setVersionCode(AppUtil.getVersionCode(context));
        ActiveRecordCloudSync.setEndPoint(adminSettingsInstance.getApiUrl());
        ActiveRecordCloudSync.addReceiveTable("instruments", Instrument.class);
        ActiveRecordCloudSync.addReceiveTable("sections", Section.class);
        ActiveRecordCloudSync.addReceiveTable("grids", Grid.class);
        ActiveRecordCloudSync.addReceiveTable("questions", Question.class);
        ActiveRecordCloudSync.addReceiveTable("options", Option.class);
        ActiveRecordCloudSync.addReceiveTable("grid_labels", GridLabel.class);
        ActiveRecordCloudSync.addReceiveTable("images", Image.class);
        ActiveRecordCloudSync.addReceiveTable("device_users", DeviceUser.class);
        ActiveRecordCloudSync.addReceiveTable("skips", Skip.class);
        ActiveRecordCloudSync.addReceiveTable("rules", Rule.class);
        ActiveRecordCloudSync.addSendTable("surveys", Survey.class);
        ActiveRecordCloudSync.addSendTable("responses", Response.class);
        ActiveRecordCloudSync.addSendTable("response_images", ResponsePhoto.class);
        ActiveRecordCloudSync.addSendTable("device_sync_entries", DeviceSyncEntry.class);
        ActiveRecordCloudSync.addSendTable("rosters", Roster.class);

        PollService.setServiceAlarm(context.getApplicationContext(), true);
    }

    public static void authorize() {
        if (AppUtil.getAdminSettingsInstance() != null && AppUtil.getAdminSettingsInstance()
                .getRequirePassword() && !AuthUtils.isSignedIn()) {
            Intent i = new Intent(getContext(), LoginActivity.class);
            getContext().startActivity(i);
        }
    }

    private static void setAdminSettingsInstance() {
        if (mContext != null) {
            if (mContext.getResources().getBoolean(R.bool.default_admin_settings)) {
                adminSettingsInstance = DefaultAdminSettings.getInstance();
            } else {
                adminSettingsInstance = AdminSettings.getInstance();
            }
        }
    }

    /*
     * Security checks that must pass for the application to start.
     * 
     * If the application fails any security checks, display
     * AlertDialog indicating why and immediately stop execution
     * of the application.
     * 
     * Current security checks: require encryption
     */
    public static final boolean runDeviceSecurityChecks(Context context) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context
                .getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (devicePolicyManager.getStorageEncryptionStatus() != DevicePolicyManager
                .ENCRYPTION_STATUS_ACTIVE) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.encryption_required_title)
                    .setMessage(R.string.encryption_required_text)
                    .setCancelable(false)
                    .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Kill app on OK
                            int pid = android.os.Process.myPid();
                            android.os.Process.killProcess(pid);
                        }
                    })
                    .show();
            return false;
        }
        return true;
    }


    /*
     * Hash the entered password and compare it with admin password hash
     */
    public static boolean checkAdminPassword(String password) {
        return BCrypt.checkpw(password, ADMIN_PASSWORD_HASH);
    }

    public static Context getContext() {
        return mContext;
    }

    public static void setContext(Context context) {
        mContext = context;
    }

    public static AdminSettings getAdminSettingsInstance() {
        if (adminSettingsInstance == null) {
            setAdminSettingsInstance();
        }
        return adminSettingsInstance;
    }

    public static String getOsBuildNumber() {
        return Build.DISPLAY;
    }

    public static void deleteApplicationData() {
        ActiveAndroid.beginTransaction();
        try {
            AppUtil.getAdminSettingsInstance().resetLastSyncTime();
            new Delete().from(ResponsePhoto.class).execute();
            new Delete().from(Response.class).execute();
            new Delete().from(Survey.class).execute();
            new Delete().from(Roster.class).execute();
            new Delete().from(Rule.class).execute();
            new Delete().from(Skip.class).execute();
            new Delete().from(DeviceUser.class).execute();
            new Delete().from(Image.class).execute();
            new Delete().from(GridLabel.class).execute();
            new Delete().from(Grid.class).execute();
            new Delete().from(InstrumentTranslation.class).execute();
            new Delete().from(OptionTranslation.class).execute();
            new Delete().from(QuestionTranslation.class).execute();
            new Delete().from(SectionTranslation.class).execute();
            new Delete().from(Option.class).execute();
            new Delete().from(Question.class).execute();
            new Delete().from(Section.class).execute();
            new Delete().from(EventLog.class).execute();
            new Delete().from(Instrument.class).execute();
            ActiveAndroid.setTransactionSuccessful();
        } finally {
            ActiveAndroid.endTransaction();
        }
    }

    public static void orderInstrumentsSections() {
        new OrderInstrumentSectionsTask().execute();
    }

    private static class OrderInstrumentSectionsTask extends AsyncTask<Void, Void, Void> {

        public OrderInstrumentSectionsTask() {
        }

        @Override
        protected Void doInBackground(Void... params) {
            List<Instrument> instruments = Instrument.getAllProjectInstruments(
                    Long.valueOf(adminSettingsInstance.getProjectId()));
            for (Instrument instrument : instruments) {
                instrument.orderSections();
            }
            return null;
        }
    }
}