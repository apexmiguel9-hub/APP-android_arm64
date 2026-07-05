package com.epai.oblender;

import android.Manifest;
import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.epai.oblfiles.InstallOBLFiles;

public class StartupActivity extends AppCompatActivity {
    private String stringHomePath="";
    private String stringConfigPath="";
    private final int mIntRequrestID=1000;
    private final int mIntRequrestIDInternet=1001;
    private final int mIntTimerDelay=1500;
    private enum MSG_ID{
        MSG_ID_PERMISSION,
        MSG_ID_INTERNETPERMISSION,
        MSG_ID_COPYFILE,
        MSG_ID_STARTACTIVITY
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScreenUtils.fullScreen(getWindow());

        setContentView(R.layout.activity_startup);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage(MSG_ID.MSG_ID_PERMISSION.ordinal());
            }
        }, mIntTimerDelay);
    }

    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_ID.MSG_ID_PERMISSION.ordinal()) {
                //  Request external file read permission
                if (!checkWriteExternalFilePermission()) {
                    //  Request external file read permission
                    showAskExternalFileWritePermissionDlg();
                } else {
                    mHandler.sendEmptyMessage(MSG_ID.MSG_ID_INTERNETPERMISSION.ordinal());
                }
            } else if(msg.what==MSG_ID.MSG_ID_INTERNETPERMISSION.ordinal()){
                if (ContextCompat.checkSelfPermission(StartupActivity.this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(StartupActivity.this, new String[]{Manifest.permission.INTERNET}, mIntRequrestIDInternet);
                } else {
                    mHandler.sendEmptyMessage(MSG_ID.MSG_ID_COPYFILE.ordinal());
                }
            }else if (msg.what == MSG_ID.MSG_ID_COPYFILE.ordinal()) {
                //  Copy files
                Intent currentitent = getIntent();
                String action = currentitent.getAction();
                if (!Intent.ACTION_SEND.equals(action)) {
                    InstallOBLFiles installOBLFiles = new InstallOBLFiles();
                    InstallOBLFiles.OBLFilePath oblFilePath=installOBLFiles.installOBLFiles(StartupActivity.this);
                    stringHomePath=oblFilePath.mStringHomePath;
                    stringConfigPath=oblFilePath.mStringConfigPath;
                }
                mHandler.sendEmptyMessage(MSG_ID.MSG_ID_STARTACTIVITY.ordinal());
            } else if (msg.what == MSG_ID.MSG_ID_STARTACTIVITY.ordinal()) {
                //  Open modeling activity
                Intent intent = new Intent(StartupActivity.this, OBLNativeActivity.class);
                intent.putExtra("HomePath",stringHomePath);
                intent.putExtra("ConfigPath",stringConfigPath);
                startActivity(intent);
                StartupActivity.this.finish();
            }
            return false;
        }
    });

    //  Check external file write permission
    private boolean checkWriteExternalFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //  First check if permission is granted
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED;
        }
    }

    //  Prompt to obtain external write permission
    private void showAskExternalFileWritePermissionDlg() {
        AlertDialog.Builder normalDialog = new AlertDialog.Builder(this);
        normalDialog.setTitle("Storage Permission Required");
        normalDialog.setMessage("This app needs storage access to read and save files.");
        normalDialog.setPositiveButton("Allow",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //  Allow external write permission
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + StartupActivity.this.getPackageName()));
                            startActivityForResult(intent, mIntRequrestID);
                        } else {
                            String[] stringsPermission = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
                            ActivityCompat.requestPermissions(StartupActivity.this, stringsPermission, mIntRequrestID);
                        }
                    }
                });
        normalDialog.setNegativeButton("Deny",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //  Deny external file permission
                        Toast.makeText(StartupActivity.this,
                                "Storage permission denied. Cannot save files.",
                                Toast.LENGTH_LONG).show();
                        mHandler.sendEmptyMessage(MSG_ID.MSG_ID_INTERNETPERMISSION.ordinal());
                    }
                });
        AlertDialog dlg = normalDialog.create();
        dlg.show();
        Button button = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        button.setTextColor(Color.BLUE);
    }

    //  Permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mIntRequrestID == requestCode) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage(MSG_ID.MSG_ID_INTERNETPERMISSION.ordinal());
                }
            },mIntTimerDelay);
        }else if (mIntRequrestIDInternet==requestCode){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage(MSG_ID.MSG_ID_COPYFILE.ordinal());
                }
            },mIntTimerDelay);
        }
    }

    //  Force exit app
    private void exitApp() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mIntRequrestID == requestCode) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage(MSG_ID.MSG_ID_INTERNETPERMISSION.ordinal());
                }
            },mIntTimerDelay);
        }
    }
}