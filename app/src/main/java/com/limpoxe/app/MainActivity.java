package com.limpoxe.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.limpoxe.zipsplitter.ZipSpliter;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClick(View view) {
        String zipFile = getApplicationInfo().sourceDir;
        String fileInZip = "classes.dex";
        String targetZipFile = getApplicationInfo().dataDir + "/classes.dex.zip";
        File dexZip = ZipSpliter.split(zipFile, fileInZip, targetZipFile);

        boolean isValid = dexZip != null && checkValidate(targetZipFile);

        if (isValid) {
            Toast.makeText(this, "splite success, " + targetZipFile, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "splite fail, check logcat", Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkValidate(String zipFilePath) {
        try {
            ZipFile zipFile = new ZipFile(zipFilePath);
            if (zipFile.size() == 1) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
